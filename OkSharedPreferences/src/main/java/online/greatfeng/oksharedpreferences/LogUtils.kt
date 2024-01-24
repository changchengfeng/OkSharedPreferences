package online.greatfeng.oksharedpreferences

import android.util.Log

internal object LogUtils {

    private var level: Int = Log.ERROR
        set(value) {
            field = value
        }

    fun v(tag: String, msg: String) {
        if (level <= Log.VERBOSE) {
            Log.v(tag, msg)
        }
    }

    fun d(tag: String, msg: String) {
        if (level <= Log.DEBUG) {
            Log.d(tag, msg)
        }
    }

    fun i(tag: String, msg: String) {
        if (level <= Log.INFO) {
            Log.i(tag, msg)
        }
    }

    fun w(tag: String, msg: String) {
        if (level <= Log.WARN) {
            Log.w(tag, msg)
        }
    }

    fun e(tag: String, msg: String) {
        if (level <= Log.ERROR) {
            Log.e(tag, msg)
        }
    }

    fun e(tag: String, msg: String, tr: Throwable) {
        if (level <= Log.ERROR) {
            Log.e(tag, msg, tr)
        }
    }

    fun wtf(tag: String, s: String, tr: Throwable) {
        if (level <= Log.ERROR) {
            Log.wtf(tag, s, tr)
        }
    }

}