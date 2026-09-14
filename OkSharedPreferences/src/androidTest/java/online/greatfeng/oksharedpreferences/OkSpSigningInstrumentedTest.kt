package online.greatfeng.oksharedpreferences

import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

@RunWith(AndroidJUnit4::class)
class OkSpSigningInstrumentedTest {

    private val context get() = OkSpTestHelper.context()

    @After
    fun tearDown() {
        OkSpTestHelper.cleanupAll(context)
    }

    @Test
    fun storageFileName_containsSigningIdPrefix() {
        val name = OkSpTestHelper.uniqueName("signed_file")
        val signingId = OkSpSigning.signingId(context)
        context.getOkSharedPreferences(name).edit().putInt("k", 1).commit()

        val file = OkSpTestHelper.okSpFile(context, name)
        assertTrue(file.exists())
        assertTrue(file.name.startsWith("${signingId}_"))
        assertEquals(
            OkSpSigning.storageBaseName(context, name) + OkSharedPreferencesImpl.SUFFIX_OKSP,
            file.name
        )
    }

    @Test
    fun fileObserver_ignoresLegacyUnprefixedFiles() {
        val name = OkSpTestHelper.uniqueName("legacy_ignore")
        val prefs = context.getOkSharedPreferences(name)
        prefs.edit().putInt("from_signed", 1).commit()

        val legacyFile = File(OkSpTestHelper.okSpDir(context), name + OkSharedPreferencesImpl.SUFFIX_OKSP)
        legacyFile.writeBytes(byteArrayOf(0x84.toByte(), 0x00, 0x00, 0x00, 0x05))

        OkSpTestHelper.await(300)
        prefs.reload()
        assertEquals(1, prefs.getInt("from_signed", 0))
        assertFalse(legacyFile.name.startsWith(OkSpSigning.signingId(context) + "_"))
    }
}
