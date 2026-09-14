package online.greatfeng.oksharedpreferences

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.ByteBuffer

/**
 * JVM unit tests for the DER-style length-value codec used by the on-disk format.
 * These do not require Android; they verify encode/decode round-trips that
 * SharedPreferences persistence depends on.
 */
class DerLVCodecTest {

    @Test
    fun lengthEncoding_shortForm_under128() {
        for (len in listOf(0, 1, 127)) {
            val encoded = len.toDerLVByteArray()
            assertEquals(1, encoded.size)
            assertEquals(len.toByte(), encoded[0])
            assertEquals(len, ByteBuffer.wrap(encoded).getLen())
        }
    }

    @Test
    fun lengthEncoding_oneExtraByte_128to255() {
        for (len in listOf(128, 200, 255)) {
            val encoded = len.toDerLVByteArray()
            assertEquals(2, encoded.size)
            assertEquals(0x81.toByte(), encoded[0])
            assertEquals(len, ByteBuffer.wrap(encoded).getLen())
        }
    }

    @Test
    fun lengthEncoding_twoExtraBytes_256to65535() {
        for (len in listOf(256, 1000, 65535)) {
            val encoded = len.toDerLVByteArray()
            assertEquals(3, encoded.size)
            assertEquals(0x82.toByte(), encoded[0])
            assertEquals(len, ByteBuffer.wrap(encoded).getLen())
        }
    }

    @Test
    fun lengthEncoding_threeExtraBytes() {
        val len = 65536
        val encoded = len.toDerLVByteArray()
        assertEquals(4, encoded.size)
        assertEquals(0x83.toByte(), encoded[0])
        assertEquals(len, ByteBuffer.wrap(encoded).getLen())
    }

    @Test
    fun stringRoundTrip_asciiEmptyAndUnicode() {
        val samples = listOf(
            "",
            "a",
            "hello",
            "中文测试",
            "emoji 😀",
            "a".repeat(200),
            "测".repeat(300)
        )
        for (s in samples) {
            val bytes = s.toDerLVByteArray()
            val decoded = ByteBuffer.wrap(bytes).getString()
            assertEquals(s, decoded)
        }
    }

    @Test
    fun stringSetRoundTrip() {
        val set = linkedSetOf(
            "one",
            "two",
            "中文",
            "",
            "long-" + "x".repeat(180)
        )
        val bytes = set.toDerLVByteArray()
        val decoded = ByteBuffer.wrap(bytes).getSet()
        assertEquals(set, decoded)
    }

    @Test
    fun stringSetRoundTrip_empty() {
        val set = emptySet<String>()
        val bytes = set.toDerLVByteArray()
        val decoded = ByteBuffer.wrap(bytes).getSet()
        assertTrue(decoded.isEmpty())
    }

    @Test
    fun sequentialEntries_decodeInOrder() {
        val first = "key1"
        val second = "value-中文"
        val buffer = ByteBuffer.allocate(first.toDerLVByteArray().size + second.toDerLVByteArray().size)
        buffer.put(first.toDerLVByteArray())
        buffer.put(second.toDerLVByteArray())
        buffer.flip()
        assertEquals(first, buffer.getString())
        assertEquals(second, buffer.getString())
        assertEquals(buffer.limit(), buffer.position())
    }

    @Test
    fun lengthPrefix_isStableForSameInput() {
        val s = "stable"
        assertArrayEquals(s.toDerLVByteArray(), s.toDerLVByteArray())
    }
}
