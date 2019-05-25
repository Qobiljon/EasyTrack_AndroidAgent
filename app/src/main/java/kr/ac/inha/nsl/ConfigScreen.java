package kr.ac.inha.nsl;

import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.TextView;

import java.util.Locale;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

public class ConfigScreen extends AppCompatActivity {

    private TextView tvStatus;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_config_screen);

        tvStatus = findViewById(R.id.tvStatus);
        tvStatus.setText(getString(R.string.dummy));
    }

    @Override
    public void onBackPressed() {
        super.onBackPressed();
        overridePendingTransition(0, 0);
    }

    public void sendClick(View view) {
        Executor exec = Executors.newCachedThreadPool();
        exec.execute(new Runnable() {
            @Override
            public void run() {
                try {
                    // TODO: IMPORTANT, PLEASE MEMORIZE THE SEQUENCE
                    short intervalMs = 1000;
                    byte[] intervBytes = Tools.short2bytes(intervalMs);
                    byte[] durationBytes = Tools.short2bytes(SensorSampleDurations._1000ms(intervalMs));
                    byte[] data = new byte[]{SensorTypes.ALL, intervBytes[0], intervBytes[1], durationBytes[0], durationBytes[1]};

                    /*
                    for (byte SENSOR : SensorTypes.ALL_SENSORS) {
                        data[0] = SENSOR;
                        if (MainActivity.mConsumerService.sendData(data))
                            Log.i("SENSOR_DATA_REQUEST", String.format(Locale.US, "REQUESTED SENSOR %d FROM WEARABLE DEVICE", data[0]));
                        try {
                            Thread.sleep(10);
                        } catch (InterruptedException e) {
                            e.printStackTrace();
                        }
                    }
                    */

                    if (MainActivity.mConsumerService.sendData(data))
                        Log.i("SENSOR_DATA_REQUEST", String.format(Locale.US, "REQUESTED SENSOR %d FROM WEARABLE DEVICE", data[0]));
                    try {
                        Thread.sleep(10);
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }

                } catch (SensorSampleDurations.IncorrectDurationException e) {
                    e.printStackTrace();
                }
            }
        });
        tvStatus.setText(getString(R.string.dummy));
    }

    public void cleanDbClick(View view) {
        Tools.cleanDb();
    }
}
