package kr.ac.inha.nsl

import android.annotation.SuppressLint
import android.app.*
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.graphics.Color
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Binder
import android.os.Build
import android.os.IBinder
import android.os.SystemClock
import android.util.Log
import android.util.SparseIntArray
import com.google.android.gms.location.ActivityRecognition
import com.google.android.gms.location.ActivityRecognitionClient
import inha.nsl.easytrack.ETServiceGrpc
import inha.nsl.easytrack.EtService.SubmitDataRecordRequestMessage
import inha.nsl.easytrack.EtService.SubmitHeartbeatRequestMessage
import io.grpc.ManagedChannelBuilder
import io.grpc.StatusRuntimeException
import org.json.JSONException
import org.json.JSONObject
import java.util.*

class DataCollectorService : Service() {
    private val mBinder: IBinder = LocalBinder()
    private lateinit var sensorManager: SensorManager
    private lateinit var dataSourceNameToSensorIdMap: HashMap<String, Int>
    private lateinit var sensorId2DataSourceNameMap: SparseIntArray
    private lateinit var sensorEventListeners: ArrayList<MySensorEventListener>
    private lateinit var usageStatsManager: UsageStatsManager
    private lateinit var activityRecognitionClient: ActivityRecognitionClient
    private lateinit var activityRecognitionPendingIntent: PendingIntent
    private var activityRecognitionRunning = false
    private var highestOrderCumulantsRunning = false
    private var runThreads = true


    override fun onBind(intent: Intent): IBinder {
        return mBinder
    }

    override fun onCreate() {
        Log.e(MainActivity.TAG, "DataCollectorService.onCreate()")

        // region setup sensor data sources
        sensorManager = getSystemService(Context.SENSOR_SERVICE) as SensorManager
        sensorEventListeners = arrayListOf()
        sensorId2DataSourceNameMap = SparseIntArray()
        @Suppress("deprecation")
        dataSourceNameToSensorIdMap = hashMapOf(
                "ANDROID_ACCELEROMETER" to Sensor.TYPE_ACCELEROMETER,
                "ANDROID_AMBIENT_TEMPERATURE" to Sensor.TYPE_AMBIENT_TEMPERATURE,
                "ANDROID_GRAVITY" to Sensor.TYPE_GRAVITY,
                "ANDROID_GYROSCOPE" to Sensor.TYPE_GYROSCOPE,
                "ANDROID_LIGHT" to Sensor.TYPE_LIGHT,
                "ANDROID_LINEAR_ACCELERATION" to Sensor.TYPE_LINEAR_ACCELERATION,
                "ANDROID_MAGNETIC_FIELD" to Sensor.TYPE_MAGNETIC_FIELD,
                "ANDROID_ORIENTATION" to Sensor.TYPE_ORIENTATION,
                "ANDROID_PRESSURE" to Sensor.TYPE_PRESSURE,
                "ANDROID_PROXIMITY" to Sensor.TYPE_PROXIMITY,
                "ANDROID_RELATIVE_HUMIDITY" to Sensor.TYPE_RELATIVE_HUMIDITY,
                "ANDROID_ROTATION_VECTOR" to Sensor.TYPE_ROTATION_VECTOR,
                "ANDROID_TEMPERATURE" to Sensor.TYPE_TEMPERATURE
        )
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR2) {
            dataSourceNameToSensorIdMap["ANDROID_GAME_ROTATION_VECTOR"] = Sensor.TYPE_GAME_ROTATION_VECTOR
            dataSourceNameToSensorIdMap["ANDROID_SIGNIFICANT_MOTION"] = Sensor.TYPE_SIGNIFICANT_MOTION
            dataSourceNameToSensorIdMap["ANDROID_GYROSCOPE_UNCALIBRATED"] = Sensor.TYPE_GYROSCOPE_UNCALIBRATED
            dataSourceNameToSensorIdMap["ANDROID_MAGNETIC_FIELD_UNCALIBRATED"] = Sensor.TYPE_MAGNETIC_FIELD_UNCALIBRATED
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
                dataSourceNameToSensorIdMap["ANDROID_STEP_COUNTER"] = Sensor.TYPE_STEP_COUNTER
                dataSourceNameToSensorIdMap["ANDROID_GEOMAGNETIC_ROTATION_VECTOR"] = Sensor.TYPE_GEOMAGNETIC_ROTATION_VECTOR
                dataSourceNameToSensorIdMap["ANDROID_STEP_DETECTOR"] = Sensor.TYPE_STEP_DETECTOR
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT_WATCH) {
                    dataSourceNameToSensorIdMap["ANDROID_HEART_RATE"] = Sensor.TYPE_HEART_RATE
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                        dataSourceNameToSensorIdMap["ANDROID_MOTION_DETECTION"] = Sensor.TYPE_MOTION_DETECT
                        dataSourceNameToSensorIdMap["ANDROID_POSE_6DOF"] = Sensor.TYPE_POSE_6DOF
                        dataSourceNameToSensorIdMap["ANDROID_HEART_BEAT"] = Sensor.TYPE_HEART_BEAT
                        dataSourceNameToSensorIdMap["ANDROID_STATIONARY_DETECT"] = Sensor.TYPE_STATIONARY_DETECT
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                            dataSourceNameToSensorIdMap["ANDROID_LOW_LATENCY_OFFBODY_DETECT"] = Sensor.TYPE_LOW_LATENCY_OFFBODY_DETECT
                            dataSourceNameToSensorIdMap["ANDROID_ACCELEROMETER_UNCALIBRATED"] = Sensor.TYPE_ACCELEROMETER_UNCALIBRATED
                        }
                    }
                }
            }
        }
        // endregion

        // region setup other data sources (i.e., gps, activity recognition, etc)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP_MR1)
            usageStatsManager = getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
        activityRecognitionClient = ActivityRecognition.getClient(applicationContext)
        activityRecognitionPendingIntent = PendingIntent.getService(applicationContext, 2, Intent(applicationContext, ETActivityRecognitionIntentService::class.java), PendingIntent.FLAG_UPDATE_CURRENT)
        // endregion

        // region setup foreground service (notification)
        val notificationId = 98765
        val notificationIntent = Intent(this, AuthenticationActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(this, 0, notificationIntent, 0)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val notificationChannelId = "kr.ac.inha.nsl.DataCollectorService"
            val notificationChannelName = "EasyTrack Data Collection Service"
            val notificationChannel = NotificationChannel(notificationChannelId, notificationChannelName, NotificationManager.IMPORTANCE_DEFAULT)
            notificationChannel.lightColor = Color.BLUE
            notificationChannel.lockscreenVisibility = Notification.VISIBILITY_PUBLIC
            val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(notificationChannel)
            val notification = Notification.Builder(this, notificationChannelId)
                    .setContentTitle("EasyTrack")
                    .setContentText("Data Collection service is running now...")
                    .setSmallIcon(R.mipmap.ic_launcher)
                    .setContentIntent(pendingIntent)
                    .build()
            startForeground(notificationId, notification)
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN) {
            @Suppress("deprecation")
            val notification = Notification.Builder(this)
                    .setContentTitle("EasyTrack")
                    .setContentText("Data Collection service is running now...")
                    .setSmallIcon(R.mipmap.ic_launcher)
                    .setContentIntent(pendingIntent)
                    .build()
            startForeground(notificationId, notification)
        }
        // endregion

        super.onCreate()
    }

    override fun onDestroy() {
        Log.e(MainActivity.TAG, "DataCollectorService.onDestroy()")

        runThreads = false
        if (activityRecognitionRunning)
            activityRecognitionClient.removeActivityUpdates(activityRecognitionPendingIntent)
        stopDataCollection()

        super.onDestroy()
    }

    override fun onStartCommand(intent: Intent, flags: Int, startId: Int): Int {
        Log.e(MainActivity.TAG, "DataCollectorService.onStartCommand()")

        Tools.vibrate(applicationContext)
        try {
            initCampaignDataSources()
            setUpDataSubmissionThread()
            setUpHeartbeatSubmissionThread()
        } catch (e: JSONException) {
            e.printStackTrace()
            stopSelf()
        }
        // return super.onStartCommand(intent, flags, startId);
        return START_STICKY
    }

    private fun stopDataCollection() {
        for (sensorEventListener in sensorEventListeners) sensorManager.unregisterListener(sensorEventListener)
    }

    @Throws(JSONException::class)
    private fun initCampaignDataSources() {
        val prefs = getSharedPreferences(packageName, Context.MODE_PRIVATE)
        val dataSourceNames = prefs.getString("dataSourceNames", null)
        if (dataSourceNames != null) for (dataSourceName in dataSourceNames.split(",").toTypedArray()) {
            val json = prefs.getString("config_json_$dataSourceName", null) ?: "{}"
            setUpDataSource(dataSourceName, json, prefs)
        }
    }

    @Throws(JSONException::class)
    private fun setUpDataSource(dataSourceName: String, configJson: String, prefs: SharedPreferences) {
        val json = JSONObject(configJson)
        if (dataSourceNameToSensorIdMap.containsKey(dataSourceName)) {
            // case: device sensors (i.e., Accelerometer, Light, etc.)
            val sensorId = dataSourceNameToSensorIdMap[dataSourceName] ?: -1
            assert(sensorId != -1)
            val sensor = sensorManager.getDefaultSensor(sensorId)
            var delay = -1
            if (json.has("delay_ms") && Tools.isNumber(json.getString("delay_ms")))
                delay = json.getString("delay_ms").toInt()
            val sensorEventListener = MySensorEventListener(delay)
            if (delay == -1)
                sensorManager.registerListener(sensorEventListener, sensor, SensorManager.SENSOR_DELAY_NORMAL)
            else
                sensorManager.registerListener(sensorEventListener, sensor, SensorManager.SENSOR_DELAY_FASTEST)
            sensorEventListeners.add(sensorEventListener)
            sensorId2DataSourceNameMap.put(sensorId, prefs.getInt(dataSourceName, -1))
        } else {
            // case: GPS, Activity Recognition, App Usage, Survey, etc.
            when (dataSourceName) {
                "APPLICATION_USAGE" -> {
                    var delay = 1000L // 1 second by default
                    if (json.has("delay_ms") && Tools.isNumber(json.getString("delay_ms")))
                        delay = json.getString("delay_ms").toLong()
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP)
                        Thread {
                            var lastCheckTsMs = 0L.coerceAtLeast(prefs.getLong("lastAppUseTsMs", -1))
                            while (runThreads) {
                                try {
                                    Thread.sleep(delay)
                                } catch (e: InterruptedException) {
                                    e.printStackTrace()
                                }
                                val nowTsMs = System.currentTimeMillis()

                                val usageStatsMap = usageStatsManager.queryUsageStats(UsageStatsManager.INTERVAL_BEST, lastCheckTsMs, nowTsMs)
                                for (usage in usageStatsMap)
                                    if (usage.totalTimeInForeground > 0)
                                        AppUseDb.saveAppUsageStat(usage.packageName, usage.lastTimeUsed, usage.totalTimeInForeground)
                                lastCheckTsMs = nowTsMs
                                val editor = prefs.edit()
                                editor.putLong("lastAppUseTsMs", lastCheckTsMs)
                                editor.apply()
                            }
                        }.start()
                }
                "ACTIVITY_RECOGNITION" -> {
                    activityRecognitionRunning = true
                    var interval = 60000L // 1 minute by default
                    if (json.has("delay_ms") && Tools.isNumber(json.getString("delay_ms")))
                        interval = json.getString("delay_ms").toLong()
                    ETActivityRecognitionIntentService.dataSourceId = getSharedPreferences(packageName, Context.MODE_PRIVATE).getInt("ACTIVITY_RECOGNITION", -1)
                    activityRecognitionClient.requestActivityUpdates(16000L.coerceAtLeast(interval), activityRecognitionPendingIntent)
                }
                "HIGHEST_ORDER_CUMULANTS" -> {
                    highestOrderCumulantsRunning = true
                    var delay = 2000L // 1 minute by default
                    if (json.has("delay_ms") && Tools.isNumber(json.getString("delay_ms")))
                        delay = 2000L.coerceAtLeast(json.getString("delay_ms").toLong())
                    Thread {
                        var lastCheckTsMs = 0L.coerceAtLeast(prefs.getLong("lastHOCTsMs", -1))
                        while (runThreads) {
                            try {
                                Thread.sleep(delay)
                            } catch (e: InterruptedException) {
                                e.printStackTrace()
                            }
                            val nowTsMs = System.currentTimeMillis()

                            // TODO: calculate HOC for the last datasets for timespan [lastCheckTsMs : nowTsMs]

                            lastCheckTsMs = nowTsMs
                            val editor = prefs.edit()
                            editor.putLong("lastHOCTsMs", lastCheckTsMs)
                            editor.apply()
                        }
                    }.start()
                }
                else -> Log.e(MainActivity.TAG, "Unrecognized data source: $dataSourceName")
            }
        }
    }

    private fun setUpDataSubmissionThread() {
        Thread {
            while (runThreads) {
                if (Tools.isNetworkAvailable) {
                    val cursor = DbMgr.sensorData
                    if (cursor.moveToFirst()) {
                        val channel = ManagedChannelBuilder.forAddress(
                                getString(R.string.grpc_host), getString(R.string.grpc_port).toInt()).usePlaintext().build()
                        val stub = ETServiceGrpc.newBlockingStub(channel)
                        val prefs = getSharedPreferences(packageName, Context.MODE_PRIVATE)
                        val userId = prefs.getInt("userId", 1)
                        val email = prefs.getString("email", "nslabinha@gmail.com")
                        try {
                            do {
                                val submitDataRecordRequestMessage = SubmitDataRecordRequestMessage.newBuilder()
                                        .setUserId(userId)
                                        .setEmail(email)
                                        .setDataSource(cursor.getInt(1))
                                        .setTimestamp(cursor.getLong(2))
                                        .setValues(cursor.getString(4))
                                        .build()
                                val responseMessage = stub.submitDataRecord(submitDataRecordRequestMessage)
                                if (responseMessage.doneSuccessfully) DbMgr.deleteRecord(cursor.getInt(0))
                            } while (cursor.moveToNext())
                        } catch (e: StatusRuntimeException) {
                            Log.e(MainActivity.TAG, "DataCollectorService.setUpDataSubmissionThread() exception: " + e.message)
                            e.printStackTrace()
                        } finally {
                            channel.shutdown()
                        }
                    }
                    cursor.close()
                }

                try {
                    Thread.sleep(1000)
                } catch (e: InterruptedException) {
                    e.printStackTrace()
                }
            }
        }.start()
    }

    @SuppressLint("CheckResult")
    private fun setUpHeartbeatSubmissionThread() {
        Thread {
            while (runThreads) {
                val channel = ManagedChannelBuilder.forAddress(
                        getString(R.string.grpc_host), getString(R.string.grpc_port).toInt()).usePlaintext().build()
                val prefs = getSharedPreferences(packageName, Context.MODE_PRIVATE)
                val stub = ETServiceGrpc.newBlockingStub(channel)
                val submitHeartbeatRequestMessage = SubmitHeartbeatRequestMessage.newBuilder()
                        .setUserId(prefs.getInt("userId", 1))
                        .setEmail(prefs.getString("email", "nslabinha@gmail.com"))
                        .build()
                try {
                    stub.submitHeartbeat(submitHeartbeatRequestMessage)
                } catch (e: StatusRuntimeException) {
                    Log.e(MainActivity.TAG, "DataCollectorService.setUpHeartbeatSubmissionThread() exception: " + e.message)
                    e.printStackTrace()
                } finally {
                    channel.shutdown()
                }
                try {
                    Thread.sleep(5000)
                } catch (e: InterruptedException) {
                    e.printStackTrace()
                }
            }
        }.start()
    }


    private inner class LocalBinder : Binder() {
        @Suppress("unused")
        val getService: DataCollectorService
            get() = this@DataCollectorService
    }

    private inner class MySensorEventListener internal constructor(private val delay: Int) : SensorEventListener {
        private var nextTimestamp: Long = 0
        private var defaultDelay = false
        override fun onSensorChanged(event: SensorEvent) {
            val dataSourceId = sensorId2DataSourceNameMap[event.sensor.type]
            val timestamp = System.currentTimeMillis() + (event.timestamp - System.nanoTime()) / 1000000L
            if (defaultDelay) handleSensorChangedEvent(dataSourceId, timestamp, event) else {
                if (event.timestamp < nextTimestamp) return
                nextTimestamp = event.timestamp + delay * 1000000
                handleSensorChangedEvent(dataSourceId, timestamp, event)
            }
        }

        override fun onAccuracyChanged(sensor: Sensor, accuracy: Int) {}
        private fun handleSensorChangedEvent(dataSourceId: Int, timestamp: Long, event: SensorEvent) {
            DbMgr.saveNumericData(dataSourceId, timestamp, event.accuracy.toFloat(), event.values)
        }

        init {
            if (delay < 1) defaultDelay = true
        }
    }
}