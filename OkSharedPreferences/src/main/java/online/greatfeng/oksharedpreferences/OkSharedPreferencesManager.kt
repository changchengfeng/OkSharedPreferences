package online.greatfeng.oksharedpreferences

import android.content.Context
import android.os.Handler
import android.os.HandlerThread
import android.os.SystemClock
import online.greatfeng.oksharedpreferences.OkSharedPreferencesImpl.Companion.SUFFIX_BAK
import online.greatfeng.oksharedpreferences.OkSharedPreferencesImpl.Companion.SUFFIX_OKSP
import online.greatfeng.oksharedpreferences.OkSharedPreferencesImpl.Companion.SUFFIX_TMP
import online.greatfeng.oksharedpreferences.fileobserver.OkFileObserver
import java.io.File
import java.util.concurrent.ConcurrentHashMap


internal class OkSharedPreferencesManager private constructor(val context: Context) {

    companion object {
        private const val TAG = "OkSharedPreferencesManager"
        private const val RELOAD_DEBOUNCE_MS = 50L

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

    private val handler by lazy {
        Handler(handlerThread.looper)
    }

    private val dir by lazy {
        File(context.dataDir, "ok-sp").also {
            if (!it.exists()) {
                it.mkdirs()
            }
        }
    }

    private val fileObserver by lazy {
        object : OkFileObserver(
            listOf(dir),
            MOVED_TO or CREATE or DELETE or CLOSE_WRITE
        ) {
            override fun onEvent(event: Int, path: String?) {
                if (path == null || !path.endsWith(SUFFIX_OKSP)) {
                    return
                }
                val storageBase = path.substring(0, path.length - SUFFIX_OKSP.length)
                val name = OkSpSigning.logicalNameFromStorageBaseName(context, storageBase)
                    ?: return
                val okSharedPreferences = cacheMap[name] as? OkSharedPreferencesImpl ?: return
                LogUtils.d(
                    TAG,
                    "onEvent event=$event name=$name storage=$storageBase pid=${android.os.Process.myPid()}"
                )
                handler.removeCallbacksAndMessages(okSharedPreferences)
                handler.postAtTime(
                    { okSharedPreferences.reloadFromDiskOnExternalChange() },
                    okSharedPreferences,
                    SystemClock.uptimeMillis() + RELOAD_DEBOUNCE_MS
                )
            }
        }
    }

    init {
        fileObserver.startWatching()
    }

    private val cacheMap = ConcurrentHashMap<String, OkSharedPreferences>()

    fun getOkSharedPreferences(name: String, migration: Boolean): OkSharedPreferences {
        (cacheMap[name] as? OkSharedPreferencesImpl)?.let {
            if (!it.isDestroyed()) {
                return it
            }
        }
        synchronized(this) {
            (cacheMap[name] as? OkSharedPreferencesImpl)?.let {
                if (!it.isDestroyed()) {
                    return it
                }
                cacheMap.remove(name, it)
            }
            val storageBaseName = OkSpSigning.storageBaseName(context, name)
            val lockFile = File(dir, ".$storageBaseName.lock")
            if (!lockFile.exists()) {
                lockFile.createNewFile()
            }
            migrateLegacyLockIfNeeded(name, storageBaseName)
            val impl = OkSharedPreferencesImpl(
                if (migration) context.getSharedPreferences(name, Context.MODE_PRIVATE) else null,
                lockFile.absolutePath,
                dir.absolutePath,
                storageBaseName,
                handler,
                name
            )
            cacheMap[name] = impl
            return impl
        }
    }

    fun deleteSharedPreferences(name: String): Boolean {
        val cached = cacheMap[name] as? OkSharedPreferencesImpl
        if (cached != null) {
            cached.cancelPendingSave()
            handler.removeCallbacksAndMessages(cached)
            cached.clearData(true)
            cacheMap.remove(name, cached)
        }
        deleteFilesLocked(name)
        val storageBaseName = OkSpSigning.storageBaseName(context, name)
        return !File(dir, storageBaseName + SUFFIX_OKSP).exists()
    }

    private fun deleteFilesLocked(name: String) {
        val storageBaseName = OkSpSigning.storageBaseName(context, name)
        val lockFile = File(dir, ".$storageBaseName.lock")
        if (!lockFile.exists()) {
            lockFile.createNewFile()
        }
        OkSpFileLocks.withExclusiveLock(lockFile.absolutePath) {
            File(dir, storageBaseName + SUFFIX_OKSP).delete()
            File(dir, storageBaseName + SUFFIX_BAK).delete()
            File(dir, storageBaseName + SUFFIX_TMP).delete()
            lockFile.delete()
            if (name != storageBaseName) {
                File(dir, ".$name.lock").delete()
            }
        }
    }

    private fun migrateLegacyLockIfNeeded(logicalName: String, storageBaseName: String) {
        if (logicalName == storageBaseName) {
            return
        }
        val legacyLock = File(dir, ".$logicalName.lock")
        val newLock = File(dir, ".$storageBaseName.lock")
        if (!newLock.exists() && legacyLock.exists()) {
            if (!legacyLock.renameTo(newLock)) {
                LogUtils.e(TAG, "failed to migrate legacy lock ${legacyLock.absolutePath}")
            }
        }
    }
}
