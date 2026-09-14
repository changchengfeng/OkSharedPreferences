package online.greatfeng.oksharedpreferences

import android.content.Context
import android.content.SharedPreferences
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

@RunWith(AndroidJUnit4::class)
class OkSpPersistenceInstrumentedTest {

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

    private fun newName(prefix: String): String {
        val name = OkSpTestHelper.uniqueName(prefix)
        names.add(name)
        return name
    }

    private fun okSpFile(name: String): File {
        return OkSpTestHelper.okSpFile(context, name)
    }

    @Test
    fun commit_createsOkspFile() {
        val name = newName("file_create")
        context.getOkSharedPreferences(name).edit().putString("k", "v").commit()
        assertTrue(okSpFile(name).exists())
        assertTrue(okSpFile(name).length() > 0)
    }

    @Test
    fun reloadFromDisk_readsWhatWasCommitted() {
        val name = newName("reload")
        val prefs = context.getOkSharedPreferences(name) as OkSharedPreferencesImpl
        prefs.edit().putInt("n", 123).putString("s", "disk").commit()
        prefs.reloadFromDisk()
        assertEquals(123, prefs.getInt("n", 0))
        assertEquals("disk", prefs.getString("s", null))
    }

    @Test
    fun apply_eventuallyPersistsToDisk() {
        val name = newName("apply_disk")
        val prefs = context.getOkSharedPreferences(name) as OkSharedPreferencesImpl
        prefs.edit().putString("k", "applied").apply()
        assertEquals("applied", prefs.getString("k", null))

        val deadline = System.currentTimeMillis() + 3000
        while (System.currentTimeMillis() < deadline) {
            if (okSpFile(name).exists() && okSpFile(name).length() > 0) {
                break
            }
            OkSpTestHelper.await(50)
        }
        assertTrue("apply did not persist in time", okSpFile(name).exists())
        prefs.reloadFromDisk()
        assertEquals("applied", prefs.getString("k", null))
    }

    @Test
    fun clear_writesEmptyFileNotMissingFile() {
        val name = newName("clear_file")
        val prefs = context.getOkSharedPreferences(name)
        prefs.edit().putInt("n", 1).commit()
        prefs.edit().clear().commit()
        assertTrue(okSpFile(name).exists())
        assertFalse(prefs.contains("n"))
        (prefs as OkSharedPreferencesImpl).reloadFromDisk()
        assertTrue(prefs.all.isEmpty())
    }

    @Test
    fun delete_removesOkspFile() {
        val name = newName("delete_file")
        context.getOkSharedPreferences(name).edit().putInt("n", 1).commit()
        assertTrue(context.deleteOkSharedPreferences(name))
        assertFalse(okSpFile(name).exists())
    }

    @Test
    fun unicodeAndLargeString_surviveReload() {
        val name = newName("unicode")
        val prefs = context.getOkSharedPreferences(name) as OkSharedPreferencesImpl
        val big = "测".repeat(4000)
        prefs.edit().putString("big", big).putString("emoji", "🚗😀").commit()
        prefs.reloadFromDisk()
        assertEquals(big, prefs.getString("big", null))
        assertEquals("🚗😀", prefs.getString("emoji", null))
    }

    @Test
    fun listener_onCommitSeesNewValue() {
        val name = newName("listener_value")
        val prefs = context.getOkSharedPreferences(name)
        val seen = Collections.synchronizedList(mutableListOf<Int>())
        val latch = CountDownLatch(1)
        val listener = SharedPreferences.OnSharedPreferenceChangeListener { sp, key ->
            if (key == "n") {
                seen.add(sp.getInt("n", -1))
                latch.countDown()
            }
        }
        prefs.registerOnSharedPreferenceChangeListener(listener)
        try {
            prefs.edit().putInt("n", 55).commit()
            assertTrue(latch.await(3, TimeUnit.SECONDS))
            assertEquals(listOf(55), seen.toList())
        } finally {
            prefs.unregisterOnSharedPreferenceChangeListener(listener)
        }
    }

    @Test
    fun twoAppliesDifferentKeys_bothVisibleInMemory() {
        val name = newName("two_apply")
        val prefs = context.getOkSharedPreferences(name)
        prefs.edit().putInt("a", 1).apply()
        prefs.edit().putInt("b", 2).apply()
        assertEquals(1, prefs.getInt("a", 0))
        assertEquals(2, prefs.getInt("b", 0))
    }

    @Test
    fun replaceValue_notifiesListener() {
        val name = newName("replace")
        val prefs = context.getOkSharedPreferences(name)
        prefs.edit().putString("k", "old").commit()
        OkSpTestHelper.drainNotifications(prefs)
        val values = mutableListOf<String?>()
        val listener = SharedPreferences.OnSharedPreferenceChangeListener { sp, key ->
            if (key == "k") {
                values.add(sp.getString("k", null))
            }
        }
        prefs.registerOnSharedPreferenceChangeListener(listener)
        try {
            prefs.edit().putString("k", "new").commit()
            OkSpTestHelper.drainNotifications(prefs)
            assertEquals(listOf("new"), values)
        } finally {
            prefs.unregisterOnSharedPreferenceChangeListener(listener)
        }
    }

    @Test
    fun fileObserver_reloadsCachedInstanceWhenAnotherWriterCommits() {
        val name = newName("observer")
        val viaManager = context.getOkSharedPreferences(name)
        viaManager.edit().putInt("n", 1).commit()

        val dir = OkSpTestHelper.okSpDir(context)
        val storageBase = OkSpSigning.storageBaseName(context, name)
        val lock = File(dir, ".$storageBase.lock").absolutePath
        val thread = android.os.HandlerThread("observer-writer").also { it.start() }
        try {
            val other = OkSharedPreferencesImpl(
                null,
                lock,
                dir.absolutePath,
                storageBase,
                android.os.Handler(thread.looper),
                name
            )
            other.edit().putInt("n", 2).putString("s", "from-other").commit()

            val deadline = System.currentTimeMillis() + 4000
            while (System.currentTimeMillis() < deadline) {
                if (viaManager.getInt("n", 0) == 2) {
                    break
                }
                OkSpTestHelper.await(50)
            }
            assertEquals(2, viaManager.getInt("n", 0))
            assertEquals("from-other", viaManager.getString("s", null))
        } finally {
            thread.quitSafely()
        }
    }

    @Test
    fun sameValueCommit_doesNotNotify() {
        val name = newName("same_value")
        val prefs = context.getOkSharedPreferences(name)
        prefs.edit().putInt("k", 1).commit()
        var calls = 0
        val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, _ -> calls++ }
        prefs.registerOnSharedPreferenceChangeListener(listener)
        try {
            prefs.edit().putInt("k", 1).commit()
            assertEquals(0, calls)
        } finally {
            prefs.unregisterOnSharedPreferenceChangeListener(listener)
        }
    }
}
