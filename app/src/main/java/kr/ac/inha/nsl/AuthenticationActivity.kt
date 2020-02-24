package kr.ac.inha.nsl

import android.Manifest
import android.app.Activity
import android.app.AppOpsManager
import android.app.usage.UsageStats
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Process
import android.provider.Settings
import android.provider.Settings.SettingNotFoundException
import android.util.Log
import android.view.View
import android.view.WindowManager
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import inha.nsl.easytrack.ETServiceGrpc
import inha.nsl.easytrack.EtService.BindUserToCampaignRequestMessage
import io.grpc.ManagedChannelBuilder
import io.grpc.StatusRuntimeException
import java.net.InetAddress
import java.text.SimpleDateFormat
import java.util.*

class AuthenticationActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_authentication)
        if (authAppIsNotInstalled()) {
            Toast.makeText(this, "Please install the EasyTrack Authenticator and reopen the application!", Toast.LENGTH_SHORT).show()
            val intent = Intent(Intent.ACTION_VIEW)
            intent.data = Uri.parse("https://play.google.com/store/apps/details?id=inha.nsl.easytrack")
            intent.setPackage("com.android.vending")
            startActivityForResult(intent, RC_OPEN_APP_STORE)
        } else {
            val prefs = getSharedPreferences(packageName, Context.MODE_PRIVATE)
            if (prefs.getInt("userId", -1) != -1 && prefs.getString("email", null) != null) startMainActivity()
        }
        var askForPermission = false
        if (isLocationPermissionDenied(this)) {
            val GPS_PERMISSION_REQUEST_CODE = 1
            ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION), GPS_PERMISSION_REQUEST_CODE)
            askForPermission = true
        }
        if (isUsageAccessDenied(this) && Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            val intent = Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS)
            val USAGE_ACCESS_PERMISSION_REQUEST_CODE = 2
            startActivityForResult(intent, USAGE_ACCESS_PERMISSION_REQUEST_CODE)
            askForPermission = true
        }
        if (askForPermission) Toast.makeText(this, "Please grant Usage Access and Location permissions first to use this app!", Toast.LENGTH_LONG).show()
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == RC_OPEN_ET_AUTHENTICATOR) {
            if (resultCode == Activity.RESULT_OK) {
                if (data != null) {
                    val fullName = data.getStringExtra("fullName")
                    val email = data.getStringExtra("email")
                    val userId = data.getIntExtra("userId", -1)
                    Thread(Runnable {
                        val channel = ManagedChannelBuilder.forAddress(
                                getString(R.string.grpc_host), getString(R.string.grpc_port).toInt()).usePlaintext().build()
                        val stub = ETServiceGrpc.newBlockingStub(channel)
                        val requestMessage = BindUserToCampaignRequestMessage.newBuilder()
                                .setUserId(userId)
                                .setEmail(email)
                                .setCampaignId(getString(R.string.easytrack_campaign_id).toInt())
                                .build()
                        try {
                            val responseMessage = stub.bindUserToCampaign(requestMessage)
                            if (responseMessage.doneSuccessfully) runOnUiThread {
                                Toast.makeText(this, "Successfully authorized and connected to the EasyTrack campaign!", Toast.LENGTH_SHORT).show()
                                val prefs = getSharedPreferences(packageName, Context.MODE_PRIVATE)
                                val editor = prefs.edit()
                                editor.putString("fullName", fullName)
                                editor.putString("email", email)
                                editor.putInt("userId", userId)
                                editor.apply()
                                startMainActivity()
                            } else runOnUiThread {
                                val cal = Calendar.getInstance()
                                cal.timeInMillis = responseMessage.campaignStartTimestamp
                                Toast.makeText(
                                        this, String.format(
                                        Locale.getDefault(),
                                        "EasyTrack campaign hasn't started. Campaign start time is: %s",
                                        SimpleDateFormat.getDateTimeInstance().format(cal.time)
                                ),
                                        Toast.LENGTH_LONG
                                ).show()
                            }
                        } catch (e: StatusRuntimeException) {
                            runOnUiThread { Toast.makeText(this, "An error occurred when connecting to the EasyTrack campaign. Please try again later!", Toast.LENGTH_SHORT).show() }
                            Log.e(MainActivity.TAG, "onCreate: gRPC server unavailable")
                        } finally {
                            channel.shutdown()
                        }
                    }).start()
                }
            } else if (resultCode == Activity.RESULT_FIRST_USER) Toast.makeText(this, "Canceled", Toast.LENGTH_SHORT).show() else if (resultCode == Activity.RESULT_CANCELED) Toast.makeText(this, "Technical issue. Please check your internet connectivity and try again!", Toast.LENGTH_SHORT).show()
        } else if (requestCode == RC_OPEN_MAIN_ACTIVITY) {
            finish()
        } else if (requestCode == RC_OPEN_APP_STORE) {
            if (authAppIsNotInstalled()) finish()
        }
    }

    private fun startMainActivity() {
        val intent = Intent(this, MainActivity::class.java)
        intent.flags = 0
        startActivityForResult(intent, RC_OPEN_MAIN_ACTIVITY)
        overridePendingTransition(0, 0)
    }

    private fun authAppIsNotInstalled(): Boolean {
        return try {
            packageManager.getPackageInfo("inha.nsl.easytrack", 0)
            false
        } catch (e: PackageManager.NameNotFoundException) {
            true
        }
    }

    fun loginButtonClick(view: View?) {
        if (authAppIsNotInstalled()) Toast.makeText(this, "Please install the EasyTrack Authenticator and reopen the application!", Toast.LENGTH_SHORT).show() else {
            val launchIntent = packageManager.getLaunchIntentForPackage("inha.nsl.easytrack")
            if (launchIntent != null) {
                launchIntent.flags = 0
                startActivityForResult(launchIntent, RC_OPEN_ET_AUTHENTICATOR)
            }
        }
    }

    companion object {
        private const val RC_OPEN_ET_AUTHENTICATOR = 100
        private const val RC_OPEN_MAIN_ACTIVITY = 101
        private const val RC_OPEN_APP_STORE = 102

        fun isGPSDeviceEnabled(context: Context): Boolean {
            return if (Build.VERSION.SDK_INT < Build.VERSION_CODES.KITKAT) false else try {
                Settings.Secure.getInt(context.contentResolver, Settings.Secure.LOCATION_MODE) != Settings.Secure.LOCATION_MODE_OFF
            } catch (e: SettingNotFoundException) {
                e.printStackTrace()
                false
            }
        }

        fun isLocationPermissionDenied(context: Context?): Boolean {
            return ActivityCompat.checkSelfPermission(context!!, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED || ActivityCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED
        }

        fun isUsageAccessDenied(context: Context): Boolean {
            // Usage Stats is theoretically available on API v19+, but official/reliable support starts with API v21.
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP_MR1) return true
            val appOpsManager = context.getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager
            val mode = appOpsManager.checkOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), context.packageName)
            if (mode != AppOpsManager.MODE_ALLOWED) return true
            // Verify that access is possible. Some devices "lie" and return MODE_ALLOWED even when it's not.
            val now = System.currentTimeMillis()
            val mUsageStatsManager = context.getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
            val stats: List<UsageStats> = mUsageStatsManager.queryUsageStats(UsageStatsManager.INTERVAL_DAILY, now - 1000 * 10, now)
            return stats.isEmpty()
        }

        val isNetworkAvailable: Boolean
            get() = try {
                InetAddress.getByName("google.com").toString() != ""
            } catch (e: Exception) {
                false
            }

        fun disableTouch(activity: Activity) {
            activity.window.setFlags(WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE, WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE)
        }

        fun enableTouch(activity: Activity) {
            activity.window.clearFlags(WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE)
        }
    }
}