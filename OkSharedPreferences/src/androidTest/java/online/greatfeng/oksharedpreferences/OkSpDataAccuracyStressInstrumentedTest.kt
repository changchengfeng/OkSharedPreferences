package online.greatfeng.oksharedpreferences

import android.content.Context
import android.os.Handler
import android.os.HandlerThread
import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import kotlin.random.Random

/**
 * High-intensity read/write accuracy tests across all supported value types.
 */
@RunWith(AndroidJUnit4::class)
class OkSpDataAccuracyStressInstrumentedTest {

    companion object {
        private const val TAG = "OkSpStress"
        private const val STRESS_ROUNDS = 10_000
        private const val PROGRESS_INTERVAL = 1_000
    }

    private lateinit var context: Context
    private lateinit var handlerThread: HandlerThread
    private lateinit var handler: Handler
    private lateinit var dir: File
    private val names = mutableListOf<String>()

    @Before
    fun setUp() {
        context = OkSpTestHelper.context()
        dir = File(context.cacheDir, "oksp-stress-${System.nanoTime()}")
        assertTrue(dir.mkdirs())
        handlerThread = HandlerThread("oksp-stress").also { it.start() }
        handler = Handler(handlerThread.looper)
    }

    @After
    fun tearDown() {
        handlerThread.quitSafely()
        names.forEach { context.deleteOkSharedPreferences(it) }
        names.clear()
        dir.deleteRecursively()
    }

    private fun newPrefs(prefix: String): OkSharedPreferences {
        val name = OkSpTestHelper.uniqueName(prefix)
        names.add(name)
        return context.getOkSharedPreferences(name)
    }

    private fun newPair(prefix: String): Pair<OkSharedPreferencesImpl, OkSharedPreferencesImpl> {
        val name = OkSpTestHelper.uniqueName(prefix)
        names.add(name)
        val lock = File(dir, ".$name.lock").apply { createNewFile() }.absolutePath
        val a = OkSharedPreferencesImpl(null, lock, dir.absolutePath, name, handler)
        val b = OkSharedPreferencesImpl(null, lock, dir.absolutePath, name, handler)
        return a to b
    }

    private fun assertAllTypes(
        prefs: OkSharedPreferences,
        expected: ExpectedPayload,
        message: String
    ) {
        assertEquals("$message/boolean", expected.boolean, prefs.getBoolean("b", !expected.boolean))
        assertEquals("$message/float", expected.float, prefs.getFloat("f", -1f))
        assertEquals("$message/int", expected.int, prefs.getInt("i", -1))
        assertEquals("$message/long", expected.long, prefs.getLong("l", -1L))
        assertEquals("$message/string", expected.string, prefs.getString("s", null))
        assertEquals("$message/set", expected.set, prefs.getStringSet("set", null))

        val all = prefs.all
        assertEquals("$message/getAll-size", 6, all.size)
        assertEquals("$message/getAll-boolean", expected.boolean, all["b"])
        assertEquals("$message/getAll-float", expected.float, all["f"])
        assertEquals("$message/getAll-int", expected.int, all["i"])
        assertEquals("$message/getAll-long", expected.long, all["l"])
        assertEquals("$message/getAll-string", expected.string, all["s"])
        assertEquals("$message/getAll-set", expected.set, all["set"])
    }

    private fun writeAllTypes(
        editor: android.content.SharedPreferences.Editor,
        payload: ExpectedPayload
    ): android.content.SharedPreferences.Editor {
        editor.putBoolean("b", payload.boolean)
        editor.putFloat("f", payload.float)
        editor.putInt("i", payload.int)
        editor.putLong("l", payload.long)
        editor.putString("s", payload.string)
        editor.putStringSet("set", payload.set)
        return editor
    }

    @Test
    fun edgeValues_commitAndReload_roundTripAccurately() {
        val prefs = newPrefs("edge") as OkSharedPreferencesImpl
        val payloads = listOf(
            ExpectedPayload(
                boolean = false,
                float = 0f,
                int = 0,
                long = 0L,
                string = "",
                set = emptySet()
            ),
            ExpectedPayload(
                boolean = true,
                float = -3.1415926f,
                int = Int.MIN_VALUE,
                long = Long.MIN_VALUE,
                string = "零一二三四五六七八九十 🚗 中文 English",
                set = linkedSetOf("", "a", "中文", "emoji😀")
            ),
            ExpectedPayload(
                boolean = false,
                float = Float.MAX_VALUE,
                int = Int.MAX_VALUE,
                long = Long.MAX_VALUE,
                string = "x".repeat(8_192),
                set = linkedSetOf("long-${"y".repeat(256)}", "z")
            )
        )

        for ((index, payload) in payloads.withIndex()) {
            assertTrue(writeAllTypes(prefs.edit(), payload).commit())
            assertAllTypes(prefs, payload, "memory-$index")
            prefs.reloadFromDisk()
            assertAllTypes(prefs, payload, "reload-$index")
        }
    }

    @LargeTest
    @Test(timeout = 3_600_000)
    fun repeatedCommitApply_tenThousandRoundsPreserveAccuracy() {
        val prefs = newPrefs("cycles") as OkSharedPreferencesImpl
        val random = Random(42)
        val startedAt = System.currentTimeMillis()

        repeat(STRESS_ROUNDS) { round ->
            val payload = ExpectedPayload(
                boolean = round % 2 == 0,
                float = random.nextFloat() * 10_000f,
                int = random.nextInt(),
                long = random.nextLong(),
                string = "round=$round-${random.nextInt(1_000_000)}-中文",
                set = linkedSetOf("r$round", "中文$round", "k${random.nextInt(100)}")
            )
            val editor = prefs.edit()
            writeAllTypes(editor, payload)
            if (round % 2 == 0) {
                assertTrue(editor.commit())
            } else {
                editor.apply()
                assertTrue(
                    OkSpTestHelper.awaitApplyPersisted(
                        context,
                        prefs,
                        names.last(),
                        expected = { prefs.getInt("i", -1) == payload.int }
                    )
                )
            }
            assertAllTypes(prefs, payload, "round-$round-memory")

            prefs.reloadFromDisk()
            assertAllTypes(prefs, payload, "round-$round-reload")

            if (round > 0 && round % PROGRESS_INTERVAL == 0) {
                val elapsedSec = (System.currentTimeMillis() - startedAt) / 1000
                Log.i(TAG, "progress $round/$STRESS_ROUNDS rounds, elapsed ${elapsedSec}s")
            }
        }

        val totalSec = (System.currentTimeMillis() - startedAt) / 1000
        Log.i(TAG, "completed $STRESS_ROUNDS rounds in ${totalSec}s")
    }

    @Test
    fun overwriteRemoveClear_sequenceMaintainsAccuracy() {
        val prefs = newPrefs("mutate") as OkSharedPreferencesImpl
        val base = ExpectedPayload(
            boolean = true,
            float = 1.25f,
            int = 42,
            long = 4242L,
            string = "base",
            set = linkedSetOf("a", "b")
        )
        assertTrue(writeAllTypes(prefs.edit(), base).commit())

        assertTrue(prefs.edit().putInt("i", 99).putString("s", "updated").commit())
        assertEquals(99, prefs.getInt("i", 0))
        assertEquals("updated", prefs.getString("s", null))
        assertEquals(1.25f, prefs.getFloat("f", 0f))

        assertTrue(prefs.edit().remove("set").remove("missing").commit())
        assertNull(prefs.getStringSet("set", null))
        assertFalse(prefs.contains("set"))
        prefs.reloadFromDisk()
        assertNull(prefs.getStringSet("set", null))

        assertTrue(prefs.edit().clear().putLong("l", 7L).putString("s", "after-clear").commit())
        assertFalse(prefs.contains("b"))
        assertFalse(prefs.contains("i"))
        assertEquals(7L, prefs.getLong("l", 0L))
        assertEquals("after-clear", prefs.getString("s", null))
        prefs.reloadFromDisk()
        assertEquals(7L, prefs.getLong("l", 0L))
        assertEquals("after-clear", prefs.getString("s", null))
        assertEquals(2, prefs.all.size)
    }

    @Test
    fun crossInstance_stressWritesRemainConsistent() {
        val (writer, reader) = newPair("cross_stress")
        val random = Random(7)

        repeat(40) { round ->
            val payload = ExpectedPayload(
                boolean = round % 3 == 0,
                float = 0.5f + round,
                int = 1_000 + round,
                long = 9_000_000_000L + round,
                string = "writer-$round-数据",
                set = linkedSetOf("w$round", "读$round")
            )
            assertTrue(writeAllTypes(writer.edit(), payload).commit())
            reader.reloadFromDisk()
            assertAllTypes(reader, payload, "cross-$round")
        }

        assertTrue(writer.edit().remove("s").putInt("i", random.nextInt()).commit())
        reader.reloadFromDisk()
        assertNull(reader.getString("s", null))
        assertEquals(writer.getInt("i", -1), reader.getInt("i", -1))
    }

    @Test
    fun manyKeys_batchWriteAndReadBack() {
        val prefs = newPrefs("many_keys") as OkSharedPreferencesImpl
        val keyCount = 120

        val editor = prefs.edit()
        for (i in 0 until keyCount) {
            when (i % 5) {
                0 -> editor.putBoolean("k$i", i % 2 == 0)
                1 -> editor.putInt("k$i", i)
                2 -> editor.putLong("k$i", i.toLong() * 1_000)
                3 -> editor.putFloat("k$i", i + 0.125f)
                else -> editor.putString("k$i", "value-$i-中文")
            }
        }
        assertTrue(editor.commit())
        prefs.reloadFromDisk()

        for (i in 0 until keyCount) {
            when (i % 5) {
                0 -> assertEquals(i % 2 == 0, prefs.getBoolean("k$i", false))
                1 -> assertEquals(i, prefs.getInt("k$i", -1))
                2 -> assertEquals(i.toLong() * 1_000, prefs.getLong("k$i", -1L))
                3 -> assertEquals(i + 0.125f, prefs.getFloat("k$i", -1f))
                else -> assertEquals("value-$i-中文", prefs.getString("k$i", null))
            }
        }
        assertEquals(keyCount, prefs.all.size)
    }

    private data class ExpectedPayload(
        val boolean: Boolean,
        val float: Float,
        val int: Int,
        val long: Long,
        val string: String,
        val set: Set<String>
    )
}
