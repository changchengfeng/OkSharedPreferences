package online.greatfeng.oksharedpreferences

import android.content.SharedPreferences
import android.content.SharedPreferences.OnSharedPreferenceChangeListener
import android.os.Handler
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.io.DataOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.nio.ByteBuffer
import java.util.concurrent.locks.ReentrantReadWriteLock


internal class OkSharedPreferencesImpl(
    migration: SharedPreferences?,
    val fileLock: String,
    val dir: String,
    val sharePreferencesName: String,
    val handler: Handler,
    private val logicalName: String? = null
) : OkSharedPreferences {

    private val readWriteLock = ReentrantReadWriteLock()
    private val readLock = readWriteLock.readLock()
    private val writeLock = readWriteLock.writeLock()
    private val cacheMap = mutableMapOf<String, Any>()
    private val lock = Any()
    private var memoryGeneration = 0L
    private var diskGeneration = 0L
    @Volatile
    private var destroyed = false

    /** Keys modified locally since the last successful disk sync or external reload. */
    private val dirtyKeys = mutableSetOf<String>()
    private var dirtyClear = false
    private var syncedDiskSnapshot = DiskSnapshot.missing()
    private val listeners = mutableSetOf<OnSharedPreferenceChangeListener>()
    private val pendingNotifyKeys = LinkedHashSet<String>()
    private val notifyRunnable = Runnable {
        val keys = pendingNotifyKeys.toList()
        pendingNotifyKeys.clear()
        dispatchListenerCallbacks(keys)
    }

    private val saveRunnable = Runnable {
        var rollbackNotify: List<String>? = null
        writeLock.lock()
        try {
            if (destroyed) {
                return@Runnable
            }
            withExclusiveFileLock {
                if (!destroyed) {
                    try {
                        saveDiskLocked()
                        diskGeneration = memoryGeneration
                    } catch (e: Exception) {
                        LogUtils.e(TAG, "async save failed", e)
                        rollbackNotify = rollbackAndAlignGeneration()
                    }
                }
            }
        } finally {
            writeLock.unlock()
        }
        rollbackNotify?.let { notifyListeners(it) }
    }

    init {
        writeLock.lock()
        try {
            withExclusiveFileLock {
                migrateLegacyFilesIfNeeded()
                val okSpFile = File(dir, sharePreferencesName + SUFFIX_OKSP)
                val bakFile = File(dir, sharePreferencesName + SUFFIX_BAK)
                val existed = okSpFile.exists() || bakFile.exists()
                loadFromDiskLocked()
                if (!existed && migration != null) {
                    val all = migration.all
                    if (all.isNotEmpty()) {
                        @Suppress("UNCHECKED_CAST")
                        cacheMap.putAll(all as Map<out String, Any>)
                        dirtyKeys.addAll(cacheMap.keys)
                        memoryGeneration++
                        saveDiskLocked()
                        diskGeneration = memoryGeneration
                        migration.edit().clear().apply()
                    }
                }
            }
        } catch (e: Exception) {
            LogUtils.e(TAG, "init load failed", e)
        } finally {
            writeLock.unlock()
        }
    }

    companion object {
        const val TAG = "OkSharedPreferencesImpl"
        const val SUFFIX_OKSP = ".oksp"
        const val SUFFIX_BAK = ".bak"
        const val SUFFIX_TMP = ".tmp"
        const val B = 1 // Boolean
        const val F = 2  // Float
        const val I = 4  // Int
        const val L = 8  // Long
        const val S = 16  // String
        const val T = 32  // MutableSet<String>

        val REMOVE_SENTINEL = Any()

    }

    fun cancelPendingSave() {
        handler.removeCallbacks(saveRunnable)
    }

    fun isDestroyed(): Boolean = destroyed

    /** Test helper: wait until all notifications posted before this call are dispatched. */
    internal fun drainNotificationQueue(timeoutMs: Long = 3000): Boolean {
        val latch = CountDownLatch(1)
        handler.post { latch.countDown() }
        return latch.await(timeoutMs, TimeUnit.MILLISECONDS)
    }

    fun clearData(deleteSharedPreference: Boolean) {
        writeLock.lock()
        val changedKeys = try {
            val keys = cacheMap.keys.toList()
            cacheMap.clear()
            dirtyClear = true
            dirtyKeys.addAll(keys)
            try {
                memoryGeneration++
                if (deleteSharedPreference) {
                    destroyed = true
                }
                withExclusiveFileLock {
                    File(dir, sharePreferencesName + SUFFIX_OKSP).delete()
                    File(dir, sharePreferencesName + SUFFIX_BAK).delete()
                    File(dir, sharePreferencesName + SUFFIX_TMP).delete()
                    if (!deleteSharedPreference) {
                        saveDiskLocked()
                    }
                    diskGeneration = memoryGeneration
                    clearDirtyState()
                }
            } catch (e: Exception) {
                LogUtils.e(TAG, "clearData failed", e)
            }
            keys
        } finally {
            writeLock.unlock()
        }
        notifyListeners(changedKeys)
    }

    /**
     * Reload from disk after another process changed the file.
     * Never blocks waiting for the file to appear: missing file means empty data.
     */
    override fun reload() {
        reloadFromDisk()
    }

    fun reloadFromDisk() {
        reloadFromDiskInternal()
    }

    internal fun reloadFromDiskOnExternalChange() {
        reloadFromDiskInternal(force = true)
    }

    private fun reloadFromDiskInternal(force: Boolean = false) {
        var changedKeys: List<String> = emptyList()
        writeLock.lock()
        try {
            if (destroyed || memoryGeneration != diskGeneration) {
                return
            }
            withExclusiveFileLock {
                if (!destroyed && memoryGeneration == diskGeneration) {
                    val okSpFile = okSpFile()
                    if (force || !syncedDiskSnapshot.matches(okSpFile)) {
                        changedKeys = loadFromDiskLocked()
                        clearDirtyState()
                    }
                }
            }
        } catch (e: Exception) {
            LogUtils.e(TAG, "reloadFromDisk failed", e)
            return
        } finally {
            writeLock.unlock()
        }
        notifyListeners(changedKeys)
    }

    private fun clearDirtyState() {
        dirtyKeys.clear()
        dirtyClear = false
    }

    private fun migrateLegacyFilesIfNeeded() {
        val legacyBase = logicalName ?: return
        if (legacyBase == sharePreferencesName) {
            return
        }
        migrateFileIfNeeded(
            File(dir, legacyBase + SUFFIX_OKSP),
            File(dir, sharePreferencesName + SUFFIX_OKSP)
        )
        migrateFileIfNeeded(
            File(dir, legacyBase + SUFFIX_BAK),
            File(dir, sharePreferencesName + SUFFIX_BAK)
        )
        migrateFileIfNeeded(
            File(dir, legacyBase + SUFFIX_TMP),
            File(dir, sharePreferencesName + SUFFIX_TMP)
        )
    }

    private fun migrateFileIfNeeded(legacy: File, target: File) {
        if (target.exists() || !legacy.exists()) {
            return
        }
        if (!legacy.renameTo(target)) {
            LogUtils.e(
                TAG,
                "failed to migrate legacy file ${legacy.absolutePath} -> ${target.absolutePath}"
            )
        }
    }

    private fun rollbackAndAlignGeneration(): List<String> {
        val changed = loadFromDiskLocked()
        memoryGeneration = diskGeneration
        clearDirtyState()
        return changed
    }

    private fun recoverBakIfNeeded(okSpFile: File, bakFile: File) {
        if (!okSpFile.exists() && bakFile.exists()) {
            if (!bakFile.renameTo(okSpFile)) {
                LogUtils.e(TAG, "failed to recover bak file ${bakFile.absolutePath}")
            }
        }
    }

    private fun readDiskBytesLocked(): ByteArray? {
        val okSpFile = File(dir, sharePreferencesName + SUFFIX_OKSP)
        val bakFile = File(dir, sharePreferencesName + SUFFIX_BAK)
        recoverBakIfNeeded(okSpFile, bakFile)
        if (!okSpFile.exists()) {
            return null
        }
        val bytes = FileInputStream(okSpFile).use { it.readBytes() }
        val maxFileBytes = OkSharedPreferences.maxFileBytes
        if (bytes.size > maxFileBytes) {
            throw DecodeException("file too large: ${bytes.size} bytes (max $maxFileBytes)")
        }
        return bytes
    }

    private fun readDiskMapLocked(): Map<String, Any> {
        val bytes = readDiskBytesLocked() ?: return emptyMap()
        return parseMap(bytes)
    }

    /**
     * Merge disk snapshot with local dirty changes so concurrent writers in other
     * processes do not lose keys this instance never loaded into memory.
     */
    private fun buildMergedMapForSave(): Map<String, Any> {
        if (dirtyClear) {
            return LinkedHashMap(cacheMap)
        }
        val okSpFile = okSpFile()
        if (syncedDiskSnapshot.matches(okSpFile)) {
            // Disk unchanged since our last load/save: cache already reflects disk + local edits.
            return LinkedHashMap(cacheMap)
        }
        val diskMap = readDiskMapLocked()
        val merged = LinkedHashMap(diskMap)
        for (key in dirtyKeys) {
            val value = cacheMap[key]
            if (value != null) {
                merged[key] = value
            } else {
                merged.remove(key)
            }
        }
        return merged
    }

    private fun okSpFile(): File = File(dir, sharePreferencesName + SUFFIX_OKSP)

    private fun refreshSyncedDiskSnapshot() {
        syncedDiskSnapshot = DiskSnapshot.capture(okSpFile())
    }

    private fun loadFromDiskLocked(): List<String> {
        val okSpFile = File(dir, sharePreferencesName + SUFFIX_OKSP)
        val bakFile = File(dir, sharePreferencesName + SUFFIX_BAK)
        recoverBakIfNeeded(okSpFile, bakFile)
        if (!okSpFile.exists()) {
            if (cacheMap.isEmpty()) {
                refreshSyncedDiskSnapshot()
                return emptyList()
            }
            val removed = cacheMap.keys.toList()
            cacheMap.clear()
            refreshSyncedDiskSnapshot()
            return removed
        }
        val bytes = readDiskBytesLocked()
            ?: return if (cacheMap.isEmpty()) {
                refreshSyncedDiskSnapshot()
                emptyList()
            } else {
                val removed = cacheMap.keys.toList()
                cacheMap.clear()
                refreshSyncedDiskSnapshot()
                removed
            }
        val newMap = try {
            parseMap(bytes)
        } catch (e: Exception) {
            LogUtils.e(TAG, "parse failed, keep previous cache. file=${okSpFile.absolutePath}", e)
            return emptyList()
        }
        val changed = swapCacheAndCollectChanges(newMap)
        refreshSyncedDiskSnapshot()
        return changed
    }

    private fun parseMap(byteArray: ByteArray): Map<String, Any> {
        val result = LinkedHashMap<String, Any>()
        if (byteArray.isEmpty()) {
            return result
        }
        val byteBuffer = ByteBuffer.wrap(byteArray)
        while (byteBuffer.position() < byteBuffer.limit()) {
            byteBuffer.requireRemaining(1)
            val key = byteBuffer.getString()
            byteBuffer.requireRemaining(1)
            val type = byteBuffer.get().toUByte().toInt()
            when (type) {
                B -> {
                    byteBuffer.requireRemaining(1)
                    val data = byteBuffer.get()
                    result[key] = data.toInt() == 1
                }

                F -> {
                    byteBuffer.requireRemaining(4)
                    result[key] = byteBuffer.getFloat()
                }

                I -> {
                    byteBuffer.requireRemaining(4)
                    result[key] = byteBuffer.getInt()
                }

                L -> {
                    byteBuffer.requireRemaining(8)
                    result[key] = byteBuffer.getLong()
                }

                S -> result[key] = byteBuffer.getString()
                T -> result[key] = byteBuffer.getSet()
                else -> throw DecodeException(
                    "not support data type $type, file ${sharePreferencesName}$SUFFIX_OKSP"
                )
            }
        }
        return result
    }

    private fun swapCacheAndCollectChanges(newMap: Map<String, Any>): List<String> {
        val changed = ArrayList<String>()
        for (key in cacheMap.keys) {
            if (key !in newMap) {
                changed.add(key)
            }
        }
        for ((key, value) in newMap) {
            val old = cacheMap[key]
            if (old == null || old != value) {
                changed.add(key)
            }
        }
        cacheMap.clear()
        cacheMap.putAll(newMap)
        return changed
    }

    private fun backupCurrentFile(okSpFile: File, bakFile: File) {
        if (!okSpFile.exists()) {
            return
        }
        if (bakFile.exists() && !bakFile.delete()) {
            LogUtils.e(TAG, "failed to delete old bak file ${bakFile.absolutePath}")
        }
        FileInputStream(okSpFile).use { input ->
            FileOutputStream(bakFile).use { output ->
                input.copyTo(output)
                output.fd.sync()
            }
        }
    }

    private fun saveDiskLocked() {
        val merged = buildMergedMapForSave()
        val tmpFile = File(dir, sharePreferencesName + SUFFIX_TMP)
        val okSpFile = File(dir, sharePreferencesName + SUFFIX_OKSP)
        val bakFile = File(dir, sharePreferencesName + SUFFIX_BAK)
        if (tmpFile.exists() && !tmpFile.delete()) {
            LogUtils.e(TAG, "failed to delete tmp file ${tmpFile.absolutePath}")
        }
        FileOutputStream(tmpFile).use { fos ->
            val out = DataOutputStream(fos.buffered())
            for ((key, value) in merged) {
                writeEntry(out, key, value)
            }
            out.flush()
            fos.fd.sync()
        }
        backupCurrentFile(okSpFile, bakFile)
        if (!tmpFile.renameTo(okSpFile)) {
            FileOutputStream(okSpFile).use { dest ->
                FileInputStream(tmpFile).use { src ->
                    src.copyTo(dest)
                }
                dest.fd.sync()
            }
            if (!tmpFile.delete()) {
                LogUtils.e(TAG, "failed to delete tmp file ${tmpFile.absolutePath}")
            }
        }
        refreshSyncedDiskSnapshot()
        syncCacheAfterSave(merged)
    }

    private fun syncCacheAfterSave(merged: Map<String, Any>) {
        cacheMap.clear()
        cacheMap.putAll(merged)
        clearDirtyState()
    }

    private fun writeEntry(out: DataOutputStream, key: String, value: Any) {
        when (value) {
            is Boolean -> {
                out.writeDerLVUtf8(key)
                out.writeByte(B)
                out.writeByte(if (value) 1 else 0)
            }

            is Float -> {
                out.writeDerLVUtf8(key)
                out.writeByte(F)
                out.writeFloat(value)
            }

            is Int -> {
                out.writeDerLVUtf8(key)
                out.writeByte(I)
                out.writeInt(value)
            }

            is Long -> {
                out.writeDerLVUtf8(key)
                out.writeByte(L)
                out.writeLong(value)
            }

            is String -> {
                out.writeDerLVUtf8(key)
                out.writeByte(S)
                out.writeDerLVUtf8(value)
            }

            is Set<*> -> {
                out.writeDerLVUtf8(key)
                out.writeByte(T)
                @Suppress("UNCHECKED_CAST")
                out.writeDerLVStringSet(value as Set<String>)
            }

            else -> LogUtils.e(TAG, "skip unsupported value type for key=$key value=$value")
        }
    }

    private fun withExclusiveFileLock(block: () -> Unit) {
        OkSpFileLocks.withExclusiveLock(fileLock, block)
    }

    private fun commitToMemory(
        modifiedMap: Map<String, Any>,
        clear: Boolean
    ): List<String> {
        val changed = LinkedHashSet<String>()
        if (clear) {
            changed.addAll(cacheMap.keys)
            dirtyClear = true
            dirtyKeys.addAll(cacheMap.keys)
            cacheMap.clear()
        }
        for ((key, value) in modifiedMap) {
            dirtyKeys.add(key)
            if (value === REMOVE_SENTINEL) {
                if (cacheMap.remove(key) != null) {
                    changed.add(key)
                }
            } else {
                val cacheValue = cacheMap[key]
                if (cacheValue == null || cacheValue != value) {
                    cacheMap[key] = value
                    changed.add(key)
                }
            }
        }
        if (changed.isNotEmpty() || clear) {
            memoryGeneration++
        }
        return changed.toList()
    }

    private fun snapshotListeners(): List<OnSharedPreferenceChangeListener> {
        synchronized(lock) {
            return listeners.toList()
        }
    }

    private fun notifyListeners(keys: Collection<String>) {
        if (keys.isEmpty()) {
            return
        }
        pendingNotifyKeys.addAll(keys)
        handler.removeCallbacks(notifyRunnable)
        handler.post(notifyRunnable)
    }

    private fun dispatchListenerCallbacks(keys: Collection<String>) {
        val snapshot = snapshotListeners()
        if (snapshot.isEmpty()) {
            return
        }
        for (key in keys) {
            for (listener in snapshot) {
                try {
                    listener.onSharedPreferenceChanged(this, key)
                } catch (t: Throwable) {
                    LogUtils.e(TAG, "Unhandled exception in listener $listener", t)
                }
            }
        }
    }

    override fun getAll(): MutableMap<String, *> {
        readLock.lock()
        try {
            val copy = HashMap<String, Any>(cacheMap.size)
            for ((key, value) in cacheMap) {
                copy[key] = if (value is Set<*>) {
                    @Suppress("UNCHECKED_CAST")
                    HashSet(value as Set<String>)
                } else {
                    value
                }
            }
            return copy
        } finally {
            readLock.unlock()
        }
    }

    override fun getString(key: String?, defValue: String?) =
        if (!key.checkKey()) {
            defValue
        } else {
            readLock.lock()
            try {
                val value = cacheMap[key]
                if (value == null) {
                    defValue
                } else if (value !is String) {
                    LogUtils.e(TAG, "getString = $value maybe not String type")
                    defValue
                } else {
                    value
                }
            } finally {
                readLock.unlock()
            }
        }

    override fun getStringSet(key: String?, defValues: MutableSet<String>?): MutableSet<String>? =
        if (!key.checkKey()) {
            defValues
        } else {
            readLock.lock()
            try {
                val value = cacheMap[key]
                if (value == null) {
                    defValues
                } else if (value !is Set<*>) {
                    LogUtils.e(TAG, "getStringSet = $value maybe not Set<String> type")
                    defValues
                } else {
                    @Suppress("UNCHECKED_CAST")
                    HashSet(value as Set<String>)
                }
            } finally {
                readLock.unlock()
            }
        }

    override fun getInt(key: String?, defValue: Int) =
        if (!key.checkKey()) {
            defValue
        } else {
            readLock.lock()
            try {
                val value = cacheMap[key]
                if (value == null) {
                    defValue
                } else if (value !is Int) {
                    LogUtils.e(TAG, "getInt = $value maybe not Int type")
                    defValue
                } else {
                    value
                }
            } finally {
                readLock.unlock()
            }
        }

    override fun getLong(key: String?, defValue: Long) =
        if (!key.checkKey()) {
            defValue
        } else {
            readLock.lock()
            try {
                val value = cacheMap[key]
                if (value == null) {
                    defValue
                } else if (value !is Long) {
                    LogUtils.e(TAG, "getLong = $value maybe not Long type")
                    defValue
                } else {
                    value
                }
            } finally {
                readLock.unlock()
            }
        }

    override fun getFloat(key: String?, defValue: Float) =
        if (!key.checkKey()) {
            defValue
        } else {
            readLock.lock()
            try {
                val value = cacheMap[key]
                if (value == null) {
                    defValue
                } else if (value !is Float) {
                    LogUtils.e(TAG, "getFloat = $value maybe not Float type")
                    defValue
                } else {
                    value
                }
            } finally {
                readLock.unlock()
            }
        }

    override fun getBoolean(key: String?, defValue: Boolean) =
        if (!key.checkKey()) {
            defValue
        } else {
            readLock.lock()
            try {
                val value = cacheMap[key]
                if (value == null) {
                    defValue
                } else if (value !is Boolean) {
                    LogUtils.e(TAG, "getBoolean = $value maybe not Boolean type")
                    defValue
                } else {
                    value
                }
            } finally {
                readLock.unlock()
            }
        }

    override fun contains(key: String?) =
        if (!key.checkKey()) {
            false
        } else {
            readLock.lock()
            try {
                cacheMap.contains(key)
            } finally {
                readLock.unlock()
            }
        }

    override fun edit(): SharedPreferences.Editor {
        return OkEditor()
    }

    override fun registerOnSharedPreferenceChangeListener(listener: OnSharedPreferenceChangeListener?) {
        if (listener == null) {
            throw NullPointerException("can not registerOnSharedPreferenceChangeListener with null")
        }
        synchronized(lock) {
            listeners.add(listener)
        }
    }

    override fun unregisterOnSharedPreferenceChangeListener(listener: OnSharedPreferenceChangeListener?) {
        if (listener == null) {
            throw NullPointerException("can not unregisterOnSharedPreferenceChangeListener with null")
        }
        synchronized(lock) {
            listeners.remove(listener)
        }
    }

    override fun clearOnSharedPreferenceChangeListener() {
        synchronized(lock) {
            listeners.clear()
        }
    }

    inner class OkEditor : SharedPreferences.Editor {

        private val modifiedMap = mutableMapOf<String, Any>()
        private var clear = false

        override fun putString(key: String?, value: String?): SharedPreferences.Editor {
            if (key == null || !key.checkKey() || !value.checkValue()) {
                return this
            }
            if (value == null) {
                modifiedMap[key] = REMOVE_SENTINEL
            } else {
                modifiedMap[key] = value
            }
            return this
        }

        override fun putStringSet(
            key: String?,
            values: MutableSet<String>?
        ): SharedPreferences.Editor {
            if (key == null || !key.checkKey() || !values.checkValue()) {
                return this
            }
            if (values == null) {
                modifiedMap[key] = REMOVE_SENTINEL
            } else {
                modifiedMap[key] = HashSet(values)
            }
            return this
        }

        override fun putInt(key: String?, value: Int): SharedPreferences.Editor {
            if (key == null || !key.checkKey()) {
                return this
            }
            modifiedMap[key] = value
            return this
        }

        override fun putLong(key: String?, value: Long): SharedPreferences.Editor {
            if (key == null || !key.checkKey()) {
                return this
            }
            modifiedMap[key] = value
            return this
        }

        override fun putFloat(key: String?, value: Float): SharedPreferences.Editor {
            if (key == null || !key.checkKey()) {
                return this
            }
            modifiedMap[key] = value
            return this
        }

        override fun putBoolean(key: String?, value: Boolean): SharedPreferences.Editor {
            if (key == null || !key.checkKey()) {
                return this
            }
            modifiedMap[key] = value
            return this
        }

        override fun remove(key: String?): SharedPreferences.Editor {
            if (key == null || !key.checkKey()) {
                return this
            }
            modifiedMap[key] = REMOVE_SENTINEL
            return this
        }

        override fun clear(): SharedPreferences.Editor {
            clear = true
            return this
        }

        override fun commit(): Boolean {
            val changedKeys: List<String>
            var notifyKeys: List<String>
            var success = true
            val hadClear = clear
            writeLock.lock()
            try {
                if (destroyed) {
                    return false
                }
                changedKeys = commitToMemory(modifiedMap, clear)
                modifiedMap.clear()
                clear = false
                notifyKeys = changedKeys
                if (changedKeys.isEmpty() && !hadClear) {
                    return true
                }
                handler.removeCallbacks(saveRunnable)
                try {
                    withExclusiveFileLock {
                        saveDiskLocked()
                        diskGeneration = memoryGeneration
                    }
                } catch (e: Exception) {
                    LogUtils.e(TAG, "commit save failed", e)
                    withExclusiveFileLock {
                        notifyKeys = rollbackAndAlignGeneration()
                    }
                    success = false
                }
            } finally {
                writeLock.unlock()
            }
            notifyListeners(notifyKeys)
            return success
        }

        override fun apply() {
            val changedKeys: List<String>
            val hadClear = clear
            writeLock.lock()
            try {
                if (destroyed) {
                    return
                }
                changedKeys = commitToMemory(modifiedMap, clear)
                modifiedMap.clear()
                clear = false
                if (changedKeys.isNotEmpty() || hadClear) {
                    handler.removeCallbacks(saveRunnable)
                    handler.post(saveRunnable)
                }
            } finally {
                writeLock.unlock()
            }
            notifyListeners(changedKeys)
        }
    }
}
