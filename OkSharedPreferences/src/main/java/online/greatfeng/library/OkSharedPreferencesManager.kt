package online.greatfeng.library

import android.content.Context
import android.util.Log
import online.greatfeng.library.fileobserver.OkFileObserver
import java.io.File


class OkSharedPreferencesManager private constructor(val context: Context) {

    companion object {
        private const val TAG = "OkSharedPreferences"

        @Volatile
        private var instantiation: OkSharedPreferencesManager? = null

        @JvmStatic
        fun getInstance(context: Context) = instantiation ?: synchronized(this) {
            instantiation ?: OkSharedPreferencesManager(context.applicationContext)
                .also { instantiation = it }
        }
    }

    private val dir by lazy {
        File(context.dataDir, "ok-sp").also {
            if (!it.exists()) {
                it.mkdirs()
            }
        }
    }

    private val fileObserver by lazy {
        object : OkFileObserver(listOf(File(context.dataDir, "ok-sp")), ALL_EVENTS) {
            override fun onEvent(event: Int, path: String?) {
                Log.d(OkSharedPreferencesImpl.TAG, "onEvent() called with: event = $event, path = $path")
            }
        }
    }

    init {
        fileObserver.startWatching()
    }

    val cacheMap = mutableMapOf<String, OkSharedPreferencesImpl>()

    fun getOkSharedPreferences(name: String): OkSharedPreferencesImpl {
        return cacheMap[name] ?: OkSharedPreferencesImpl(dir.absolutePath,name
        ).also {
            cacheMap[name] = it
        }
    }
}