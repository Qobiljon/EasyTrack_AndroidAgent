package kr.ac.inha.nsl;

import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.database.sqlite.SQLiteDatabase;
import android.os.SystemClock;
import android.support.annotation.Nullable;
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
import java.io.FilenameFilter;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.Executors;
import java.util.regex.Pattern;

import static android.content.Context.MODE_PRIVATE;
import static java.lang.System.currentTimeMillis;

@SuppressWarnings("unused")
public class Tools {
    // region Variables
    static SharedPreferences prefs;
    static String APP_DIR;
    private static SQLiteDatabase db;
    // endregion

    // region Constant values
    static final long LAST_REBOOT_TIMESTAMP = currentTimeMillis() - SystemClock.elapsedRealtime();

    static final short CHANNEL_ID = 104;
    static final String API_REGISTER = "register";
    static final String API_UNREGISTER = "unregister";
    static final String API_AUTHENTICATE = "authenticate";
    static final String API_FETCH_CAMPAIGN_SETTINGS = "get_campaign_settings";
    static final String API_SUBMIT_DATA = "submit_data";
    static final String API_NOTIFY = "notify";
    private static final String API_SUBMIT_HEARTBEAT = "heartbeat";
    // endregion


    static void init(Context context) {
        APP_DIR = context.getFilesDir().getAbsolutePath();
        db = context.openOrCreateDatabase("EasyTrack_TizenAgent_LocalDB", MODE_PRIVATE, null);
        db.execSQL("CREATE TABLE IF NOT EXISTS SensorRecords(sensorId TINYINT DEFAULT(0), timestamp BIGINT DEFAULT(0), accuracy INT DEFAULT(0), data BLOB DEFAULT(NULL));");
        prefs = context.getSharedPreferences(context.getString(R.string.app_name), MODE_PRIVATE);
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

    static synchronized void saveSensorRecord(byte[] data) {
        db.execSQL("INSERT INTO SensorRecords(sensorId, timestamp, accuracy, data) VALUES(?, ?, ?, ?)", new Object[]{
                data[1],
                bytes2long(data),
                bytes2int(data),
                Arrays.copyOfRange(data, 14, data.length)
        });
    }

    @SuppressWarnings("unused")
    static synchronized void cleanDb() {
        db.execSQL("DELETE FROM SensorRecords WHERE 1;");
    }

    @SuppressWarnings("unused")
    static float bytes2float(final byte[] data, final int startIndex) {
        byte[] floatBytes = new byte[4];
        System.arraycopy(data, startIndex, floatBytes, 0, 4);
        return ByteBuffer.wrap(floatBytes).order(ByteOrder.LITTLE_ENDIAN).getFloat();
    }

    private static int bytes2int(final byte[] data) {
        byte[] intBytes = new byte[4];
        System.arraycopy(data, 10, intBytes, 0, 4);
        return ByteBuffer.wrap(intBytes).order(ByteOrder.LITTLE_ENDIAN).getInt();
    }

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

    static String[] getFiles(final String path) {
        final Pattern re = Pattern.compile("[0-9]+\\.csv");
        return new File(path).list(new FilenameFilter() {
            @Override
            public boolean accept(File dir, String name) {
                return new File(dir, name).isFile() && re.matcher(name).matches();
            }
        });
    }

    static int countSensorDataFiles() {
        String[] res = getFiles(APP_DIR);
        return res == null ? 0 : res.length;
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
}

@SuppressWarnings("unused")
class ServerResult {
    static final short OK = 0;
    static final short FAIL = 1;
    static final short BAD_JSON_PARAMETERS = 2;
    static final short USERNAME_TAKEN = 3;
    static final short TOO_SHORT_PASSWORD = 4;
    static final short TOO_LONG_PASSWORD = 5;
    static final short USER_DOES_NOT_EXIST = 6;
    static final short BAD_PASSWORD = 7;
}

@SuppressWarnings("unused")
class MessagingConstants {
    static final byte RES_OK = 0x01;
    static final byte RES_FAIL = 0x02;

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

class ConsumerServiceReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
        ArrayList<String> args = intent.getStringArrayListExtra("args");
        String action = args.remove(0);

        if (action.equals("updateTextView"))
            Log.e("ConsumerServiceReceiver", TextUtils.concat(args.toArray(new String[0])).toString());
        else if (action.equals("log"))
            Log.e("ConsumerServiceReceiver", TextUtils.concat(args.toArray(new String[0])).toString());
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
