package online.greatfeng.oksharedpreferences

import android.content.Context
import android.content.SharedPreferences

interface OkSharedPreferences : SharedPreferences {
    fun clearOnSharedPreferenceChangeListener()
}

fun Context.getOkSharedPreferences(name: String): OkSharedPreferences {
    return getOkSharedPreferences(name, false)
}

fun Context.getOkSharedPreferences(name: String, migration: Boolean): OkSharedPreferences {
    return OkSharedPreferencesManager.getInstance(this).getOkSharedPreferences(name, migration)
}

fun Context.deleteSharedPreferences(name: String): Boolean {
    return OkSharedPreferencesManager.getInstance(this).deleteSharedPreferences(name)
}