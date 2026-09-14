package online.greatfeng.oksharedpreferences

import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/**
 * Tests [OkSharedPreferencesManager] via public [Context] extensions and singleton behavior.
 */
@RunWith(AndroidJUnit4::class)
class OkSpManagerApiInstrumentedTest {

    private val context get() = OkSpTestHelper.context()

    @Before
    fun setUp() {
    }

    @After
    fun tearDown() {
        OkSpTestHelper.cleanupAll(context)
    }

    @Test
    fun getInstance_returnsSameManagerForSameAppContext() {
        val a = context.getOkSharedPreferences(OkSpTestHelper.uniqueName("mgr_a"))
        val b = context.getOkSharedPreferences(OkSpTestHelper.uniqueName("mgr_b"))
        val name = OkSpTestHelper.uniqueName("mgr_same")
        val first = context.getOkSharedPreferences(name)
        val second = context.applicationContext.getOkSharedPreferences(name)
        assertTrue(first === second)
        a.edit().putInt("x", 1).commit()
        b.edit().putInt("y", 2).commit()
    }

    @Test
    fun getOkSharedPreferences_cachesByName() {
        val name = OkSpTestHelper.uniqueName("mgr_cache")
        val first = context.getOkSharedPreferences(name)
        val second = context.getOkSharedPreferences(name)
        assertTrue(first === second)
        first.edit().putInt("k", 1).commit()
        assertEquals(1, second.getInt("k", 0))
    }

    @Test
    fun deleteSharedPreferences_whenCached() {
        val name = OkSpTestHelper.uniqueName("mgr_del_cached")
        val prefs = context.getOkSharedPreferences(name)
        prefs.edit().putInt("k", 1).commit()
        assertTrue(context.deleteOkSharedPreferences(name))
        assertFalse(OkSpTestHelper.okSpFile(context, name).exists())
        val again = context.getOkSharedPreferences(name)
        assertFalse(again.contains("k"))
        assertFalse((again as OkSharedPreferencesImpl).isDestroyed())
    }

    @Test
    fun deleteSharedPreferences_whenNotCached() {
        val name = OkSpTestHelper.uniqueName("mgr_del_disk")
        OkSpTestHelper.okSpDir(context).mkdirs()
        val file = OkSpTestHelper.okSpFile(context, name)
        file.writeBytes(byteArrayOf(1, 2, 3))
        OkSpTestHelper.track(name)
        assertTrue(context.deleteOkSharedPreferences(name))
        assertFalse(file.exists())
    }

    @Test
    fun fileObserverDirectory_isCreated() {
        val name = OkSpTestHelper.uniqueName("mgr_dir")
        context.getOkSharedPreferences(name)
        assertTrue(OkSpTestHelper.okSpDir(context).isDirectory)
    }
}
