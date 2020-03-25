package kr.ac.inha.nsl

import android.app.IntentService
import android.content.Intent
import com.google.android.gms.location.ActivityRecognitionResult
import com.google.android.gms.location.DetectedActivity
import java.io.IOException

class ETActivityRecognitionIntentService : IntentService("ETActivityRecognitionIntentService") {
    companion object {
        @JvmField
        var dataSourceId: Int = -1
    }

    // region Override
    override fun onHandleIntent(intent: Intent?) {
        if (intent != null) {
            if (ActivityRecognitionResult.hasResult(intent)) {
                val result: ActivityRecognitionResult = ActivityRecognitionResult.extractResult(intent)!!
                val detectedActivity: DetectedActivity = result.mostProbableActivity
                val activity: String
                val confidence = detectedActivity.confidence.toFloat() / 100
                activity = when (detectedActivity.type) {
                    DetectedActivity.STILL -> "STILL"
                    DetectedActivity.WALKING -> "WALKING"
                    DetectedActivity.RUNNING -> "RUNNING"
                    DetectedActivity.ON_BICYCLE -> "ON_BICYCLE"
                    DetectedActivity.IN_VEHICLE -> "IN_VEHICLE"
                    DetectedActivity.ON_FOOT -> "ON_FOOT"
                    DetectedActivity.TILTING -> "TILTING"
                    DetectedActivity.UNKNOWN -> "UNKNOWN"
                    else -> "N/A"
                }
                try {
                    DbMgr.saveMixedData(dataSourceId, result.time, confidence, activity)
                    Tools.vibrate(applicationContext)
                } catch (e: IOException) {
                    e.printStackTrace()
                }
            }
        }
    } // endregion
}
