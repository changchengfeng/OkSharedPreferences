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
import java.io.FileOutputStream

@RunWith(AndroidJUnit4::class)
class OkSpRobustnessInstrumentedTest {

    private lateinit var dir: File
    private lateinit var handlerThread: android.os.HandlerThread
    private lateinit var handler: android.os.Handler
    private val names = mutableListOf<String>()

    @Before
    fun setUp() {
        val context = OkSpTestHelper.context()
        dir = File(context.cacheDir, "oksp-robust-${System.nanoTime()}")
        assertTrue(dir.mkdirs())
        handlerThread = android.os.HandlerThread("oksp-robust").also { it.start() }
        handler = android.os.Handler(handlerThread.looper)
    }

    @After
    fun tearDown() {
        handlerThread.quitSafely()
        dir.deleteRecursively()
        names.clear()
    }

    private fun newImpl(prefix: String): OkSharedPreferencesImpl {
        val name = OkSpTestHelper.uniqueName(prefix)
        names.add(name)
        val lock = File(dir, ".$name.lock").apply { createNewFile() }.absolutePath
        return OkSharedPreferencesImpl(null, lock, dir.absolutePath, name, handler)
    }

    @Test
    fun corruptFile_keepsPreviousCacheOnReload() {
        val impl = newImpl("corrupt")
        assertTrue(impl.edit().putInt("good", 42).commit())
        val file = File(dir, names.last() + OkSharedPreferencesImpl.SUFFIX_OKSP)
        FileOutputStream(file).use { out ->
            out.write(byteArrayOf(0x84.toByte(), 0x7F.toByte(), 0xFF.toByte(), 0xFF.toByte(), 0xFF.toByte()))
        }
        impl.reloadFromDisk()
        assertEquals(42, impl.getInt("good", 0))
    }

    @Test
    fun bakFile_recoveredWhenMainMissing() {
        val name = OkSpTestHelper.uniqueName("bak")
        names.add(name)
        val lock = File(dir, ".$name.lock").apply { createNewFile() }.absolutePath
        val impl = OkSharedPreferencesImpl(null, lock, dir.absolutePath, name, handler)
        assertTrue(impl.edit().putString("k", "v1").commit())
        assertTrue(impl.edit().putString("k", "v2").commit())
        val okSp = File(dir, name + OkSharedPreferencesImpl.SUFFIX_OKSP)
        val bak = File(dir, name + OkSharedPreferencesImpl.SUFFIX_BAK)
        assertTrue(bak.exists())
        assertTrue(okSp.delete())
        val reloaded = OkSharedPreferencesImpl(null, lock, dir.absolutePath, name, handler)
        assertEquals("v1", reloaded.getString("k", null))
    }

    @Test
    fun clearThenPut_onlyKeepsNewKeysOnDisk() {
        val (a, b) = run {
            val name = OkSpTestHelper.uniqueName("clear_put_disk")
            names.add(name)
            val lock = File(dir, ".$name.lock").apply { createNewFile() }.absolutePath
            val first = OkSharedPreferencesImpl(null, lock, dir.absolutePath, name, handler)
            val second = OkSharedPreferencesImpl(null, lock, dir.absolutePath, name, handler)
            first to second
        }
        assertTrue(a.edit().putInt("old", 1).commit())
        b.reloadFromDisk()
        assertTrue(a.edit().clear().putInt("new", 9).commit())
        b.reloadFromDisk()
        assertFalse(b.contains("old"))
        assertEquals(9, b.getInt("new", 0))
    }
}
