package online.greatfeng.oksharedpreferences

import android.content.SharedPreferences
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.util.Collections
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference

/**
 * Instrumented tests that specifically prove the previously reported bugs are fixed.
 */
@RunWith(AndroidJUnit4::class)
class OkSpBugfixInstrumentedTest {

    private lateinit var context: android.content.Context
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

    private fun newPrefs(prefix: String): OkSharedPreferences {
        val name = OkSpTestHelper.uniqueName(prefix)
        names.add(name)
        return context.getOkSharedPreferences(name)
    }

    @Test(timeout = 5000)
    fun firstGet_doesNotHangWhenFileMissing() {
        val prefs = newPrefs("first_get")
        assertFalse(prefs.contains("missing"))
        assertEquals(0, prefs.getInt("missing", 0))
    }

    @Test
    fun remove_actuallyDeletesKeyFromMemoryAndDisk() {
        val prefs = newPrefs("remove")
        assertTrue(prefs.edit().putInt("keep", 1).putInt("drop", 2).commit())
        assertTrue(prefs.contains("drop"))

        assertTrue(prefs.edit().remove("drop").commit())
        assertFalse(prefs.contains("drop"))
        assertEquals(0, prefs.getInt("drop", 0))
        assertEquals(1, prefs.getInt("keep", 0))

        (prefs as OkSharedPreferencesImpl).reloadFromDisk()
        assertFalse(prefs.contains("drop"))
        assertEquals(1, prefs.getInt("keep", 0))
    }

    @Test
    fun putStringNull_isTreatedAsRemove() {
        val prefs = newPrefs("put_null")
        prefs.edit().putString("k", "v").commit()
        prefs.edit().putString("k", null).commit()
        assertFalse(prefs.contains("k"))
        assertNull(prefs.getString("k", null))
    }

    @Test
    fun apply_updatesMemoryImmediately() {
        val prefs = newPrefs("apply_mem")
        prefs.edit().putInt("n", 42).apply()
        assertEquals(42, prefs.getInt("n", -1))
        assertTrue(prefs.contains("n"))
    }

    @Test
    fun clearThenPut_onSameEditor_keepsPut() {
        val prefs = newPrefs("clear_put")
        prefs.edit().putInt("old", 1).putInt("keep", 2).commit()

        prefs.edit().clear().putInt("new", 3).commit()

        assertFalse(prefs.contains("old"))
        assertFalse(prefs.contains("keep"))
        assertEquals(3, prefs.getInt("new", 0))
    }

    @Test
    fun getAll_returnsDefensiveCopy() {
        val prefs = newPrefs("get_all")
        prefs.edit().putInt("a", 1).commit()
        val all = prefs.all
        all.remove("a")
        @Suppress("UNCHECKED_CAST")
        (all as MutableMap<String, Any>)["injected"] = 99

        assertTrue(prefs.contains("a"))
        assertEquals(1, prefs.getInt("a", 0))
        assertFalse(prefs.contains("injected"))
        assertNotSame(prefs.all, prefs.all)
    }

    @Test
    fun putStringSet_storesCopyNotCallerReference() {
        val prefs = newPrefs("string_set_copy")
        val caller = mutableSetOf("one")
        prefs.edit().putStringSet("s", caller).commit()
        caller.add("two")

        val stored = prefs.getStringSet("s", emptySet())
        assertEquals(setOf("one"), stored)
        stored?.add("three")
        assertEquals(setOf("one"), prefs.getStringSet("s", emptySet()))
    }

    @Test
    fun differentNames_canCommitConcurrentlyWithoutOverlappingLockCrash() {
        val a = newPrefs("lock_a")
        val b = newPrefs("lock_b")
        val errors = Collections.synchronizedList(mutableListOf<Throwable>())
        val latch = CountDownLatch(2)
        val pool = Executors.newFixedThreadPool(2)
        try {
            pool.execute {
                try {
                    repeat(40) { i ->
                        assertTrue(a.edit().putInt("a", i).commit())
                    }
                } catch (t: Throwable) {
                    errors.add(t)
                } finally {
                    latch.countDown()
                }
            }
            pool.execute {
                try {
                    repeat(40) { i ->
                        assertTrue(b.edit().putInt("b", i).commit())
                    }
                } catch (t: Throwable) {
                    errors.add(t)
                } finally {
                    latch.countDown()
                }
            }
            assertTrue("concurrent commit timed out", latch.await(15, TimeUnit.SECONDS))
            assertTrue("concurrent commit failed: $errors", errors.isEmpty())
            assertEquals(39, a.getInt("a", -1))
            assertEquals(39, b.getInt("b", -1))
        } finally {
            pool.shutdownNow()
        }
    }

    @Test
    fun deleteWithoutPriorGet_removesDiskFile() {
        val name = OkSpTestHelper.uniqueName("delete_unopened")
        names.add(name)
        OkSpTestHelper.okSpDir(context).mkdirs()
        val file = OkSpTestHelper.okSpFile(context, name)
        file.writeBytes(byteArrayOf(1, 2, 3))
        assertTrue(file.exists())

        assertTrue(context.deleteOkSharedPreferences(name))
        assertFalse(file.exists())
    }

    @Test
    fun delete_thenGet_doesNotReturnDestroyedInstance() {
        val name = OkSpTestHelper.uniqueName("delete_recreate")
        names.add(name)
        val first = context.getOkSharedPreferences(name)
        first.edit().putInt("v", 7).commit()
        context.deleteOkSharedPreferences(name)

        val second = context.getOkSharedPreferences(name)
        assertFalse(second.contains("v"))
        assertTrue(second.edit().putInt("v", 8).commit())
        assertEquals(8, second.getInt("v", 0))
    }

    @Test
    fun listener_notifiedOnRemoveAndNotDeadlockedWhenReading() {
        val prefs = newPrefs("listener_remove")
        prefs.edit().putInt("gone", 1).putInt("stay", 2).commit()
        val changed = Collections.synchronizedList(mutableListOf<String?>())
        val latch = CountDownLatch(1)
        val listener = SharedPreferences.OnSharedPreferenceChangeListener { sp, key ->
            changed.add(key)
            sp.getInt("stay", -1)
            if (key == "gone") {
                latch.countDown()
            }
        }
        prefs.registerOnSharedPreferenceChangeListener(listener)
        try {
            prefs.edit().remove("gone").commit()
            assertTrue(latch.await(3, TimeUnit.SECONDS))
            assertTrue(changed.contains("gone"))
            assertEquals(2, prefs.getInt("stay", 0))
        } finally {
            prefs.unregisterOnSharedPreferenceChangeListener(listener)
        }
    }

    @Test
    fun listener_notifiedForKeysClearedByEmptyReload() {
        val prefs = newPrefs("listener_clear")
        prefs.edit().putString("a", "1").putString("b", "2").commit()
        val changed = Collections.synchronizedList(mutableListOf<String?>())
        val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
            changed.add(key)
        }
        prefs.registerOnSharedPreferenceChangeListener(listener)
        try {
            prefs.edit().clear().commit()
            OkSpTestHelper.drainNotifications(prefs)
            assertTrue(changed.contains("a"))
            assertTrue(changed.contains("b"))
        } finally {
            prefs.unregisterOnSharedPreferenceChangeListener(listener)
        }
    }

    @Test
    fun firstGet_onBackgroundThreadCompletes() {
        val name = OkSpTestHelper.uniqueName("bg_first")
        names.add(name)
        val pool = Executors.newSingleThreadExecutor()
        val error = AtomicReference<Throwable>()
        try {
            val future = pool.submit<Boolean> {
                try {
                    val prefs = context.getOkSharedPreferences(name)
                    prefs.edit().putBoolean("ok", true).commit()
                    prefs.getBoolean("ok", false)
                } catch (t: Throwable) {
                    error.set(t)
                    false
                }
            }
            val result = future.get(5, TimeUnit.SECONDS)
            assertNull(error.get())
            assertTrue(result)
        } finally {
            pool.shutdownNow()
        }
    }

    @Test
    fun applyThenCommit_doesNotLoseLaterCommit() {
        val prefs = newPrefs("apply_commit")
        prefs.edit().putInt("n", 1).apply()
        assertTrue(prefs.edit().putInt("n", 2).commit())
        assertEquals(2, prefs.getInt("n", -1))
        OkSpTestHelper.await(200)
        (prefs as OkSharedPreferencesImpl).reloadFromDisk()
        assertEquals(2, prefs.getInt("n", -1))
    }

    @Test
    fun overlappingFileLock_doesNotHappenForSameNameSerializedByWriteLock() {
        val prefs = newPrefs("same_name_threads")
        val errors = Collections.synchronizedList(mutableListOf<Throwable>())
        val latch = CountDownLatch(8)
        val pool = Executors.newFixedThreadPool(8)
        try {
            repeat(8) { t ->
                pool.execute {
                    try {
                        repeat(20) { i ->
                            prefs.edit().putInt("t$t", i).commit()
                        }
                    } catch (th: Throwable) {
                        errors.add(th)
                    } finally {
                        latch.countDown()
                    }
                }
            }
            assertTrue(latch.await(20, TimeUnit.SECONDS))
            assertTrue("same-name concurrent commit failed: $errors", errors.isEmpty())
        } finally {
            pool.shutdownNow()
        }
    }

    @Test
    fun reloadFromDisk_keepsCacheWhenFileMissingAfterDeleteBySameProcess() {
        val prefs = newPrefs("reload_deleted")
        prefs.edit().putInt("n", 9).commit()
        context.deleteOkSharedPreferences(names.last())
        val next = context.getOkSharedPreferences(names.last())
        assertEquals(0, next.getInt("n", 0))
    }

    @Test
    fun fail_ifRemoveStillStoresEditorInstance() {
        val prefs = newPrefs("remove_type")
        prefs.edit().putString("k", "v").commit()
        prefs.edit().remove("k").commit()
        val all = prefs.all
        for ((_, value) in all) {
            if (value is SharedPreferences.Editor) {
                fail("remove() stored Editor sentinel in cacheMap: $all")
            }
        }
        assertFalse(all.containsKey("k"))
    }
}
