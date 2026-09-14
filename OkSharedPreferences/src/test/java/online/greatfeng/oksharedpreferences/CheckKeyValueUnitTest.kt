package online.greatfeng.oksharedpreferences

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CheckKeyValueUnitTest {

    @Test
    fun nullKey_isInvalid() {
        val key: String? = null
        assertFalse(key.checkKey())
    }

    @Test
    fun emptyKey_isValid() {
        assertTrue("".checkKey())
    }

    @Test
    fun normalKey_isValid() {
        assertTrue("settings.user.name".checkKey())
    }

    @Test
    fun nullStringValue_isValidBecauseItMeansRemove() {
        val value: String? = null
        assertTrue(value.checkValue())
    }

    @Test
    fun emptyStringValue_isValid() {
        assertTrue("".checkValue())
    }

    @Test
    fun nullStringSet_isValidBecauseItMeansRemove() {
        val values: MutableSet<String>? = null
        assertTrue(values.checkValue())
    }

    @Test
    fun emptyStringSet_isValid() {
        assertTrue(mutableSetOf<String>().checkValue())
    }

    @Test
    fun stringSetWithNormalItems_isValid() {
        assertTrue(mutableSetOf("a", "b").checkValue())
    }
}
