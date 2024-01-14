package online.greatfeng.library

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import java.io.DataOutputStream
import java.io.File
import java.nio.ByteBuffer
import java.util.concurrent.locks.ReentrantReadWriteLock


class OkSharedPreferencesImpl(val context: Context, val name: String) : OkSharedPreferences {


    private val readWriteLock by lazy { ReentrantReadWriteLock() }
    private val readLock by lazy { readWriteLock.readLock() }
    private val writeLock by lazy { readWriteLock.writeLock() }
    private val cacheMap by lazy { mutableMapOf<String, Any>() }

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
        cacheMap.clear()
        val okSpFile = File(context.dataDir, name + SUFFIX_OKSP)
        okSpFile.deleteOnExit()
    }

    fun loadDataFromDisk() {
        val okSpFile = File(context.dataDir, name + SUFFIX_OKSP)
        Log.d(TAG, "okSpFile $okSpFile")
        if (!okSpFile.exists()) {
            okSpFile.createNewFile()
        }
        val byteArray = okSpFile.inputStream().readBytes()
        if (byteArray.isNotEmpty()) {
            val byteBuffer = ByteBuffer.wrap(byteArray)
            while (byteBuffer.position() < byteBuffer.limit()) {
                val key = byteBuffer.getString()
                Log.d(TAG, "loadDataFromDisk() key $key")
                val type = byteBuffer.get().toUByte().toInt()
                when (type) {
                    B -> {
                        val data = byteBuffer.get()
                        cacheMap.put(key, data.toInt() == 1)
                        Log.d(TAG, "loadDataFromDisk() data ${data.toInt() == 1}")
                    }

                    F -> {
                        val data = byteBuffer.getFloat()
                        cacheMap.put(key, data)
                        Log.d(TAG, "loadDataFromDisk() data $data")
                    }

                    I -> {
                        val data = byteBuffer.getInt()
                        cacheMap.put(key, data)
                        Log.d(TAG, "loadDataFromDisk() data $data")
                    }

                    L -> {
                        val data = byteBuffer.getLong()
                        cacheMap.put(key, data)
                        Log.d(TAG, "loadDataFromDisk() data $data")
                    }

                    S -> {
                        val data = byteBuffer.getString()
                        cacheMap.put(key, data)
                        Log.d(TAG, "loadDataFromDisk() data $data")
                    }

                    T -> {
                        val data = byteBuffer.getSet()
                        cacheMap.put(key, data)
                        Log.d(TAG, "loadDataFromDisk() data $data")
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
    }


    fun saveDisk() {
        val bakFile = File(context.dataDir, name + SUFFIX_BAK)
        if (bakFile.exists()) {
            bakFile.delete()
        }
        bakFile.createNewFile()
        val outputStream = DataOutputStream(bakFile.outputStream().buffered())
        outputStream.use {
            for ((name, value) in cacheMap) {
                Log.i(TAG, "saveDisk() name $name value $value")
                when (value) {
                    is Boolean -> {
                        it.write(name.toDerLVByteArray())
                        it.writeByte(B)
                        it.writeByte(if (value) 1 else 0)
                    }

                    is Float -> {
                        it.write(name.toDerLVByteArray())
                        it.writeByte(F)
                        it.writeFloat(value)
                    }

                    is Int -> {
                        it.write(name.toDerLVByteArray())
                        it.writeByte(I)
                        it.writeInt(value)
                    }

                    is Long -> {
                        it.write(name.toDerLVByteArray())
                        it.writeByte(L)
                        it.writeLong(value)
                    }

                    is String -> {
                        it.write(name.toDerLVByteArray())
                        it.writeByte(S)
                        it.write(value.toDerLVByteArray())
                    }

                    is Set<*> -> {
                        it.write(name.toDerLVByteArray())
                        it.writeByte(T)
                        it.write((value as Set<String>).toDerLVByteArray())
                    }
                }
            }
            it.flush()
            it.close()
            val okSpFile = File(context.dataDir, name + SUFFIX_OKSP)
            okSpFile.deleteOnExit()
            bakFile.renameTo(okSpFile)
        }


    }

    override fun getAll(): MutableMap<String, *> = cacheMap

    override fun getString(key: String?, defValue: String?) =
        if (key == null) {
            defValue
        } else {
            readLock.lock()
            try {
                if (cacheMap.get(key) == null) {
                    defValue
                } else {
                    cacheMap.get(key) as String
                }
            } finally {
                readLock.unlock()
            }
        }

    override fun getStringSet(key: String?, defValues: MutableSet<String>?): MutableSet<String>? {
        if (key == null) {
            return defValues
        } else {
            readLock.lock()
            try {
                if (cacheMap.get(key) == null) {
                    return defValues
                } else {
                    return cacheMap.get(key) as MutableSet<String>
                }
            } finally {
                readLock.unlock()
            }
        }
    }


    override fun getInt(key: String?, defValue: Int) =
        if (key == null) {
            defValue
        } else {
            readLock.lock()
            try {
                if (cacheMap.get(key) == null) {
                    defValue
                } else {
                    cacheMap.get(key) as Int
                }
            } finally {
                readLock.unlock()
            }
        }


    override fun getLong(key: String?, defValue: Long) =
        if (key == null) {
            defValue
        } else {
            readLock.lock()
            try {
                if (cacheMap.get(key) == null) {
                    defValue
                } else {
                    cacheMap.get(key) as Long
                }
            } finally {
                readLock.unlock()
            }
        }


    override fun getFloat(key: String?, defValue: Float) =
        if (key == null) {
            defValue
        } else {
            readLock.lock()
            try {
                if (cacheMap.get(key) == null) {
                    defValue
                } else {
                    cacheMap.get(key) as Float
                }
            } finally {
                readLock.unlock()
            }
        }


    override fun getBoolean(key: String?, defValue: Boolean) =
        if (key == null) {
            defValue
        } else {
            readLock.lock()
            try {
                if (cacheMap.get(key) == null) {
                    defValue
                } else {
                    cacheMap.get(key) as Boolean
                }
            } finally {
                readLock.unlock()
            }
        }


    override fun contains(key: String?) =
        if (key == null) {
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

    override fun registerOnSharedPreferenceChangeListener(listener: SharedPreferences.OnSharedPreferenceChangeListener?) {
        TODO("Not yet implemented")
    }

    override fun unregisterOnSharedPreferenceChangeListener(listener: SharedPreferences.OnSharedPreferenceChangeListener?) {
        TODO("Not yet implemented")
    }

    fun handleModifiedMap(modifiedMap: MutableMap<String, Any>) {
        for ((name, value) in modifiedMap) {
            if (value == this) {
                cacheMap.remove(name)
            } else {
                cacheMap.put(name, value)
            }
        }
    }


    inner class OkEditor : SharedPreferences.Editor {

        val modifiedMap = mutableMapOf<String, Any>()
        var clear = false
        override fun putString(key: String?, value: String?): SharedPreferences.Editor {

            if (key.checkKey() && value.checkValue()) {
                modifiedMap.put(key!!, value ?: "")
            }
            return this
        }

        override fun putStringSet(
            key: String?,
            values: MutableSet<String>?
        ): SharedPreferences.Editor {
            if (key.checkKey() && values.checkValue()) {
                modifiedMap.put(key!!, values ?: "")
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
                if (clear) {
                    clearData()
                    modifiedMap.clear()
                    clear = false
                } else {
                    handleModifiedMap(modifiedMap)
                    saveDisk()
                    modifiedMap.clear()
                }
                return true
            } finally {
                writeLock.unlock()
            }
        }

        override fun apply() {

        }
    }
}