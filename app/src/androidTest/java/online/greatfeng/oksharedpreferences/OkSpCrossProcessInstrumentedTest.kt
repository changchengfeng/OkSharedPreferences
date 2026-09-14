package online.greatfeng.oksharedpreferences

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.IBinder
import android.os.Message
import android.os.Messenger
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.After
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/**
 * End-to-end cross-process check using the demo [:remoter] [SharedPreferenceService].
 * The remote process writes; this process must observe the update via FileObserver reload.
 */
@RunWith(AndroidJUnit4::class)
class OkSpCrossProcessInstrumentedTest {

    private lateinit var context: Context
    private var messenger: Messenger? = null
    private var bound = false
    private var activeConnection: ServiceConnection? = null

    @Before
    fun setUp() {
        context = InstrumentationRegistry.getInstrumentation().targetContext
        context.deleteOkSharedPreferences(OKSHAREDPREFERENCES_TEST_NAME)
        val connected = CountDownLatch(1)
        val connection = object : ServiceConnection {
            override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
                messenger = service?.let { Messenger(it) }
                bound = true
                connected.countDown()
            }

            override fun onServiceDisconnected(name: ComponentName?) {
                messenger = null
                bound = false
            }
        }
        activeConnection = connection
        val ok = context.bindService(
            Intent(context, SharedPreferenceService::class.java),
            connection,
            Context.BIND_AUTO_CREATE
        )
        assertTrue("bindService failed", ok)
        assertTrue("service not connected", connected.await(5, TimeUnit.SECONDS))
    }

    @After
    fun tearDown() {
        if (bound) {
            try {
                activeConnection?.let { context.unbindService(it) }
            } catch (_: Exception) {
            }
        }
        context.deleteOkSharedPreferences(OKSHAREDPREFERENCES_TEST_NAME)
    }

    @Test
    fun remoteCommit_isVisibleInLocalProcess() {
        val local = context.getOkSharedPreferences(OKSHAREDPREFERENCES_TEST_NAME)
        local.edit().clear().commit()

        val remote = messenger ?: throw AssertionError("messenger is null")
        remote.send(Message.obtain(null, SharedPreferenceService.SAVE_COMMIT))

        val deadline = System.currentTimeMillis() + 5000
        var visible = false
        while (System.currentTimeMillis() < deadline) {
            local.reload()
            if (local.getBoolean(KEY_TEST_BOOLEAN, false) &&
                local.getInt(KEY_TEST_INT, 0) == 123456
            ) {
                visible = true
                break
            }
            Thread.sleep(50)
        }
        assertTrue("local process did not observe remote commit", visible)
        assertTrue(local.getLong(KEY_TEST_LONG, 0L) == 987654321L)
    }
}
