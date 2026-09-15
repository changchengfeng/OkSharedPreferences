package online.greatfeng.oksharedpreferences

import android.content.SharedPreferences
import android.os.Handler
import android.os.HandlerThread
import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference
import kotlin.random.Random

/**
 * Cross-process stress: writer [OkSharedPreferencesImpl] commits; reader via manager
 * receives [FileObserver] reload + listener, then verifies correctness.
 */
@RunWith(AndroidJUnit4::class)
class OkSpCrossProcessMegaStressInstrumentedTest {

    companion object {
        private const val TAG = "OkSpMegaStress"
        private const val KEY_COUNT = 4_000
        private const val LARGE_KEY_COUNT = 64
        private const val LARGE_STRING_CHARS = 4_096
        private const val STRESS_ROUNDS = 30_000
        private const val PROGRESS_INTERVAL = 3_000
        private const val FULL_AUDIT_INTERVAL = 2_000
        private const val LARGE_UPDATE_INTERVAL = 100
        private const val META_ROUND = "__round__"
        private const val LISTENER_TIMEOUT_SEC = 30L
    }

    private fun awaitReaderRound(
        reader: OkSharedPreferences,
        round: Int,
        latch: CountDownLatch
    ): Boolean {
        if (latch.await(LISTENER_TIMEOUT_SEC, TimeUnit.SECONDS)) {
            if (reader.getInt(META_ROUND, -1) == round) {
                return true
            }
        }
        val deadline = System.currentTimeMillis() + LISTENER_TIMEOUT_SEC * 1000
        while (System.currentTimeMillis() < deadline) {
            if (reader.getInt(META_ROUND, -1) == round) {
                return true
            }
            reader.reload()
            OkSpTestHelper.await(50)
        }
        return reader.getInt(META_ROUND, -1) == round
    }

    private lateinit var context: android.content.Context
    private val names = mutableListOf<String>()
    private val writerThreads = mutableListOf<HandlerThread>()

    @Before
    fun setUp() {
        context = OkSpTestHelper.context()
    }

    @After
    fun tearDown() {
        writerThreads.forEach { it.quitSafely() }
        writerThreads.clear()
        names.forEach { context.deleteOkSharedPreferences(it) }
        names.clear()
    }

    private fun newWriterReaderPair(prefix: String): Pair<OkSharedPreferencesImpl, OkSharedPreferences> {
        val name = OkSpTestHelper.uniqueName(prefix)
        names.add(name)
        val reader = context.getOkSharedPreferences(name)
        val dir = OkSpTestHelper.okSpDir(context)
        val storageBase = OkSpSigning.storageBaseName(context, name)
        val lock = File(dir, ".$storageBase.lock").absolutePath
        val writerThread = HandlerThread("oksp-writer-$prefix").also { it.start() }
        writerThreads.add(writerThread)
        val writer = OkSharedPreferencesImpl(
            null,
            lock,
            dir.absolutePath,
            storageBase,
            Handler(writerThread.looper),
            name
        )
        return writer to reader
    }

    private fun buildLargeString(round: Int, slot: Int): String {
        val seed = "round=$round-slot=$slot-大数据-"
        if (seed.length >= LARGE_STRING_CHARS) {
            return seed.substring(0, LARGE_STRING_CHARS)
        }
        return seed + "X".repeat(LARGE_STRING_CHARS - seed.length)
    }

    private fun writeSeed(writer: OkSharedPreferencesImpl, expected: MutableMap<String, Any>, random: Random) {
        val editor = writer.edit()
        for (i in 0 until KEY_COUNT) {
            when (i % 5) {
                0 -> {
                    val v = random.nextBoolean()
                    editor.putBoolean(keyName(i), v)
                    expected[keyName(i)] = v
                }
                1 -> {
                    val v = random.nextInt()
                    editor.putInt(keyName(i), v)
                    expected[keyName(i)] = v
                }
                2 -> {
                    val v = random.nextLong()
                    editor.putLong(keyName(i), v)
                    expected[keyName(i)] = v
                }
                3 -> {
                    val v = random.nextFloat()
                    editor.putFloat(keyName(i), v)
                    expected[keyName(i)] = v
                }
                else -> {
                    val v = "seed-$i-${random.nextInt(1_000_000)}-中文"
                    editor.putString(keyName(i), v)
                    expected[keyName(i)] = v
                }
            }
        }
        for (i in 0 until LARGE_KEY_COUNT) {
            val v = buildLargeString(0, i)
            editor.putString(largeKeyName(i), v)
            expected[largeKeyName(i)] = v
        }
        for (i in 0 until 16) {
            val set = linkedSetOf("s$i-a", "s$i-中文", "s$i-${random.nextInt(1000)}")
            editor.putStringSet(setKeyName(i), set)
            expected[setKeyName(i)] = HashSet(set)
        }
        editor.putInt(META_ROUND, 0)
        expected[META_ROUND] = 0
        assertTrue(editor.commit())
    }

    private fun keyName(index: Int) = "k$index"
    private fun largeKeyName(index: Int) = "large_$index"
    private fun setKeyName(index: Int) = "set_$index"

    private fun putRoundValue(
        editor: SharedPreferences.Editor,
        expected: MutableMap<String, Any>,
        index: Int,
        round: Int
    ) {
        when (index % 5) {
            0 -> {
                val v = round % 2 == 0
                editor.putBoolean(keyName(index), v)
                expected[keyName(index)] = v
            }
            1 -> {
                val v = round + index
                editor.putInt(keyName(index), v)
                expected[keyName(index)] = v
            }
            2 -> {
                val v = round.toLong() * 1_000L + index
                editor.putLong(keyName(index), v)
                expected[keyName(index)] = v
            }
            3 -> {
                val v = round + index * 0.125f
                editor.putFloat(keyName(index), v)
                expected[keyName(index)] = v
            }
            else -> {
                val v = "r$round-i$index-数据-${round xor index}"
                editor.putString(keyName(index), v)
                expected[keyName(index)] = v
            }
        }
    }

    private fun assertKeyMatches(reader: OkSharedPreferences, key: String, expected: Any) {
        when (expected) {
            is Boolean -> assertEquals("$key/boolean", expected, reader.getBoolean(key, !expected))
            is Int -> assertEquals("$key/int", expected, reader.getInt(key, expected xor 1))
            is Long -> assertEquals("$key/long", expected, reader.getLong(key, expected + 1))
            is Float -> assertEquals("$key/float", expected, reader.getFloat(key, -1f))
            is String -> assertEquals("$key/string", expected, reader.getString(key, null))
            is Set<*> -> assertEquals("$key/set", expected, reader.getStringSet(key, null))
            else -> throw AssertionError("unsupported type for $key: ${expected::class}")
        }
    }

    private fun auditSample(reader: OkSharedPreferences, expected: Map<String, Any>, round: Int, random: Random) {
        assertEquals("$round/all-size", expected.size, reader.all.size)
        assertEquals("$round/meta-round", round, reader.getInt(META_ROUND, -1))
        repeat(80) {
            val idx = random.nextInt(KEY_COUNT)
            assertKeyMatches(reader, keyName(idx), expected[keyName(idx)]!!)
        }
        repeat(8) {
            val idx = random.nextInt(LARGE_KEY_COUNT)
            assertKeyMatches(reader, largeKeyName(idx), expected[largeKeyName(idx)]!!)
        }
        repeat(4) {
            val idx = random.nextInt(16)
            assertKeyMatches(reader, setKeyName(idx), expected[setKeyName(idx)]!!)
        }
    }

    @LargeTest
    @Test(timeout = 14_400_000)
    fun crossProcess_thirtyThousandRounds_writerNotifiesReaderCorrectly() {
        val (writer, reader) = newWriterReaderPair("mega")
        val random = Random(20240915)
        val expected = LinkedHashMap<String, Any>()
        writeSeed(writer, expected, random)

        reader.reload()
        assertEquals(KEY_COUNT + LARGE_KEY_COUNT + 16 + 1, reader.all.size)
        auditSample(reader, expected, 0, random)

        var expectedListenerRound = 0
        val roundLatch = AtomicReference<CountDownLatch>()
        val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
            if (key == META_ROUND && reader.getInt(META_ROUND, -1) == expectedListenerRound) {
                roundLatch.get()?.countDown()
            }
        }
        reader.registerOnSharedPreferenceChangeListener(listener)
        val startedAt = System.currentTimeMillis()

        try {
            for (round in 1..STRESS_ROUNDS) {
                expectedListenerRound = round
                val latch = CountDownLatch(1)
                roundLatch.set(latch)

                val idx1 = round % KEY_COUNT
                val idx2 = (round * 7) % KEY_COUNT
                val idx3 = (round * 13) % KEY_COUNT
                val editor = writer.edit()
                putRoundValue(editor, expected, idx1, round)
                putRoundValue(editor, expected, idx2, round)
                putRoundValue(editor, expected, idx3, round)

                var largeSlot = -1
                if (round % LARGE_UPDATE_INTERVAL == 0) {
                    largeSlot = (round / LARGE_UPDATE_INTERVAL) % LARGE_KEY_COUNT
                    val large = buildLargeString(round, largeSlot)
                    editor.putString(largeKeyName(largeSlot), large)
                    expected[largeKeyName(largeSlot)] = large
                }

                if (round % 500 == 0) {
                    val setIdx = round % 16
                    val set = linkedSetOf("r$round", "中文$round", "h${round % 1000}")
                    editor.putStringSet(setKeyName(setIdx), set)
                    expected[setKeyName(setIdx)] = HashSet(set)
                }

                editor.putInt(META_ROUND, round)
                expected[META_ROUND] = round
                assertTrue(editor.commit())

                assertTrue(
                    "reader not synced at round $round (meta=${reader.getInt(META_ROUND, -1)})",
                    awaitReaderRound(reader, round, latch)
                )
                OkSpTestHelper.drainNotifications(reader)

                assertEquals("round-$round/meta", round, reader.getInt(META_ROUND, -1))
                assertKeyMatches(reader, keyName(idx1), expected[keyName(idx1)]!!)
                assertKeyMatches(reader, keyName(idx2), expected[keyName(idx2)]!!)
                assertKeyMatches(reader, keyName(idx3), expected[keyName(idx3)]!!)

                if (largeSlot >= 0) {
                    assertKeyMatches(reader, largeKeyName(largeSlot), expected[largeKeyName(largeSlot)]!!)
                }

                if (round % FULL_AUDIT_INTERVAL == 0) {
                    auditSample(reader, expected, round, random)
                }

                if (round % PROGRESS_INTERVAL == 0) {
                    val elapsedSec = (System.currentTimeMillis() - startedAt) / 1000
                    Log.i(TAG, "progress $round/$STRESS_ROUNDS elapsed=${elapsedSec}s keys=${reader.all.size}")
                }
            }
        } finally {
            reader.unregisterOnSharedPreferenceChangeListener(listener)
        }

        val totalSec = (System.currentTimeMillis() - startedAt) / 1000
        Log.i(TAG, "completed $STRESS_ROUNDS rounds in ${totalSec}s with ${reader.all.size} keys")
        auditSample(reader, expected, STRESS_ROUNDS, random)
    }
}
