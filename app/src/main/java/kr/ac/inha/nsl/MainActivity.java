package kr.ac.inha.nsl;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.IBinder;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;

import org.json.JSONException;
import org.json.JSONObject;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Locale;

import inha.nslab.easytrack.ETServiceGrpc;
import inha.nslab.easytrack.EtService;
import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import io.grpc.StatusRuntimeException;

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

    private ServiceConnection dataCollectorServiceConnection;
    private boolean dataCollectorServiceBound = false;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        Tools.init(this);
        Tools.cleanDb();
        loadCampaign(getSharedPreferences(getPackageName(), Context.MODE_PRIVATE));

        startDataCollectionButton = findViewById(R.id.startDataCollectionButton);
        stopDataCollectionButton = findViewById(R.id.stopDataCollectionButton);
        uploadSensorDataButton = findViewById(R.id.uploadSensorDataButton);
        logEditText = findViewById(R.id.logTextView);

        logLinesCount = 0;
        uploadSensorDataButton.setText(getString(R.string.upload_sensor_data, 0));

        SharedPreferences prefs = getSharedPreferences(getPackageName(), Context.MODE_PRIVATE);
        TextView usernameTextView = findViewById(R.id.usernameTextView);
        usernameTextView.setText(String.format("User: %s", prefs.getString("email", null)));
    }

    @Override
    protected void onDestroy() {
        stopSamplesCounterThread();
        if (dataCollectorServiceBound)
            unbindService(dataCollectorServiceConnection);
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


    private void loadCampaign(SharedPreferences prefs) {
        new Thread(() -> {
            ManagedChannel channel = ManagedChannelBuilder.forAddress(
                    getString(R.string.grpc_host),
                    Integer.parseInt(getString(R.string.grpc_port))
            ).usePlaintext().build();

            try {
                ETServiceGrpc.ETServiceBlockingStub stub = ETServiceGrpc.newBlockingStub(channel);
                EtService.RetrieveCampaignRequestMessage retrieveCampaignRequestMessage = EtService.RetrieveCampaignRequestMessage.newBuilder()
                        .setUserId(prefs.getInt("userId", -1))
                        .setEmail(prefs.getString("email", null))
                        .setCampaignId(Integer.parseInt(getString(R.string.easytrack_campaign_id)))
                        .build();

                EtService.RetrieveCampaignResponseMessage retrieveCampaignResponseMessage = stub.retrieveCampaign(retrieveCampaignRequestMessage);
                if (retrieveCampaignResponseMessage.getDoneSuccessfully()) {
                    setUpCampaignConfigurations(
                            retrieveCampaignResponseMessage.getName(),
                            retrieveCampaignResponseMessage.getNotes(),
                            retrieveCampaignResponseMessage.getCreatorEmail(),
                            retrieveCampaignResponseMessage.getConfigJson(),
                            retrieveCampaignResponseMessage.getStartTimestamp(),
                            retrieveCampaignResponseMessage.getEndTimestamp(),
                            retrieveCampaignResponseMessage.getParticipantCount(),
                            prefs
                    );
                    stopDataCollectionService();
                    startDataCollectionService();
                    log("Study configuration has been reloaded and services have restarted");
                } else
                    log("Failed to retrieve campaign details");
            } catch (StatusRuntimeException e) {
                log("An error occurred in the gRPC connection establishment");
            } catch (JSONException e) {
                log("An error occurred parsing the campaign configuration (JSON)");
            } finally {
                channel.shutdown();
            }
        }).start();
    }

    private void setUpCampaignConfigurations(String name, String notes, String creatorEmail, String configJson, long startTimestamp, long endTimestamp, int participantCount, SharedPreferences prefs) throws JSONException {
        Calendar fromCal = Calendar.getInstance(), tillCal = Calendar.getInstance();
        fromCal.setTimeInMillis(startTimestamp);
        tillCal.setTimeInMillis(endTimestamp);
        log("Campaign configurations loaded");
        log("Name: " + name + "(by " + creatorEmail + ")");
        log("From: " + SimpleDateFormat.getDateTimeInstance().format(fromCal.getTime()) + " till: " + SimpleDateFormat.getDateTimeInstance().format(tillCal.getTime()));
        log(participantCount + " participants enrolled to this campaign");

        String oldConfigJson = prefs.getString(String.format(Locale.getDefault(), "%s_configJson", name), null);
        if (configJson.equals(oldConfigJson))
            return;

        SharedPreferences.Editor editor = prefs.edit();
        editor.putString(String.format(Locale.getDefault(), "%s_configJson", name), configJson);

        StringBuilder sb = new StringBuilder();
        JSONObject root = new JSONObject(configJson);
        int index = 0;
        while (root.has(String.valueOf(index))) {
            JSONObject elem = root.getJSONObject(String.valueOf(index));
            String _name = elem.getString("name");
            sb.append(_name).append(',');
            int _dataSourceId = elem.getInt("data_source_id");
            editor.putInt(_name, _dataSourceId);
            if (elem.has("rate")) {
                int _rate = elem.getInt("rate");
                editor.putInt(String.format(Locale.getDefault(), "%s_rate", _name), _rate);
            } else if (elem.has("json")) {
                String _json = elem.getString("json");
                editor.putString(String.format(Locale.getDefault(), "%s_rate", _name), _json);
            } else {
                Log.e(TAG, "setUpCampaignConfigurations: weird data source json case " + elem.toString());
                throw new JSONException("rate/json must be in the data source json: " + elem.toString());
            }
            index++;
        }
        if (sb.length() > 0)
            sb.replace(sb.length() - 1, sb.length(), "");
        editor.putString("dataSourceNames", sb.toString());
        editor.apply();
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
                Log.e(TAG, "DataCollectorService has been bound to MainActivity");
            }

            @Override
            public void onServiceDisconnected(ComponentName name) {
                dataCollectorServiceBound = false;
                Log.e(TAG, "DataCollectorService has been unbound from MainActivity");
            }
        };
        dataCollectorServiceBound = bindService(intent, dataCollectorServiceConnection, Context.BIND_AUTO_CREATE);
    }

    private void stopDataCollectionService() {
        // Unbind the service
        if (dataCollectorServiceBound) {
            unbindService(dataCollectorServiceConnection);
            dataCollectorServiceBound = false;
        }

        // Stop the service
        Intent intent = new Intent(this, DataCollectorService.class);
        stopService(intent);
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
}
