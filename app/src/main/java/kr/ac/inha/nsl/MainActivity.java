package kr.ac.inha.nsl;

import java.util.ArrayList;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.os.Bundle;
import android.os.IBinder;
import android.text.TextUtils;
import android.util.Log;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;

public class MainActivity extends Activity {
    private TextView tvStatus;
    static boolean mIsBound = false;
    static ConsumerService mConsumerService;
    private ConsumerServiceReceiver receiver;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Tools.init(this);
        setContentView(R.layout.activity_main);
        tvStatus = findViewById(R.id.tvStatus);
        // Bind service
        Intent intent = new Intent(MainActivity.this, ConsumerService.class);
        mIsBound = bindService(intent, mConnection, Context.BIND_AUTO_CREATE);

        receiver = new ConsumerServiceReceiver();
    }

    @Override
    protected void onStart() {
        super.onStart();
        IntentFilter filter = new IntentFilter();
        filter.addAction("kr.ac.inha.nsl.ConsumerService");
        registerReceiver(receiver, filter);
    }

    @Override
    protected void onStop() {
        super.onStop();
        unregisterReceiver(receiver);
    }

    @Override
    protected void onDestroy() {
        Log.e("CLOSING SERVICE", "CLOSING SERVICE");
        // Clean up connections
        if (mIsBound && mConsumerService != null && !mConsumerService.closeConnection()) {
            tvStatus.setText(getString(R.string.disconnected));
        }
        // Unbind service
        if (mIsBound) {
            unbindService(mConnection);
            mIsBound = false;
        }
        super.onDestroy();
    }

    @SuppressLint("SetTextI18n")
    private final ServiceConnection mConnection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName className, IBinder service) {
            mConsumerService = ((ConsumerService.LocalBinder) service).getService();
            tvStatus.setText("Service connected");
        }

        @Override
        public void onServiceDisconnected(ComponentName className) {
            mConsumerService = null;
            mIsBound = false;
            tvStatus.setText("Service disconnected");
        }
    };

    public void connectClick(View view) {
        if (mIsBound && mConsumerService != null)
            mConsumerService.findPeers();
    }

    public void disconnectClick(View view) {
        if (mIsBound && mConsumerService != null && !mConsumerService.closeConnection()) {
            tvStatus.setText(getString(R.string.disconnected));
            Toast.makeText(getApplicationContext(), R.string.ConnectionDoesNotExists, Toast.LENGTH_SHORT).show();
        }
    }

    public class ConsumerServiceReceiver extends BroadcastReceiver {
        @Override
        public void onReceive(Context context, Intent intent) {
            ArrayList<String> args = intent.getStringArrayListExtra("args");
            String action = args.remove(0);

            if (action.equals("updateTextView"))
                tvStatus.setText(TextUtils.concat(args.toArray(new String[0])).toString());
            else if (action.equals("log"))
                Log.i("LOG", TextUtils.concat(args.toArray(new String[0])).toString());
        }
    }
}
