package online.greatfeng.oksharedpreferences

import android.content.Context
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

@RunWith(AndroidJUnit4::class)
class OkSpContractInstrumentedTest {

    private lateinit var context: Context
    private val names = mutableListOf<String>()

    @Before
    fun setUp() {
        context = OkSpTestHelper.context()
    }

    @After
    fun tearDown() {
        names.forEach { context.deleteOkSharedPreferences(it) }
        names.clear()
    }

    private fun newPrefs(prefix: String = "contract"): OkSharedPreferences {
        val name = OkSpTestHelper.uniqueName(prefix)
        names.add(name)
        return context.getOkSharedPreferences(name)
    }

    @Test
    fun allPrimitiveTypes_roundTripCommit() {
        val prefs = newPrefs("types")
        val set = mutableSetOf("a", "b", "中文")
        assertTrue(
            prefs.edit()
                .putBoolean("b", true)
                .putFloat("f", 3.1415926f)
                .putInt("i", Int.MIN_VALUE)
                .putLong("l", Long.MAX_VALUE)
                .putString("s", "hello 你好")
                .putStringSet("set", set)
                .commit()
        )
        assertEquals(true, prefs.getBoolean("b", false))
        assertEquals(3.1415926f, prefs.getFloat("f", 0f), 0f)
        assertEquals(Int.MIN_VALUE, prefs.getInt("i", 0))
        assertEquals(Long.MAX_VALUE, prefs.getLong("l", 0L))
        assertEquals("hello 你好", prefs.getString("s", null))
        assertEquals(set, prefs.getStringSet("set", emptySet()))
    }

    @Test
    fun defaults_whenKeyMissing() {
        val prefs = newPrefs("defaults")
        assertEquals(false, prefs.getBoolean("b", false))
        assertEquals(1.5f, prefs.getFloat("f", 1.5f), 0f)
        assertEquals(9, prefs.getInt("i", 9))
        assertEquals(8L, prefs.getLong("l", 8L))
        assertEquals("d", prefs.getString("s", "d"))
        assertEquals(setOf("d"), prefs.getStringSet("set", mutableSetOf("d")))
        assertFalse(prefs.contains("b"))
    }

    @Test
    fun typeMismatch_returnsDefault() {
        val prefs = newPrefs("mismatch")
        prefs.edit().putInt("k", 1).commit()
        assertEquals("def", prefs.getString("k", "def"))
        assertEquals(false, prefs.getBoolean("k", false))
        assertEquals(0L, prefs.getLong("k", 0L))
        assertEquals(0f, prefs.getFloat("k", 0f), 0f)
        assertNull(prefs.getStringSet("k", null))
    }

    @Test
    fun nullKey_isRejected() {
        val prefs = newPrefs("null_key")
        prefs.edit().putInt(null, 1).putString(null, "x").remove(null).commit()
        assertTrue(prefs.all.isEmpty())
        assertEquals(0, prefs.getInt(null, 0))
        assertFalse(prefs.contains(null))
    }

    @Test
    fun emptyStringAndEmptySet() {
        val prefs = newPrefs("empty")
        prefs.edit().putString("s", "").putStringSet("set", mutableSetOf()).commit()
        assertEquals("", prefs.getString("s", "d"))
        assertTrue(prefs.getStringSet("set", mutableSetOf("x"))?.isEmpty() == true)
    }

    @Test
    fun overwriteSameKey() {
        val prefs = newPrefs("overwrite")
        prefs.edit().putInt("k", 1).commit()
        prefs.edit().putInt("k", 2).commit()
        assertEquals(2, prefs.getInt("k", 0))
    }

    @Test
    fun getAll_containsAllKeys() {
        val prefs = newPrefs("all_keys")
        prefs.edit().putInt("i", 1).putString("s", "v").commit()
        val all = prefs.all
        assertEquals(2, all.size)
        assertEquals(1, all["i"])
        assertEquals("v", all["s"])
    }

    @Test
    fun sameName_returnsSameInstance() {
        val name = OkSpTestHelper.uniqueName("same")
        names.add(name)
        val a = context.getOkSharedPreferences(name)
        val b = context.getOkSharedPreferences(name)
        assertTrue(a === b)
    }

    @Test
    fun migration_copiesOldSharedPreferencesThenClearsThem() {
        val name = OkSpTestHelper.uniqueName("migrate")
        names.add(name)
        val old = context.getSharedPreferences(name, Context.MODE_PRIVATE)
        old.edit().putInt("legacy", 77).putString("s", "old").commit()

        val ok = context.getOkSharedPreferences(name, true)
        assertEquals(77, ok.getInt("legacy", 0))
        assertEquals("old", ok.getString("s", null))
        assertEquals(0, context.getSharedPreferences(name, Context.MODE_PRIVATE).getInt("legacy", 0))
    }

    @Test
    fun migrationFalse_doesNotCopyOldSharedPreferences() {
        val name = OkSpTestHelper.uniqueName("no_migrate")
        names.add(name)
        context.getSharedPreferences(name, Context.MODE_PRIVATE)
            .edit().putInt("legacy", 77).commit()
        val ok = context.getOkSharedPreferences(name, false)
        assertEquals(0, ok.getInt("legacy", 0))
    }

    @Test
    fun registerNullListener_throws() {
        val prefs = newPrefs("npe")
        try {
            prefs.registerOnSharedPreferenceChangeListener(null)
            throw AssertionError("expected NPE")
        } catch (e: NullPointerException) {
            assertNotNull(e.message)
        }
    }

    @Test
    fun unregisterNullListener_throws() {
        val prefs = newPrefs("npe2")
        try {
            prefs.unregisterOnSharedPreferenceChangeListener(null)
            throw AssertionError("expected NPE")
        } catch (e: NullPointerException) {
            assertNotNull(e.message)
        }
    }

    @Test
    fun clearOnSharedPreferenceChangeListener_stopsCallbacks() {
        val prefs = newPrefs("clear_listeners")
        var calls = 0
        val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, _ -> calls++ }
        prefs.registerOnSharedPreferenceChangeListener(listener)
        prefs.clearOnSharedPreferenceChangeListener()
        prefs.edit().putInt("k", 1).commit()
        assertEquals(0, calls)
    }

    @Test
    fun javaDeleteAlias_works() {
        val name = OkSpTestHelper.uniqueName("java_delete")
        names.add(name)
        context.getOkSharedPreferences(name).edit().putInt("k", 1).commit()
        assertTrue(deleteSharedPreferences(context, name))
        assertFalse(context.getOkSharedPreferences(name).contains("k"))
    }

    @Test
    fun editorChaining_returnsSameEditor() {
        val prefs = newPrefs("chain")
        val editor = prefs.edit()
        assertTrue(editor === editor.putInt("a", 1).putBoolean("b", true).remove("c"))
        assertTrue(editor.commit())
        assertEquals(1, prefs.getInt("a", 0))
        assertTrue(prefs.getBoolean("b", false))
    }

    @Test
    fun booleanFalse_persists() {
        val prefs = newPrefs("bool_false")
        prefs.edit().putBoolean("b", false).commit()
        assertTrue(prefs.contains("b"))
        assertFalse(prefs.getBoolean("b", true))
    }

    @Test
    fun zeroValues_persist() {
        val prefs = newPrefs("zeros")
        prefs.edit().putInt("i", 0).putLong("l", 0L).putFloat("f", 0f).commit()
        assertEquals(0, prefs.getInt("i", 1))
        assertEquals(0L, prefs.getLong("l", 1L))
        assertEquals(0f, prefs.getFloat("f", 1f), 0f)
    }
}
