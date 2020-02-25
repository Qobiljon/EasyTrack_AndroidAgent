package kr.ac.inha.nsl

import android.app.Activity
import android.view.WindowManager
import java.net.InetAddress

object Tools {
    fun isNumber(str: String): Boolean {
        try {
            str.toLong()
        } catch (nfe: NumberFormatException) {
            return false
        }
        return true
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