package kr.ac.inha.nsl;

import android.app.AlarmManager;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.os.Binder;
import android.os.Build;
import android.os.IBinder;
import android.os.SystemClock;
import android.util.Log;

import androidx.annotation.RequiresApi;
import androidx.core.app.NotificationCompat;

import static kr.ac.inha.nsl.MainActivity.TAG;

public class DataCollectorService extends Service {
    private final IBinder mBinder = new LocalBinder();

    @Override
    public IBinder onBind(Intent intent) {
        return mBinder;
    }

    @Override
    public void onCreate() {
        Log.e(TAG, "DataCollectorService.onCreate()");

        Intent notificationIntent = new Intent(this, AuthenticationActivity.class);
        PendingIntent pendingIntent = PendingIntent.getActivity(this, 0, notificationIntent, 0);
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            String channelId = createNotificationChannel("kr.ac.inha.nsl.DataCollectorService", "EasyTrack Data Collection Service");
            Notification notification = new Notification.Builder(this, channelId)
                    .setContentTitle("EasyTrack")
                    .setContentText("Data Collection service is running now...")
                    .setSmallIcon(R.mipmap.ic_launcher)
                    .setContentIntent(pendingIntent)
                    .build();
            startForeground(98765, notification);
        } else if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.JELLY_BEAN) {
            Notification notification = new Notification.Builder(this)
                    .setContentTitle("EasyTrack")
                    .setContentText("Data Collection service is running now...")
                    .setSmallIcon(R.mipmap.ic_launcher)
                    .setContentIntent(pendingIntent)
                    .build();
            startForeground(98765, notification);
        }

        super.onCreate();
    }

    @Override
    public void onDestroy() {
        Log.e(TAG, "DataCollectorService.onDestroy()");
        super.onDestroy();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        Log.e(TAG, "DataCollectorService.onStartCommand()");
        // return super.onStartCommand(intent, flags, startId);
        return START_STICKY;
    }

    @Override
    public void onTaskRemoved(Intent rootIntent) {
        Log.e(TAG, "DataCollectorService.onTaskRemoved()");
        
        // super.onTaskRemoved(rootIntent);
        Intent intent = new Intent(getApplicationContext(), DataCollectorService.class);
        intent.setPackage(Tools.PACKAGE_NAME);
        PendingIntent restartServicePendingIntent = PendingIntent.getService(getApplicationContext(), 1, intent, PendingIntent.FLAG_ONE_SHOT);

        AlarmManager alarmService = (AlarmManager) getApplicationContext().getSystemService(Context.ALARM_SERVICE);
        alarmService.set(AlarmManager.ELAPSED_REALTIME, SystemClock.elapsedRealtime() + 100, restartServicePendingIntent);
    }


    @RequiresApi(Build.VERSION_CODES.O)
    private String createNotificationChannel(String channelId, String channelName) {
        NotificationChannel chan = new NotificationChannel(channelId, channelName, NotificationManager.IMPORTANCE_DEFAULT);
        chan.setLightColor(Color.BLUE);
        chan.setLockscreenVisibility(Notification.VISIBILITY_PUBLIC);
        NotificationManager notificationManager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        assert notificationManager != null;
        notificationManager.createNotificationChannel(chan);
        return channelId;
    }

    private class LocalBinder extends Binder {
        //MF_DataCollectorService getService() {
        //    return MF_DataCollectorService.this;
        //}
    }
}