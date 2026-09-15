package online.greatfeng.oksharedpreferences

import android.os.Handler
import android.os.HandlerThread
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.util.Collections

/**
 * Correctness tests for v1.2 performance fast paths (disk snapshot skip / debounced reload).
 */
@RunWith(AndroidJUnit4::class)
class OkSpPerformanceInstrumentedTest {

    private lateinit var dir: File
    private lateinit var handlerThread: HandlerThread
    private lateinit var handler: Handler

    @Before
    fun setUp() {
        val context = OkSpTestHelper.context()
        dir = File(context.cacheDir, "oksp-perf-${System.nanoTime()}")
        assertTrue(dir.mkdirs())
        handlerThread = HandlerThread("oksp-perf-test").also { it.start() }
        handler = Handler(handlerThread.looper)
    }

    @After
    fun tearDown() {
        handlerThread.quitSafely()
        dir.deleteRecursively()
    }

    private fun newPair(prefix: String): Pair<OkSharedPreferencesImpl, OkSharedPreferencesImpl> {
        val name = OkSpTestHelper.uniqueName(prefix)
        val lock = File(dir, ".$name.lock").apply { createNewFile() }.absolutePath
        val a = OkSharedPreferencesImpl(null, lock, dir.absolutePath, name, handler)
        val b = OkSharedPreferencesImpl(null, lock, dir.absolutePath, name, handler)
        return a to b
    }

    @Test
    fun reload_whenDiskUnchanged_doesNotNotifyListeners() {
        val (a, _) = newPair("reload_skip")
        assertTrue(a.edit().putInt("k", 1).commit())
        assertTrue(a.drainNotificationQueue())

        val changed = Collections.synchronizedList(mutableListOf<String>())
        a.registerOnSharedPreferenceChangeListener { _, key -> changed.add(key) }

        a.reloadFromDisk()
        assertTrue(changed.isEmpty())
    }

    @Test
    fun fastSave_whenDiskChangedExternally_preservesOtherWriterKeys() {
        val (a, b) = newPair("fast_save_merge")
        assertTrue(a.edit().putInt("a", 1).commit())
        assertTrue(b.edit().putInt("b", 2).commit())
        assertTrue(a.edit().putInt("c", 3).commit())

        a.reloadFromDisk()
        assertEquals(1, a.getInt("a", 0))
        assertEquals(2, a.getInt("b", 0))
        assertEquals(3, a.getInt("c", 0))
    }

    @Test
    fun fastSave_whenOnlyLocalEdits_roundTripsWithoutReload() {
        val (a, _) = newPair("fast_save_local")
        assertTrue(a.edit().putInt("a", 1).putString("s", "x").commit())
        assertTrue(a.edit().putInt("a", 9).putString("s", "y").commit())
        a.reloadFromDisk()
        assertEquals(9, a.getInt("a", 0))
        assertEquals("y", a.getString("s", null))
    }
}
