package kr.ac.inha.nsl;

import android.Manifest;
import android.app.Activity;
import android.app.AppOpsManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.os.SystemClock;

import androidx.annotation.Nullable;
import androidx.core.app.ActivityCompat;

import android.text.TextUtils;
import android.util.Log;

import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.NameValuePair;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.mime.HttpMultipartMode;
import org.apache.http.entity.mime.MultipartEntityBuilder;
import org.apache.http.entity.mime.content.FileBody;
import org.apache.http.impl.client.DefaultHttpClient;
import org.apache.http.message.BasicNameValuePair;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.Executors;

import static android.content.Context.MODE_PRIVATE;
import static java.lang.System.currentTimeMillis;

public class Tools {
    // region Variables
    static SharedPreferences prefs;
    private static SQLiteDatabase db;
    static String PACKAGE_NAME;
    // endregion

    // region Constant values
    static final long LAST_REBOOT_TIMESTAMP = currentTimeMillis() - SystemClock.elapsedRealtime();

    @SuppressWarnings("unused")
    static final short CHANNEL_ID = 104;
    static final String API_REGISTER = "register";
    @SuppressWarnings("unused")
    static final String API_UNREGISTER = "unregister";
    static final String API_AUTHENTICATE = "authenticate";
    static final String API_FETCH_CAMPAIGN_SETTINGS = "get_campaign_settings";
    @SuppressWarnings("unused")
    static final String API_SUBMIT_DATA = "submit_data";
    @SuppressWarnings("unused")
    static final String API_NOTIFY = "notify";
    private static final String API_SUBMIT_HEARTBEAT = "heartbeat";
    // endregion

    static void init(Context context) {
        db = context.openOrCreateDatabase("EasyTrack_TizenAgent_LocalDB", MODE_PRIVATE, null);
        db.execSQL("CREATE TABLE IF NOT EXISTS SensorRecords(sensorId INT DEFAULT(0), timestamp BIGINT DEFAULT(0), accuracy FLOAT DEFAULT(0.0), data VARCHAR(164) DEFAULT(NULL));");
        prefs = context.getSharedPreferences(context.getString(R.string.app_name), MODE_PRIVATE);
        PACKAGE_NAME = context.getPackageName();
    }


    static void exitApp(Activity activity) {
        do {
            Activity current = activity;
            activity = activity.getParent();
            current.finishAffinity();
        } while (activity != null);
        System.exit(0);
    }

    static void hideApp(Activity activity) {
        Intent intent = new Intent(Intent.ACTION_MAIN);
        intent.addCategory(Intent.CATEGORY_HOME);
        activity.startActivity(intent);
    }


    static void saveNumericData(int sensorId, long timestamp, float accuracy, Number... data) {
        StringBuilder sb = new StringBuilder();
        if (data[0] instanceof Float)
            for (Number value : data) {
                sb.append(',');
                sb.append((float) value);
            }
        else if (data[0] instanceof Double)
            for (Number value : data) {
                sb.append(',');
                sb.append((double) value);
            }
        else if (data[0] instanceof Integer)
            for (Number value : data) {
                sb.append(',');
                sb.append((int) value);
            }
        saveStringData(sensorId, timestamp, accuracy, sb.substring(1));
    }

    static synchronized void saveStringData(int dataSourceId, long timestamp, float accuracy, String data) {
        db.execSQL("INSERT INTO SensorRecords(sensorId, timestamp, accuracy, data) VALUES(?, ?, ?, ?)", new Object[]{
                dataSourceId,
                timestamp,
                accuracy,
                data
        });
    }

    static synchronized void cleanDb() {
        db.execSQL("DELETE FROM SensorRecords WHERE 1;");
    }

    static void submitCollectedData() {
        /*String[] files = Tools.getFiles(Tools.APP_DIR);
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
                    samplesCounterTextView.setText(String.format(Locale.US, "%d%% UPLOADED", (n + 1) * 100 / fileNamesInLong.size()));
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
                samplesCounterTextView.setText("100%% UPLOADED");
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
        });*/
    }

    static int countSamples() {
        Cursor cursor = db.rawQuery("SELECT COUNT(*) FROM SensorRecords", new String[0]);
        int result = 0;
        if (cursor.moveToFirst())
            result = cursor.getInt(0);
        cursor.close();
        return result;
    }


    static boolean isLocationPermissionDenied(Context context) {
        return ActivityCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED || ActivityCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED;
    }

    static boolean isUsageAccessDenied(Context context) {
        try {
            PackageManager packageManager = context.getPackageManager();
            ApplicationInfo applicationInfo = packageManager.getApplicationInfo(context.getPackageName(), 0);
            AppOpsManager appOpsManager = (AppOpsManager) context.getSystemService(Context.APP_OPS_SERVICE);
            int mode = appOpsManager.checkOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, applicationInfo.uid, applicationInfo.packageName);
            return (mode != AppOpsManager.MODE_ALLOWED);
        } catch (PackageManager.NameNotFoundException e) {
            return true;
        }
    }

    static HttpResponse post(String api, List<NameValuePair> parameters, @Nullable File file) throws IOException {
        final String SERVER_URL = "http://165.246.43.97:36012";

        HttpPost httppost = new HttpPost(String.format(Locale.US, "%s/%s", SERVER_URL, api));
        HttpClient httpclient = new DefaultHttpClient();

        MultipartEntityBuilder entityBuilder = MultipartEntityBuilder.create();
        entityBuilder.setMode(HttpMultipartMode.STRICT);
        for (NameValuePair pair : parameters)
            entityBuilder.addTextBody(pair.getName(), pair.getValue());
        if (file != null)
            entityBuilder.addPart("file", new FileBody(file));
        HttpEntity entity = entityBuilder.build();

        httppost.setEntity(entity);
        return httpclient.execute(httppost);
    }

    static String inputStreamToString(InputStream is) throws IOException {
        InputStreamReader reader = new InputStreamReader(is);
        StringBuilder sb = new StringBuilder();

        char[] buf = new char[128];
        int read;
        while ((read = reader.read(buf)) > 0)
            sb.append(buf, 0, read);

        reader.close();
        return sb.toString();
    }

    static void sendHeartBeatMessage() {
        Executors.newCachedThreadPool().execute(new Runnable() {
            @Override
            public void run() {
                List<NameValuePair> params = new ArrayList<>();
                params.add(new BasicNameValuePair("username", Tools.prefs.getString("username", null)));
                params.add(new BasicNameValuePair("password", Tools.prefs.getString("password", null)));
                try {
                    post(API_SUBMIT_HEARTBEAT, params, null);
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        });
    }


    // region Tizen-service-related utility methods
    @SuppressWarnings("unused")
    static float bytes2float(final byte[] data, final int startIndex) {
        byte[] floatBytes = new byte[4];
        System.arraycopy(data, startIndex, floatBytes, 0, 4);
        return ByteBuffer.wrap(floatBytes).order(ByteOrder.LITTLE_ENDIAN).getFloat();
    }

    @SuppressWarnings("unused")
    private static int bytes2int(final byte[] data) {
        byte[] intBytes = new byte[4];
        System.arraycopy(data, 10, intBytes, 0, 4);
        return ByteBuffer.wrap(intBytes).order(ByteOrder.LITTLE_ENDIAN).getInt();
    }

    @SuppressWarnings("unused")
    private static long bytes2long(final byte[] data) {
        byte[] longBytes = new byte[8];
        System.arraycopy(data, 2, longBytes, 0, 8);
        return ByteBuffer.wrap(longBytes).order(ByteOrder.LITTLE_ENDIAN).getLong();
    }

    @SuppressWarnings("unused")
    static byte[] short2bytes(final short value) {
        return new byte[]{(byte) value, (byte) (value >> 8)};
    }

    private static String[] bytes2hexStrings(final byte[] bytes, final int offset) {
        String[] res = new String[bytes.length - offset];
        for (int n = offset; n < bytes.length; n++) {
            int intVal = bytes[n] & 0xff;
            res[n - offset] = "";
            if (intVal < 0x10)
                res[n - offset] += "0";
            res[n - offset] += Integer.toHexString(intVal).toUpperCase();
        }
        return res;
    }

    private static String[] bytes2hexStrings(final byte[] bytes) {
        return bytes2hexStrings(bytes, 0);
    }

    @SuppressWarnings("unused")
    public static String bytes2hexString(final byte[] bytes, final int offset) {
        return TextUtils.join(" ", bytes2hexStrings(bytes, offset));
    }

    @SuppressWarnings("unused")
    public static String bytes2hexString(final byte[] bytes) {
        return TextUtils.join(" ", bytes2hexStrings(bytes));
    }
    // endregion
}


class ServerResult {
    static final short OK = 0;
    @SuppressWarnings("unused")
    static final short FAIL = 1;
    @SuppressWarnings("unused")
    static final short BAD_JSON_PARAMETERS = 2;
    static final short USERNAME_TAKEN = 3;
    static final short TOO_SHORT_PASSWORD = 4;
    static final short TOO_LONG_PASSWORD = 5;
    @SuppressWarnings("unused")
    static final short USER_DOES_NOT_EXIST = 6;
    @SuppressWarnings("unused")
    static final short BAD_PASSWORD = 7;
}

class MessagingConstants {
    static final byte RES_OK = 0x01;
    static final byte RES_FAIL = 0x02;

    @SuppressWarnings("unused")
    static String name4constant(final byte value) {
        switch (value) {
            case RES_OK:
                return "SUCCESSFUL";
            case RES_FAIL:
                return "FAILURE";
            default:
                return null;
        }
    }
}

class DataCollectorServiceReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
        ArrayList<String> args = intent.getStringArrayListExtra("args");
        String action = args.remove(0);

        if (action.equals("updateTextView"))
            Log.e("TizenServiceReceiver", TextUtils.concat(args.toArray(new String[0])).toString());
        else if (action.equals("log"))
            Log.e("TizenServiceReceiver", TextUtils.concat(args.toArray(new String[0])).toString());
    }
}

abstract class StoppableRunnable implements Runnable {
    boolean terminate = false;
}

abstract class RunnableWithArguments implements Runnable {
    Object[] args;

    RunnableWithArguments(Object... args) {
        this.args = args;
    }
}
