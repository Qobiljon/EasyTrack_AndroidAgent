package kr.ac.inha.nsl;

import android.content.Intent;
import android.content.SharedPreferences;
import android.os.ResultReceiver;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
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

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_authentication);

        usernameEditText = findViewById(R.id.usernameEditText);
        passwordEditText = findViewById(R.id.passwordEditText);
        clickBlockSpace = findViewById(R.id.clickBlockPanel);
        Tools.init(this);
        if (Tools.prefs.getBoolean("logged_in", false)) {
            Intent intent = new Intent(this, MainActivity.class);
            startActivity(intent);
            overridePendingTransition(0, 0);
        }
    }

    @Override
    public void onBackPressed() {
        Tools.hideApp(this);
    }

    // region Variables
    private EditText usernameEditText;
    private EditText passwordEditText;
    private FrameLayout clickBlockSpace;
    // endregion

    public void loginButtonClick(View view) {
        clickBlockSpace.setVisibility(View.VISIBLE);
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
                                                editor.putBoolean("logged_in", true);
                                                editor.putString("username", usernameEditText.getText().toString());
                                                editor.putString("password", passwordEditText.getText().toString());
                                                editor.putInt("campaign_id", result.getInt("campaign_id"));
                                                editor.putBoolean("recruited", result.getBoolean("recruited"));
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
