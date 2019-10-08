package kr.ac.inha.nsl;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;

import android.content.Intent;
import android.os.Handler;
import android.os.Binder;
import android.os.IBinder;
import android.widget.Toast;
import android.util.Log;

import com.samsung.android.sdk.SsdkUnsupportedException;
import com.samsung.android.sdk.accessory.SA;
import com.samsung.android.sdk.accessory.SAAgent;
import com.samsung.android.sdk.accessory.SAPeerAgent;
import com.samsung.android.sdk.accessory.SASocket;

public class TizenAgentService extends SAAgent {
    static final String TAG = "EasyTrack_SAAgent";
    private final IBinder mBinder = new LocalBinder();
    private ServiceConnection mConnectionHandler;
    private Handler mHandler = new Handler();

    protected TizenAgentService() {
        super(TAG, ServiceConnection.class);
    }

    @Override
    public void onCreate() {
        super.onCreate();
        SA mAccessory = new SA();
        try {
            mAccessory.initialize(this);
        } catch (SsdkUnsupportedException e) {
            if (processUnsupportedException(e)) {
                e.printStackTrace();
                //return;
            }
        } catch (Exception e1) {
            e1.printStackTrace();
            stopSelf();
        }
    }

    @Override
    public IBinder onBind(Intent intent) {
        return mBinder;
    }

    @Override
    protected void onFindPeerAgentsResponse(SAPeerAgent[] peerAgents, int result) {
        if ((result == SAAgent.PEER_AGENT_FOUND) && (peerAgents != null))
            for (SAPeerAgent peerAgent : peerAgents)
                requestServiceConnection(peerAgent);
        else if (result == SAAgent.FINDPEER_DEVICE_NOT_CONNECTED) {
            Toast.makeText(getApplicationContext(), "FINDPEER_DEVICE_NOT_CONNECTED", Toast.LENGTH_SHORT).show();
            sendResultBroadcast("updateTextView", "Disconnected");
        } else if (result == SAAgent.FINDPEER_SERVICE_NOT_FOUND) {
            Toast.makeText(getApplicationContext(), "FINDPEER_SERVICE_NOT_FOUND", Toast.LENGTH_SHORT).show();
            sendResultBroadcast("updateTextView", "Disconnected");
        } else
            Toast.makeText(getApplicationContext(), R.string.NoPeersFound, Toast.LENGTH_SHORT).show();
    }

    @Override
    protected void onServiceConnectionRequested(SAPeerAgent peerAgent) {
        if (peerAgent != null)
            acceptServiceConnectionRequest(peerAgent);
    }

    @Override
    protected void onServiceConnectionResponse(SAPeerAgent peerAgent, SASocket socket, int result) {
        if (result == SAAgent.CONNECTION_SUCCESS) {
            this.mConnectionHandler = (ServiceConnection) socket;
            sendResultBroadcast("updateTextView", getString(R.string.connected));
        } else if (result == SAAgent.CONNECTION_ALREADY_EXIST) {
            Toast.makeText(getBaseContext(), "CONNECTION_ALREADY_EXIST", Toast.LENGTH_SHORT).show();
            sendResultBroadcast("updateTextView", getString(R.string.connected));
        } else if (result == SAAgent.CONNECTION_DUPLICATE_REQUEST) {
            Toast.makeText(getBaseContext(), "CONNECTION_DUPLICATE_REQUEST", Toast.LENGTH_SHORT).show();
        } else {
            Toast.makeText(getBaseContext(), R.string.ConnectionFailure, Toast.LENGTH_SHORT).show();
        }
    }

    @Override
    protected void onError(SAPeerAgent peerAgent, String errorMessage, int errorCode) {
        super.onError(peerAgent, errorMessage, errorCode);
    }

    @Override
    protected void onPeerAgentsUpdated(SAPeerAgent[] peerAgents, int result) {
        final SAPeerAgent[] peers = peerAgents;
        final int status = result;
        mHandler.post(new Runnable() {
            @Override
            public void run() {
                if (peers != null) {
                    if (status == SAAgent.PEER_AGENT_AVAILABLE) {
                        Toast.makeText(getApplicationContext(), "PEER_AGENT_AVAILABLE", Toast.LENGTH_SHORT).show();
                    } else {
                        Toast.makeText(getApplicationContext(), "PEER_AGENT_UNAVAILABLE", Toast.LENGTH_SHORT).show();
                    }
                }
            }
        });
    }

    public class ServiceConnection extends SASocket {
        public ServiceConnection() {
            super(ServiceConnection.class.getName());
        }

        @Override
        public void onError(int channelId, String errorMessage, int errorCode) {
        }

        @Override
        public void onReceive(int channelId, byte[] data) {
            //noinspection StatementWithEmptyBody
            if (data[0] == MessagingConstants.RES_OK) {
                // TODO: Tools.saveNumericData(data);
                //sendResultBroadcast("log", "RECV_DATA: ", String.format(Locale.US, "SENS=%d DLEN=%d", data[1], data.length - 14));
            } else if (data[0] == MessagingConstants.RES_FAIL)
                sendResultBroadcast("log", "RECV_DATA: ", "RES SENSOR FAIL (UNAVAILABLE)");
            else
                sendResultBroadcast("log", "RECV_DATA: ", "UNRECOGNIZED MESSAGE");
        }

        @Override
        protected void onServiceConnectionLost(int reason) {
            sendResultBroadcast("updateTextView", "Disconnected");
            closeConnection();
        }
    }

    class LocalBinder extends Binder {
        TizenAgentService getService() {
            return TizenAgentService.this;
        }
    }

    public void findPeers() {
        findPeerAgents();
    }

    @SuppressWarnings("unused")
    public synchronized boolean sendData(final byte[] data) {
        boolean retVal = false;
        if (mConnectionHandler != null) {
            try {
                mConnectionHandler.send(getServiceChannelId(0), data);
                retVal = true;
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        return retVal;
    }

    public boolean closeConnection() {
        if (mConnectionHandler != null) {
            mConnectionHandler.close();
            mConnectionHandler = null;
            return true;
        } else {
            return false;
        }
    }

    private boolean processUnsupportedException(SsdkUnsupportedException e) {
        e.printStackTrace();
        int errType = e.getType();
        if (errType == SsdkUnsupportedException.VENDOR_NOT_SUPPORTED || errType == SsdkUnsupportedException.DEVICE_NOT_SUPPORTED)
            stopSelf();
        else if (errType == SsdkUnsupportedException.LIBRARY_NOT_INSTALLED)
            Log.e(TAG, "You need to install Samsung Accessory SDK to use this application.");
        else if (errType == SsdkUnsupportedException.LIBRARY_UPDATE_IS_REQUIRED)
            Log.e(TAG, "You need to update Samsung Accessory SDK to use this application.");
        else if (errType == SsdkUnsupportedException.LIBRARY_UPDATE_IS_RECOMMENDED) {
            Log.e(TAG, "We recommend that you update your Samsung Accessory SDK before using this application.");
            return false;
        }
        return true;
    }

    public void sendResultBroadcast(String... args) {
        Intent intent = new Intent();
        intent.setAction("kr.ac.inha.nsl.TizenAgentService");
        if (args.length > 0) {
            intent.putStringArrayListExtra("args", new ArrayList<>(Arrays.asList(args)));
            sendBroadcast(intent);
        }
    }
}