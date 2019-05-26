package kr.ac.inha.nsl;

import android.app.Activity;
import android.content.Context;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.hardware.TriggerEvent;
import android.hardware.TriggerEventListener;
import android.os.Bundle;
import android.os.FileObserver;
import android.os.Looper;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import org.apache.http.HttpResponse;
import org.apache.http.NameValuePair;
import org.apache.http.message.BasicNameValuePair;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.SocketException;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class MainActivity extends Activity {
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Tools.init(this);
        setContentView(R.layout.activity_main);

        logEditText = findViewById(R.id.logTextView);
        filesCountTextView = findViewById(R.id.filesCountTextView);
        startDataCollectionButton = findViewById(R.id.startDataCollectionButton);
        stopDataCollectionButton = findViewById(R.id.stopDataCollectionButton);
        logLinesCount = 0;

        initDataSources();
    }

    private void initDataSources() {
        sensorManager = (SensorManager) getSystemService(Context.SENSOR_SERVICE);
        sensorEventListener = new SensorEventListener() {
            @Override
            public void onSensorChanged(SensorEvent event) {
                try {
                    StringBuilder sb = new StringBuilder("smartphone-android,");
                    sb.append(Calendar.getInstance().getTimeInMillis()).append(',');
                    sb.append(event.sensor.getType()).append(',');
                    sb.append(event.sensor.getName()).append(',');
                    sb.append(event.accuracy).append(',');
                    for (float val : event.values)
                        sb.append(val).append(',');
                    sb.deleteCharAt(sb.length() - 1);

                    checkUpdateCurrentLogWriter();
                    if (logWriter != null) {
                        logWriter.flush();
                        logWriter.write(String.format("%s%n", sb.toString()));
                    }
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }

            @Override
            public void onAccuracyChanged(Sensor sensor, int accuracy) {

            }
        };
    }

    @Override
    protected void onStart() {
        if (filesCounterObserver != null)
            filesCounterObserver.stopWatching();

        filesCount = Tools.countSensorDataFiles();
        filesCountTextView.setText(String.format(Locale.US, "FILES: %d", filesCount));
        filesCounterObserver = new FileObserver(Tools.APP_DIR) {
            @Override
            public void onEvent(int event, String path) {
                if (event == FileObserver.CREATE) {
                    filesCountTextView.setText(String.format(Locale.US, "FILES: %d", ++filesCount));
                } else if (event == FileObserver.DELETE) {
                    filesCountTextView.setText(String.format(Locale.US, "FILES: %d", --filesCount));
                }
            }
        };
        filesCounterObserver.startWatching();

        super.onStart();
    }

    @Override
    protected void onStop() {
        if (filesCounterObserver != null)
            filesCounterObserver.stopWatching();
        filesCounterObserver = null;
        super.onStop();
    }

    // region Variable
    private TextView filesCountTextView;
    private TextView logEditText;
    private Button startDataCollectionButton;
    private Button stopDataCollectionButton;
    private FileObserver filesCounterObserver;
    private int filesCount;
    private int logLinesCount;
    private FileWriter logWriter;
    private String openLogWriterStamp;

    private SensorManager sensorManager;
    private SensorEventListener sensorEventListener;

    private ExecutorService submitDataExecutor;
    // endregion

    private void log(String message) {
        if (logLinesCount == logEditText.getMaxLines())
            logEditText.setText(String.format(Locale.US, "%s%n%s", logEditText.getText().toString().substring(logEditText.getText().toString().indexOf('\n') + 1), message));
        else {
            logEditText.setText(String.format("%s%n%s", logEditText.getText(), message));
            logLinesCount++;
        }
    }

    private void checkUpdateCurrentLogWriter() throws IOException {
        Calendar nowTimeStamp = Calendar.getInstance();
        nowTimeStamp.set(nowTimeStamp.get(Calendar.YEAR), nowTimeStamp.get(Calendar.MONTH), nowTimeStamp.get(Calendar.DAY_OF_MONTH), nowTimeStamp.get(Calendar.HOUR_OF_DAY), nowTimeStamp.get(Calendar.MINUTE), 0);
        nowTimeStamp.set(Calendar.MILLISECOND, 0);
        String nowStamp = String.valueOf(nowTimeStamp.getTimeInMillis() / 10000);

        if (logWriter == null) {
            this.openLogWriterStamp = nowStamp;
            String filePath = String.format(Locale.US, "%s%s%s.csv", Tools.APP_DIR, File.separator, nowStamp);
            logWriter = new FileWriter(filePath, true);

            log("\nData-log file created/attached");
            Tools.sendHeartBeatMessage();
        } else if (!nowStamp.equals(openLogWriterStamp)) {
            logWriter.flush();
            logWriter.close();

            openLogWriterStamp = nowStamp;
            String filePath = String.format(Locale.US, "%s%s%s.csv", Tools.APP_DIR, File.separator, nowStamp);
            logWriter = new FileWriter(filePath, false);

            log("New data-log file created");
            Tools.sendHeartBeatMessage();
        }
    }

    public void startDataCollectionClick(View view) {
        log("Sensor data collection started");

        List<Sensor> deviceSensors = sensorManager.getSensorList(Sensor.TYPE_ALL);
        for (Sensor sensor : deviceSensors) {
            sensorManager.registerListener(sensorEventListener, sensor, SensorManager.SENSOR_DELAY_NORMAL);
            sensorManager.requestTriggerSensor(new TriggerEventListener() {
                @Override
                public void onTrigger(TriggerEvent event) {
                    Log.e("EVENT", event.sensor.getName() + " has been triggered");
                }
            }, sensor);
        }
        startDataCollectionButton.setClickable(false);
        stopDataCollectionButton.setClickable(true);
    }

    public void stopDataCollectionClick(View view) {
        List<Sensor> deviceSensors = sensorManager.getSensorList(Sensor.TYPE_ALL);
        for (Sensor sensor : deviceSensors)
            sensorManager.unregisterListener(sensorEventListener);
        startDataCollectionButton.setClickable(true);
        stopDataCollectionButton.setClickable(false);

        log("Sensor data collection stopped");
    }

    public void uploadSensorDataClick(View view) {
        if (submitDataExecutor != null && !submitDataExecutor.isShutdown())
            submitDataExecutor.shutdownNow();

        submitDataExecutor = Executors.newCachedThreadPool();
        submitDataExecutor.execute(new Runnable() {
            @Override
            public void run() {
                String[] files = Tools.getFiles(Tools.APP_DIR);
                if (files != null) {
                    List<Long> fileNamesInLong = new ArrayList<>();
                    for (String file : files) {
                        String tmp = file.substring(file.lastIndexOf('/') + 1);
                        fileNamesInLong.add(Long.parseLong(tmp.substring(0, tmp.lastIndexOf('.'))));
                    }
                    Collections.sort(fileNamesInLong);
                    try {
                        for (int n = 0; n < fileNamesInLong.size() - 1; n++) {
                            String filePath = String.format(Locale.US, "%s%s%s.csv", Tools.APP_DIR, File.separator, fileNamesInLong.get(n));
                            List<NameValuePair> params = new ArrayList<>();
                            params.add(new BasicNameValuePair("username", "test"));
                            params.add(new BasicNameValuePair("password", "0123456789"));
                            File file = new File(filePath);
                            HttpResponse response = Tools.post(Tools.API_SUBMIT_DATA, params, file);
                            if (response.getStatusLine().getStatusCode() == HttpURLConnection.HTTP_OK) {
                                JSONObject result = new JSONObject(Tools.inputStreamToString(response.getEntity().getContent()));
                                if (result.has("result") && result.getInt("result") == ServerResult.OK)
                                    Log.d("IO ACTION", String.format("File %s %s uploaded!", file.getName(), file.delete() ? "was" : "wasn't"));
                                else
                                    Log.e("UPLOAD ERROR", result.toString());
                            } else {
                                Log.e("HTTP ERROR", response.getStatusLine().getReasonPhrase());
                            }
                        }
                        log("Data uploaded on Server");
                    } catch (IOException e) {
                        e.printStackTrace();
                        Looper.prepare();
                        Toast.makeText(MainActivity.this, "Please check your connection first!", Toast.LENGTH_SHORT).show();
                    } catch (JSONException e) {
                        e.printStackTrace();
                    }
                }
            }
        });
    }
}
