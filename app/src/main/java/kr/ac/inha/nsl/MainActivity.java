package kr.ac.inha.nsl;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.Context;
import android.content.SharedPreferences;
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
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

public class MainActivity extends Activity {
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        logEditText = findViewById(R.id.logTextView);
        filesCountTextView = findViewById(R.id.filesCountTextView);
        startDataCollectionButton = findViewById(R.id.startDataCollectionButton);
        stopDataCollectionButton = findViewById(R.id.stopDataCollectionButton);
        uploadSensorDataButton = findViewById(R.id.uploadSensorDataButton);
        logLinesCount = 0;

        TextView usernameTextView = findViewById(R.id.usernameTextView);
        usernameTextView.setText(String.format("User: %s", Tools.prefs.getString("username", null)));

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

    @Override
    public void onBackPressed() {
        Tools.hideApp(this);
    }

    // region Variable
    private TextView filesCountTextView;
    private TextView logEditText;
    private Button startDataCollectionButton;
    private Button stopDataCollectionButton;
    private Button uploadSensorDataButton;
    private FileObserver filesCounterObserver;
    private int filesCount;
    private int logLinesCount;
    private FileWriter logWriter;
    private String openLogWriterStamp;

    private SensorManager sensorManager;
    private SensorEventListener sensorEventListener;

    private Thread submitDataThread;
    private StoppableRunnable submitDataRunnable;
    // endregion

    private void log(final String message) {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                if (logLinesCount == logEditText.getMaxLines())
                    logEditText.setText(String.format(Locale.US, "%s%n%s", logEditText.getText().toString().substring(logEditText.getText().toString().indexOf('\n') + 1), message));
                else {
                    logEditText.setText(String.format("%s%n%s", logEditText.getText(), message));
                    logLinesCount++;
                }
            }
        });
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

            log("Data-log file created/attached");
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

    public void startFilesCounterThread() {
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
    }

    public void stopFilesCounterThread() {
        if (filesCounterObserver != null)
            filesCounterObserver.stopWatching();
        filesCounterObserver = null;
    }

    public void uploadSensorDataClick(View view) {
        if (submitDataRunnable != null && !submitDataRunnable.terminate) {
            submitDataRunnable.terminate = true;
            try {
                submitDataThread.join();
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
            uploadSensorDataButton.setText(getString(R.string.upload_sensor_data));
        } else {
            submitDataThread = new Thread(submitDataRunnable = new StoppableRunnable() {
                @SuppressLint("SetTextI18n")
                @Override
                public void run() {
                    stopFilesCounterThread();
                    String[] files = Tools.getFiles(Tools.APP_DIR);
                    if (files != null) {
                        List<Long> fileNamesInLong = new ArrayList<>();
                        for (String file : files) {
                            if (!file.endsWith(".csv"))
                                continue;
                            String tmp = file.substring(file.lastIndexOf('/') + 1);
                            fileNamesInLong.add(Long.parseLong(tmp.substring(0, tmp.lastIndexOf('.'))));
                        }
                        Collections.sort(fileNamesInLong);
                        try {
                            for (int n = 0; n < fileNamesInLong.size() - 1; n++) {
                                if (this.terminate)
                                    break;
                                filesCountTextView.setText(String.format(Locale.US, "%d%% UPLOADED", (n + 1) * 100 / fileNamesInLong.size()));
                                String filePath = String.format(Locale.US, "%s%s%s.csv", Tools.APP_DIR, File.separator, fileNamesInLong.get(n));
                                List<NameValuePair> params = new ArrayList<>();
                                params.add(new BasicNameValuePair("username", Tools.prefs.getString("username", null)));
                                params.add(new BasicNameValuePair("password", Tools.prefs.getString("password", null)));
                                File file = new File(filePath);
                                HttpResponse response = Tools.post(Tools.API_SUBMIT_DATA, params, file);
                                if (response.getStatusLine().getStatusCode() == HttpURLConnection.HTTP_OK) {
                                    JSONObject result = new JSONObject(Tools.inputStreamToString(response.getEntity().getContent()));

                                    if (result.has("result") && result.getInt("result") == ServerResult.OK) {
                                        boolean deleted = file.delete();
                                        Log.d("IO ACTION", String.format("File %s %s uploaded!", file.getName(), deleted ? "was" : "wasn't"));
                                    } else
                                        Log.e("UPLOAD ERROR", result.toString());
                                } else {
                                    Log.e("HTTP ERROR", response.getStatusLine().getReasonPhrase());
                                }
                            }
                            log("Data uploaded on Server");
                            filesCountTextView.setText("100%% UPLOADED");
                            try {
                                Thread.sleep(300);
                            } catch (InterruptedException e) {
                                e.printStackTrace();
                            }
                        } catch (IOException e) {
                            e.printStackTrace();
                            Looper.prepare();
                            Toast.makeText(MainActivity.this, "Please check your connection first!", Toast.LENGTH_SHORT).show();
                        } catch (JSONException e) {
                            e.printStackTrace();
                        }
                    }
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            submitDataRunnable = null;
                            submitDataThread = null;
                            uploadSensorDataButton.setText(getString(R.string.upload_sensor_data));
                        }
                    });
                    startFilesCounterThread();
                }
            });
            submitDataThread.start();
            uploadSensorDataButton.setText(getString(R.string.cancel_upload_sensor_data));
        }
    }

    public void logoutButtonClick(View view) {
        SharedPreferences.Editor editor = Tools.prefs.edit();
        editor.remove("username");
        editor.remove("password");
        editor.putBoolean("logged_in", false);
        editor.apply();
        finish();
    }
}
