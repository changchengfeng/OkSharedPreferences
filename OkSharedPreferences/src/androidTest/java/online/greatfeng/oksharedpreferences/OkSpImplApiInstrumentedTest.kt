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

/**
 * Tests [OkSharedPreferencesImpl] methods not exposed on [OkSharedPreferences].
 */
@RunWith(AndroidJUnit4::class)
class OkSpImplApiInstrumentedTest {

    private lateinit var impl: OkSharedPreferencesImpl
    private lateinit var name: String
    private lateinit var dir: File
    private lateinit var handlerThread: HandlerThread
    private val context get() = OkSpTestHelper.context()

    @Before
    fun setUp() {
        name = OkSpTestHelper.uniqueName("impl")
        handlerThread = HandlerThread("impl-test").also { it.start() }
        dir = File(context.cacheDir, "oksp-impl-${System.nanoTime()}").apply { mkdirs() }
        val lock = File(dir, ".$name.lock").apply { createNewFile() }.absolutePath
        impl = OkSharedPreferencesImpl(
            null,
            lock,
            dir.absolutePath,
            name,
            Handler(handlerThread.looper)
        )
    }

    @After
    fun tearDown() {
        handlerThread.quitSafely()
        dir.deleteRecursively()
    }

    @Test
    fun isDestroyed_falseByDefault() {
        assertFalse(impl.isDestroyed())
    }

    @Test
    fun cancelPendingSave_doesNotPreventLaterApply() {
        impl.edit().putInt("n", 1).apply()
        impl.cancelPendingSave()
        impl.edit().putInt("n", 2).commit()
        assertEquals(2, impl.getInt("n", 0))
    }

    @Test
    fun clearData_false_clearsCacheAndWritesEmptyFile() {
        impl.edit().putInt("k", 1).commit()
        impl.clearData(false)
        assertTrue(impl.all.isEmpty())
        assertTrue(File(dir, name + OkSharedPreferencesImpl.SUFFIX_OKSP).exists())
        assertFalse(impl.isDestroyed())
    }

    @Test
    fun clearData_true_marksDestroyedAndDeletesFile() {
        impl.edit().putInt("k", 1).commit()
        impl.clearData(true)
        assertTrue(impl.isDestroyed())
        assertFalse(File(dir, name + OkSharedPreferencesImpl.SUFFIX_OKSP).exists())
        assertFalse(impl.edit().putInt("k", 2).commit())
    }

    @Test
    fun reloadFromDisk_readsCommittedData() {
        impl.edit().putString("s", "disk").commit()
        impl.reloadFromDisk()
        assertEquals("disk", impl.getString("s", null))
    }

    @Test
    fun reloadFromDisk_whenFileDeleted_clearsCache() {
        impl.edit().putInt("k", 1).commit()
        File(dir, name + OkSharedPreferencesImpl.SUFFIX_OKSP).delete()
        impl.reloadFromDisk()
        assertFalse(impl.contains("k"))
    }

    @Test
    fun reloadFromDisk_skipsWhenPendingUnsavedApply() {
        impl.edit().putInt("k", 1).commit()
        impl.edit().putInt("k", 99).apply()
        impl.reloadFromDisk()
        assertEquals(99, impl.getInt("k", 0))
    }
}
