package online.greatfeng.oksharedpreferences

import android.content.SharedPreferences
import android.content.SharedPreferences.OnSharedPreferenceChangeListener
import android.os.ConditionVariable
import android.os.Handler
import android.util.Log
import java.io.DataOutputStream
import java.io.File
import java.io.RandomAccessFile
import java.nio.ByteBuffer
import java.util.concurrent.locks.ReentrantReadWriteLock


internal class OkSharedPreferencesImpl(
    val lock: String,
    val dir: String,
    val sharePreferencesName: String,
    val handler: Handler
) :
    OkSharedPreferences {


    private val readWriteLock by lazy { ReentrantReadWriteLock() }
    private val readLock by lazy { readWriteLock.readLock() }
    private val writeLock by lazy { readWriteLock.writeLock() }
    private val cacheMap by lazy { mutableMapOf<String, Any>() }

    private val listeners by lazy {
        mutableSetOf<OnSharedPreferenceChangeListener>()
    }
    val conditionVariable by lazy {
        ConditionVariable()
    }
    @Volatile
    var holdLock = false


    init {
        loadDataFromDisk()
    }

    companion object {
        const val TAG = "OkSharedPreferencesImpl"
        const val SUFFIX_OKSP = ".oksp"
        const val SUFFIX_BAK = ".bak"

        const val B = 1 // Boolean
        const val F = 2  // Float
        const val I = 4  // Int
        const val L = 8  // Long
        const val S = 16  // String
        const val T = 32  // MutableSet<String>
    }


    fun clearData() {
        val tempSet = mutableSetOf<String>()
        tempSet.addAll(cacheMap.keys)
        cacheMap.clear()
        for (it in listeners) {
            for (key in tempSet) {
                it.onSharedPreferenceChanged(this, key)
            }
        }
        val randomAccessFile = RandomAccessFile(lock, "rw")
        randomAccessFile.channel.lock().use {
            holdLock = true
            val okSpFile = File(dir, sharePreferencesName + SUFFIX_OKSP)
            if (okSpFile.exists()){
                okSpFile.delete()
            }

            conditionVariable.close()
            conditionVariable.block()
            Log.d(TAG, "saveDisk() holdLock $holdLock")
            holdLock = false
        }
    }

    fun loadDataFromDisk() {
        writeLock.lock()
        try {
            cacheMap.clear()
            val okSpFile = File(dir, sharePreferencesName + SUFFIX_OKSP)
            Log.d(TAG, "loadDataFromDisk okSpFile $okSpFile")
            if (!okSpFile.exists()) {
                okSpFile.createNewFile()
            }
            val byteArray = okSpFile.inputStream().readBytes()
            if (byteArray.isNotEmpty()) {
                val byteBuffer = ByteBuffer.wrap(byteArray)
                while (byteBuffer.position() < byteBuffer.limit()) {
                    val key = byteBuffer.getString()
//                Log.d(TAG, "loadDataFromDisk() key $key")
                    val type = byteBuffer.get().toUByte().toInt()
                    when (type) {
                        B -> {
                            val data = byteBuffer.get()
                            cacheMap.put(key, data.toInt() == 1)
//                        Log.d(TAG, "loadDataFromDisk() data ${data.toInt() == 1}")
                        }

                        F -> {
                            val data = byteBuffer.getFloat()
                            cacheMap.put(key, data)
//                        Log.d(TAG, "loadDataFromDisk() data $data")
                        }

                        I -> {
                            val data = byteBuffer.getInt()
                            cacheMap.put(key, data)
//                        Log.d(TAG, "loadDataFromDisk() data $data")
                        }

                        L -> {
                            val data = byteBuffer.getLong()
                            cacheMap.put(key, data)
//                        Log.d(TAG, "loadDataFromDisk() data $data")
                        }

                        S -> {
                            val data = byteBuffer.getString()
                            cacheMap.put(key, data)
//                        Log.d(TAG, "loadDataFromDisk() data $data")
                        }

                        T -> {
                            val data = byteBuffer.getSet()
                            cacheMap.put(key, data)
//                        Log.d(TAG, "loadDataFromDisk() data $data")
                        }

                        else -> {
                            throw IllegalStateException(
                                "not support data type $type " +
                                        "please check OkSharedPreferences file ${okSpFile.absolutePath}"
                            )
                        }
                    }
                }
            }
        }finally {
            writeLock.unlock()
        }

    }


    fun saveDisk() {
        Log.d(TAG, "saveDisk() called lock $lock")
        val randomAccessFile = RandomAccessFile(lock, "rw")
        randomAccessFile.channel.lock().use {
            holdLock = true
            val bakFile = File(dir, sharePreferencesName + SUFFIX_BAK)
            if (bakFile.exists()) {
                bakFile.delete()
            }
            bakFile.createNewFile()
            val outputStream = DataOutputStream(bakFile.outputStream().buffered())
            outputStream.use {
                for ((key, value) in cacheMap) {
                    Log.i(TAG, "saveDisk() key $key value $value")
                    when (value) {
                        is Boolean -> {
                            it.write(key.toDerLVByteArray())
                            it.writeByte(B)
                            it.writeByte(if (value) 1 else 0)
                        }

                        is Float -> {
                            it.write(key.toDerLVByteArray())
                            it.writeByte(F)
                            it.writeFloat(value)
                        }

                        is Int -> {
                            it.write(key.toDerLVByteArray())
                            it.writeByte(I)
                            it.writeInt(value)
                        }

                        is Long -> {
                            it.write(key.toDerLVByteArray())
                            it.writeByte(L)
                            it.writeLong(value)
                        }

                        is String -> {
                            it.write(key.toDerLVByteArray())
                            it.writeByte(S)
                            it.write(value.toDerLVByteArray())
                        }

                        is Set<*> -> {
                            it.write(key.toDerLVByteArray())
                            it.writeByte(T)
                            it.write((value as Set<String>).toDerLVByteArray())
                        }
                    }
                }
                it.flush()
                it.close()
                val okSpFile = File(dir, sharePreferencesName + SUFFIX_OKSP)
                okSpFile.deleteOnExit()
                if (okSpFile.exists()){
                    okSpFile.delete()
                }
                bakFile.renameTo(okSpFile)
            }
            conditionVariable.close()
            conditionVariable.block()
            Log.d(TAG, "saveDisk() holdLock $holdLock")
            holdLock = false
        }
    }

    override fun getAll(): MutableMap<String, *> = cacheMap

    override fun getString(key: String?, defValue: String?) =
        if (!key.checkKey()) {
            defValue
        } else {
            readLock.lock()
            try {
                val value = cacheMap.get(key)
                if (value == null || value !is String) {
                    Log.e(TAG, "getString = $value maybe not String type")
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
                val value = cacheMap.get(key)
                if (value == null || value !is MutableSet<*>) {
                    Log.e(TAG, "getStringSet = $value maybe not MutableSet<String>? type")
                    defValues
                } else {
                    value as MutableSet<String>?
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
                val value = cacheMap.get(key)
                if (value == null || value !is Int) {
                    Log.e(TAG, "getInt = $value maybe not Int type")
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
                val value = cacheMap.get(key)
                if (value == null || value !is Long) {
                    Log.e(TAG, "getLong = $value maybe not Long type")
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
                val value = cacheMap.get(key)
                if (value == null || value !is Float) {
                    Log.e(TAG, "getFloat = $value maybe not Float type")
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
                val value = cacheMap.get(key)
                if (value == null || value !is Boolean) {
                    Log.e(TAG, "getBoolean = $value maybe not Boolean type")
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
        listeners.add(listener)

    }

    override fun unregisterOnSharedPreferenceChangeListener(listener: OnSharedPreferenceChangeListener?) {
        if (listener == null) {
            throw NullPointerException("can not registerOnSharedPreferenceChangeListener with null")
        }
        listeners.add(listener)
    }

    override fun clearOnSharedPreferenceChangeListener() {
        listeners.clear()
    }

    fun handleModifiedMap(modifiedMap: MutableMap<String, Any>) {
        for ((key, value) in modifiedMap) {
            if (value == this) {
                if (cacheMap.containsKey(key)) {
                    Log.d(TAG, "handleModifiedMap() called with: key = $key value = $value")
                    cacheMap.remove(key)
                    for (it in listeners) {
                        it.onSharedPreferenceChanged(this, key)
                    }
                }

            } else {
                val cacheValue = cacheMap.get(key)
                if (cacheValue == null || !cacheValue.equals(value)) {
                    Log.d(TAG, "handleModifiedMap() called with: key = $key value = $value")
                    cacheMap.put(key, value)
                    for (it in listeners) {
                        it.onSharedPreferenceChanged(this, key)
                    }
                }
            }
        }
    }


    inner class OkEditor : SharedPreferences.Editor {

        val modifiedMap = mutableMapOf<String, Any>()
        var clear = false
        override fun putString(key: String?, value: String?): SharedPreferences.Editor {

            if (key.checkKey() && value.checkValue()) {
                if (value == null) {
                    modifiedMap.put(key!!, this)
                } else {
                    modifiedMap.put(key!!, value)
                }
            }
            return this
        }

        override fun putStringSet(
            key: String?,
            values: MutableSet<String>?
        ): SharedPreferences.Editor {
            if (key.checkKey() && values.checkValue()) {
                if (values == null) {
                    modifiedMap.put(key!!, this)
                } else {
                    modifiedMap.put(key!!, values)
                }
            }
            return this
        }

        override fun putInt(key: String?, value: Int): SharedPreferences.Editor {
            if (key.checkKey()) {
                modifiedMap.put(key!!, value)
            }
            return this
        }

        override fun putLong(key: String?, value: Long): SharedPreferences.Editor {
            if (key.checkKey()) {
                modifiedMap.put(key!!, value)
            }
            return this
        }

        override fun putFloat(key: String?, value: Float): SharedPreferences.Editor {
            if (key.checkKey()) {
                modifiedMap.put(key!!, value)
            }
            return this
        }

        override fun putBoolean(key: String?, value: Boolean): SharedPreferences.Editor {
            if (key.checkKey()) {
                modifiedMap.put(key!!, value)
            }
            return this
        }

        override fun remove(key: String?): SharedPreferences.Editor {
            if (key.checkKey()) {
                modifiedMap.put(key!!, this)
            }
            return this
        }

        override fun clear(): SharedPreferences.Editor {
            clear = true
            return this
        }

        override fun commit(): Boolean {
            writeLock.lock()
            try {
                doSave()
                return true
            } finally {
                writeLock.unlock()
            }
        }

        override fun apply() {
            handler.removeCallbacks(runnable)
            handler.post(runnable)
        }

        private val runnable = object : Runnable {
            override fun run() {
                writeLock.lock()
                try {
                    doSave()
                } finally {
                    writeLock.unlock()
                }
            }
        }

        private fun doSave() {
            if (clear) {
                clearData()
                modifiedMap.clear()
                clear = false
            } else {
                handleModifiedMap(modifiedMap)
                saveDisk()
                modifiedMap.clear()
            }
        }
    }
}