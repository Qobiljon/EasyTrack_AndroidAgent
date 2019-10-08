package kr.ac.inha.nsl;

import android.Manifest;
import android.content.Intent;
import android.content.SharedPreferences;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;

import android.os.Bundle;
import android.provider.Settings;
import android.view.View;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.Toast;

import org.apache.http.HttpResponse;
import org.apache.http.NameValuePair;
import org.apache.http.message.BasicNameValuePair;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.net.HttpURLConnection;
import java.util.ArrayList;
import java.util.List;

public class AuthenticationActivity extends AppCompatActivity {

    // region Constants
    private final int GPS_PERMISSION_REQUEST_CODE = 1;
    private final int USAGE_ACCESS_PERMISSION_REQUEST_CODE = 2;

    static final String KEY_USERNAME = "username";
    static final String KEY_PASSWORD = "password";
    static final String KEY_CAMPAIGN_ID = "campaign_id";
    static final String KEY_RECRUITED = "recruited";
    static final String KEY_CAMPAIGN_OWNER = "campaign_owner";
    static final String KEY_CAMPAIGN_NAME = "campaign_name";
    static final String KEY_CAMPAIGN_START_DATE = "campaign_start_date";
    static final String KEY_CAMPAIGN_END_DATE = "campaign_end_date";
    static final String KEY_CAMPAIGN_DESCRIPTION = "campaign_description";
    static final String KEY_CAMPAIGN_DATA_SOURCES_JSON = "data_sources_json";
    // endregion

    // region Variables
    private EditText usernameEditText;
    private EditText passwordEditText;
    private FrameLayout clickBlockSpace;
    // endregion

    // region Override
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_authentication);

        usernameEditText = findViewById(R.id.usernameEditText);
        passwordEditText = findViewById(R.id.passwordEditText);
        clickBlockSpace = findViewById(R.id.clickBlockPanel);

        boolean askForPermission = false;
        if (Tools.isLocationPermissionDenied(this)) {
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION}, GPS_PERMISSION_REQUEST_CODE);
            askForPermission = true;
        }
        if (Tools.isUsageAccessDenied(this)) {
            Intent intent = new Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS);
            startActivityForResult(intent, USAGE_ACCESS_PERMISSION_REQUEST_CODE);
            askForPermission = true;
        }
        if (askForPermission)
            Toast.makeText(this, "Please grant Usage Access and Location permissions first to use this app!", Toast.LENGTH_LONG).show();

        Tools.init(this);
        if (Tools.prefs.getString(KEY_USERNAME, null) != null && Tools.prefs.getString(KEY_PASSWORD, null) != null) {
            usernameEditText.setText(Tools.prefs.getString(KEY_USERNAME, null));
            passwordEditText.setText(Tools.prefs.getString(KEY_PASSWORD, null));
        }
    }

    @Override
    public void onBackPressed() {
        Tools.hideApp(this);
    }
    // endregion

    public void loginButtonClick(View view) {
        clickBlockSpace.setVisibility(View.VISIBLE);

        if (Tools.isUsageAccessDenied(this) || Tools.isLocationPermissionDenied(this)) {
            Toast.makeText(this, "Please grant Usage Access and Location permissions first to use this app!", Toast.LENGTH_SHORT).show();

            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION}, GPS_PERMISSION_REQUEST_CODE);
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION}, GPS_PERMISSION_REQUEST_CODE);
            Intent intent = new Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS);
            startActivityForResult(intent, USAGE_ACCESS_PERMISSION_REQUEST_CODE);

            clickBlockSpace.setVisibility(View.GONE);
            return;
        }

        new Thread(new Runnable() {
            @Override
            public void run() {
                int serverResult = Integer.MAX_VALUE;
                try {
                    List<NameValuePair> params = new ArrayList<>();
                    params.add(new BasicNameValuePair("username", usernameEditText.getText().toString()));
                    params.add(new BasicNameValuePair("password", passwordEditText.getText().toString()));
                    HttpResponse response = Tools.post(Tools.API_AUTHENTICATE, params, null);
                    Thread.sleep(1000);
                    if (response.getStatusLine().getStatusCode() == HttpURLConnection.HTTP_OK) {
                        JSONObject result = new JSONObject(Tools.inputStreamToString(response.getEntity().getContent()));
                        if (result.has("result") && result.getInt("result") == ServerResult.OK) {
                            runOnUiThread(new RunnableWithArguments(result) {
                                @Override
                                public void run() {
                                    runOnUiThread(new Runnable() {
                                        @Override
                                        public void run() {
                                            JSONObject result = (JSONObject) args[0];

                                            try {
                                                SharedPreferences.Editor editor = Tools.prefs.edit();
                                                editor.putString(KEY_USERNAME, usernameEditText.getText().toString());
                                                editor.putString(KEY_PASSWORD, passwordEditText.getText().toString());
                                                editor.putInt(KEY_CAMPAIGN_ID, result.getInt("campaign_id"));
                                                editor.putBoolean(KEY_RECRUITED, result.getBoolean("recruited"));
                                                editor.apply();

                                                clickBlockSpace.setVisibility(View.GONE);

                                                if (result.getBoolean("recruited")) {
                                                    usernameEditText.setText("");
                                                    passwordEditText.setText("");

                                                    Intent intent = new Intent(AuthenticationActivity.this, MainActivity.class);
                                                    startActivity(intent);
                                                } else
                                                    Toast.makeText(AuthenticationActivity.this, "You haven't been registered for any campaign yet, please get registered first!", Toast.LENGTH_LONG).show();
                                            } catch (JSONException e) {
                                                e.printStackTrace();
                                            }
                                        }
                                    });
                                }
                            });
                        } else {
                            serverResult = result.has("result") ? result.getInt("result") : -1;
                            throw new JSONException("Parameter 'result' wasn't in response json");
                        }
                    } else
                        throw new IOException("HTTP error while sending a post request for autentication");
                } catch (IOException | JSONException | InterruptedException e) {
                    e.printStackTrace();
                    runOnUiThread(new RunnableWithArguments(serverResult) {
                        @Override
                        public void run() {
                            clickBlockSpace.setVisibility(View.GONE);
                            switch ((int) args[0]) {
                                case ServerResult.TOO_LONG_PASSWORD:
                                    Toast.makeText(AuthenticationActivity.this, "Password length is too long, maximum length is " + 12, Toast.LENGTH_SHORT).show();
                                    break;
                                case ServerResult.TOO_SHORT_PASSWORD:
                                    Toast.makeText(AuthenticationActivity.this, "Password length is too short, minimum length is " + 8, Toast.LENGTH_SHORT).show();
                                    break;
                                case ServerResult.USERNAME_TAKEN:
                                    Toast.makeText(AuthenticationActivity.this, "Username is already taken, please try picking another username!", Toast.LENGTH_SHORT).show();
                                    break;
                                default:
                                    break;
                            }
                        }
                    });
                }
            }
        }).start();
    }

    public void registerButtonClick(View view) {
        clickBlockSpace.setVisibility(View.VISIBLE);
        new Thread(new Runnable() {
            @Override
            public void run() {
                int serverResult = Integer.MAX_VALUE;
                try {
                    List<NameValuePair> params = new ArrayList<>();
                    params.add(new BasicNameValuePair("username", usernameEditText.getText().toString()));
                    params.add(new BasicNameValuePair("password", passwordEditText.getText().toString()));
                    HttpResponse response = Tools.post(Tools.API_REGISTER, params, null);
                    if (response.getStatusLine().getStatusCode() == HttpURLConnection.HTTP_OK) {
                        JSONObject result = new JSONObject(Tools.inputStreamToString(response.getEntity().getContent()));
                        if (result.has("result") && result.getInt("result") == ServerResult.OK) {
                            SharedPreferences.Editor editor = Tools.prefs.edit();
                            editor.putBoolean("logged_in", true);
                            editor.putString("username", usernameEditText.getText().toString());
                            editor.putString("password", passwordEditText.getText().toString());
                            usernameEditText.setText("");
                            passwordEditText.setText("");
                            editor.apply();
                            runOnUiThread(new Runnable() {
                                @Override
                                public void run() {
                                    runOnUiThread(new Runnable() {
                                        @Override
                                        public void run() {
                                            clickBlockSpace.setVisibility(View.GONE);
                                            Toast.makeText(AuthenticationActivity.this, "Account successfully registered. You can sign in now!", Toast.LENGTH_LONG).show();
                                        }
                                    });
                                }
                            });
                        } else {
                            serverResult = result.getInt("result");
                            throw new JSONException("Parameter 'result' wasn't in response json");
                        }
                    } else
                        throw new IOException("HTTP error while sending a post request for autentication");
                } catch (IOException | JSONException e) {
                    e.printStackTrace();
                    runOnUiThread(new RunnableWithArguments(serverResult) {
                        @Override
                        public void run() {
                            clickBlockSpace.setVisibility(View.GONE);
                            switch ((int) args[0]) {
                                case ServerResult.TOO_LONG_PASSWORD:
                                    Toast.makeText(AuthenticationActivity.this, "Password length is too long, maximum length is " + 12, Toast.LENGTH_SHORT).show();
                                    break;
                                case ServerResult.TOO_SHORT_PASSWORD:
                                    Toast.makeText(AuthenticationActivity.this, "Password length is too short, minimum length is " + 8, Toast.LENGTH_SHORT).show();
                                    break;
                                case ServerResult.USERNAME_TAKEN:
                                    Toast.makeText(AuthenticationActivity.this, "Username is already taken, please try picking another username!", Toast.LENGTH_SHORT).show();
                                    break;
                                default:
                                    Toast.makeText(AuthenticationActivity.this, "Couldn't connect to server, please try again!", Toast.LENGTH_SHORT).show();
                                    break;
                            }
                        }
                    });
                }
            }
        }).start();
    }
}
