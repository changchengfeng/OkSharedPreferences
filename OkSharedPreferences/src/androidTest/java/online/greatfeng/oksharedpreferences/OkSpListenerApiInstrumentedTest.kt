package online.greatfeng.oksharedpreferences

import android.content.SharedPreferences
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.util.Collections
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

@RunWith(AndroidJUnit4::class)
class OkSpListenerApiInstrumentedTest {

    private lateinit var prefs: OkSharedPreferences

    @Before
    fun setUp() {
        val name = OkSpTestHelper.uniqueName("listener")
        prefs = OkSpTestHelper.context().getOkSharedPreferences(name)
    }

    @After
    fun tearDown() {
        OkSpTestHelper.cleanupAll(OkSpTestHelper.context())
    }

    @Test
    fun registerOnSharedPreferenceChangeListener_notifiesOnCommit() {
        val latch = CountDownLatch(1)
        val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
            if (key == "k") latch.countDown()
        }
        prefs.registerOnSharedPreferenceChangeListener(listener)
        prefs.edit().putInt("k", 1).commit()
        assertTrue(latch.await(3, TimeUnit.SECONDS))
        prefs.unregisterOnSharedPreferenceChangeListener(listener)
    }

    @Test
    fun registerOnSharedPreferenceChangeListener_notifiesOnApply() {
        val keys = Collections.synchronizedList(mutableListOf<String?>())
        val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, key -> keys.add(key) }
        prefs.registerOnSharedPreferenceChangeListener(listener)
        prefs.edit().putInt("k", 2).apply()
        OkSpTestHelper.await(100)
        assertTrue(keys.contains("k"))
        prefs.unregisterOnSharedPreferenceChangeListener(listener)
    }

    @Test
    fun unregisterOnSharedPreferenceChangeListener_stopsCallbacks() {
        var calls = 0
        val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, _ -> calls++ }
        prefs.registerOnSharedPreferenceChangeListener(listener)
        prefs.unregisterOnSharedPreferenceChangeListener(listener)
        prefs.edit().putInt("k", 1).commit()
        assertEquals(0, calls)
    }

    @Test
    fun registerOnSharedPreferenceChangeListener_null_throwsNpe() {
        try {
            prefs.registerOnSharedPreferenceChangeListener(null)
            throw AssertionError("expected NPE")
        } catch (e: NullPointerException) {
            assertNotNull(e.message)
        }
    }

    @Test
    fun unregisterOnSharedPreferenceChangeListener_null_throwsNpe() {
        try {
            prefs.unregisterOnSharedPreferenceChangeListener(null)
            throw AssertionError("expected NPE")
        } catch (e: NullPointerException) {
            assertNotNull(e.message)
        }
    }

    @Test
    fun listener_exceptionDoesNotBreakCommit() {
        prefs.registerOnSharedPreferenceChangeListener { _, _ ->
            throw RuntimeException("boom")
        }
        assertTrue(prefs.edit().putInt("k", 3).commit())
        assertEquals(3, prefs.getInt("k", 0))
    }
}
