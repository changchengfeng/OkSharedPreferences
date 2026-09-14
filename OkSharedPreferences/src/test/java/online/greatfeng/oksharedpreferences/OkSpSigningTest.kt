package online.greatfeng.oksharedpreferences

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class OkSpSigningTest {

    private val signingId = "a1b2c3d4e5f6789012345678901234567890abcdef1234567890abcdef123456"

    @Test
    fun storageBaseNameWithSigningId_formatsAsSigningUnderscoreName() {
        assertEquals(
            "${signingId}_user_prefs",
            OkSpSigning.storageBaseNameWithSigningId(signingId, "user_prefs")
        )
    }

    @Test
    fun logicalNameFromStorageBaseName_roundTrips() {
        val base = OkSpSigning.storageBaseNameWithSigningId(signingId, "settings")
        assertEquals("settings", OkSpSigning.logicalNameFromStorageBaseName(signingId, base))
    }

    @Test
    fun logicalNameFromStorageBaseName_rejectsForeignSigningId() {
        val foreign = OkSpSigning.storageBaseNameWithSigningId("other_signing_id", "settings")
        assertNull(OkSpSigning.logicalNameFromStorageBaseName(signingId, foreign))
    }

    @Test
    fun logicalNameFromStorageBaseName_rejectsLegacyUnprefixedName() {
        assertNull(OkSpSigning.logicalNameFromStorageBaseName(signingId, "legacy_name"))
    }
}
