package online.greatfeng.oksharedpreferences

import android.os.Handler
import android.os.HandlerThread
import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.After
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.util.Locale

/**
 * Micro-benchmarks for OkSP I/O paths. Results are printed to logcat tag [BENCHMARK_TAG].
 */
@RunWith(AndroidJUnit4::class)
class OkSpBenchmarkInstrumentedTest {

    companion object {
        private const val BENCHMARK_TAG = "OkSpBenchmark"
        private const val WARMUP = 10
        private const val ITERATIONS = 50
    }

    private lateinit var dir: File
    private lateinit var handlerThread: HandlerThread
    private lateinit var handler: Handler

    @Before
    fun setUp() {
        val context = OkSpTestHelper.context()
        dir = File(context.cacheDir, "oksp-bench-${System.nanoTime()}")
        assertTrue(dir.mkdirs())
        handlerThread = HandlerThread("oksp-bench").also { it.start() }
        handler = Handler(handlerThread.looper)
    }

    @After
    fun tearDown() {
        handlerThread.quitSafely()
        dir.deleteRecursively()
    }

    private fun newImpl(prefix: String): OkSharedPreferencesImpl {
        val name = OkSpTestHelper.uniqueName(prefix)
        val lock = File(dir, ".$name.lock").apply { createNewFile() }.absolutePath
        return OkSharedPreferencesImpl(null, lock, dir.absolutePath, name, handler)
    }

    private fun medianMillis(iterations: Int, warmup: Int, block: () -> Unit): Double {
        repeat(warmup) { block() }
        val samples = LongArray(iterations)
        repeat(iterations) { i ->
            val start = System.nanoTime()
            block()
            samples[i] = System.nanoTime() - start
        }
        samples.sort()
        return samples[samples.size / 2] / 1_000_000.0
    }

    private fun medianMeasuredMillis(iterations: Int, warmup: Int, measure: () -> Double): Double {
        repeat(warmup) { measure() }
        val samples = DoubleArray(iterations)
        repeat(iterations) { i ->
            samples[i] = measure()
        }
        samples.sort()
        return samples[samples.size / 2]
    }

    private fun logMetric(name: String, millis: Double) {
        val line = String.format(Locale.US, "METRIC %s=%.3fms", name, millis)
        Log.i(BENCHMARK_TAG, line)
    }

    private fun seedPrefs(impl: OkSharedPreferencesImpl, keyCount: Int) {
        val editor = impl.edit()
        for (i in 0 until keyCount) {
            editor.putInt("key_$i", i)
            editor.putString("str_$i", "value_$i")
        }
        assertTrue(editor.commit())
        OkSpTestHelper.drainNotifications(impl)
    }

    @Test
    fun printBenchmarkReport() {
        val version = try {
            OkSpTestHelper.context().packageManager
                .getPackageInfo(OkSpTestHelper.context().packageName, 0).versionName
        } catch (_: Exception) {
            "unknown"
        }
        Log.i(BENCHMARK_TAG, "=== OkSP benchmark start (app=$version) ===")

        runSingleInstanceBenchmarks()
        runCrossInstanceBenchmarks()

        Log.i(BENCHMARK_TAG, "=== OkSP benchmark end ===")
    }

    private fun runSingleInstanceBenchmarks() {
        val impl = newImpl("single")
        seedPrefs(impl, 50)

        logMetric(
            "commit_single_key_disk_unchanged",
            medianMillis(ITERATIONS, WARMUP) {
                assertTrue(impl.edit().putInt("key_0", impl.getInt("key_0", 0) + 1).commit())
            }
        )

        logMetric(
            "reload_disk_unchanged",
            medianMillis(ITERATIONS, WARMUP) {
                impl.reloadFromDisk()
            }
        )

        logMetric(
            "getInt_cached",
            medianMillis(ITERATIONS, WARMUP) {
                impl.getInt("key_0", 0)
            }
        )

        var batchCounter = 0
        logMetric(
            "commit_batch_7keys",
            medianMillis(20, 5) {
                batchCounter++
                assertTrue(
                    impl.edit()
                        .putBoolean("b", batchCounter % 2 == 0)
                        .putFloat("f", 3.14f + batchCounter)
                        .putInt("i", 123456 + batchCounter)
                        .putLong("l", 987654321L + batchCounter)
                        .putString("s", "benchmark_string_payload_$batchCounter")
                        .putStringSet("set", setOf("a", "b", "c$batchCounter"))
                        .putInt("x", batchCounter)
                        .commit()
                )
            }
        )
    }

    private fun runCrossInstanceBenchmarks() {
        val a = newImpl("cross_a")
        val name = a.sharePreferencesName
        val lock = File(dir, ".$name.lock").absolutePath
        seedPrefs(a, 10)

        val b = OkSharedPreferencesImpl(null, lock, dir.absolutePath, name, handler)
        assertTrue(b.edit().putInt("external", 1).commit())
        OkSpTestHelper.drainNotifications(b)

        logMetric(
            "commit_after_external_disk_change",
            medianMillis(30, 5) {
                assertTrue(a.edit().putInt("local", a.getInt("local", 0) + 1).commit())
                assertTrue(b.edit().putInt("external", b.getInt("external", 0) + 1).commit())
            }
        )

        b.reloadFromDisk()
        logMetric(
            "reload_after_external_change",
            medianMillis(ITERATIONS, WARMUP) {
                a.reloadFromDisk()
                assertTrue(b.edit().putInt("ping", b.getInt("ping", 0) + 1).commit())
            }
        )

        val syncMs = medianMeasuredMillis(20, 5) {
            val start = System.nanoTime()
            assertTrue(b.edit().putInt("sync_marker", b.getInt("sync_marker", 0) + 1).commit())
            val deadline = System.currentTimeMillis() + 3000
            var visible = false
            while (System.currentTimeMillis() < deadline) {
                a.reloadFromDisk()
                if (a.getInt("sync_marker", -1) == b.getInt("sync_marker", -1)) {
                    visible = true
                    break
                }
                OkSpTestHelper.await(10)
            }
            assertTrue(visible)
            (System.nanoTime() - start) / 1_000_000.0
        }
        logMetric("cross_instance_visible_reload", syncMs)
    }
}
