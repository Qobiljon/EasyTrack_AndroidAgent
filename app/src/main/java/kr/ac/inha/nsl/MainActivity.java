package kr.ac.inha.nsl;

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
import android.widget.TextView;

import org.json.JSONArray;
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
    private TextView logTextView;
    private ServiceConnection dataCollectorServiceConnection;
    private boolean dataCollectorServiceBound = false;
    private boolean runThread;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        Log.e(TAG, "MainActivity.onBackPressed()");
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        logTextView = findViewById(R.id.logTextView);

        SharedPreferences prefs = getSharedPreferences(getPackageName(), Context.MODE_PRIVATE);
        TextView usernameTextView = findViewById(R.id.usernameTextView);
        usernameTextView.setText(String.format("User: %s", prefs.getString("email", null)));

        DbMgr.init(this);
        loadCampaign(getSharedPreferences(getPackageName(), Context.MODE_PRIVATE));
    }

    @Override
    protected void onDestroy() {
        Log.e(TAG, "MainActivity.onDestroy()");
        if (dataCollectorServiceBound)
            unbindService(dataCollectorServiceConnection);
        super.onDestroy();
    }

    @Override
    public void onBackPressed() {
        Log.e(TAG, "MainActivity.onBackPressed()");

        Intent intent = new Intent(Intent.ACTION_MAIN);
        intent.addCategory(Intent.CATEGORY_HOME);
        startActivity(intent);
    }

    @Override
    protected void onPause() {
        Log.e(TAG, "MainActivity.onPause()");
        runThread = false;
        super.onPause();
    }

    @Override
    protected void onResume() {
        Log.e(TAG, "MainActivity.onResume()");
        /*runThread = true;
        new Thread(() -> {
            while (runThread) {
                logTextView.setText(getString(R.string.samples_count, DbMgr.countSamples()));
                try {
                    Thread.sleep(500);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }
        }).start();*/
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
        log("Campaign notes: " + notes);
        log("From: " + SimpleDateFormat.getDateTimeInstance().format(fromCal.getTime()) + " till: " + SimpleDateFormat.getDateTimeInstance().format(tillCal.getTime()));
        log(participantCount + " participants enrolled to this campaign");

        String oldConfigJson = prefs.getString(String.format(Locale.getDefault(), "%s_configJson", name), null);
        if (configJson.equals(oldConfigJson))
            return;

        SharedPreferences.Editor editor = prefs.edit();
        editor.putString(String.format(Locale.getDefault(), "%s_configJson", name), configJson);

        StringBuilder sb = new StringBuilder();
        JSONArray dataSourceArray = new JSONArray(configJson);
        for (int n = 0; n < dataSourceArray.length(); n++) {
            JSONObject dataSource = dataSourceArray.getJSONObject(n);

            String _name = dataSource.getString("name");
            editor.putInt(_name, dataSource.getInt("data_source_id"));
            editor.putString(String.format(Locale.getDefault(), "config_json_%s", _name), dataSource.getString("config_json"));

            sb.append(_name).append(',');
        }
        if (sb.length() > 0)
            sb.replace(sb.length() - 1, sb.length(), "");
        editor.putString("dataSourceNames", sb.toString());
        editor.apply();
    }

    private void log(String message) {
        logTextView.setText(message);
        /*runOnUiThread(() -> {
            if (logLinesCount == 100)
                logTextView.setText(String.format(Locale.US, "%s%n%s", logTextView.getText().toString().substring(logTextView.getText().toString().indexOf('\n') + 1), message));
            else {
                logTextView.setText(String.format("%s%n%s", logTextView.getText(), message));
                logLinesCount++;
            }
        });*/
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
}
