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

import static kr.ac.inha.nsl.AuthenticationActivity.KEY_USERNAME;
import static kr.ac.inha.nsl.AuthenticationActivity.KEY_PASSWORD;

public class MainActivity extends Activity {

    // region Variable
    private int logLinesCount;

    // UI elements
    private TextView logEditText;
    private Button startDataCollectionButton;
    private Button stopDataCollectionButton;
    private Button uploadSensorDataButton;

    // Threads
    private Thread submitDataThread;
    private StoppableRunnable submitDataRunnable;
    private Thread sampleCounterThread;
    private StoppableRunnable sampleCounterRunnable;

    // Tizen-service related variables
    private TizenAgentService tizenAgentService;
    private TizenBroadcastReceiver tizenBroadcastReceiver;
    private ServiceConnection tizenServiceConnection;
    private boolean tizenServiceBound = false;

    // Data-collector service related variables
    private DataCollectorBroadcastReceiver dataCollectorBroadcastReceiver;
    private ServiceConnection dataCollectorServiceConnection;
    private boolean dataCollectorServiceBound = false;
    // endregion

    // region Override
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        Tools.cleanDb();

        startDataCollectionButton = findViewById(R.id.startDataCollectionButton);
        stopDataCollectionButton = findViewById(R.id.stopDataCollectionButton);
        uploadSensorDataButton = findViewById(R.id.uploadSensorDataButton);
        logEditText = findViewById(R.id.logTextView);

        logLinesCount = 0;
        uploadSensorDataButton.setText(getString(R.string.upload_sensor_data, 0));

        TextView usernameTextView = findViewById(R.id.usernameTextView);
        usernameTextView.setText(String.format("User: %s", Tools.prefs.getString("username", null)));
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
    // endregion


    public void logoutButtonClick(View view) {
        SharedPreferences.Editor editor = Tools.prefs.edit();
        editor.remove(KEY_USERNAME);
        editor.remove(KEY_PASSWORD);
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
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            uploadSensorDataButton.setText(getString(R.string.upload_sensor_data, Tools.countSamples()));
                        }
                    });
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


    private void startTizenService() {
        // Start service
        Intent intent = new Intent(this, TizenAgentService.class);
        startService(intent);

        // Bind service
        tizenServiceConnection = new ServiceConnection() {
            @Override
            public void onServiceConnected(ComponentName name, IBinder service) {
                tizenAgentService = ((TizenAgentService.LocalBinder) service).getService();
                tizenServiceBound = true;
                Log.e("MainActivity", "onServiceConnected: tizenServiceConnection");
            }

            @Override
            public void onServiceDisconnected(ComponentName name) {
                tizenAgentService = null;
                tizenServiceBound = false;
                Log.e("MainActivity", "onServiceDisconnected: tizenServiceConnection");
            }
        };
        tizenServiceBound = bindService(intent, tizenServiceConnection, Context.BIND_AUTO_CREATE);

        // Set up receiver
        tizenBroadcastReceiver = new TizenBroadcastReceiver();
        IntentFilter filter = new IntentFilter();
        filter.addAction(TizenBroadcastReceiver.PACKAGE);
        registerReceiver(tizenBroadcastReceiver, filter);

        // Find peers & connect
        if (tizenServiceBound && tizenAgentService != null)
            tizenAgentService.findPeers();
    }

    private void stopTizenService() {
        // Unregister receiver
        unregisterReceiver(tizenBroadcastReceiver);

        // Disconnect from service
        if (tizenServiceBound && tizenAgentService != null && !tizenAgentService.closeConnection())
            Log.e("MainActivity", "stopTizenService: closeConnection");

        // Unbind the service
        if (tizenServiceBound) {
            unbindService(tizenServiceConnection);
            tizenServiceBound = false;
        }

        // Stop the service
        Intent intent = new Intent(this, DataCollectorService.class);
        stopService(intent);
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


    class TizenBroadcastReceiver extends BroadcastReceiver {
        static final String PACKAGE = "kr.ac.nsl.inha.MainActivity$TizenBroadcastReceiver";

        @Override
        public void onReceive(Context context, Intent intent) {
            Log.e("TizenReceiver", "onReceive: TizenBroadcastReceiver");
        }
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
