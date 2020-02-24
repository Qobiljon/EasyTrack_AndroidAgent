package kr.ac.inha.nsl

import android.app.Activity
import android.content.*
import android.os.Bundle
import android.os.IBinder
import android.util.Log
import android.view.View
import android.widget.Toast
import inha.nsl.easytrack.ETServiceGrpc
import inha.nsl.easytrack.EtService.RetrieveCampaignRequestMessage
import io.grpc.ManagedChannelBuilder
import io.grpc.StatusRuntimeException
import kotlinx.android.synthetic.main.activity_main.*
import org.json.JSONArray
import org.json.JSONException
import java.util.*

class MainActivity : Activity() {
    private lateinit var dataCollectorServiceConnection: ServiceConnection
    private var dataCollectorServiceBound = false
    private var runThread = false

    override fun onCreate(savedInstanceState: Bundle?) {
        Log.e(TAG, "MainActivity.onBackPressed()")
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        val prefs = getSharedPreferences(packageName, Context.MODE_PRIVATE)
        usernameTextView.text = String.format("User: %s", prefs.getString("email", null))

        DbMgr.init(this)
        loadCampaign(getSharedPreferences(packageName, Context.MODE_PRIVATE))
    }

    override fun onDestroy() {
        Log.e(TAG, "MainActivity.onDestroy()")
        if (dataCollectorServiceBound) unbindService(dataCollectorServiceConnection)
        super.onDestroy()
    }

    override fun onBackPressed() {
        Log.e(TAG, "MainActivity.onBackPressed()")
        val intent = Intent(Intent.ACTION_MAIN)
        intent.addCategory(Intent.CATEGORY_HOME)
        startActivity(intent)
    }

    override fun onPause() {
        Log.e(TAG, "MainActivity.onPause()")
        runThread = false
        super.onPause()
    }

    override fun onResume() {
        Log.e(TAG, "MainActivity.onResume()")
        /*runThread = true;
        new Thread(() -> {
            while (runThread) {
                logTextView.setText(getString(R.string.samples_count, DbMgr.countSamples()));
                try {
                    Thread.sleep(500);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }
        }).start();*/super.onResume()
    }

    private fun loadCampaign(prefs: SharedPreferences) {
        Thread(Runnable {
            val channel = ManagedChannelBuilder.forAddress(
                    getString(R.string.grpc_host), getString(R.string.grpc_port).toInt()).usePlaintext().build()
            try {
                val stub = ETServiceGrpc.newBlockingStub(channel)
                val retrieveCampaignRequestMessage = RetrieveCampaignRequestMessage.newBuilder()
                        .setUserId(prefs.getInt("userId", -1))
                        .setEmail(prefs.getString("email", null))
                        .setCampaignId(getString(R.string.easytrack_campaign_id).toInt())
                        .build()
                val retrieveCampaignResponseMessage = stub.retrieveCampaign(retrieveCampaignRequestMessage)
                if (retrieveCampaignResponseMessage.doneSuccessfully) {
                    setUpCampaignConfigurations(
                            retrieveCampaignResponseMessage.name,
                            retrieveCampaignResponseMessage.notes,
                            retrieveCampaignResponseMessage.startTimestamp,
                            retrieveCampaignResponseMessage.endTimestamp,
                            prefs
                    )
                    stopDataCollectionService()
                    startDataCollectionService()
                } else
                    runOnUiThread { Toast.makeText(this, "", Toast.LENGTH_SHORT).show() }
            } catch (e: StatusRuntimeException) {
                e.printStackTrace()
            } catch (e: JSONException) {
                e.printStackTrace()
            } finally {
                channel.shutdown()
            }
        }).start()
    }

    @Throws(JSONException::class)
    private fun setUpCampaignConfigurations(campaignName: String, campaignConfigJson: String, campaignStartTimestamp: Long, campaignEndTimestamp: Long, prefs: SharedPreferences) {
        val fromCal = Calendar.getInstance()
        val tillCal = Calendar.getInstance()
        fromCal.timeInMillis = campaignStartTimestamp
        tillCal.timeInMillis = campaignEndTimestamp
        val oldConfigJson = prefs.getString(String.format(Locale.getDefault(), "%s_configJson", campaignName), null)
        if (campaignConfigJson == oldConfigJson) return
        val editor = prefs.edit()
        editor.putString(String.format(Locale.getDefault(), "%s_configJson", campaignName), campaignConfigJson)
        val sb = StringBuilder()
        val dataSourceArray = JSONArray(campaignConfigJson)
        for (n in 0 until dataSourceArray.length()) {
            val dataSource = dataSourceArray.getJSONObject(n)
            val dataSourceName = dataSource.getString("name")
            editor.putInt(dataSourceName, dataSource.getInt("data_source_id"))
            editor.putString(String.format(Locale.getDefault(), "config_json_%s", dataSourceName), dataSource.getString("config_json"))
            sb.append(dataSourceName).append(',')
        }
        if (sb.isNotEmpty()) sb.replace(sb.length - 1, sb.length, "")
        editor.putString("dataSourceNames", sb.toString())
        editor.apply()
    }

    private fun startDataCollectionService() { // Start service
        val intent = Intent(this, DataCollectorService::class.java)
        startService(intent)
        // Bind service
        dataCollectorServiceConnection = object : ServiceConnection {
            override fun onServiceConnected(name: ComponentName, service: IBinder) {
                dataCollectorServiceBound = true
                Log.e(TAG, "DataCollectorService has been bound to MainActivity")
            }

            override fun onServiceDisconnected(name: ComponentName) {
                dataCollectorServiceBound = false
                Log.e(TAG, "DataCollectorService has been unbound from MainActivity")
            }
        }
        dataCollectorServiceBound = bindService(intent, dataCollectorServiceConnection, Context.BIND_AUTO_CREATE)
    }

    private fun stopDataCollectionService() { // Unbind the service
        if (dataCollectorServiceBound) {
            unbindService(dataCollectorServiceConnection)
            dataCollectorServiceBound = false
        }
        // Stop the service
        val intent = Intent(this, DataCollectorService::class.java)
        stopService(intent)
    }

    fun logoutButtonClick(view: View) {
        val prefs = getSharedPreferences(packageName, Context.MODE_PRIVATE)
        val editor = prefs.edit()
        editor.clear()
        editor.apply()
        finish()
    }

    companion object {
        var TAG = "EasyTrack"
    }
}