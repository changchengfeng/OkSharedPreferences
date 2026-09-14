package online.greatfeng.oksharedpreferences

import android.content.SharedPreferences
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * One test per [SharedPreferences.Editor] method on [OkSharedPreferencesImpl.OkEditor].
 */
@RunWith(AndroidJUnit4::class)
class OkSpEditorApiInstrumentedTest {

    private lateinit var prefs: OkSharedPreferences
    private lateinit var name: String
    private val context get() = OkSpTestHelper.context()

    @Before
    fun setUp() {
        name = OkSpTestHelper.uniqueName("editor")
        prefs = context.getOkSharedPreferences(name)
    }

    @After
    fun tearDown() {
        OkSpTestHelper.cleanupAll(context)
    }

    @Test
    fun putString_storesValue() {
        prefs.edit().putString("k", "v").commit()
        assertEquals("v", prefs.getString("k", null))
    }

    @Test
    fun putString_nullValue_removesKey() {
        prefs.edit().putString("k", "v").commit()
        prefs.edit().putString("k", null).commit()
        assertFalse(prefs.contains("k"))
    }

    @Test
    fun putString_nullKey_ignored() {
        prefs.edit().putString(null, "v").commit()
        assertTrue(prefs.all.isEmpty())
    }

    @Test
    fun putStringSet_storesCopy() {
        val caller = mutableSetOf("one")
        prefs.edit().putStringSet("s", caller).commit()
        caller.add("two")
        assertEquals(setOf("one"), prefs.getStringSet("s", emptySet()))
    }

    @Test
    fun putStringSet_nullValue_removesKey() {
        prefs.edit().putStringSet("s", mutableSetOf("a")).commit()
        prefs.edit().putStringSet("s", null).commit()
        assertFalse(prefs.contains("s"))
    }

    @Test
    fun putStringSet_nullKey_ignored() {
        prefs.edit().putStringSet(null, mutableSetOf("a")).commit()
        assertTrue(prefs.all.isEmpty())
    }

    @Test
    fun putInt_storesValue() {
        prefs.edit().putInt("i", -7).commit()
        assertEquals(-7, prefs.getInt("i", 0))
    }

    @Test
    fun putInt_nullKey_ignored() {
        prefs.edit().putInt(null, 1).commit()
        assertTrue(prefs.all.isEmpty())
    }

    @Test
    fun putLong_storesValue() {
        prefs.edit().putLong("l", Long.MIN_VALUE).commit()
        assertEquals(Long.MIN_VALUE, prefs.getLong("l", 0L))
    }

    @Test
    fun putLong_nullKey_ignored() {
        prefs.edit().putLong(null, 1L).commit()
        assertTrue(prefs.all.isEmpty())
    }

    @Test
    fun putFloat_storesValue() {
        prefs.edit().putFloat("f", 3.14f).commit()
        assertEquals(3.14f, prefs.getFloat("f", 0f), 0f)
    }

    @Test
    fun putFloat_nullKey_ignored() {
        prefs.edit().putFloat(null, 1f).commit()
        assertTrue(prefs.all.isEmpty())
    }

    @Test
    fun putBoolean_storesValue() {
        prefs.edit().putBoolean("b", false).commit()
        assertFalse(prefs.getBoolean("b", true))
    }

    @Test
    fun putBoolean_nullKey_ignored() {
        prefs.edit().putBoolean(null, true).commit()
        assertTrue(prefs.all.isEmpty())
    }

    @Test
    fun remove_deletesKey() {
        prefs.edit().putInt("k", 1).commit()
        prefs.edit().remove("k").commit()
        assertFalse(prefs.contains("k"))
    }

    @Test
    fun remove_nullKey_ignored() {
        prefs.edit().putInt("k", 1).commit()
        prefs.edit().remove(null).commit()
        assertTrue(prefs.contains("k"))
    }

    @Test
    fun clear_removesAllKeys() {
        prefs.edit().putInt("a", 1).putInt("b", 2).commit()
        prefs.edit().clear().commit()
        assertTrue(prefs.all.isEmpty())
    }

    @Test
    fun clear_thenPut_keepsPutOnSameEditor() {
        prefs.edit().putInt("old", 1).commit()
        prefs.edit().clear().putInt("new", 2).commit()
        assertFalse(prefs.contains("old"))
        assertEquals(2, prefs.getInt("new", 0))
    }

    @Test
    fun commit_returnsTrueAndPersists() {
        assertTrue(prefs.edit().putString("k", "disk").commit())
        assertTrue(OkSpTestHelper.awaitDisk(context, name))
        val reloaded = context.getOkSharedPreferences(name)
        assertEquals("disk", reloaded.getString("k", null))
    }

    @Test
    fun commit_afterDestroy_returnsFalse() {
        val impl = prefs as OkSharedPreferencesImpl
        impl.clearData(true)
        assertFalse(prefs.edit().putInt("k", 1).commit())
    }

    @Test
    fun apply_updatesMemoryImmediately() {
        prefs.edit().putInt("n", 5).apply()
        assertEquals(5, prefs.getInt("n", 0))
    }

    @Test
    fun apply_persistsToDiskEventually() {
        prefs.edit().putString("k", "async").apply()
        val ok = OkSpTestHelper.awaitApplyPersisted(
            context,
            prefs,
            name,
            expected = { prefs.getString("k", null) == "async" }
        )
        assertTrue(ok)
        assertTrue(OkSpTestHelper.okSpFile(context, name).exists())
    }

    @Test
    fun editor_chainingReturnsSameInstance() {
        val editor = prefs.edit()
        assertNotNull(editor)
        assertTrue(editor === editor.putInt("a", 1).putBoolean("b", true))
    }
}
