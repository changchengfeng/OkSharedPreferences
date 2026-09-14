package online.greatfeng.oksharedpreferences.fileobserver

import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

@RunWith(AndroidJUnit4::class)
class OkSpFileObserverApiInstrumentedTest {

    private fun tempDir(): File {
        return File.createTempFile("oksp-fo-", null).apply {
            delete()
            mkdirs()
        }
    }

    @Test
    fun startWatching_isIdempotent() {
        val dir = tempDir()
        val observer = object : OkFileObserver(listOf(dir), OkFileObserver.CREATE) {
            override fun onEvent(event: Int, path: String?) {}
        }
        observer.startWatching()
        observer.startWatching()
        observer.stopWatching()
        dir.deleteRecursively()
    }

    @Test
    fun stopWatching_canBeCalledTwice() {
        val dir = tempDir()
        val observer = object : OkFileObserver(listOf(dir), OkFileObserver.CREATE) {
            override fun onEvent(event: Int, path: String?) {}
        }
        observer.startWatching()
        observer.stopWatching()
        observer.stopWatching()
        dir.deleteRecursively()
    }

    @Test
    fun okFileObserverThread_startWatching_returnsValidDescriptors() {
        val dir = tempDir()
        val thread = OkFileObserver.s_observerThread
        val observer = object : OkFileObserver(listOf(dir), OkFileObserver.CREATE) {
            override fun onEvent(event: Int, path: String?) {}
        }
        val wfds = thread.startWatching(listOf(dir), OkFileObserver.CREATE, observer)
        assertTrue(wfds.any { it >= 0 })
        thread.stopWatching(wfds)
        dir.deleteRecursively()
    }

    @Test
    fun okFileObserverThread_stopWatching_null_isNoOp() {
        OkFileObserver.s_observerThread.stopWatching(null)
    }

    @Test
    fun okFileObserverThread_onEvent_dispatchesToRegisteredObserver() {
        val dir = tempDir()
        val latch = CountDownLatch(1)
        val observer = object : OkFileObserver(listOf(dir), OkFileObserver.CREATE) {
            override fun onEvent(event: Int, path: String?) {
                latch.countDown()
            }
        }
        val thread = OkFileObserver.s_observerThread
        val wfds = thread.startWatching(listOf(dir), OkFileObserver.CREATE, observer)
        val wfd = wfds.first { it >= 0 }
        thread.onEvent(wfd, OkFileObserver.CREATE, "test.oksp")
        assertTrue(latch.await(2, TimeUnit.SECONDS))
        thread.stopWatching(wfds)
        dir.deleteRecursively()
    }

    @Test
    fun okFileObserverThread_onEvent_swallowsObserverException() {
        val dir = tempDir()
        val observer = object : OkFileObserver(listOf(dir), OkFileObserver.CREATE) {
            override fun onEvent(event: Int, path: String?) {
                throw RuntimeException("observer failed")
            }
        }
        val thread = OkFileObserver.s_observerThread
        val wfds = thread.startWatching(listOf(dir), OkFileObserver.CREATE, observer)
        val wfd = wfds.first { it >= 0 }
        thread.onEvent(wfd, OkFileObserver.CREATE, "x.oksp")
        thread.stopWatching(wfds)
        dir.deleteRecursively()
    }
}
