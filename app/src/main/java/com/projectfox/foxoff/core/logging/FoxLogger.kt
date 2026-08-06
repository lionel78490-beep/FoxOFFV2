package com.projectfox.foxoff.core.logging

import android.util.Log

object FoxLogger {
    private const val TAG = "FOX"
    fun i(msg: String) = Log.i(TAG, msg)
    fun w(msg: String) = Log.w(TAG, msg)
    fun e(msg: String, t: Throwable? = null) = Log.e(TAG, msg, t)
}
