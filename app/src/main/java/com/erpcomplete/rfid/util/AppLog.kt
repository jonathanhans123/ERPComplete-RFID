package com.erpcomplete.rfid.util

import android.util.Log
import com.erpcomplete.rfid.BuildConfig

object AppLog {
    private const val TAG = "ERPCompleteRFID"

    fun d(message: String) {
        if (BuildConfig.DEBUG) Log.d(TAG, message)
    }

    fun i(message: String) {
        Log.i(TAG, message)
    }

    fun w(message: String, throwable: Throwable? = null) {
        Log.w(TAG, message, throwable)
    }

    fun e(message: String, throwable: Throwable? = null) {
        Log.e(TAG, message, throwable)
    }

    fun api(method: String, url: String, code: Int? = null, detail: String = "") {
        val codePart = code?.let { " → $it" }.orEmpty()
        d("API $method $url$codePart $detail".trim())
    }
}
