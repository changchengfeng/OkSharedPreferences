package online.greatfeng.library

import android.content.SharedPreferences

interface OkSharedPreferences : SharedPreferences {
    fun clearOnSharedPreferenceChangeListener()
}