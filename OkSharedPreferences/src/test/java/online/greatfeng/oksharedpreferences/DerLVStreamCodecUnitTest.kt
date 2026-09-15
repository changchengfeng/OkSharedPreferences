package online.greatfeng.oksharedpreferences

import org.junit.Assert.assertArrayEquals
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.io.DataOutputStream

class DerLVStreamCodecUnitTest {

    @Test
    fun streamUtf8_matchesByteArrayCodec() {
        val value = "hello_中文"
        val expected = value.toDerLVByteArray()
        val actual = ByteArrayOutputStream().use { buffer ->
            DataOutputStream(buffer).use { out ->
                out.writeDerLVUtf8(value)
                out.flush()
            }
            buffer.toByteArray()
        }
        assertArrayEquals(expected, actual)
    }

    @Test
    fun streamStringSet_matchesByteArrayCodec() {
        val values = linkedSetOf("a", "b", "中文")
        val expected = values.toDerLVByteArray()
        val actual = ByteArrayOutputStream().use { buffer ->
            DataOutputStream(buffer).use { out ->
                out.writeDerLVStringSet(values)
                out.flush()
            }
            buffer.toByteArray()
        }
        assertArrayEquals(expected, actual)
    }
}
