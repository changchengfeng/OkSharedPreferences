package online.greatfeng.library

import android.content.Context
import android.util.Log
import java.io.ByteArrayOutputStream
import java.io.DataOutputStream
import java.nio.ByteBuffer

const val MAX_LEN = 0x80000000

fun String?.checkKey(): Boolean {
    if (this == null || length >= MAX_LEN) {
        Log.e(TAG, "$this key or value can not be null and length must less $MAX_LEN")
        return false
    }
    return true
}

fun String?.checkValue(): Boolean {
    if (this != null && length >= MAX_LEN) {
        Log.e(TAG, "$this key or value can not be null and length must less $MAX_LEN")
        return false
    }
    return true
}

fun Set<String>?.checkValue(): Boolean {
    if (this != null && this.any { it.length >= MAX_LEN }) {
        Log.e(TAG, "$this key or value can not be null and length must less $MAX_LEN")
        return false
    }
    return true
}


fun Context.getOkSharedPreferences(name: String): OkSharedPreferences {
    return OkSharedPreferencesManager.getInstance(this).getOkSharedPreferences(name)
}

fun ByteBuffer.getLen(): Int {
    val size = get().toUByte().toInt()
    var len: Int
    when (size) {
        0x81 -> {
            len = this.get().toUByte().toInt()
        }

        0x82 -> {
            len = this.getShort().toUShort().toInt()
        }

        0x83 -> {
            val byte1 = this.get().toUByte().toInt()
            val byte2 = this.get().toUByte().toInt()
            val byte3 = this.get().toUByte().toInt()
            len = (byte1 shl 16) + (byte2 shl 8) + byte3
        }

        0x84 -> {
            len = this.getInt().toUInt().toInt()
        }

        else -> {
            len = size
        }
    }
    return len
}

private const val TAG = "OkSharedPreferences"

fun ByteBuffer.getString(): String {
    val len = getLen()
//    Log.d(TAG, "getString() len $len")
    val byteArray = ByteArray(len)
    get(byteArray)
    return String(byteArray)
}

fun ByteBuffer.getSet(): Set<String> {
    val len = getLen()
//    Log.d(TAG, "getSet() len $len")
    val mutableSet = mutableSetOf<String>()
    for (i in 0 until len) {
        val str = getString()
//        Log.d(TAG, "getSet() str $str")
        mutableSet.add(str)
    }
    return mutableSet
}

fun String.toDerLVByteArray(): ByteArray {
    val byteArray = this.toByteArray()
    val len = byteArray.size
    val derLVByteArray = len.toDerLVByteArray()
    return ByteBuffer.allocate(derLVByteArray.size + len).put(derLVByteArray)
        .put(byteArray)
        .array()
}

fun Set<String>.toDerLVByteArray(): ByteArray {
    val byteArrayOutputStream = ByteArrayOutputStream()
    val dataOutputStream = DataOutputStream(byteArrayOutputStream)
    dataOutputStream.write(size.toDerLVByteArray())
    for (str in this) {
        dataOutputStream.write(str.toDerLVByteArray())
    }
    return byteArrayOutputStream.toByteArray()
}

fun Int.toDerLVByteArray(): ByteArray {
    if (this < 0x80) {
        return ByteBuffer.allocate(1).put(this.toByte()).array()
    }
    var temp = this
    val allocate = ByteBuffer.allocate(4)
    var byteLen = 0
    do {
        byteLen++
        allocate.put((temp and 0xFF).toByte())
        temp = temp shr 8
    } while (temp > 0)

    var data = 0x80 + byteLen
    val sliceBytes = allocate.array().sliceArray(0 until byteLen)
    sliceBytes.reverse()
    return ByteBuffer.allocate(byteLen + 1).put(data.toByte()).put(sliceBytes).array()
}