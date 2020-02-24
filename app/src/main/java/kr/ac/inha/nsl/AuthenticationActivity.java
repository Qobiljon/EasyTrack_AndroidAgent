package kr.ac.inha.nsl;

import android.Manifest;
import android.app.Activity;
import android.app.AppOpsManager;
import android.app.usage.UsageStats;
import android.app.usage.UsageStatsManager;
import android.content.Context;
import android.content.Intent;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;

import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.util.Log;
import android.view.View;
import android.view.WindowManager;
import android.widget.Toast;

import java.net.InetAddress;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.List;
import java.util.Locale;

import inha.nslab.easytrack.ETServiceGrpc;
import inha.nslab.easytrack.EtService;
import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import io.grpc.StatusRuntimeException;

import static kr.ac.inha.nsl.MainActivity.TAG;

public class AuthenticationActivity extends AppCompatActivity {
    private static final int RC_OPEN_ET_AUTHENTICATOR = 100;
    private static final int RC_OPEN_MAIN_ACTIVITY = 101;
    private static final int RC_OPEN_APP_STORE = 102;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_authentication);

        if (authAppIsNotInstalled()) {
            Toast.makeText(this, "Please install the EasyTrack Authenticator and reopen the application!", Toast.LENGTH_SHORT).show();
            Intent intent = new Intent(Intent.ACTION_VIEW);
            intent.setData(Uri.parse("https://play.google.com/store/apps/details?id=inha.nslab.easytrack"));
            intent.setPackage("com.android.vending");
            startActivityForResult(intent, RC_OPEN_APP_STORE);
        } else {
            SharedPreferences prefs = getSharedPreferences(getPackageName(), Context.MODE_PRIVATE);
            if (prefs.getInt("userId", -1) != -1 && prefs.getString("email", null) != null)
                startMainActivity();
        }

        boolean askForPermission = false;
        if (isLocationPermissionDenied(this)) {
            int GPS_PERMISSION_REQUEST_CODE = 1;
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION}, GPS_PERMISSION_REQUEST_CODE);
            askForPermission = true;
        }
        if (isUsageAccessDenied(this) && android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.LOLLIPOP) {
            Intent intent = new Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS);
            int USAGE_ACCESS_PERMISSION_REQUEST_CODE = 2;
            startActivityForResult(intent, USAGE_ACCESS_PERMISSION_REQUEST_CODE);
            askForPermission = true;
        }
        if (askForPermission)
            Toast.makeText(this, "Please grant Usage Access and Location permissions first to use this app!", Toast.LENGTH_LONG).show();
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        if (requestCode == RC_OPEN_ET_AUTHENTICATOR) {
            if (resultCode == Activity.RESULT_OK) {
                if (data != null) {
                    String fullName = data.getStringExtra("fullName");
                    String email = data.getStringExtra("email");
                    int userId = data.getIntExtra("userId", -1);

                    new Thread(() -> {
                        ManagedChannel channel = ManagedChannelBuilder.forAddress(
                                getString(R.string.grpc_host),
                                Integer.parseInt(getString(R.string.grpc_port))
                        ).usePlaintext().build();

                        ETServiceGrpc.ETServiceBlockingStub stub = ETServiceGrpc.newBlockingStub(channel);
                        EtService.BindUserToCampaignRequestMessage requestMessage = EtService.BindUserToCampaignRequestMessage.newBuilder()
                                .setUserId(userId)
                                .setEmail(email)
                                .setCampaignId(Integer.parseInt(getString(R.string.easytrack_campaign_id)))
                                .build();

                        try {
                            EtService.BindUserToCampaignResponseMessage responseMessage = stub.bindUserToCampaign(requestMessage);
                            if (responseMessage.getDoneSuccessfully())
                                runOnUiThread(() -> {
                                    Toast.makeText(this, "Successfully authorized and connected to the EasyTrack campaign!", Toast.LENGTH_SHORT).show();
                                    SharedPreferences prefs = getSharedPreferences(getPackageName(), Context.MODE_PRIVATE);
                                    SharedPreferences.Editor editor = prefs.edit();
                                    editor.putString("fullName", fullName);
                                    editor.putString("email", email);
                                    editor.putInt("userId", userId);
                                    editor.apply();
                                    startMainActivity();
                                });
                            else
                                runOnUiThread(() -> {
                                    Calendar cal = Calendar.getInstance();
                                    cal.setTimeInMillis(responseMessage.getCampaignStartTimestamp());
                                    Toast.makeText(
                                            this,
                                            String.format(
                                                    Locale.getDefault(),
                                                    "EasyTrack campaign hasn't started. Campaign start time is: %s",
                                                    SimpleDateFormat.getDateTimeInstance().format(cal.getTime())
                                            ),
                                            Toast.LENGTH_LONG
                                    ).show();
                                });
                        } catch (StatusRuntimeException e) {
                            runOnUiThread(() -> Toast.makeText(this, "An error occurred when connecting to the EasyTrack campaign. Please try again later!", Toast.LENGTH_SHORT).show());
                            Log.e(TAG, "onCreate: gRPC server unavailable");
                        } finally {
                            channel.shutdown();
                        }
                    }).start();
                }
            } else if (resultCode == Activity.RESULT_FIRST_USER)
                Toast.makeText(this, "Canceled", Toast.LENGTH_SHORT).show();
            else if (resultCode == Activity.RESULT_CANCELED)
                Toast.makeText(this, "Technical issue. Please check your internet connectivity and try again!", Toast.LENGTH_SHORT).show();
        } else if (requestCode == RC_OPEN_MAIN_ACTIVITY) {
            finish();
        } else if (requestCode == RC_OPEN_APP_STORE) {
            if (authAppIsNotInstalled())
                finish();
        }
    }


    private void startMainActivity() {
        Intent intent = new Intent(this, MainActivity.class);
        intent.setFlags(0);
        startActivityForResult(intent, RC_OPEN_MAIN_ACTIVITY);
        overridePendingTransition(0, 0);
    }

    private boolean authAppIsNotInstalled() {
        try {
            getPackageManager().getPackageInfo("inha.nslab.easytrack", 0);
            return false;
        } catch (PackageManager.NameNotFoundException e) {
            return true;
        }
    }

    static boolean isGPSDeviceEnabled(Context context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.KITKAT)
            return false;

        try {
            return Settings.Secure.getInt(context.getContentResolver(), Settings.Secure.LOCATION_MODE) != Settings.Secure.LOCATION_MODE_OFF;
        } catch (Settings.SettingNotFoundException e) {
            e.printStackTrace();
            return false;
        }
    }

    static boolean isLocationPermissionDenied(Context context) {
        return ActivityCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED || ActivityCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED;
    }

    static boolean isUsageAccessDenied(@NonNull final Context context) {
        // Usage Stats is theoretically available on API v19+, but official/reliable support starts with API v21.
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP_MR1)
            return true;

        final AppOpsManager appOpsManager = (AppOpsManager) context.getSystemService(Context.APP_OPS_SERVICE);

        if (appOpsManager == null)
            return true;

        final int mode = appOpsManager.checkOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, android.os.Process.myUid(), context.getPackageName());
        if (mode != AppOpsManager.MODE_ALLOWED)
            return true;

        // Verify that access is possible. Some devices "lie" and return MODE_ALLOWED even when it's not.
        final long now = System.currentTimeMillis();
        final UsageStatsManager mUsageStatsManager = (UsageStatsManager) context.getSystemService(Context.USAGE_STATS_SERVICE);
        final List<UsageStats> stats;
        if (mUsageStatsManager == null)
            return true;
        stats = mUsageStatsManager.queryUsageStats(UsageStatsManager.INTERVAL_DAILY, now - 1000 * 10, now);
        return stats == null || stats.isEmpty();
    }

    static boolean isNetworkAvailable() {
        try {
            return !InetAddress.getByName("google.com").toString().equals("");
        } catch (Exception e) {
            return false;
        }
    }

    static void disableTouch(Activity activity) {
        activity.getWindow().setFlags(WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE, WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE);
    }

    static void enableTouch(Activity activity) {
        activity.getWindow().clearFlags(WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE);
    }


    public void loginButtonClick(View view) {
        if (authAppIsNotInstalled())
            Toast.makeText(this, "Please install the EasyTrack Authenticator and reopen the application!", Toast.LENGTH_SHORT).show();
        else {
            Intent launchIntent = getPackageManager().getLaunchIntentForPackage("inha.nsl.easytrack");
            if (launchIntent != null) {
                launchIntent.setFlags(0);
                startActivityForResult(launchIntent, RC_OPEN_ET_AUTHENTICATOR);
            }
        }
    }
}
