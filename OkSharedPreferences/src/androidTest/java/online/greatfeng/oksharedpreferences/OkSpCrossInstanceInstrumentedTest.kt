package online.greatfeng.oksharedpreferences

import android.os.Handler
import android.os.HandlerThread
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.util.Collections
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/**
 * Two [OkSharedPreferencesImpl] instances sharing the same files simulate two processes
 * without going through the process-level singleton cache.
 */
@RunWith(AndroidJUnit4::class)
class OkSpCrossInstanceInstrumentedTest {

    private lateinit var dir: File
    private lateinit var handlerThread: HandlerThread
    private lateinit var handler: Handler
    private val names = mutableListOf<String>()

    @Before
    fun setUp() {
        val context = OkSpTestHelper.context()
        dir = File(context.cacheDir, "oksp-cross-${System.nanoTime()}")
        assertTrue(dir.mkdirs())
        handlerThread = HandlerThread("oksp-test").also { it.start() }
        handler = Handler(handlerThread.looper)
    }

    @After
    fun tearDown() {
        handlerThread.quitSafely()
        dir.deleteRecursively()
        names.clear()
    }

    private fun newPair(prefix: String): Pair<OkSharedPreferencesImpl, OkSharedPreferencesImpl> {
        val name = OkSpTestHelper.uniqueName(prefix)
        names.add(name)
        val lock = File(dir, ".$name.lock").apply { createNewFile() }.absolutePath
        val a = OkSharedPreferencesImpl(null, lock, dir.absolutePath, name, handler)
        val b = OkSharedPreferencesImpl(null, lock, dir.absolutePath, name, handler)
        return a to b
    }

    @Test
    fun secondInstance_seesCommittedWritesAfterReload() {
        val (a, b) = newPair("see_write")
        assertTrue(a.edit().putInt("n", 41).putString("s", "x").commit())
        b.reloadFromDisk()
        assertEquals(41, b.getInt("n", 0))
        assertEquals("x", b.getString("s", null))
    }

    @Test
    fun secondInstance_seesRemoveAfterReload() {
        val (a, b) = newPair("see_remove")
        a.edit().putInt("keep", 1).putInt("drop", 2).commit()
        b.reloadFromDisk()
        assertTrue(b.contains("drop"))

        a.edit().remove("drop").commit()
        b.reloadFromDisk()
        assertFalse(b.contains("drop"))
        assertEquals(1, b.getInt("keep", 0))
    }

    @Test
    fun secondInstance_listenerNotifiedForRemovedKey() {
        val (a, b) = newPair("listen_remove")
        a.edit().putString("gone", "v").putString("stay", "s").commit()
        b.reloadFromDisk()

        val changed = Collections.synchronizedList(mutableListOf<String?>())
        val latch = CountDownLatch(1)
        b.registerOnSharedPreferenceChangeListener { _, key ->
            changed.add(key)
            if (key == "gone") {
                latch.countDown()
            }
        }
        a.edit().remove("gone").commit()
        b.reloadFromDisk()
        assertTrue(latch.await(3, TimeUnit.SECONDS))
        assertTrue(changed.contains("gone"))
        assertFalse(b.contains("gone"))
        assertEquals("s", b.getString("stay", null))
    }

    @Test
    fun secondInstance_seesClearThenPut() {
        val (a, b) = newPair("clear_put")
        a.edit().putInt("old", 1).commit()
        b.reloadFromDisk()
        a.edit().clear().putInt("new", 9).commit()
        b.reloadFromDisk()
        assertFalse(b.contains("old"))
        assertEquals(9, b.getInt("new", 0))
    }

    @Test
    fun commitOnA_thenReloadB_seesDiskData() {
        val (a, b) = newPair("commit_reload")
        assertTrue(a.edit().putBoolean("ok", true).commit())
        b.reloadFromDisk()
        assertTrue(b.getBoolean("ok", false))
    }

    @Test
    fun applyOnA_thenReloadB_afterFlush() {
        val (a, b) = newPair("apply_reload")
        a.edit().putBoolean("ok", true).apply()
        val okSp = File(dir, names.last() + OkSharedPreferencesImpl.SUFFIX_OKSP)
        val deadline = System.currentTimeMillis() + 8000
        var seen = false
        while (System.currentTimeMillis() < deadline) {
            if (okSp.exists() && okSp.length() > 0) {
                b.reloadFromDisk()
                if (b.getBoolean("ok", false)) {
                    seen = true
                    break
                }
            }
            OkSpTestHelper.await(100)
        }
        assertTrue("B never observed A's apply()", seen)
    }

    @Test
    fun mergeOnSave_preservesKeysWrittenByOtherInstance() {
        val (a, b) = newPair("merge_save")
        assertTrue(a.edit().putInt("a", 1).commit())
        b.reloadFromDisk()
        assertTrue(a.edit().putInt("b", 2).commit())
        // B never reloaded after b=2 was written; stale cache only has a=1.
        assertTrue(b.edit().putInt("c", 3).commit())
        b.reloadFromDisk()
        assertEquals(1, b.getInt("a", 0))
        assertEquals(2, b.getInt("b", 0))
        assertEquals(3, b.getInt("c", 0))
    }

    @Test
    fun firstConstruct_missingFileDoesNotHang() {
        val name = OkSpTestHelper.uniqueName("missing")
        val lock = File(dir, ".$name.lock").apply { createNewFile() }.absolutePath
        val impl = OkSharedPreferencesImpl(null, lock, dir.absolutePath, name, handler)
        assertTrue(impl.all.isEmpty())
    }
}
