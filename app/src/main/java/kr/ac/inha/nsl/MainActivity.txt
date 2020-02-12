package inha.nslab.easytrack;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;

import com.google.android.gms.auth.api.signin.GoogleSignIn;
import com.google.android.gms.auth.api.signin.GoogleSignInAccount;
import com.google.android.gms.auth.api.signin.GoogleSignInClient;
import com.google.android.gms.auth.api.signin.GoogleSignInOptions;

import java.util.Calendar;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.TimeUnit;

import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import io.grpc.StatusRuntimeException;

public class MainActivity extends AppCompatActivity {

    static final String TAG = "ET_AUTH_EXAMPLE_APP";
    static final int RC_OPEN_AUTH_ACTIVITY = 100;
    private TextView logTextView;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        logTextView = findViewById(R.id.logTextView);

        /*SharedPreferences prefs = getSharedPreferences(getString(R.string.app_name), Context.MODE_PRIVATE);
        if (prefs.getInt("userId", -1) == -1) {
            logTextView.setText(getString(R.string.account, "N/A", -1, false));
            startActivityForResult(new Intent(this, GoogleAuthActivity.class), RC_OPEN_AUTH_ACTIVITY);
        } else {
            new Thread(() -> {
                ManagedChannel channel = ManagedChannelBuilder.forAddress(
                        getString(R.string.grpc_server_ip),
                        Integer.parseInt(getString(R.string.grpc_server_port))
                ).usePlaintext().build();

                String idToken = prefs.getString("idToken", null);

                ETServiceGrpc.ETServiceBlockingStub stub = ETServiceGrpc.newBlockingStub(channel);
                EtService.LoginWithGoogleIdRequestMessage requestMessage = EtService.LoginWithGoogleIdRequestMessage.newBuilder()
                        .setIdToken(idToken)
                        .build();
                try {
                    EtService.LoginWithGoogleIdResponseMessage responseMessage = stub.loginWithGoogleId(requestMessage);
                    if (responseMessage.getDoneSuccessfully())
                        runOnUiThread(() -> logTextView.setText(getString(
                                R.string.account,
                                prefs.getString("email", null),
                                prefs.getInt("userId", -1),
                                prefs.getBoolean("isParticipant", false)
                        )));
                    else
                        runOnUiThread(() -> {
                            logTextView.setText(getString(R.string.account, "N/A", -1, false));
                            startActivityForResult(new Intent(this, GoogleAuthActivity.class), RC_OPEN_AUTH_ACTIVITY);
                        });
                } catch (StatusRuntimeException e) {
                    Log.e(TAG, "onCreate: gRPC server unavailable");
                }
                try {
                    channel.shutdown().awaitTermination(1, TimeUnit.SECONDS);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }).start();
        }*/
    }

    public void loginClick(View view) {
        /*Intent launchIntent = getPackageManager().getLaunchIntentForPackage("inha.nslab.easytrack");
        if (launchIntent != null) {
            startActivityForResult(launchIntent, RC_OPEN_AUTH_ACTIVITY);
        }*/
        Intent intent = new Intent(this, GoogleAuthActivity.class);
        startActivityForResult(intent, RC_OPEN_AUTH_ACTIVITY);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == RC_OPEN_AUTH_ACTIVITY) {
            if (resultCode == Activity.RESULT_OK && data != null) {
                String idToken = data.getStringExtra("idToken");
                String fullName = data.getStringExtra("fullName");
                String email = data.getStringExtra("email");
                int userId = data.getIntExtra("userId", -1);

                SharedPreferences prefs = getSharedPreferences(getString(R.string.app_name), Context.MODE_PRIVATE);
                if (prefs.getInt("userId", -1) == -1)
                    logTextView.setText(getString(
                            R.string.account,
                            prefs.getString("email", null),
                            prefs.getInt("userId", -1),
                            prefs.getBoolean("isParticipant", false)
                    ));
            } else {
                logTextView.setText(getString(R.string.account, "N/A", -1, false));
            }
        }
    }

    public void logoutClick(View view) {
        GoogleSignInAccount account = GoogleSignIn.getLastSignedInAccount(this);
        if (account != null) {
            GoogleSignInOptions gso = new GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
                    .requestIdToken(getString(R.string.server_google_client_id))
                    .requestEmail()
                    .build();
            GoogleSignInClient signInClient = GoogleSignIn.getClient(this, gso);
            signInClient.signOut().addOnCompleteListener(this, task -> {
                logTextView.setText(getString(R.string.account, "N/A", -1, false));

                SharedPreferences prefs = getSharedPreferences(getString(R.string.app_name), Context.MODE_PRIVATE);
                SharedPreferences.Editor editor = prefs.edit();
                editor.clear();
                editor.apply();

                startActivityForResult(new Intent(this, GoogleAuthActivity.class), RC_OPEN_AUTH_ACTIVITY);
            });
        }
    }

    public void submitDataClick(View view) {
        new Thread(() -> {
            ManagedChannel channel = ManagedChannelBuilder.forAddress(
                    getString(R.string.grpc_server_ip),
                    Integer.parseInt(getString(R.string.grpc_server_port))
            ).usePlaintext().build();

            SharedPreferences prefs = getSharedPreferences(getString(R.string.app_name), Context.MODE_PRIVATE);
            int userId = prefs.getInt("userId", -1);
            String email = prefs.getString("email", null);
            int dataSource = 1;
            String values = "0.0,1.0,2.0";
            long timestamp = Calendar.getInstance().getTimeInMillis();

            ETServiceGrpc.ETServiceBlockingStub stub = ETServiceGrpc.newBlockingStub(channel);
            EtService.SubmitDataRequestMessage requestMessage = EtService.SubmitDataRequestMessage.newBuilder()
                    .setUserId(userId)
                    .setEmail(email)
                    .setDataSource(dataSource)
                    .setValues(values)
                    .setTimestamp(timestamp)
                    .build();
            try {
                EtService.DefaultResponseMessage responseMessage = stub.submitData(requestMessage);
                if (responseMessage.getDoneSuccessfully())
                    runOnUiThread(() -> Toast.makeText(MainActivity.this, "Data submitted successfully", Toast.LENGTH_SHORT).show());
                else
                    runOnUiThread(() -> Toast.makeText(MainActivity.this, "Failed to submit data", Toast.LENGTH_SHORT).show());
            } catch (StatusRuntimeException e) {
                Log.e(TAG, "submitDataClick: gRPC server unavailable");
            }

            try {
                channel.shutdown().awaitTermination(1, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }).start();
    }

    public void submitHeartbeatClick(View view) {
        new Thread(() -> {
            ManagedChannel channel = ManagedChannelBuilder.forAddress(
                    getString(R.string.grpc_server_ip),
                    Integer.parseInt(getString(R.string.grpc_server_port))
            ).usePlaintext().build();

            SharedPreferences prefs = getSharedPreferences(getString(R.string.app_name), Context.MODE_PRIVATE);
            int userId = prefs.getInt("userId", -1);
            String email = prefs.getString("email", null);

            ETServiceGrpc.ETServiceBlockingStub stub = ETServiceGrpc.newBlockingStub(channel);
            EtService.SubmitHeartbeatRequestMessage requestMessage = EtService.SubmitHeartbeatRequestMessage.newBuilder()
                    .setUserId(userId)
                    .setEmail(email)
                    .build();
            try {
                EtService.DefaultResponseMessage responseMessage = stub.submitHeartbeat(requestMessage);

                if (responseMessage.getDoneSuccessfully())
                    runOnUiThread(() -> Toast.makeText(MainActivity.this, "Heartbeat submitted successfully", Toast.LENGTH_SHORT).show());
                else
                    runOnUiThread(() -> Toast.makeText(MainActivity.this, "Failed to submit a heartbeat", Toast.LENGTH_SHORT).show());
            } catch (StatusRuntimeException e) {
                Log.e(TAG, "submitHeartbeatClick: gRPC server unavailable");
            }

            try {
                channel.shutdown().awaitTermination(1, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }).start();
    }

    public void submitDirectMessageClick(View view) {
        new Thread(() -> {
            ManagedChannel channel = ManagedChannelBuilder.forAddress(
                    getString(R.string.grpc_server_ip),
                    Integer.parseInt(getString(R.string.grpc_server_port))
            ).usePlaintext().build();

            SharedPreferences prefs = getSharedPreferences(getString(R.string.app_name), Context.MODE_PRIVATE);
            int userId = prefs.getInt("userId", -1);
            String email = prefs.getString("email", null);

            ETServiceGrpc.ETServiceBlockingStub stub = ETServiceGrpc.newBlockingStub(channel);
            EtService.SubmitDirectMessageRequestMessage requestMessage = EtService.SubmitDirectMessageRequestMessage.newBuilder()
                    .setUserId(userId)
                    .setEmail(email)
                    .setTargetEmail("nslabinha@gmail.com")
                    .setSubject("Test title")
                    .setContent("Test content")
                    .build();
            try {
                EtService.DefaultResponseMessage responseMessage = stub.submitDirectMessage(requestMessage);

                if (responseMessage.getDoneSuccessfully())
                    runOnUiThread(() -> Toast.makeText(MainActivity.this, "Direct message submitted successfully", Toast.LENGTH_SHORT).show());
                else
                    runOnUiThread(() -> Toast.makeText(MainActivity.this, "Failed to submit a direct message ", Toast.LENGTH_SHORT).show());
            } catch (StatusRuntimeException e) {
                Log.e(TAG, "submitDirectMessageClick: gRPC server unavailable");
            }

            try {
                channel.shutdown().awaitTermination(1, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }).start();
    }

    public void retrieveParticipantsClick(View view) {
        new Thread(() -> {
            ManagedChannel channel = ManagedChannelBuilder.forAddress(
                    getString(R.string.grpc_server_ip),
                    Integer.parseInt(getString(R.string.grpc_server_port))
            ).usePlaintext().build();

            SharedPreferences prefs = getSharedPreferences(getString(R.string.app_name), Context.MODE_PRIVATE);
            int userId = prefs.getInt("userId", -1);
            String email = prefs.getString("email", null);

            ETServiceGrpc.ETServiceBlockingStub stub = ETServiceGrpc.newBlockingStub(channel);
            EtService.RetrieveParticipantsRequestMessage requestMessage = EtService.RetrieveParticipantsRequestMessage.newBuilder()
                    .setUserId(userId)
                    .setEmail(email)
                    .build();
            try {
                EtService.RetrieveParticipantsResponseMessage responseMessage = stub.retrieveParticipants(requestMessage);

                if (responseMessage.getDoneSuccessfully()) {
                    runOnUiThread(() -> Toast.makeText(MainActivity.this, "Participants retrieved successfully", Toast.LENGTH_SHORT).show());
                    List<String> emails = responseMessage.getEmailList();
                    List<String> names = responseMessage.getNameList();
                    StringBuilder sb = new StringBuilder();
                    for (int n = 0; n < emails.size(); n++)
                        sb.append(String.format(Locale.getDefault(), "(%s, %s) ", emails.get(n), names.get(n)));
                    Log.e(TAG, "participants: " + sb.toString());
                } else
                    runOnUiThread(() -> Toast.makeText(MainActivity.this, "Failed to retrieve participants", Toast.LENGTH_SHORT).show());
            } catch (StatusRuntimeException e) {
                e.printStackTrace();
                Log.e(TAG, "retrieveParticipantsClick: gRPC server unavailable");
            }

            try {
                channel.shutdown().awaitTermination(1, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }).start();
    }

    public void retrieveParticipantStatisticsClick(View view) {
        new Thread(() -> {
            ManagedChannel channel = ManagedChannelBuilder.forAddress(
                    getString(R.string.grpc_server_ip),
                    Integer.parseInt(getString(R.string.grpc_server_port))
            ).usePlaintext().build();

            SharedPreferences prefs = getSharedPreferences(getString(R.string.app_name), Context.MODE_PRIVATE);
            int userId = prefs.getInt("userId", -1);
            String email = prefs.getString("email", null);

            ETServiceGrpc.ETServiceBlockingStub stub = ETServiceGrpc.newBlockingStub(channel);
            EtService.RetrieveParticipantStatisticsRequestMessage requestMessage = EtService.RetrieveParticipantStatisticsRequestMessage.newBuilder()
                    .setUserId(userId)
                    .setEmail(email)
                    .setTargetEmail("nslabinha@gmail.com")
                    .build();
            try {
                EtService.RetrieveParticipantStatisticsResponseMessage responseMessage = stub.retrieveParticipantStatistics(requestMessage);

                if (responseMessage.getDoneSuccessfully()) {
                    runOnUiThread(() -> Toast.makeText(MainActivity.this, "Participant statistics retrieved successfully", Toast.LENGTH_SHORT).show());
                    long lastSyncedTimestamp = responseMessage.getLastSyncedTimestamp();
                    long lastHeartbeatTimestamp = responseMessage.getLastHeartbeatTimestamp();
                    int amountOfSubmittedDataSamples = responseMessage.getAmountOfSubmittedDataSamples();
                    Log.e(TAG, String.format("lastSyncedTimestamp=%d, lastHeartbeatTimestamp=%d, amountOfSubmittedDataSamples=%d", lastSyncedTimestamp, lastHeartbeatTimestamp, amountOfSubmittedDataSamples));
                } else
                    runOnUiThread(() -> Toast.makeText(MainActivity.this, "Failed to retrieve participant statistics", Toast.LENGTH_SHORT).show());
            } catch (StatusRuntimeException e) {
                Log.e(TAG, "retrieveParticipantsClick: gRPC server unavailable");
            }

            try {
                channel.shutdown().awaitTermination(1, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }).start();
    }

    public void retrieveDataClick(View view) {
        new Thread(() -> {
            ManagedChannel channel = ManagedChannelBuilder.forAddress(
                    getString(R.string.grpc_server_ip),
                    Integer.parseInt(getString(R.string.grpc_server_port))
            ).usePlaintext().build();

            SharedPreferences prefs = getSharedPreferences(getString(R.string.app_name), Context.MODE_PRIVATE);
            int userId = prefs.getInt("userId", -1);
            String email = prefs.getString("email", null);

            ETServiceGrpc.ETServiceBlockingStub stub = ETServiceGrpc.newBlockingStub(channel);
            EtService.RetrieveDataRequestMessage requestMessage = EtService.RetrieveDataRequestMessage.newBuilder()
                    .setUserId(userId)
                    .setEmail(email)
                    .setFromTimestamp(1581075001123L)
                    .setTillTimestamp(-1)
                    .setTargetEmail("nslabinha@gmail.com")
                    .build();
            try {
                EtService.RetrieveDataResponseMessage responseMessage = stub.retrieveData(requestMessage);

                if (responseMessage.getDoneSuccessfully()) {
                    runOnUiThread(() -> Toast.makeText(MainActivity.this, "Data successfully", Toast.LENGTH_SHORT).show());
                    List<Long> timestampList = responseMessage.getTimestampList();
                    List<Integer> dataSourceList = responseMessage.getDataSourceList();
                    List<String> valueList = responseMessage.getValueList();
                    StringBuilder sb = new StringBuilder();
                    for (int n = 0; n < timestampList.size(); n++)
                        sb.append(String.format(Locale.getDefault(), "(%d, %d, %s) ", timestampList.get(n), dataSourceList.get(n), valueList.get(n)));
                    Log.e(TAG, "data: " + sb.toString());
                } else
                    runOnUiThread(() -> Toast.makeText(MainActivity.this, "Failed to retrieve data", Toast.LENGTH_SHORT).show());
            } catch (StatusRuntimeException e) {
                e.printStackTrace();
                Log.e(TAG, "retrieveDataClick: gRPC server unavailable");
            }

            try {
                channel.shutdown().awaitTermination(1, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }).start();
    }

    public void retrieveUnreadDirectMessagesClick(View view) {
        new Thread(() -> {
            ManagedChannel channel = ManagedChannelBuilder.forAddress(
                    getString(R.string.grpc_server_ip),
                    Integer.parseInt(getString(R.string.grpc_server_port))
            ).usePlaintext().build();

            SharedPreferences prefs = getSharedPreferences(getString(R.string.app_name), Context.MODE_PRIVATE);
            int userId = prefs.getInt("userId", -1);
            String email = prefs.getString("email", null);

            ETServiceGrpc.ETServiceBlockingStub stub = ETServiceGrpc.newBlockingStub(channel);
            EtService.RetrieveUnreadDirectMessagesRequestMessage requestMessage = EtService.RetrieveUnreadDirectMessagesRequestMessage.newBuilder()
                    .setUserId(userId)
                    .setEmail(email)
                    .build();
            try {
                EtService.RetrieveUnreadDirectMessagesResponseMessage responseMessage = stub.retrieveUnreadDirectMessages(requestMessage);

                if (responseMessage.getDoneSuccessfully()) {
                    runOnUiThread(() -> Toast.makeText(MainActivity.this, "Unread direct messages retrieved successfully", Toast.LENGTH_SHORT).show());
                    List<Long> timestampList = responseMessage.getTimestampList();
                    List<String> sourceEmailList = responseMessage.getSourceEmailList();
                    List<String> subjectList = responseMessage.getSubjectList();
                    List<String> contentList = responseMessage.getContentList();
                    StringBuilder sb = new StringBuilder();
                    for (int n = 0; n < timestampList.size(); n++)
                        sb.append(String.format(Locale.getDefault(), "(%d, %s, %s, %s) ", timestampList.get(n), sourceEmailList.get(n), subjectList.get(n), contentList.get(n)));
                    Log.e(TAG, "unread direct messages: " + sb.toString());
                } else
                    runOnUiThread(() -> Toast.makeText(MainActivity.this, "Failed to retrieve unread direct messages", Toast.LENGTH_SHORT).show());
            } catch (StatusRuntimeException e) {
                Log.e(TAG, "retrieveUnreadDirectMessagesClick: gRPC server unavailable");
            }

            try {
                channel.shutdown().awaitTermination(1, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }).start();
    }

    public void retrieveUnreadNotificationsClick(View view) {
        new Thread(() -> {
            ManagedChannel channel = ManagedChannelBuilder.forAddress(
                    getString(R.string.grpc_server_ip),
                    Integer.parseInt(getString(R.string.grpc_server_port))
            ).usePlaintext().build();

            SharedPreferences prefs = getSharedPreferences(getString(R.string.app_name), Context.MODE_PRIVATE);
            int userId = prefs.getInt("userId", -1);
            String email = prefs.getString("email", null);

            ETServiceGrpc.ETServiceBlockingStub stub = ETServiceGrpc.newBlockingStub(channel);
            EtService.RetrieveUnreadNotificationsRequestMessage requestMessage = EtService.RetrieveUnreadNotificationsRequestMessage.newBuilder()
                    .setUserId(userId)
                    .setEmail(email)
                    .build();
            try {
                EtService.RetrieveUnreadNotificationsResponseMessage responseMessage = stub.retrieveUnreadNotifications(requestMessage);

                if (responseMessage.getDoneSuccessfully()) {
                    runOnUiThread(() -> Toast.makeText(MainActivity.this, "Unread notifications retrieved successfully", Toast.LENGTH_SHORT).show());
                    List<Long> timestampList = responseMessage.getTimestampList();
                    List<String> subjectList = responseMessage.getSubjectList();
                    List<String> contentList = responseMessage.getContentList();
                    StringBuilder sb = new StringBuilder();
                    for (int n = 0; n < timestampList.size(); n++)
                        sb.append(String.format(Locale.getDefault(), "(%d, %s, %s) ", timestampList.get(n), subjectList.get(n), contentList.get(n)));
                    Log.e(TAG, "unread notifications: " + sb.toString());
                } else
                    runOnUiThread(() -> Toast.makeText(MainActivity.this, "Failed to retrieve unread notifications", Toast.LENGTH_SHORT).show());
            } catch (StatusRuntimeException e) {
                Log.e(TAG, "retrieveUnreadNotificationsClick: gRPC server unavailable");
            }

            try {
                channel.shutdown().awaitTermination(1, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }).start();
    }

    public void bindDataSourceClick(View view) {
        new Thread(() -> {
            ManagedChannel channel = ManagedChannelBuilder.forAddress(
                    getString(R.string.grpc_server_ip),
                    Integer.parseInt(getString(R.string.grpc_server_port))
            ).usePlaintext().build();

            SharedPreferences prefs = getSharedPreferences(getString(R.string.app_name), Context.MODE_PRIVATE);
            int userId = prefs.getInt("userId", -1);
            String email = prefs.getString("email", null);

            ETServiceGrpc.ETServiceBlockingStub stub = ETServiceGrpc.newBlockingStub(channel);
            EtService.BindDataSourceRequestMessage requestMessage = EtService.BindDataSourceRequestMessage.newBuilder()
                    .setUserId(userId)
                    .setEmail(email)
                    .setName("Test data source name")
                    .build();
            try {
                EtService.BindDataSourceResponseMessage responseMessage = stub.bindDataSource(requestMessage);

                if (responseMessage.getDoneSuccessfully()) {
                    runOnUiThread(() -> Toast.makeText(MainActivity.this, "Data source bound successfully", Toast.LENGTH_SHORT).show());
                    Log.e(TAG, "bound data source id: " + responseMessage.getDataSourceId());
                } else
                    runOnUiThread(() -> Toast.makeText(MainActivity.this, "Failed to retrieve unread notifications", Toast.LENGTH_SHORT).show());
            } catch (StatusRuntimeException e) {
                e.printStackTrace();
                Log.e(TAG, "bindDataSourceClick: gRPC server unavailable");
            }

            try {
                channel.shutdown().awaitTermination(1, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }).start();
    }

    public void retrieveAllDataSourcesClick(View view) {
        new Thread(() -> {
            ManagedChannel channel = ManagedChannelBuilder.forAddress(
                    getString(R.string.grpc_server_ip),
                    Integer.parseInt(getString(R.string.grpc_server_port))
            ).usePlaintext().build();

            SharedPreferences prefs = getSharedPreferences(getString(R.string.app_name), Context.MODE_PRIVATE);
            int userId = prefs.getInt("userId", -1);
            String email = prefs.getString("email", null);

            ETServiceGrpc.ETServiceBlockingStub stub = ETServiceGrpc.newBlockingStub(channel);
            EtService.RetrieveAllDataSourcesRequestMessage requestMessage = EtService.RetrieveAllDataSourcesRequestMessage.newBuilder()
                    .setUserId(userId)
                    .setEmail(email)
                    .build();
            try {
                EtService.RetrieveAllDataSourcesResponseMessage responseMessage = stub.retrieveAllDataSources(requestMessage);

                if (responseMessage.getDoneSuccessfully()) {
                    runOnUiThread(() -> Toast.makeText(MainActivity.this, "All data sources retrieved successfully", Toast.LENGTH_SHORT).show());
                    List<Integer> dataSourceIdList = responseMessage.getDataSourceIdList();
                    List<String> nameList = responseMessage.getNameList();
                    List<String> descriptionList = responseMessage.getDescriptionList();
                    StringBuilder sb = new StringBuilder();
                    for (int n = 0; n < dataSourceIdList.size(); n++)
                        sb.append(String.format(Locale.getDefault(), "(%d, %s, %s) ", dataSourceIdList.get(n), nameList.get(n), descriptionList.get(n)));
                    Log.e(TAG, "all data sources: " + sb.toString());
                } else
                    runOnUiThread(() -> Toast.makeText(MainActivity.this, "Failed to retrieve all data sources", Toast.LENGTH_SHORT).show());
            } catch (StatusRuntimeException e) {
                Log.e(TAG, "retrieveAllDataSourcesClick: gRPC server unavailable");
            }

            try {
                channel.shutdown().awaitTermination(1, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }).start();
    }
}
