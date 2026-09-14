package online.greatfeng.oksharedpreferences

import android.content.Context
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Tests every public entry-point on [OkSharedPreferences] extensions and
 * [OkSharedPreferences.clearOnSharedPreferenceChangeListener].
 */
@RunWith(AndroidJUnit4::class)
class OkSpContextApiInstrumentedTest {

    private lateinit var context: Context

    @Before
    fun setUp() {
        context = OkSpTestHelper.context()
    }

    @After
    fun tearDown() {
        OkSpTestHelper.cleanupAll(context)
    }

    @Test
    fun getOkSharedPreferences_singleArg_returnsInstance() {
        val name = OkSpTestHelper.uniqueName("ctx_single")
        val prefs = context.getOkSharedPreferences(name)
        assertNotNull(prefs)
        assertTrue(prefs.edit().putInt("k", 1).commit())
    }

    @Test
    fun getOkSharedPreferences_twoArgs_withoutMigration() {
        val name = OkSpTestHelper.uniqueName("ctx_no_mig")
        context.getSharedPreferences(name, Context.MODE_PRIVATE)
            .edit().putInt("legacy", 9).commit()
        val prefs = context.getOkSharedPreferences(name, false)
        assertEquals(0, prefs.getInt("legacy", 0))
    }

    @Test
    fun getOkSharedPreferences_twoArgs_withMigration() {
        val name = OkSpTestHelper.uniqueName("ctx_mig")
        context.getSharedPreferences(name, Context.MODE_PRIVATE)
            .edit().putInt("legacy", 77).commit()
        val prefs = context.getOkSharedPreferences(name, true)
        assertEquals(77, prefs.getInt("legacy", 0))
        assertEquals(0, context.getSharedPreferences(name, Context.MODE_PRIVATE).getInt("legacy", 0))
    }

    @Test
    fun deleteOkSharedPreferences_removesFile() {
        val name = OkSpTestHelper.uniqueName("ctx_delete")
        context.getOkSharedPreferences(name).edit().putInt("k", 1).commit()
        assertTrue(OkSpTestHelper.okSpFile(context, name).exists())
        assertTrue(context.deleteOkSharedPreferences(name))
        assertFalse(OkSpTestHelper.okSpFile(context, name).exists())
    }

    @Test
    fun deleteSharedPreferences_javaAlias() {
        val name = OkSpTestHelper.uniqueName("ctx_java_delete")
        context.getOkSharedPreferences(name).edit().putString("s", "v").commit()
        assertTrue(deleteSharedPreferences(context, name))
        assertFalse(context.getOkSharedPreferences(name).contains("s"))
    }

    @Test
    fun clearOnSharedPreferenceChangeListener_clearsCallbacks() {
        val name = OkSpTestHelper.uniqueName("ctx_clear_listener")
        val prefs = context.getOkSharedPreferences(name)
        var calls = 0
        prefs.registerOnSharedPreferenceChangeListener { _, _ -> calls++ }
        prefs.clearOnSharedPreferenceChangeListener()
        prefs.edit().putInt("k", 1).commit()
        assertEquals(0, calls)
    }
}
