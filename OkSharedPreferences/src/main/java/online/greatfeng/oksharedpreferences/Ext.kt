package online.greatfeng.oksharedpreferences

import java.io.ByteArrayOutputStream
import java.io.DataOutputStream
import java.nio.ByteBuffer

internal const val MAX_LEN = Int.MAX_VALUE

internal fun String?.checkKey(): Boolean {
    if (this == null || length >= MAX_LEN) {
        LogUtils.e(TAG, "$this key can not be null and length must less $MAX_LEN")
        return false
    }
    return true
}

internal fun String?.checkValue(): Boolean {
    if (this == null) {
        return true
    }
    if (length >= MAX_LEN) {
        LogUtils.e(TAG, "$this value length must less $MAX_LEN")
        return false
    }
    if (this.toByteArray().size > OkSharedPreferences.maxDecodeBytesPerField) {
        LogUtils.e(
            TAG,
            "value byte size ${this.toByteArray().size} exceeds maxDecodeBytesPerField " +
                OkSharedPreferences.maxDecodeBytesPerField
        )
        return false
    }
    return true
}

internal fun MutableSet<String>?.checkValue(): Boolean {
    if (this == null) {
        return true
    }
    val maxBytes = OkSharedPreferences.maxDecodeBytesPerField
    if (this.any { it.length >= MAX_LEN || it.toByteArray().size > maxBytes }) {
        LogUtils.e(TAG, "$this value exceeds max length or maxDecodeBytesPerField $maxBytes")
        return false
    }
    return true
}

private const val TAG = "OkSharedPreferences"

internal class DecodeException(message: String) : IllegalStateException(message)

internal fun ByteBuffer.requireRemaining(n: Int) {
    if (remaining() < n) {
        throw DecodeException("truncated buffer: need $n bytes, have ${remaining()}")
    }
}

internal fun ByteBuffer.getLen(): Int {
    requireRemaining(1)
    val size = get().toUByte().toInt()
    val len = when (size) {
        0x81 -> {
            requireRemaining(1)
            get().toUByte().toInt()
        }

        0x82 -> {
            requireRemaining(2)
            getShort().toUShort().toInt()
        }

        0x83 -> {
            requireRemaining(3)
            val byte1 = get().toUByte().toInt()
            val byte2 = get().toUByte().toInt()
            val byte3 = get().toUByte().toInt()
            (byte1 shl 16) + (byte2 shl 8) + byte3
        }

        0x84 -> {
            requireRemaining(4)
            val raw = getInt()
            if (raw < 0) {
                throw DecodeException("invalid length: negative 32-bit value")
            }
            raw
        }

        else -> size
    }
    val maxDecodeBytes = OkSharedPreferences.maxDecodeBytesPerField
    if (len < 0 || len > maxDecodeBytes) {
        throw DecodeException("invalid length: $len (max $maxDecodeBytes)")
    }
    return len
}

internal fun ByteBuffer.getString(): String {
    val len = getLen()
    if (len > remaining()) {
        throw DecodeException("length $len exceeds remaining ${remaining()}")
    }
    val byteArray = ByteArray(len)
    get(byteArray)
    return String(byteArray)
}

internal fun ByteBuffer.getSet(): Set<String> {
    val len = getLen()
    val mutableSet = mutableSetOf<String>()
    for (i in 0 until len) {
        mutableSet.add(getString())
    }
    return mutableSet
}

internal fun String.toDerLVByteArray(): ByteArray {
    val byteArray = this.toByteArray()
    val len = byteArray.size
    val derLVByteArray = len.toDerLVByteArray()
    return ByteBuffer.allocate(derLVByteArray.size + len).put(derLVByteArray)
        .put(byteArray)
        .array()
}

internal fun Set<String>.toDerLVByteArray(): ByteArray {
    val byteArrayOutputStream = ByteArrayOutputStream()
    val dataOutputStream = DataOutputStream(byteArrayOutputStream)
    dataOutputStream.write(size.toDerLVByteArray())
    for (str in this) {
        dataOutputStream.write(str.toDerLVByteArray())
    }
    return byteArrayOutputStream.toByteArray()
}

internal fun Int.toDerLVByteArray(): ByteArray {
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

    val data = 0x80 + byteLen
    val sliceBytes = allocate.array().sliceArray(0 until byteLen)
    sliceBytes.reverse()
    return ByteBuffer.allocate(byteLen + 1).put(data.toByte()).put(sliceBytes).array()
}
