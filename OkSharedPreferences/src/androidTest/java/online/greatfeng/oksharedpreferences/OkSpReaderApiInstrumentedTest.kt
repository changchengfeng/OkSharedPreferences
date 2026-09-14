package online.greatfeng.oksharedpreferences

import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * One test per [android.content.SharedPreferences] read method on [OkSharedPreferences].
 */
@RunWith(AndroidJUnit4::class)
class OkSpReaderApiInstrumentedTest {

    private lateinit var prefs: OkSharedPreferences
    private lateinit var name: String

    @Before
    fun setUp() {
        name = OkSpTestHelper.uniqueName("reader")
        prefs = OkSpTestHelper.context().getOkSharedPreferences(name)
        prefs.edit()
            .putBoolean("bool", true)
            .putFloat("float", 2.5f)
            .putInt("int", 42)
            .putLong("long", 99L)
            .putString("string", "text")
            .putStringSet("set", mutableSetOf("a", "b"))
            .commit()
    }

    @After
    fun tearDown() {
        OkSpTestHelper.cleanupAll(OkSpTestHelper.context())
    }

    @Test
    fun getAll_returnsCopyWithAllEntries() {
        val all = prefs.all
        assertEquals(6, all.size)
        assertEquals(true, all["bool"])
        assertEquals(2.5f, all["float"])
        assertEquals(42, all["int"])
        assertEquals(99L, all["long"])
        assertEquals("text", all["string"])
        assertEquals(setOf("a", "b"), all["set"])
        all.clear()
        assertEquals(6, prefs.all.size)
    }

    @Test
    fun getAll_stringSetIsCopied() {
        val all = prefs.all
        @Suppress("UNCHECKED_CAST")
        val set = all["set"] as MutableSet<String>
        set.add("c")
        assertEquals(setOf("a", "b"), prefs.getStringSet("set", emptySet()))
    }

    @Test
    fun getString_present() {
        assertEquals("text", prefs.getString("string", "d"))
    }

    @Test
    fun getString_missing_returnsDefault() {
        assertEquals("d", prefs.getString("missing", "d"))
    }

    @Test
    fun getString_nullKey_returnsDefault() {
        assertNull(prefs.getString(null, null))
    }

    @Test
    fun getString_wrongType_returnsDefault() {
        assertEquals("d", prefs.getString("int", "d"))
    }

    @Test
    fun getStringSet_present() {
        assertEquals(setOf("a", "b"), prefs.getStringSet("set", emptySet()))
    }

    @Test
    fun getStringSet_missing_returnsDefault() {
        assertEquals(setOf("x"), prefs.getStringSet("missing", mutableSetOf("x")))
    }

    @Test
    fun getStringSet_nullKey_returnsDefault() {
        assertNull(prefs.getStringSet(null, null))
    }

    @Test
    fun getStringSet_wrongType_returnsDefault() {
        assertNull(prefs.getStringSet("int", null))
    }

    @Test
    fun getStringSet_returnsCopy() {
        val set = prefs.getStringSet("set", emptySet())
        set?.add("z")
        assertEquals(setOf("a", "b"), prefs.getStringSet("set", emptySet()))
        assertNotSame(prefs.getStringSet("set", emptySet()), prefs.getStringSet("set", emptySet()))
    }

    @Test
    fun getInt_present() {
        assertEquals(42, prefs.getInt("int", 0))
    }

    @Test
    fun getInt_missing_returnsDefault() {
        assertEquals(7, prefs.getInt("missing", 7))
    }

    @Test
    fun getInt_nullKey_returnsDefault() {
        assertEquals(7, prefs.getInt(null, 7))
    }

    @Test
    fun getInt_wrongType_returnsDefault() {
        assertEquals(7, prefs.getInt("string", 7))
    }

    @Test
    fun getLong_present() {
        assertEquals(99L, prefs.getLong("long", 0L))
    }

    @Test
    fun getLong_missing_returnsDefault() {
        assertEquals(8L, prefs.getLong("missing", 8L))
    }

    @Test
    fun getLong_nullKey_returnsDefault() {
        assertEquals(8L, prefs.getLong(null, 8L))
    }

    @Test
    fun getLong_wrongType_returnsDefault() {
        assertEquals(8L, prefs.getLong("int", 8L))
    }

    @Test
    fun getFloat_present() {
        assertEquals(2.5f, prefs.getFloat("float", 0f), 0f)
    }

    @Test
    fun getFloat_missing_returnsDefault() {
        assertEquals(1.1f, prefs.getFloat("missing", 1.1f), 0f)
    }

    @Test
    fun getFloat_nullKey_returnsDefault() {
        assertEquals(1.1f, prefs.getFloat(null, 1.1f), 0f)
    }

    @Test
    fun getFloat_wrongType_returnsDefault() {
        assertEquals(1.1f, prefs.getFloat("int", 1.1f), 0f)
    }

    @Test
    fun getBoolean_present() {
        assertTrue(prefs.getBoolean("bool", false))
    }

    @Test
    fun getBoolean_missing_returnsDefault() {
        assertFalse(prefs.getBoolean("missing", false))
    }

    @Test
    fun getBoolean_nullKey_returnsDefault() {
        assertTrue(prefs.getBoolean(null, true))
    }

    @Test
    fun getBoolean_wrongType_returnsDefault() {
        assertFalse(prefs.getBoolean("int", false))
    }

    @Test
    fun contains_present() {
        assertTrue(prefs.contains("int"))
    }

    @Test
    fun contains_missing() {
        assertFalse(prefs.contains("missing"))
    }

    @Test
    fun contains_nullKey() {
        assertFalse(prefs.contains(null))
    }

    @Test
    fun edit_returnsEditor() {
        val editor = prefs.edit()
        assertTrue(editor.putInt("new", 1).commit())
        assertEquals(1, prefs.getInt("new", 0))
    }
}
