package online.greatfeng.oksharedpreferences

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.ByteBuffer

class ExtByteBufferUnitTest {

    @Test
    fun getLen_shortForm() {
        val buf = ByteBuffer.wrap(byteArrayOf(5))
        assertEquals(5, buf.getLen())
    }

    @Test
    fun getLen_oneByteForm_0x81() {
        val buf = ByteBuffer.wrap(byteArrayOf(0x81.toByte(), 200.toByte()))
        assertEquals(200, buf.getLen())
    }

    @Test
    fun getLen_twoByteForm_0x82() {
        val buf = ByteBuffer.wrap(byteArrayOf(0x82.toByte(), 0x01, 0x00))
        assertEquals(256, buf.getLen())
    }

    @Test
    fun getLen_threeByteForm_0x83() {
        val buf = ByteBuffer.wrap(byteArrayOf(0x83.toByte(), 0x00, 0x01, 0x00))
        assertEquals(256, buf.getLen())
    }

    @Test
    fun getLen_fourByteForm_0x84() {
        val buf = ByteBuffer.wrap(
            byteArrayOf(0x84.toByte(), 0x00, 0x00, 0x01, 0x00)
        )
        assertEquals(256, buf.getLen())
    }

    @Test
    fun toDerLVByteArray_int_zero() {
        assertEquals(1, 0.toDerLVByteArray().size)
        assertEquals(0, ByteBuffer.wrap(0.toDerLVByteArray()).getLen())
    }

    @Test
    fun toDerLVByteArray_int_127() {
        assertEquals(1, 127.toDerLVByteArray().size)
        assertEquals(127, ByteBuffer.wrap(127.toDerLVByteArray()).getLen())
    }

    @Test
    fun getLen_truncatedBuffer_throws() {
        val buf = ByteBuffer.wrap(byteArrayOf(0x82.toByte(), 0x01))
        try {
            buf.getLen()
            throw AssertionError("expected DecodeException")
        } catch (e: DecodeException) {
            assertTrue(e.message?.contains("truncated") == true)
        }
    }

    @Test
    fun getString_lengthExceedsRemaining_throws() {
        val buf = ByteBuffer.wrap(byteArrayOf(10, 'a'.code.toByte()))
        try {
            buf.getString()
            throw AssertionError("expected DecodeException")
        } catch (e: DecodeException) {
            assertTrue(e.message?.contains("exceeds remaining") == true)
        }
    }
}
