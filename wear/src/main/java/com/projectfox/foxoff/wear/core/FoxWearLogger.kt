package com.projectfox.foxoff.wear.core

import android.util.Log

/**
 * Standardized logger for the FoxOFF Wear OS project.
 * All logs are prefixed with FOX-WEAR.
 */
object FoxWearLogger {
    private const val TAG = "FOX-WEAR"

    fun d(message: String) {
        Log.d(TAG, message)
    }

    fun i(message: String) {
        Log.i(TAG, message)
    }

    fun w(message: String) {
        Log.w(TAG, message)
    }

    fun e(message: String, throwable: Throwable? = null) {
        Log.e(TAG, message, throwable)
    }
}
