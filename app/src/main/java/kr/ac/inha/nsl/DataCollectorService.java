package kr.ac.inha.nsl;

import android.Manifest;
import android.app.AlarmManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.os.Binder;
import android.os.IBinder;
import android.os.SystemClock;
import android.util.Log;
import android.util.SparseIntArray;
import android.util.SparseLongArray;

import androidx.core.app.ActivityCompat;

import com.google.android.gms.location.ActivityRecognition;
import com.google.android.gms.location.ActivityRecognitionClient;
import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationCallback;
import com.google.android.gms.location.LocationRequest;
import com.google.android.gms.location.LocationServices;

import org.apache.http.HttpResponse;
import org.apache.http.NameValuePair;
import org.apache.http.message.BasicNameValuePair;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.net.HttpURLConnection;
import java.util.ArrayList;
import java.util.List;

import static kr.ac.inha.nsl.AuthenticationActivity.KEY_CAMPAIGN_DATA_SOURCES_JSON;
import static kr.ac.inha.nsl.AuthenticationActivity.KEY_CAMPAIGN_DESCRIPTION;
import static kr.ac.inha.nsl.AuthenticationActivity.KEY_CAMPAIGN_END_DATE;
import static kr.ac.inha.nsl.AuthenticationActivity.KEY_CAMPAIGN_ID;
import static kr.ac.inha.nsl.AuthenticationActivity.KEY_CAMPAIGN_NAME;
import static kr.ac.inha.nsl.AuthenticationActivity.KEY_CAMPAIGN_OWNER;
import static kr.ac.inha.nsl.AuthenticationActivity.KEY_CAMPAIGN_START_DATE;
import static kr.ac.inha.nsl.AuthenticationActivity.KEY_PASSWORD;
import static kr.ac.inha.nsl.AuthenticationActivity.KEY_USERNAME;
import static kr.ac.inha.nsl.MyActivityRecognitionService.ACTIVITY_RECOGNITION_DATA_SOURCE_ID;
import static kr.ac.inha.nsl.MyLocationCallback.GPS_DATA_SOURCE_ID;
import static kr.ac.inha.nsl.MyLocationCallback.GPS_FASTEST_INTERVAL;
import static kr.ac.inha.nsl.MyLocationCallback.GPS_SLOWEST_INTERVAL;

public class DataCollectorService extends Service implements SensorEventListener {

    // region Constants
    private final IBinder mBinder = new LocalBinder();
    // endregion

    // region Variables
    static boolean forceStop = false;
    static SparseIntArray dataRateMapMs;
    static SparseLongArray nextDataTimestampMs;

    private FusedLocationProviderClient fusedLocationClient;
    private LocationCallback locationCallback;

    private ActivityRecognitionClient activityRecognitionClient;
    private PendingIntent transitionPendingIntent;

    private SensorManager sensorManager;
    // endregion

    // region Override
    @Override
    public IBinder onBind(Intent intent) {
        return mBinder;
    }

    @Override
    public void onCreate() {
        Log.e("DataCollectorService", "onCreate");

        // set up location callbacks
        locationCallback = new LocationCallback();
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(getApplicationContext());

        // set up activity recognition
        transitionPendingIntent = PendingIntent.getService(
                getApplicationContext(),
                2,
                new Intent(getApplicationContext(), MyActivityRecognitionService.class),
                PendingIntent.FLAG_UPDATE_CURRENT
        );
        activityRecognitionClient = ActivityRecognition.getClient(getApplicationContext());

        // set up sensors
        dataRateMapMs = new SparseIntArray();
        nextDataTimestampMs = new SparseLongArray();
        sensorManager = (SensorManager) getSystemService(Context.SENSOR_SERVICE);

        super.onCreate();
    }

    @Override
    public void onDestroy() {
        Log.e("DataCollectorService", "onDestroy");

        for (Sensor sensor : sensorManager.getSensorList(Sensor.TYPE_ALL))
            sensorManager.unregisterListener(this, sensor);
        fusedLocationClient.removeLocationUpdates(locationCallback);
        activityRecognitionClient.removeActivityUpdates(transitionPendingIntent);

        Intent intent = new Intent(MainActivity.DataCollectorBroadcastReceiver.PACKAGE);
        intent.putExtra("log_message", "Data collection service has terminated");
        sendBroadcast(intent);

        super.onDestroy();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        Log.e("DataCollectorService", "onStartCommand");

        loadDataRateAndStartDataCollection();

        Intent log_intent = new Intent(MainActivity.DataCollectorBroadcastReceiver.PACKAGE);
        log_intent.putExtra("log_message", "Data collection service has started");
        sendBroadcast(log_intent);

        return super.onStartCommand(intent, flags, startId);
    }

    @Override
    public void onTaskRemoved(Intent rootIntent) {
        if (forceStop)
            Log.e("DataCollectorService", "onTaskRemoved: terminating the service...");
        else {
            Log.e("DataCollectorService", "onTaskRemoved: restarting service...");
            Intent intent = new Intent(getApplicationContext(), DataCollectorService.class);
            intent.setPackage(Tools.PACKAGE_NAME);
            PendingIntent restartServicePendingIntent = PendingIntent.getService(getApplicationContext(), 1, intent, PendingIntent.FLAG_ONE_SHOT);

            AlarmManager alarmService = (AlarmManager) getApplicationContext().getSystemService(Context.ALARM_SERVICE);
            alarmService.set(AlarmManager.ELAPSED_REALTIME, SystemClock.elapsedRealtime() + 100, restartServicePendingIntent);
        }
    }

    @Override
    public void onSensorChanged(SensorEvent event) {
        long timestamp = Tools.LAST_REBOOT_TIMESTAMP + (int) (event.timestamp / 1000000);
        int sensorType = event.sensor.getType();
        if (timestamp >= nextDataTimestampMs.get(sensorType)) {
            StringBuilder sb = new StringBuilder();
            for (float val : event.values)
                sb.append(',').append(val);
            Tools.saveStringData(
                    sensorType,
                    timestamp,
                    (float) event.accuracy,
                    sb.substring(1)
            );
            nextDataTimestampMs.put(sensorType, timestamp + dataRateMapMs.get(event.sensor.getType()));
        }
    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int accuracy) {

    }
    // endregion


    private boolean isSensorAvailable(int sensorType) {
        return sensorManager.getDefaultSensor(sensorType) != null;
    }

    void addDataRateMs(int sensorType, int dataRateMs) {
        boolean dataRateAdded;

        if (dataRateAdded = isSensorAvailable(sensorType))
            dataRateMapMs.put(sensorType, dataRateMs);
        else if (dataRateAdded = sensorType == GPS_DATA_SOURCE_ID)
            dataRateMapMs.put(sensorType, dataRateMs);
        else if (dataRateAdded = sensorType == ACTIVITY_RECOGNITION_DATA_SOURCE_ID)
            dataRateMapMs.put(sensorType, dataRateMs);

        if (dataRateAdded)
            nextDataTimestampMs.put(sensorType, SystemClock.elapsedRealtime());
    }

    private void loadDataRateAndStartDataCollection() {
        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    List<NameValuePair> params = new ArrayList<>();
                    params.add(new BasicNameValuePair("username", Tools.prefs.getString(KEY_USERNAME, null)));
                    params.add(new BasicNameValuePair("password", Tools.prefs.getString(KEY_PASSWORD, null)));
                    params.add(new BasicNameValuePair("device", getString(R.string.device_as_data_source)));
                    HttpResponse response = Tools.post(Tools.API_FETCH_CAMPAIGN_SETTINGS, params, null);
                    if (response.getStatusLine().getStatusCode() == HttpURLConnection.HTTP_OK) {
                        JSONObject result = new JSONObject(Tools.inputStreamToString(response.getEntity().getContent()));
                        if (result.has("result") && result.getInt("result") == ServerResult.OK) {
                            JSONObject campaign_settings = result.getJSONObject("campaign_settings");

                            // Save personal information and campaign details
                            SharedPreferences.Editor editor = Tools.prefs.edit();
                            editor.putInt(KEY_CAMPAIGN_ID, campaign_settings.getInt("id"));
                            editor.putString(KEY_CAMPAIGN_OWNER, campaign_settings.getString("owner_username"));
                            editor.putString(KEY_CAMPAIGN_NAME, campaign_settings.getString("name"));
                            editor.putInt(KEY_CAMPAIGN_START_DATE, campaign_settings.getInt("start_date"));
                            editor.putInt(KEY_CAMPAIGN_END_DATE, campaign_settings.getInt("end_date"));
                            editor.putString(KEY_CAMPAIGN_DESCRIPTION, campaign_settings.getString("description"));
                            editor.putString(KEY_CAMPAIGN_DATA_SOURCES_JSON, campaign_settings.getJSONArray("data_sources").toString());
                            editor.apply();

                            // Save sensor data-rates
                            JSONArray data_sources = campaign_settings.getJSONArray("data_sources");
                            for (int n = 0; n < data_sources.length(); n++) {
                                JSONObject data_source = data_sources.getJSONObject(0);
                                addDataRateMs(data_source.getInt("source_id"), data_source.getInt("data_rate"));
                            }
                            Intent intent = new Intent(MainActivity.DataCollectorBroadcastReceiver.PACKAGE);
                            intent.putExtra("log_message", "Campaign settings loaded (" + dataRateMapMs.size() + " data sources set up)!");
                            sendBroadcast(intent);

                            // Initialize sensor data collection
                            forceStop = false;
                            List<Sensor> deviceSensors = sensorManager.getSensorList(Sensor.TYPE_ALL);
                            for (Sensor sensor : deviceSensors)
                                if (dataRateMapMs.indexOfKey(sensor.getType()) > -1)
                                    sensorManager.registerListener(DataCollectorService.this, sensor, dataRateMapMs.get(sensor.getType()) * 1000);

                            if (activityRecognitionClient != null && fusedLocationClient != null && ActivityCompat.checkSelfPermission(getApplicationContext(), Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED && ActivityCompat.checkSelfPermission(getApplicationContext(), Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
                                LocationRequest locationRequest = new LocationRequest();
                                locationRequest.setPriority(LocationRequest.PRIORITY_BALANCED_POWER_ACCURACY);
                                locationRequest.setFastestInterval(GPS_FASTEST_INTERVAL); // fastest ==> 10 seconds
                                locationRequest.setInterval(GPS_SLOWEST_INTERVAL); // slowest ==> 10 minutes

                                // TODO: Before starting GPS and Activity Detection, check if they're enabled in campaign settings
                                // fusedLocationClient.requestLocationUpdates(locationRequest, locationCallback, Looper.myLooper());
                                // activityRecognitionClient.requestActivityUpdates(ACTIVITY_RECOGNITION_INTERVAL, transitionPendingIntent);
                            }
                        } else {
                            throw new JSONException("Parameter 'result' wasn't in response json");
                        }
                    } else
                        throw new IOException("HTTP error while sending a post request for autentication");
                } catch (IOException | JSONException e) {
                    e.printStackTrace();
                }
            }
        }).start();
    }


    private class LocalBinder extends Binder {
        /*DataCollectorService getService() {
            return DataCollectorService.this;
        }*/
    }
}
