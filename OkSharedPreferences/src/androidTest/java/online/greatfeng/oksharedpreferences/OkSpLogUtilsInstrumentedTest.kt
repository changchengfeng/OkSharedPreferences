package online.greatfeng.oksharedpreferences

import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Smoke tests for every [LogUtils] method (default level is ERROR, calls must not throw).
 */
@RunWith(AndroidJUnit4::class)
class OkSpLogUtilsInstrumentedTest {

    private val tag = "OkSpLogUtilsTest"

    @Test
    fun v_doesNotThrow() {
        LogUtils.v(tag, "verbose")
    }

    @Test
    fun d_doesNotThrow() {
        LogUtils.d(tag, "debug")
    }

    @Test
    fun i_doesNotThrow() {
        LogUtils.i(tag, "info")
    }

    @Test
    fun w_doesNotThrow() {
        LogUtils.w(tag, "warn")
    }

    @Test
    fun e_doesNotThrow() {
        LogUtils.e(tag, "error")
    }

    @Test
    fun e_withThrowable_doesNotThrow() {
        LogUtils.e(tag, "error", RuntimeException("cause"))
    }

    @Test
    fun wtf_doesNotThrow() {
        LogUtils.wtf(tag, "wtf", IllegalStateException("state"))
    }
}
