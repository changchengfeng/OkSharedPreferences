package online.greatfeng.oksharedpreferences

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.io.DataOutputStream
import java.nio.ByteBuffer

/**
 * Pure JVM reconstruction of the on-disk entry format used by [OkSharedPreferencesImpl].
 * Type tags must stay in sync with the implementation constants.
 */
class OkSpFileFormatUnitTest {

    companion object {
        const val B = 1
        const val F = 2
        const val I = 4
        const val L = 8
        const val S = 16
        const val T = 32
    }

    private fun encode(entries: List<Pair<String, Any>>): ByteArray {
        val baos = ByteArrayOutputStream()
        DataOutputStream(baos).use { out ->
            for ((key, value) in entries) {
                out.write(key.toDerLVByteArray())
                when (value) {
                    is Boolean -> {
                        out.writeByte(B)
                        out.writeByte(if (value) 1 else 0)
                    }
                    is Float -> {
                        out.writeByte(F)
                        out.writeFloat(value)
                    }
                    is Int -> {
                        out.writeByte(I)
                        out.writeInt(value)
                    }
                    is Long -> {
                        out.writeByte(L)
                        out.writeLong(value)
                    }
                    is String -> {
                        out.writeByte(S)
                        out.write(value.toDerLVByteArray())
                    }
                    is Set<*> -> {
                        out.writeByte(T)
                        @Suppress("UNCHECKED_CAST")
                        out.write((value as Set<String>).toDerLVByteArray())
                    }
                    else -> throw IllegalArgumentException("unsupported $value")
                }
            }
        }
        return baos.toByteArray()
    }

    private fun decode(bytes: ByteArray): Map<String, Any> {
        val result = LinkedHashMap<String, Any>()
        if (bytes.isEmpty()) {
            return result
        }
        val buf = ByteBuffer.wrap(bytes)
        while (buf.position() < buf.limit()) {
            val key = buf.getString()
            val type = buf.get().toUByte().toInt()
            result[key] = when (type) {
                B -> buf.get().toInt() == 1
                F -> buf.getFloat()
                I -> buf.getInt()
                L -> buf.getLong()
                S -> buf.getString()
                T -> buf.getSet()
                else -> throw IllegalStateException("unknown type $type")
            }
        }
        return result
    }

    @Test
    fun emptyPayload_decodesToEmptyMap() {
        assertTrue(decode(ByteArray(0)).isEmpty())
    }

    @Test
    fun allSupportedTypes_roundTrip() {
        val original = listOf(
            "bool_true" to true,
            "bool_false" to false,
            "float" to 3.1415926f,
            "int_min" to Int.MIN_VALUE,
            "int_max" to Int.MAX_VALUE,
            "long" to Long.MAX_VALUE,
            "string" to "hello 你好",
            "empty" to "",
            "set" to setOf("a", "b", "中文")
        )
        val decoded = decode(encode(original))
        assertEquals(original.toMap(), decoded)
    }

    @Test
    fun unknownType_throws() {
        val baos = ByteArrayOutputStream()
        DataOutputStream(baos).use { out ->
            out.write("k".toDerLVByteArray())
            out.writeByte(99)
            out.writeByte(1)
        }
        try {
            decode(baos.toByteArray())
            throw AssertionError("expected IllegalStateException")
        } catch (e: IllegalStateException) {
            assertTrue(e.message?.contains("99") == true)
        }
    }

    @Test
    fun truncatedPayload_throwsBufferUnderflow() {
        val full = encode(listOf("k" to "value"))
        val truncated = full.copyOf(full.size - 2)
        try {
            decode(truncated)
            throw AssertionError("expected underflow")
        } catch (e: Exception) {
            assertTrue(
                e is DecodeException ||
                    e is java.nio.BufferUnderflowException ||
                    e is IndexOutOfBoundsException
            )
        }
    }
}
