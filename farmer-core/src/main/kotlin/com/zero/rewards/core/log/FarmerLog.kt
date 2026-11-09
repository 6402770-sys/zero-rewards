package com.zero.rewards.core.log

import android.util.Log

object FarmerLog {
    private const val TAG = "ZeroFarmer"
    fun d(msg: String) { Log.d(TAG, msg) }
    fun i(msg: String) { Log.i(TAG, msg) }
    fun w(msg: String, t: Throwable? = null) { Log.w(TAG, msg, t) }
    fun e(msg: String, t: Throwable? = null) { Log.e(TAG, msg, t) }
}
