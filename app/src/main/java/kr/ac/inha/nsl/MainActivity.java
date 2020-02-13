package kr.ac.inha.nsl;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.IBinder;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;

import java.util.Locale;

public class MainActivity extends Activity {
    static String TAG = "EasyTrack";

    private int logLinesCount;

    private TextView logEditText;
    private Button startDataCollectionButton;
    private Button stopDataCollectionButton;
    private Button uploadSensorDataButton;

    private Thread submitDataThread;
    private StoppableRunnable submitDataRunnable;
    private Thread sampleCounterThread;
    private StoppableRunnable sampleCounterRunnable;

    private DataCollectorBroadcastReceiver dataCollectorBroadcastReceiver;
    private ServiceConnection dataCollectorServiceConnection;
    private boolean dataCollectorServiceBound = false;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        Tools.init(this);
        Tools.cleanDb();

        startDataCollectionButton = findViewById(R.id.startDataCollectionButton);
        stopDataCollectionButton = findViewById(R.id.stopDataCollectionButton);
        uploadSensorDataButton = findViewById(R.id.uploadSensorDataButton);
        logEditText = findViewById(R.id.logTextView);

        logLinesCount = 0;
        uploadSensorDataButton.setText(getString(R.string.upload_sensor_data, 0));
    }

    @Override
    protected void onStart() {
        super.onStart();

        SharedPreferences preferences = getSharedPreferences(getPackageName(), Context.MODE_PRIVATE);
        TextView usernameTextView = findViewById(R.id.usernameTextView);
        usernameTextView.setText(String.format("User: %s", preferences.getString("email", null)));
    }

    @Override
    protected void onDestroy() {
        // stopTizenService();
        stopSamplesCounterThread();
        super.onDestroy();
    }

    @Override
    public void onBackPressed() {
        Tools.hideApp(this);
    }

    @Override
    protected void onResume() {
        startSamplesCounterThread();
        super.onResume();
    }


    private void log(final String message) {
        runOnUiThread(() -> {
            if (logLinesCount == 100)
                logEditText.setText(String.format(Locale.US, "%s%n%s", logEditText.getText().toString().substring(logEditText.getText().toString().indexOf('\n') + 1), message));
            else {
                logEditText.setText(String.format("%s%n%s", logEditText.getText(), message));
                logLinesCount++;
            }
        });
    }

    public void logoutButtonClick(View view) {
        SharedPreferences prefs = getSharedPreferences(getPackageName(), Context.MODE_PRIVATE);
        SharedPreferences.Editor editor = prefs.edit();
        editor.clear();
        editor.apply();
        finish();
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
                    Tools.submitCollectedData();
                }
            });
            submitDataThread.start();
            uploadSensorDataButton.setText(getString(R.string.cancel_upload_sensor_data));
        }
    }

    public void startDataCollectionClick(View view) {
        startDataCollectionService();
        startDataCollectionButton.setClickable(false);
        stopDataCollectionButton.setClickable(true);
    }

    public void stopDataCollectionClick(View view) {
        stopDataCollectionService();
        startDataCollectionButton.setClickable(true);
        stopDataCollectionButton.setClickable(false);
        log("Data collection campaign has terminated");
    }

    public void startSamplesCounterThread() {
        stopSamplesCounterThread();

        sampleCounterThread = new Thread(sampleCounterRunnable = new StoppableRunnable() {
            @Override
            public void run() {
                while (!terminate) {
                    runOnUiThread(() -> uploadSensorDataButton.setText(getString(R.string.upload_sensor_data, Tools.countSamples())));
                    try {
                        Thread.sleep(1000);
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }
                }
            }
        });
        sampleCounterThread.start();
    }

    public void stopSamplesCounterThread() {
        if (sampleCounterRunnable != null && !sampleCounterRunnable.terminate) {
            sampleCounterRunnable.terminate = true;
            try {
                sampleCounterThread.join();
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }
    }

    private void startDataCollectionService() {
        // Start service
        Intent intent = new Intent(this, DataCollectorService.class);
        startService(intent);

        // Bind service
        dataCollectorServiceConnection = new ServiceConnection() {
            @Override
            public void onServiceConnected(ComponentName name, IBinder service) {
                dataCollectorServiceBound = true;
                Log.e("MainActivity", "onServiceConnected: dataCollectorServiceConnection");
            }

            @Override
            public void onServiceDisconnected(ComponentName name) {
                dataCollectorServiceBound = false;
                Log.e("MainActivity", "onServiceDisconnected: dataCollectorServiceConnection");
            }
        };
        dataCollectorServiceBound = bindService(intent, dataCollectorServiceConnection, Context.BIND_AUTO_CREATE);

        // Set up receiver
        dataCollectorBroadcastReceiver = new DataCollectorBroadcastReceiver();
        IntentFilter filter = new IntentFilter();
        filter.addAction(DataCollectorBroadcastReceiver.PACKAGE);
        registerReceiver(dataCollectorBroadcastReceiver, filter);
    }

    private void stopDataCollectionService() {
        // Unregister receiver
        unregisterReceiver(dataCollectorBroadcastReceiver);

        // Unbind the service
        if (dataCollectorServiceBound) {
            unbindService(dataCollectorServiceConnection);
            dataCollectorServiceBound = false;
        }

        // Stop the service
        Intent intent = new Intent(this, DataCollectorService.class);
        stopService(intent);
    }


    class DataCollectorBroadcastReceiver extends BroadcastReceiver {
        static final String PACKAGE = "kr.ac.nsl.inha.MainActivity$DataCollectorBroadcastReceiver";

        @Override
        public void onReceive(Context context, Intent intent) {
            if (intent.hasExtra("log_message"))
                log(intent.getStringExtra("log_message"));
            else
                Log.e("TizenReceiver", "onReceive: DataCollectorBroadcastReceiver");
        }
    }
}
