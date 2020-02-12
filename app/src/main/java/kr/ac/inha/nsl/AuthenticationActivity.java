package kr.ac.inha.nsl;

import android.Manifest;
import android.content.Intent;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;

import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Bundle;
import android.provider.Settings;
import android.view.View;
import android.widget.Toast;

public class AuthenticationActivity extends AppCompatActivity {
    private final int RC_OPEN_AUTH_ACTIVITY = 100;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_authentication);

        if (authAppIsNotInstalled()) {
            Toast.makeText(this, "Please install the EasyTrack Authenticator and reopen the application!", Toast.LENGTH_SHORT).show();
            Intent intent = new Intent(Intent.ACTION_VIEW);
            intent.setData(Uri.parse("https://play.google.com/store/apps/details?id=inha.nslab.easytrack"));
            intent.setPackage("com.android.vending");
            startActivity(intent);
        } else {
            boolean askForPermission = false;
            if (Tools.isLocationPermissionDenied(this)) {
                int GPS_PERMISSION_REQUEST_CODE = 1;
                ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION}, GPS_PERMISSION_REQUEST_CODE);
                askForPermission = true;
            }
            if (Tools.isUsageAccessDenied(this) && android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.LOLLIPOP) {
                Intent intent = new Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS);
                int USAGE_ACCESS_PERMISSION_REQUEST_CODE = 2;
                startActivityForResult(intent, USAGE_ACCESS_PERMISSION_REQUEST_CODE);
                askForPermission = true;
            }
            if (askForPermission)
                Toast.makeText(this, "Please grant Usage Access and Location permissions first to use this app!", Toast.LENGTH_LONG).show();

            Tools.init(this);
        }
    }

    @Override
    public void onBackPressed() {
        Tools.hideApp(this);
    }

    public void loginButtonClick(View view) {
        if (authAppIsNotInstalled())
            Toast.makeText(this, "Please install the EasyTrack Authenticator and reopen the application!", Toast.LENGTH_SHORT).show();
        else {
            Intent launchIntent = getPackageManager().getLaunchIntentForPackage("inha.nslab.easytrack");
            if (launchIntent != null) {
                launchIntent.setFlags(0);
                startActivityForResult(launchIntent, RC_OPEN_AUTH_ACTIVITY);
            }
        }
    }

    private boolean authAppIsNotInstalled() {
        try {
            getPackageManager().getPackageInfo("inha.nslab.easytrack", 0);
            return false;
        } catch (PackageManager.NameNotFoundException e) {
            return true;
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        if (requestCode == RC_OPEN_AUTH_ACTIVITY)
            if (data != null) {
                Toast.makeText(this, "Here!", Toast.LENGTH_SHORT).show();
            }
    }
}
