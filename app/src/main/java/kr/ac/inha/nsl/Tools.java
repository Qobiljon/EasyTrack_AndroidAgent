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
import android.database.sqlite.SQLiteDatabase;
import android.os.IBinder;
import android.support.annotation.Nullable;
import android.text.TextUtils;
import android.util.Log;
import android.view.View;
import android.widget.Toast;

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

public class Tools {
    // region Variables
    static SharedPreferences prefs;
    static String APP_DIR;
    private static SQLiteDatabase db;
    // endregion

    // region Constant values
    static final short CHANNEL_ID = 104;
    static final String API_REGISTER = "register";
    static final String API_UNREGISTER = "unregister";
    static final String API_AUTHENTICATE = "authenticate";
    static final String API_SUBMIT_HEARTBEAT = "heartbeat";
    static final String API_SUBMIT_DATA = "submit_data";
    static final String API_NOTIFY = "notify";
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
        final String SERVER_URL = "http://165.246.43.162:36012";

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

class SensorTypes {
    static final byte ALL = 0x00;
    static final byte ACCELEROMETER = 0x01;
    static final byte GRAVITY = 0x02;
    static final byte LINEAR_ACCELERATION = 0x03;
    static final byte MAGNETIC = 0x04;
    static final byte ROTATION_VECTOR = 0x05;
    static final byte ORIENTATION = 0x06;
    static final byte GYROSCOPE = 0x07;
    static final byte LIGHT = 0x08;
    static final byte PROXIMITY = 0x09;
    static final byte PRESSURE = 0x0A;
    static final byte ULTRAVIOLET = 0x0B;
    static final byte TEMPERATURE = 0x0C;
    static final byte HUMIDITY = 0x0D;
    static final byte HRM = 0x0E;
    static final byte HRM_LED_GREEN = 0x0F;
    static final byte HRM_LED_IR = 0x10;
    static final byte HRM_LED_RED = 0x11;
    static final byte GYROSCOPE_UNCALIBRATED = 0x12;
    static final byte GEOMAGNETIC_UNCALIBRATED = 0x13;
    static final byte GYROSCOPE_ROTATION_VECTOR = 0x14;
    static final byte GEOMAGNETIC_ROTATION_VECTOR = 0x15;
    static final byte SIGNIFICANT_MOTION = 0x16;
    static final byte HUMAN_PEDOMETER = 0x17;
    static final byte HUMAN_SLEEP_MONITOR = 0x18;
    static final byte HUMAN_SLEEP_DETECTOR = 0x19;
    static final byte HUMAN_STRESS_MONITOR = 0x1A;

    static String name4sensor(final byte sensorId) {
        switch (sensorId) {
            case ACCELEROMETER:
                return "ACCELEROMETER";
            case GRAVITY:
                return "GRAVITY";
            case LINEAR_ACCELERATION:
                return "LINEAR_ACCELERATION";
            case MAGNETIC:
                return "MAGNETIC";
            case ROTATION_VECTOR:
                return "ROTATION_VECTOR";
            case ORIENTATION:
                return "ORIENTATION";
            case GYROSCOPE:
                return "GYROSCOPE";
            case LIGHT:
                return "LIGHT";
            case PROXIMITY:
                return "PROXIMITY";
            case PRESSURE:
                return "PRESSURE";
            case ULTRAVIOLET:
                return "ULTRAVIOLET";
            case TEMPERATURE:
                return "TEMPERATURE";
            case HUMIDITY:
                return "HUMIDITY";
            case HRM:
                return "HRM";
            case HRM_LED_GREEN:
                return "HRM_LED_GREEN";
            case HRM_LED_IR:
                return "HRM_LED_IR";
            case HRM_LED_RED:
                return "HRM_LED_RED";
            case GYROSCOPE_UNCALIBRATED:
                return "GYROSCOPE_UNCALIBRATED";
            case GEOMAGNETIC_UNCALIBRATED:
                return "GEOMAGNETIC_UNCALIBRATED";
            case GYROSCOPE_ROTATION_VECTOR:
                return "GYROSCOPE_ROTATION_VECTOR";
            case GEOMAGNETIC_ROTATION_VECTOR:
                return "GEOMAGNETIC_ROTATION_VECTOR";
            case SIGNIFICANT_MOTION:
                return "SIGNIFICANT_MOTION";
            case HUMAN_PEDOMETER:
                return "HUMAN_PEDOMETER";
            case HUMAN_SLEEP_MONITOR:
                return "HUMAN_SLEEP_MONITOR";
            case HUMAN_SLEEP_DETECTOR:
                return "HUMAN_SLEEP_DETECTOR";
            case HUMAN_STRESS_MONITOR:
                return "HUMAN_STRESS_MONITOR";
            default:
                return null;
        }
    }

    static final byte[] ALL_SENSORS = {
            ACCELEROMETER,
            GRAVITY,
            LINEAR_ACCELERATION,
            MAGNETIC,
            ROTATION_VECTOR,
            ORIENTATION,
            GYROSCOPE,
            LIGHT,
            PROXIMITY,
            PRESSURE,
            ULTRAVIOLET,
            TEMPERATURE,
            HUMIDITY,
            HRM,
            HRM_LED_GREEN,
            HRM_LED_IR,
            HRM_LED_RED,
            GYROSCOPE_UNCALIBRATED,
            GEOMAGNETIC_UNCALIBRATED,
            GYROSCOPE_ROTATION_VECTOR,
            GEOMAGNETIC_ROTATION_VECTOR,
            SIGNIFICANT_MOTION,
            HUMAN_PEDOMETER,
            HUMAN_SLEEP_MONITOR,
            HUMAN_SLEEP_DETECTOR,
            HUMAN_STRESS_MONITOR
    };
}

class SensorSampleDurations {
    static short _100ms(short ms_interval) throws IncorrectDurationException {
        if (ms_interval < 1 || ms_interval > 100 || 100 % ms_interval != 0)
            throw new IncorrectDurationException("Incorrect ms_interval value was passed: " + ms_interval);
        else
            return (short) (100 / ms_interval);
    }

    static short _250ms(short ms_interval) throws IncorrectDurationException {
        if (ms_interval < 1 || ms_interval > 250 || 250 % ms_interval != 0)
            throw new IncorrectDurationException("Incorrect ms_interval value was passed: " + ms_interval);
        else
            return (short) (250 / ms_interval);
    }

    static short _500ms(short ms_interval) throws IncorrectDurationException {
        if (ms_interval < 1 || ms_interval > 500 || 500 % ms_interval != 0)
            throw new IncorrectDurationException("Incorrect ms_interval value was passed: " + ms_interval);
        else
            return (short) (500 / ms_interval);
    }

    static short _1000ms(short ms_interval) throws IncorrectDurationException {
        if (ms_interval < 1 || ms_interval > 1000 || 1000 % ms_interval != 0)
            throw new IncorrectDurationException("Incorrect ms_interval value was passed: " + ms_interval);
        else
            return (short) (1000 / ms_interval);
    }

    static short _2500ms(short ms_interval) throws IncorrectDurationException {
        if (ms_interval < 1 || ms_interval > 2500 || 2500 % ms_interval != 0)
            throw new IncorrectDurationException("Incorrect ms_interval value was passed: " + ms_interval);
        else
            return (short) (2500 / ms_interval);
    }

    static short _5000ms(short ms_interval) throws IncorrectDurationException {
        if (ms_interval < 1 || ms_interval > 5000 || 5000 % ms_interval != 0)
            throw new IncorrectDurationException("Incorrect ms_interval value was passed: " + ms_interval);
        else
            return (short) (5000 / ms_interval);
    }

    static short _10000ms(short ms_interval) throws IncorrectDurationException {
        if (ms_interval < 1 || ms_interval > 10000 || 10000 % ms_interval != 0)
            throw new IncorrectDurationException("Incorrect ms_interval value was passed: " + ms_interval);
        else
            return (short) (10000 / ms_interval);
    }

    static short _15000ms(short ms_interval) throws IncorrectDurationException {
        if (ms_interval < 1 || ms_interval > 15000 || 15000 % ms_interval != 0)
            throw new IncorrectDurationException("Incorrect ms_interval value was passed: " + ms_interval);
        else
            return (short) (15000 / ms_interval);
    }

    static short _20000ms(short ms_interval) throws IncorrectDurationException {
        if (ms_interval < 1 || ms_interval > 20000 || 20000 % ms_interval != 0)
            throw new IncorrectDurationException("Incorrect ms_interval value was passed: " + ms_interval);
        else
            return (short) (20000 / ms_interval);
    }

    static short _30000ms(short ms_interval) throws IncorrectDurationException {
        if (ms_interval < 1 || ms_interval > 30000 || 30000 % ms_interval != 0)
            throw new IncorrectDurationException("Incorrect ms_interval value was passed: " + ms_interval);
        else
            return (short) (30000 / ms_interval);
    }

    static short _32000ms(short ms_interval) throws IncorrectDurationException {
        if (ms_interval < 1 || ms_interval > 32000 || 32000 % ms_interval != 0)
            throw new IncorrectDurationException("Incorrect ms_interval value was passed: " + ms_interval);
        else
            return (short) (32000 / ms_interval);
    }

    static class IncorrectDurationException extends Exception {
        IncorrectDurationException(String message) {
            super(message);
        }
    }
}

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

class Connection {
    // region Variables
    private ConsumerServiceReceiver receiver;
    private static EasyTrack_AndroidSAAAgent mEasyTrackAndroidAgent;
    private static boolean mIsBound = false;
    // tvStatus = findViewById(R.id.tvStatus);
    // endregion

    private void init(Context context) {
        // Bind service
        Intent intent = new Intent(context, EasyTrack_AndroidSAAAgent.class);
        mIsBound = context.bindService(intent, mConnection, Context.BIND_AUTO_CREATE);

        receiver = new ConsumerServiceReceiver();

        IntentFilter filter = new IntentFilter();
        filter.addAction("kr.ac.inha.nsl.EasyTrack_AndroidSAAAgent");
        context.registerReceiver(receiver, filter);
    }

    private void destroy(Context context) {
        context.unregisterReceiver(receiver);

        Log.e("CLOSING SERVICE", "CLOSING SERVICE");
        // Clean up connections
        // if (mIsBound && mEasyTrackAndroidAgent != null && !mEasyTrackAndroidAgent.closeConnection()) {
        //     tvStatus.setText(getString(R.string.disconnected));
        // }
        // Unbind service
        if (mIsBound) {
            context.unbindService(mConnection);
            mIsBound = false;
        }
    }

    @SuppressLint("SetTextI18n")
    private final ServiceConnection mConnection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName className, IBinder service) {
            mEasyTrackAndroidAgent = ((EasyTrack_AndroidSAAAgent.LocalBinder) service).getService();
            //tvStatus.setText("Service connected");
        }

        @Override
        public void onServiceDisconnected(ComponentName className) {
            mEasyTrackAndroidAgent = null;
            mIsBound = false;
            //tvStatus.setText("Service disconnected");
        }
    };

    public void connect(View view) {
        if (mIsBound && mEasyTrackAndroidAgent != null)
            mEasyTrackAndroidAgent.findPeers();
    }

    public void disconnect(Context context, View view) {
        if (mIsBound && mEasyTrackAndroidAgent != null && !mEasyTrackAndroidAgent.closeConnection()) {
            //tvStatus.setText(getString(R.string.disconnected));
            Toast.makeText(context, R.string.ConnectionDoesNotExists, Toast.LENGTH_SHORT).show();
        }
    }

    public class ConsumerServiceReceiver extends BroadcastReceiver {
        @Override
        public void onReceive(Context context, Intent intent) {
            ArrayList<String> args = intent.getStringArrayListExtra("args");
            String action = args.remove(0);

            //if (action.equals("updateTextView"))
            //    tvStatus.setText(TextUtils.concat(args.toArray(new String[0])).toString());
            //else if (action.equals("log"))
            //    Log.i("LOG", TextUtils.concat(args.toArray(new String[0])).toString());
        }
    }
}

abstract class StoppableRunnable implements Runnable {
    boolean terminate = false;
}

abstract class RunnableWithArguments implements Runnable {
    Object[] args;

    public RunnableWithArguments(Object... args) {
        this.args = args;
    }
}
