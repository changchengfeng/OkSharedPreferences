package online.greatfeng.oksharedpreferences

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.ByteBuffer

class OkSpLimitsTest {

    @After
    fun tearDown() {
        OkSharedPreferences.resetLimits()
    }

    @Test
    fun defaults_matchDocumentedValues() {
        assertEquals(16 * 1024 * 1024, OkSharedPreferences.DEFAULT_MAX_DECODE_BYTES_PER_FIELD)
        assertEquals(64 * 1024 * 1024, OkSharedPreferences.DEFAULT_MAX_FILE_BYTES)
        assertEquals(OkSharedPreferences.DEFAULT_MAX_DECODE_BYTES_PER_FIELD, OkSharedPreferences.maxDecodeBytesPerField)
        assertEquals(OkSharedPreferences.DEFAULT_MAX_FILE_BYTES, OkSharedPreferences.maxFileBytes)
    }

    @Test
    fun configureLimits_updatesDecodeBound() {
        OkSharedPreferences.configureLimits(maxDecodeBytesPerField = 8)
        val buf = ByteBuffer.wrap(byteArrayOf(10, 'a'.code.toByte()))
        try {
            buf.getString()
            throw AssertionError("expected DecodeException")
        } catch (e: DecodeException) {
            assertTrue(e.message?.contains("max 8") == true)
        }
    }

    @Test
    fun resetLimits_restoresDefaults() {
        OkSharedPreferences.configureLimits(maxDecodeBytesPerField = 32, maxFileBytes = 128)
        OkSharedPreferences.resetLimits()
        assertEquals(OkSharedPreferences.DEFAULT_MAX_DECODE_BYTES_PER_FIELD, OkSharedPreferences.maxDecodeBytesPerField)
        assertEquals(OkSharedPreferences.DEFAULT_MAX_FILE_BYTES, OkSharedPreferences.maxFileBytes)
    }
}
