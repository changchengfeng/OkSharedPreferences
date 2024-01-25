package online.greatfeng.oksharedpreferences

import android.content.Context
import android.os.Handler
import android.os.HandlerThread
import android.os.Process
import online.greatfeng.oksharedpreferences.OkSharedPreferencesImpl.Companion.SUFFIX_OKSP
import online.greatfeng.oksharedpreferences.fileobserver.OkFileObserver
import java.io.File


internal class OkSharedPreferencesManager private constructor(val context: Context) {

    companion object {
        private const val TAG = "OkSharedPreferencesManager"

        @Volatile
        private var instantiation: OkSharedPreferencesManager? = null

        @JvmStatic
        fun getInstance(context: Context) = instantiation ?: synchronized(this) {
            instantiation ?: OkSharedPreferencesManager(context.applicationContext)
                .also { instantiation = it }
        }
    }

    private val handlerThread by lazy {
        HandlerThread("OkSharedPreferences").also {
            it.start()
        }
    }


    private val dir by lazy {
        File(context.dataDir, "ok-sp").also {
            if (!it.exists()) {
                it.mkdirs()
            }
        }
    }

    private val lock by lazy {
        File(dir, ".lock").also {
            if (!it.exists()) {
                it.createNewFile()
            }
        }
    }

    private val fileObserver by lazy {
        object : OkFileObserver(listOf(dir), DELETE) {
            override fun onEvent(event: Int, path: String?) {
                if (event and DELETE != 0
                    && path != null
                    && path.endsWith(SUFFIX_OKSP)
                ) {
                    val name = path.substring(0, path.length - SUFFIX_OKSP.length)
                    if (cacheMap.containsKey(name)) {
                        val okSharedPreferences =
                            getOkSharedPreferences(name) as OkSharedPreferencesImpl
                        LogUtils.d(
                            TAG,
                            "${Process.myPid()} onEvent() called with:name = $name "
                        )
                        okSharedPreferences.loadDataFromDisk()
                    }
                }
            }
        }
    }

    init {
        fileObserver.startWatching()
    }

    val cacheMap = mutableMapOf<String, OkSharedPreferences>()

    fun getOkSharedPreferences(name: String): OkSharedPreferences {
        return cacheMap[name] ?: OkSharedPreferencesImpl(
            lock.absolutePath,
            dir.absolutePath, name, Handler(handlerThread.looper)
        ).also {
            cacheMap[name] = it
        }
    }

    fun deleteSharedPreferences(name: String): Boolean {
        if (cacheMap.containsKey(name)) {
            (cacheMap[name] as OkSharedPreferencesImpl?)?.clearData()
            cacheMap.remove(name)
            return true
        } else {
            return false
        }
    }
}