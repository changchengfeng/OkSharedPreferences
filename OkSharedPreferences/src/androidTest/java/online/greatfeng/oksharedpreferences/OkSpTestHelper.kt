package online.greatfeng.oksharedpreferences

import android.content.Context
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

internal object OkSpTestHelper {
    private val seq = AtomicInteger(0)
    private val activeNames = mutableListOf<String>()

    fun context(): Context = InstrumentationRegistry.getInstrumentation().targetContext

    fun uniqueName(prefix: String): String {
        val name = "${prefix}_${System.nanoTime()}_${seq.incrementAndGet()}"
        activeNames.add(name)
        return name
    }

    fun track(name: String) {
        if (!activeNames.contains(name)) {
            activeNames.add(name)
        }
    }

    fun cleanupAll(context: Context) {
        val names = activeNames.toList()
        activeNames.clear()
        names.forEach { context.deleteOkSharedPreferences(it) }
        OkSharedPreferences.resetLimits()
    }

    fun okSpDir(context: Context): File = File(context.dataDir, "ok-sp")

    fun okSpFile(context: Context, name: String): File {
        val storageBase = OkSpSigning.storageBaseName(context, name)
        return File(okSpDir(context), storageBase + OkSharedPreferencesImpl.SUFFIX_OKSP)
    }

    fun storageBaseName(context: Context, logicalName: String): String {
        return OkSpSigning.storageBaseName(context, logicalName)
    }

    fun await(ms: Long) {
        TimeUnit.MILLISECONDS.sleep(ms)
    }

    fun awaitDisk(
        context: Context,
        name: String,
        timeoutMs: Long = 5000,
        predicate: (File) -> Boolean = { it.exists() && it.length() > 0 }
    ): Boolean {
        val file = okSpFile(context, name)
        val deadline = System.currentTimeMillis() + timeoutMs
        while (System.currentTimeMillis() < deadline) {
            if (predicate(file)) {
                return true
            }
            await(50)
        }
        return predicate(file)
    }

    fun drainNotifications(prefs: OkSharedPreferences, timeoutMs: Long = 3000) {
        (prefs as? OkSharedPreferencesImpl)?.drainNotificationQueue(timeoutMs)
    }

    fun awaitApplyPersisted(
        context: Context,
        prefs: OkSharedPreferences,
        name: String,
        expected: () -> Boolean,
        timeoutMs: Long = 5000
    ): Boolean {
        val deadline = System.currentTimeMillis() + timeoutMs
        while (System.currentTimeMillis() < deadline) {
            if (expected()) {
                return true
            }
            awaitDisk(context, name, timeoutMs = 100)
            (prefs as? OkSharedPreferencesImpl)?.reloadFromDisk()
            await(50)
        }
        return expected()
    }
}
