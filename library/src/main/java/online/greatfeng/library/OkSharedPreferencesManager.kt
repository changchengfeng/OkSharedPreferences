package online.greatfeng.library

import android.content.Context
import android.os.Build
import androidx.annotation.RequiresApi


@RequiresApi(Build.VERSION_CODES.Q)
class OkSharedPreferencesManager private constructor(val context: Context) {

    companion object {
        @Volatile
        private var instantiation: OkSharedPreferencesManager? = null

        @JvmStatic
        fun getInstance(context: Context) = instantiation ?: synchronized(this) {
            instantiation ?: OkSharedPreferencesManager(context).also { instantiation = it }
        }
    }

    init {
//        val dir = File(context.dataDir,"ok-sp")

    }

    val cacheMap = mutableMapOf<String, OkSharedPreferencesImpl>()

    fun getOkSharedPreferences(name: String): OkSharedPreferencesImpl {
        return cacheMap[name] ?: OkSharedPreferencesImpl(context, name).also {
            cacheMap[name] = it
        }
    }
}