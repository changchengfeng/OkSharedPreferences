package online.greatfeng.oksharedpreferences

import android.content.Context
import android.content.SharedPreferences

interface OkSharedPreferences : SharedPreferences {
    /**
     * Reload memory from the on-disk file. Call after another process may have written
     * (FileObserver also triggers this automatically when possible).
     */
    fun reload()

    fun clearOnSharedPreferenceChangeListener()

    companion object {
        const val DEFAULT_MAX_DECODE_BYTES_PER_FIELD = 16 * 1024 * 1024
        const val DEFAULT_MAX_FILE_BYTES = 64 * 1024 * 1024

        @Volatile
        @JvmStatic
        var maxDecodeBytesPerField: Int = DEFAULT_MAX_DECODE_BYTES_PER_FIELD

        @Volatile
        @JvmStatic
        var maxFileBytes: Int = DEFAULT_MAX_FILE_BYTES

        /**
         * Configure decode / file-size limits. Call before opening any [OkSharedPreferences]
         * instance (typically in [android.app.Application.onCreate]).
         */
        @JvmStatic
        fun configureLimits(
            maxDecodeBytesPerField: Int = DEFAULT_MAX_DECODE_BYTES_PER_FIELD,
            maxFileBytes: Int = DEFAULT_MAX_FILE_BYTES
        ) {
            require(maxDecodeBytesPerField > 0) {
                "maxDecodeBytesPerField must be positive"
            }
            require(maxFileBytes > 0) {
                "maxFileBytes must be positive"
            }
            synchronized(configLock) {
                Companion.maxDecodeBytesPerField = maxDecodeBytesPerField
                Companion.maxFileBytes = maxFileBytes
            }
        }

        @JvmStatic
        fun resetLimits() {
            configureLimits()
        }

        private val configLock = Any()
    }
}

fun Context.getOkSharedPreferences(name: String): OkSharedPreferences {
    return getOkSharedPreferences(name, false)
}

fun Context.getOkSharedPreferences(name: String, migration: Boolean): OkSharedPreferences {
    return OkSharedPreferencesManager.getInstance(this).getOkSharedPreferences(name, migration)
}

/**
 * Delete an OkSharedPreferences file.
 *
 * Do not name this [Context.deleteSharedPreferences]: that member on API 24+
 * shadows a same-named Kotlin extension and would delete the platform SP instead.
 */
fun Context.deleteOkSharedPreferences(name: String): Boolean {
    return OkSharedPreferencesManager.getInstance(this).deleteSharedPreferences(name)
}

/**
 * Java-compatible alias: `OkSharedPreferencesKt.deleteSharedPreferences(context, name)`.
 */
fun deleteSharedPreferences(context: Context, name: String): Boolean {
    return context.deleteOkSharedPreferences(name)
}
