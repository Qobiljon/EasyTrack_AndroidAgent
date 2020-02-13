package kr.ac.inha.nsl;

import android.Manifest;
import android.app.Activity;
import android.app.AppOpsManager;
import android.app.usage.UsageStats;
import android.app.usage.UsageStatsManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.net.ConnectivityManager;
import android.os.Build;

import androidx.annotation.NonNull;
import androidx.core.app.ActivityCompat;

import android.provider.Settings;
import android.text.TextUtils;
import android.util.Log;
import android.view.WindowManager;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.net.InetAddress;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.Locale;

class Tools {
    static SharedPreferences prefs;
    private static SQLiteDatabase db;
    static String PACKAGE_NAME;
    static File locationDataFile;
    static File activityRecognitionDataFile;
    private static UsageStatsManager usageStatsManager;
    private static ConnectivityManager connectivityManager;


    static void init(Context context) {
        db = context.openOrCreateDatabase(context.getPackageName(), Context.MODE_PRIVATE, null);
        db.execSQL("CREATE TABLE IF NOT EXISTS SensorRecords(sensorId INT DEFAULT(0), timestamp BIGINT DEFAULT(0), accuracy FLOAT DEFAULT(0.0), data VARCHAR(164) DEFAULT(NULL));");
        prefs = context.getSharedPreferences(context.getString(R.string.app_name), Context.MODE_PRIVATE);
        PACKAGE_NAME = context.getPackageName();

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP_MR1)
            usageStatsManager = (UsageStatsManager) context.getSystemService(Context.USAGE_STATS_SERVICE);
        connectivityManager = (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);
    }

    static void initDataCollectorService(final Activity activity) {
        activity.stopService(new Intent(activity, DataCollectorService.class));
        activity.startService(new Intent(activity, DataCollectorService.class));
    }


    static void hideApp(Activity activity) {
        Intent intent = new Intent(Intent.ACTION_MAIN);
        intent.addCategory(Intent.CATEGORY_HOME);
        activity.startActivity(intent);
    }


    static void checkAndSendUsageAccessStats() {
        if (usageStatsManager == null || Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP_MR1)
            return;

        long lastSavedTimestamp = 0; // TODO fill this properly

        Calendar fromCal = Calendar.getInstance(Locale.getDefault());
        if (lastSavedTimestamp == -1)
            fromCal.add(Calendar.YEAR, -1);
        else
            fromCal.setTime(new Date(lastSavedTimestamp));
        Calendar tillCal = Calendar.getInstance(Locale.getDefault());
        tillCal.set(Calendar.MILLISECOND, 0);

        StringBuilder sb = new StringBuilder();
        for (UsageStats stats : usageStatsManager.queryUsageStats(UsageStatsManager.INTERVAL_BEST, fromCal.getTimeInMillis(), System.currentTimeMillis()))
            if (stats.getPackageName().equals(PACKAGE_NAME))
                if (sb.length() == 0)
                    sb.append(String.format(
                            Locale.getDefault(),
                            "%d %d",
                            stats.getLastTimeUsed() / 1000,
                            stats.getTotalTimeInForeground() / 1000
                    ));
                else
                    sb.append(String.format(
                            Locale.getDefault(),
                            ",%d %d",
                            stats.getLastTimeUsed() / 1000,
                            stats.getTotalTimeInForeground() / 1000
                    ));

        if (sb.length() > 0) {
            // TODO: send here
        }
    }

    static synchronized void checkAndSendLocationData() throws IOException {
        if (locationDataFile == null)
            return;

        String locationData = readLocationData();
        if (locationData.length() == 0)
            return;

        if (!Character.isDigit(locationData.charAt(locationData.length() - 1)))
            locationData = locationData.substring(0, locationData.length() - 1);

        // TODO: send here
    }

    static void checkAndSendActivityData() throws IOException {
        if (activityRecognitionDataFile == null)
            return;

        String activityRecognitionData = readActivityRecognitionData();
        if (activityRecognitionData.length() == 0)
            return;

        if (!Character.isDigit(activityRecognitionData.charAt(activityRecognitionData.length() - 1)))
            activityRecognitionData = activityRecognitionData.substring(0, activityRecognitionData.length() - 1);

        // TODO: send here
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
        if (mode != AppOpsManager.MODE_ALLOWED) {
            return true;
        }

        // Verify that access is possible. Some devices "lie" and return MODE_ALLOWED even when it's not.
        final long now = System.currentTimeMillis();
        final UsageStatsManager mUsageStatsManager = (UsageStatsManager) context.getSystemService(Context.USAGE_STATS_SERVICE);
        final List<UsageStats> stats;
        if (mUsageStatsManager == null)
            return true;
        stats = mUsageStatsManager.queryUsageStats(UsageStatsManager.INTERVAL_DAILY, now - 1000 * 10, now);
        return !(stats != null && !stats.isEmpty());
    }

    static boolean isNetworkAvailable() {
        try {
            return !InetAddress.getByName("google.com").toString().equals("");
        } catch (Exception e) {
            return false;
        }
    }


    static synchronized void storeLocationData(long timestamp, double latitude, double longitude, double altitude) throws IOException {
        FileWriter writer = new FileWriter(locationDataFile, true);
        writer.write(String.format(
                Locale.getDefault(),
                "%d %f %f %f\n",
                timestamp / 1000,
                latitude,
                longitude,
                altitude
        ));
        writer.close();
    }

    private static synchronized String readLocationData() throws IOException {
        StringBuilder result = new StringBuilder();

        FileReader reader = new FileReader(locationDataFile);
        char[] buf = new char[128];
        int read;
        while ((read = reader.read(buf)) > 0)
            result.append(buf, 0, read);

        return result.toString();
    }

    static synchronized void storeActivityRecognitionData(long timestamp, String activity, float confidence) throws IOException {
        FileWriter writer = new FileWriter(activityRecognitionDataFile, true);
        writer.write(String.format(
                Locale.getDefault(),
                "%d %s %f\n",
                timestamp / 1000,
                activity,
                confidence
        ));
        writer.close();
    }

    private static synchronized String readActivityRecognitionData() throws IOException {
        StringBuilder result = new StringBuilder();

        FileReader reader = new FileReader(activityRecognitionDataFile);
        char[] buf = new char[128];
        int read;
        while ((read = reader.read(buf)) > 0)
            result.append(buf, 0, read);

        return result.toString();
    }

    private static void writeToFile(Context context, String fileName, String data) {
        try {
            FileOutputStream outputStream = context.openFileOutput(fileName, Context.MODE_PRIVATE);
            OutputStreamWriter outputStreamWriter = new OutputStreamWriter(outputStream);
            outputStreamWriter.write(data);
            outputStreamWriter.close();
            outputStream.close();
        } catch (IOException e) {
            Log.e("Exception", "File write failed: " + e.toString());
        }
    }

    private static String readFromFile(Context context, String fileName) {
        String ret = "[]";

        try {
            InputStream inputStream = context.openFileInput(fileName);

            if (inputStream != null) {
                InputStreamReader inputStreamReader = new InputStreamReader(inputStream);
                BufferedReader bufferedReader = new BufferedReader(inputStreamReader);
                String receiveString;
                StringBuilder stringBuilder = new StringBuilder();

                while ((receiveString = bufferedReader.readLine()) != null)
                    stringBuilder.append(receiveString);

                bufferedReader.close();
                inputStream.close();
                ret = stringBuilder.toString();
            }
        } catch (FileNotFoundException e) {
            Log.e("Exception", "File not found: " + e.toString());
        } catch (IOException e) {
            Log.e("Exception", "Can not read file: " + e.toString());
        }

        return ret;
    }

    static void disableTouch(Activity activity) {
        activity.getWindow().setFlags(WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE, WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE);
    }

    static void enableTouch(Activity activity) {
        activity.getWindow().clearFlags(WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE);
    }
}

class DataCollectorServiceReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
        ArrayList<String> args = intent.getStringArrayListExtra("args");
        assert args != null;
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
