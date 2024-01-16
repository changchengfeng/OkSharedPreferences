package online.greatfeng.library

import android.content.Context
import android.os.Process
import android.util.Log
import online.greatfeng.library.OkSharedPreferencesImpl.Companion.SUFFIX_OKSP
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

    private val lock by lazy {
        File(dir, ".lock").also {
            if (!it.exists()) {
                it.createNewFile()
            }
        }
    }

    private val fileObserver by lazy {
        object : OkFileObserver(listOf(dir), ALL_EVENTS) {
            override fun onEvent(event: Int, path: String?) {
                if (event and MOVED_TO != 0 && path != null && path.endsWith(SUFFIX_OKSP)) {
                    val name = path.substring(0, path.length - SUFFIX_OKSP.length)
                    if (cacheMap.containsKey(name)) {
                        val okSharedPreferences = getOkSharedPreferences(name)
                        Log.d(
                            TAG, "${Process.myPid()} onEvent() called with:name = $name , holdLock = ${
                                okSharedPreferences
                                    .holdLock
                            }"
                        )
                        if (!okSharedPreferences.holdLock) {
                            okSharedPreferences.loadDataFromDisk()
                        }
                        okSharedPreferences.conditionVariable.open()
                    }
                }
            }
        }
    }

    init {
        fileObserver.startWatching()
    }

    val cacheMap = mutableMapOf<String, OkSharedPreferencesImpl>()

    fun getOkSharedPreferences(name: String): OkSharedPreferencesImpl {
        return cacheMap[name] ?: OkSharedPreferencesImpl(
            lock.absolutePath,
            dir.absolutePath, name
        ).also {
            cacheMap[name] = it
        }
    }
}