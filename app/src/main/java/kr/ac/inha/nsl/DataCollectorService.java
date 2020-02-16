package kr.ac.inha.nsl;

import android.app.AlarmManager;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.graphics.Color;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.os.Binder;
import android.os.Build;
import android.os.IBinder;
import android.os.SystemClock;
import android.util.Log;
import android.util.SparseIntArray;

import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;

import inha.nslab.easytrack.ETServiceGrpc;
import inha.nslab.easytrack.EtService;
import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import io.grpc.StatusRuntimeException;

import static kr.ac.inha.nsl.MainActivity.TAG;

public class DataCollectorService extends Service {
    private final IBinder mBinder = new LocalBinder();
    private SensorManager sensorManager;
    private HashMap<String, Integer> dataSourceNameToSensorMap;
    private SparseIntArray sensorToDataSourceIdMap;
    private List<MySensorEventListener> sensorEventListeners;
    private boolean runThreads = true;

    @Override
    public IBinder onBind(Intent intent) {
        return mBinder;
    }

    @Override
    public void onCreate() {
        Log.e(TAG, "DataCollectorService.onCreate()");

        int notificationId = 98765;
        Intent notificationIntent = new Intent(this, AuthenticationActivity.class);
        PendingIntent pendingIntent = PendingIntent.getActivity(this, 0, notificationIntent, 0);
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            String notificationChannelId = "kr.ac.inha.nsl.DataCollectorService";
            String notificationChannelName = "EasyTrack Data Collection Service";
            NotificationChannel notificationChannel = new NotificationChannel(notificationChannelId, notificationChannelName, NotificationManager.IMPORTANCE_DEFAULT);
            notificationChannel.setLightColor(Color.BLUE);
            notificationChannel.setLockscreenVisibility(Notification.VISIBILITY_PUBLIC);
            NotificationManager notificationManager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
            if (notificationManager != null) {
                notificationManager.createNotificationChannel(notificationChannel);
                Notification notification = new Notification.Builder(this, notificationChannelId)
                        .setContentTitle("EasyTrack")
                        .setContentText("Data Collection service is running now...")
                        .setSmallIcon(R.mipmap.ic_launcher)
                        .setContentIntent(pendingIntent)
                        .build();
                startForeground(notificationId, notification);
            }
        } else if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.JELLY_BEAN) {
            Notification notification = new Notification.Builder(this)
                    .setContentTitle("EasyTrack")
                    .setContentText("Data Collection service is running now...")
                    .setSmallIcon(R.mipmap.ic_launcher)
                    .setContentIntent(pendingIntent)
                    .build();
            startForeground(notificationId, notification);
        }

        dataSourceNameToSensorMap = new HashMap<>();
        sensorToDataSourceIdMap = new SparseIntArray();
        sensorEventListeners = new ArrayList<>();
        sensorManager = (SensorManager) getSystemService(Context.SENSOR_SERVICE);
        initDataSourceNameIdMap();

        super.onCreate();
    }

    @Override
    public void onDestroy() {
        Log.e(TAG, "DataCollectorService.onDestroy()");
        runThreads = false;
        super.onDestroy();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        Log.e(TAG, "DataCollectorService.onStartCommand()");
        setUpNewDataSources();
        setUpDataSubmissionThread();
        setUpHeartbeatSubmissionThread();
        // return super.onStartCommand(intent, flags, startId);
        return START_STICKY;
    }

    @Override
    public void onTaskRemoved(Intent rootIntent) {
        stopDataCollection();
        Log.e(TAG, "DataCollectorService.onTaskRemoved()");

        Intent intent = new Intent(getApplicationContext(), DataCollectorService.class);
        intent.setPackage(getPackageName());
        PendingIntent restartServicePendingIntent = PendingIntent.getService(getApplicationContext(), 1, intent, PendingIntent.FLAG_ONE_SHOT);

        AlarmManager alarmService = (AlarmManager) getApplicationContext().getSystemService(Context.ALARM_SERVICE);
        if (alarmService != null)
            alarmService.set(AlarmManager.ELAPSED_REALTIME, SystemClock.elapsedRealtime() + 100, restartServicePendingIntent);

        // super.onTaskRemoved(rootIntent);
    }


    private void initDataSourceNameIdMap() {
        dataSourceNameToSensorMap.put("ANDROID_ACCELEROMETER", Sensor.TYPE_ACCELEROMETER);
        dataSourceNameToSensorMap.put("ANDROID_AMBIENT_TEMPERATURE", Sensor.TYPE_AMBIENT_TEMPERATURE);
        dataSourceNameToSensorMap.put("ANDROID_GRAVITY", Sensor.TYPE_GRAVITY);
        dataSourceNameToSensorMap.put("ANDROID_GYROSCOPE", Sensor.TYPE_GYROSCOPE);
        dataSourceNameToSensorMap.put("ANDROID_LIGHT", Sensor.TYPE_LIGHT);
        dataSourceNameToSensorMap.put("ANDROID_LINEAR_ACCELERATION", Sensor.TYPE_LINEAR_ACCELERATION);
        dataSourceNameToSensorMap.put("ANDROID_MAGNETIC_FIELD", Sensor.TYPE_MAGNETIC_FIELD);
        dataSourceNameToSensorMap.put("ANDROID_ORIENTATION", Sensor.TYPE_ORIENTATION);
        dataSourceNameToSensorMap.put("ANDROID_PRESSURE", Sensor.TYPE_PRESSURE);
        dataSourceNameToSensorMap.put("ANDROID_PROXIMITY", Sensor.TYPE_PROXIMITY);
        dataSourceNameToSensorMap.put("ANDROID_RELATIVE_HUMIDITY", Sensor.TYPE_RELATIVE_HUMIDITY);
        dataSourceNameToSensorMap.put("ANDROID_ROTATION_VECTOR", Sensor.TYPE_ROTATION_VECTOR);
        dataSourceNameToSensorMap.put("ANDROID_TEMPERATURE", Sensor.TYPE_TEMPERATURE);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR2) {
            dataSourceNameToSensorMap.put("ANDROID_GAME_ROTATION_VECTOR", Sensor.TYPE_GAME_ROTATION_VECTOR);
            dataSourceNameToSensorMap.put("ANDROID_SIGNIFICANT_MOTION", Sensor.TYPE_SIGNIFICANT_MOTION);
            dataSourceNameToSensorMap.put("ANDROID_GYROSCOPE_UNCALIBRATED", Sensor.TYPE_GYROSCOPE_UNCALIBRATED);
            dataSourceNameToSensorMap.put("ANDROID_MAGNETIC_FIELD_UNCALIBRATED", Sensor.TYPE_MAGNETIC_FIELD_UNCALIBRATED);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
                dataSourceNameToSensorMap.put("ANDROID_STEP_COUNTER", Sensor.TYPE_STEP_COUNTER);
                dataSourceNameToSensorMap.put("ANDROID_GEOMAGNETIC_ROTATION_VECTOR", Sensor.TYPE_GEOMAGNETIC_ROTATION_VECTOR);
                dataSourceNameToSensorMap.put("ANDROID_STEP_DETECTOR", Sensor.TYPE_STEP_DETECTOR);
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT_WATCH) {
                    dataSourceNameToSensorMap.put("ANDROID_HEART_RATE", Sensor.TYPE_HEART_RATE);
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                        dataSourceNameToSensorMap.put("ANDROID_MOTION_DETECTION", Sensor.TYPE_MOTION_DETECT);
                        dataSourceNameToSensorMap.put("ANDROID_POSE_6DOF", Sensor.TYPE_POSE_6DOF);
                        dataSourceNameToSensorMap.put("ANDROID_HEART_BEAT", Sensor.TYPE_HEART_BEAT);
                        dataSourceNameToSensorMap.put("ANDROID_STATIONARY_DETECT", Sensor.TYPE_STATIONARY_DETECT);
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                            dataSourceNameToSensorMap.put("ANDROID_LOW_LATENCY_OFFBODY_DETECT", Sensor.TYPE_LOW_LATENCY_OFFBODY_DETECT);
                            dataSourceNameToSensorMap.put("ANDROID_ACCELEROMETER_UNCALIBRATED", Sensor.TYPE_ACCELEROMETER_UNCALIBRATED);
                        }
                    }
                }
            }
        }
    }

    private void stopDataCollection() {
        for (MySensorEventListener sensorEventListener : sensorEventListeners)
            sensorManager.unregisterListener(sensorEventListener);
    }

    private void setUpNewDataSources() {
        SharedPreferences prefs = getSharedPreferences(getPackageName(), Context.MODE_PRIVATE);
        String dataSourceNames = prefs.getString("dataSourceNames", null);
        if (dataSourceNames != null)
            for (String dataSourceName : dataSourceNames.split(",")) {
                String json = prefs.getString(String.format(Locale.getDefault(), "%s_json", dataSourceName), null);
                int delay = prefs.getInt(String.format(Locale.getDefault(), "%s_delay", dataSourceName), -1);
                if (delay != -1)
                    setUpDataSource(dataSourceName, delay, prefs);
                else if (json != null)
                    setUpDataSource(dataSourceName, json, prefs);
            }
    }

    private void setUpDataSource(String dataSourceName, @NotNull Integer delay, SharedPreferences prefs) {
        if (dataSourceNameToSensorMap.containsKey(dataSourceName)) {
            Integer sensorId = dataSourceNameToSensorMap.get(dataSourceName);
            assert sensorId != null;
            Sensor sensor = sensorManager.getDefaultSensor(sensorId);
            MySensorEventListener sensorEventListener = new MySensorEventListener(delay);
            sensorManager.registerListener(sensorEventListener, sensor, SensorManager.SENSOR_DELAY_FASTEST);
            sensorEventListeners.add(sensorEventListener);
            sensorToDataSourceIdMap.put(sensorId, prefs.getInt(dataSourceName, -1));
        } else {
            // TODO: GPS, Activity Recognition, App Usage, Survey, etc.
        }
    }

    private void setUpDataSource(String dataSourceName, String configJson, SharedPreferences prefs) {
        // TODO: tbd yet
    }

    private void setUpDataSubmissionThread() {
        new Thread() {
            @Override
            public void run() {
                while (runThreads) {
                    Cursor cursor = DbMgr.getSensorData();
                    if (cursor.moveToFirst()) {
                        ManagedChannel channel = ManagedChannelBuilder.forAddress(
                                getString(R.string.grpc_host),
                                Integer.parseInt(getString(R.string.grpc_port))
                        ).usePlaintext().build();
                        ETServiceGrpc.ETServiceBlockingStub stub = ETServiceGrpc.newBlockingStub(channel);

                        SharedPreferences prefs = getSharedPreferences(getPackageName(), Context.MODE_PRIVATE);
                        int userId = prefs.getInt("userId", -1);
                        String email = prefs.getString("email", null);

                        try {
                            do {
                                EtService.SubmitDataRecordRequestMessage submitDataRecordRequestMessage = EtService.SubmitDataRecordRequestMessage.newBuilder()
                                        .setUserId(userId)
                                        .setEmail(email)
                                        .setDataSource(cursor.getInt(1))
                                        .setTimestamp(cursor.getLong(2))
                                        .setValues(cursor.getString(4))
                                        .build();
                                EtService.DefaultResponseMessage responseMessage = stub.submitDataRecord(submitDataRecordRequestMessage);

                                if (responseMessage.getDoneSuccessfully())
                                    DbMgr.deleteRecord(cursor.getInt(0));
                            } while (cursor.moveToNext());
                        } catch (StatusRuntimeException e) {
                            Log.e(TAG, "DataCollectorService.setUpDataSubmissionThread() exception: " + e.getMessage());
                            e.printStackTrace();
                        } finally {
                            channel.shutdown();
                        }
                    }
                    cursor.close();

                    /*try {
                        Thread.sleep(1000);
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }*/
                }
            }
        }.start();
    }

    private void setUpHeartbeatSubmissionThread() {
        new Thread() {
            @Override
            public void run() {
                while (runThreads) {
                    ManagedChannel channel = ManagedChannelBuilder.forAddress(
                            getString(R.string.grpc_host),
                            Integer.parseInt(getString(R.string.grpc_port))
                    ).usePlaintext().build();

                    SharedPreferences prefs = getSharedPreferences(getPackageName(), Context.MODE_PRIVATE);

                    ETServiceGrpc.ETServiceBlockingStub stub = ETServiceGrpc.newBlockingStub(channel);
                    EtService.SubmitHeartbeatRequestMessage submitHeartbeatRequestMessage = EtService.SubmitHeartbeatRequestMessage.newBuilder()
                            .setUserId(prefs.getInt("userId", -1))
                            .setEmail(prefs.getString("email", null))
                            .build();
                    try {
                        EtService.DefaultResponseMessage responseMessage = stub.submitHeartbeat(submitHeartbeatRequestMessage);
                    } catch (StatusRuntimeException e) {
                        Log.e(TAG, "DataCollectorService.setUpHeartbeatSubmissionThread() exception: " + e.getMessage());
                        e.printStackTrace();
                    } finally {
                        channel.shutdown();
                    }

                    try {
                        Thread.sleep(5000);
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }
                }
            }
        }.start();
    }

    private class LocalBinder extends Binder {
        //MF_DataCollectorService getService() {
        //    return MF_DataCollectorService.this;
        //}
    }

    private class MySensorEventListener implements SensorEventListener {
        private boolean captureData;
        private int delay;
        private long nextTimestamp;

        MySensorEventListener(int delay) {
            this.delay = delay;
            this.captureData = true;
            this.nextTimestamp = 0;
        }

        @Override
        public void onSensorChanged(SensorEvent event) {
            if (event.timestamp < nextTimestamp)
                return;
            nextTimestamp = event.timestamp + this.delay * 1000000;

            StringBuilder sb = new StringBuilder();
            for (float value : event.values)
                sb.append(value).append(',');
            if (sb.length() > 0)
                sb.replace(sb.length() - 1, sb.length(), "");

            int dataSourceId = sensorToDataSourceIdMap.get(event.sensor.getType());
            long timestamp = System.currentTimeMillis() + (event.timestamp - System.nanoTime()) / 1000000L;
            DbMgr.saveNumericData(dataSourceId, timestamp, event.accuracy, event.values);
        }

        @Override
        public void onAccuracyChanged(Sensor sensor, int accuracy) {

        }
    }
}