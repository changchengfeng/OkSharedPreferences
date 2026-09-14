package online.greatfeng.oksharedpreferences

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.content.SharedPreferences.OnSharedPreferenceChangeListener
import android.os.Bundle
import android.os.IBinder
import android.os.Message
import android.os.Messenger
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Divider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import online.greatfeng.oksharedpreferences.ui.theme.OkSharedPreferencesTheme

class MainActivity : ComponentActivity() {

    private val messengerState = mutableStateOf<Messenger?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        bindService(
            Intent(applicationContext, SharedPreferenceService::class.java),
            object : ServiceConnection {
                override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
                    Log.d(TAG, "onServiceConnected: name=$name")
                    messengerState.value = service?.let { Messenger(it) }
                }

                override fun onServiceDisconnected(name: ComponentName?) {
                    messengerState.value = null
                }
            },
            BIND_AUTO_CREATE
        )

        setContent {
            OkSharedPreferencesTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    OkSpApiDemoScreen(messenger = messengerState.value)
                }
            }
        }
    }
}

private const val TAG = "MainActivity"

const val OKSHAREDPREFERENCES_TEST_NAME = "test"
const val KEY_TEST_BOOLEAN = "test_Boolean"
const val KEY_TEST_FLOAT = "test_Float"
const val KEY_TEST_INT = "test_Int"
const val KEY_TEST_LONG = "test_Long"
const val KEY_TEST_STRING = "test_String"
const val KEY_TEST_XXX = "test_XXX"
const val KEY_TEST_SET = "test_Set"

private const val MIGRATE_DEMO_NAME = "migrate_demo"
private const val DELETE_DEMO_NAME = "delete_demo"
private const val KEY_MIGRATE = "from_platform"

@Composable
fun OkSpApiDemoScreen(messenger: Messenger?) {
    val context = LocalContext.current
    var log by remember { mutableStateOf("Tap a button to exercise OkSharedPreferences APIs.\n") }
    var counter by remember { mutableIntStateOf(0) }
    val scrollState = rememberScrollState()

    val appendLog: (String) -> Unit = { message ->
        Log.d(TAG, message)
        log = if (log.isEmpty()) message else "$log\n$message"
    }

    val sp = remember { context.getOkSharedPreferences(OKSHAREDPREFERENCES_TEST_NAME) }
    val demoListener = remember {
        OnSharedPreferenceChangeListener { _, key ->
            appendLog("onSharedPreferenceChanged: key=$key")
        }
    }

    DisposableEffect(sp) {
        sp.registerOnSharedPreferenceChangeListener(demoListener)
        onDispose {
            sp.unregisterOnSharedPreferenceChangeListener(demoListener)
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        Text(
            text = log,
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .padding(12.dp)
                .verticalScroll(rememberScrollState()),
            style = MaterialTheme.typography.bodySmall,
            fontFamily = FontFamily.Monospace
        )

        Divider()

        Column(
            modifier = Modifier
                .weight(2f)
                .fillMaxWidth()
                .verticalScroll(scrollState)
        ) {
            SectionTitle("Context extensions")
            ApiButton("getOkSharedPreferences(name)") {
                val instance = context.getOkSharedPreferences(OKSHAREDPREFERENCES_TEST_NAME)
                appendLog("getOkSharedPreferences → $instance")
            }
            ApiButton("getOkSharedPreferences(name, migration=true)") {
                context.getSharedPreferences(MIGRATE_DEMO_NAME, Context.MODE_PRIVATE)
                    .edit()
                    .putString(KEY_MIGRATE, "migrated_value")
                    .commit()
                context.deleteOkSharedPreferences(MIGRATE_DEMO_NAME)
                val migrated = context.getOkSharedPreferences(MIGRATE_DEMO_NAME, migration = true)
                val value = migrated.getString(KEY_MIGRATE, null)
                appendLog("migration result: KEY_MIGRATE=$value (expected migrated_value)")
            }
            ApiButton("deleteOkSharedPreferences(name)") {
                context.getOkSharedPreferences(DELETE_DEMO_NAME)
                    .edit()
                    .putInt("k", 42)
                    .commit()
                val deleted = context.deleteOkSharedPreferences(DELETE_DEMO_NAME)
                val after = context.getOkSharedPreferences(DELETE_DEMO_NAME)
                appendLog("deleteOkSharedPreferences → $deleted, contains(k)=${after.contains("k")}")
            }
            ApiButton("deleteSharedPreferences(context, name)") {
                context.getOkSharedPreferences(DELETE_DEMO_NAME)
                    .edit()
                    .putInt("k", 99)
                    .commit()
                val deleted = deleteSharedPreferences(context, DELETE_DEMO_NAME)
                appendLog("deleteSharedPreferences(Java alias) → $deleted")
            }

            SectionTitle("configureLimits / resetLimits")
            ApiButton("read limits") {
                appendLog(
                    "maxDecodeBytesPerField=${OkSharedPreferences.maxDecodeBytesPerField}, " +
                        "maxFileBytes=${OkSharedPreferences.maxFileBytes}"
                )
            }
            ApiButton("configureLimits(8MB, 32MB)") {
                OkSharedPreferences.configureLimits(
                    maxDecodeBytesPerField = 8 * 1024 * 1024,
                    maxFileBytes = 32 * 1024 * 1024
                )
                appendLog("configureLimits done (affects new reads/writes)")
            }
            ApiButton("resetLimits()") {
                OkSharedPreferences.resetLimits()
                appendLog("resetLimits → defaults restored")
            }

            SectionTitle("Read APIs")
            ApiButton("getAll()") {
                appendLog("getAll → ${sp.getAll()}")
            }
            ApiButton("getBoolean()") {
                appendLog("getBoolean → ${sp.getBoolean(KEY_TEST_BOOLEAN, false)}")
            }
            ApiButton("getInt()") {
                appendLog("getInt → ${sp.getInt(KEY_TEST_INT, -1)}")
            }
            ApiButton("getLong()") {
                appendLog("getLong → ${sp.getLong(KEY_TEST_LONG, -1L)}")
            }
            ApiButton("getFloat()") {
                appendLog("getFloat → ${sp.getFloat(KEY_TEST_FLOAT, -1f)}")
            }
            ApiButton("getString()") {
                appendLog("getString → ${sp.getString(KEY_TEST_STRING, null)}")
            }
            ApiButton("getStringSet()") {
                appendLog("getStringSet → ${sp.getStringSet(KEY_TEST_SET, null)}")
            }
            ApiButton("contains(existing key)") {
                appendLog("contains($KEY_TEST_INT) → ${sp.contains(KEY_TEST_INT)}")
            }
            ApiButton("contains(missing key)") {
                appendLog("contains(__missing__) → ${sp.contains("__missing__")}")
            }

            SectionTitle("Editor — put + commit")
            ApiButton("putBoolean + commit") {
                val ok = sp.edit().putBoolean(KEY_TEST_BOOLEAN, true).commit()
                appendLog("putBoolean + commit → $ok")
            }
            ApiButton("putInt + commit") {
                val ok = sp.edit().putInt(KEY_TEST_INT, 123456).commit()
                appendLog("putInt + commit → $ok")
            }
            ApiButton("putLong + commit") {
                val ok = sp.edit().putLong(KEY_TEST_LONG, 987654321L).commit()
                appendLog("putLong + commit → $ok")
            }
            ApiButton("putFloat + commit") {
                val ok = sp.edit().putFloat(KEY_TEST_FLOAT, 3.1415926f).commit()
                appendLog("putFloat + commit → $ok")
            }
            ApiButton("putString + commit") {
                val ok = sp.edit().putString(KEY_TEST_STRING, "hello_oksp").commit()
                appendLog("putString + commit → $ok")
            }
            ApiButton("putStringSet + commit") {
                val ok = sp.edit().putStringSet(KEY_TEST_SET, setOf("a", "b", "c")).commit()
                appendLog("putStringSet + commit → $ok")
            }
            ApiButton("put all types + commit") {
                val ok = sp.edit()
                    .putBoolean(KEY_TEST_BOOLEAN, true)
                    .putFloat(KEY_TEST_FLOAT, 3.1415926f)
                    .putInt(KEY_TEST_INT, 123456)
                    .putInt(KEY_TEST_XXX, counter++)
                    .putLong(KEY_TEST_LONG, 987654321L)
                    .putString(KEY_TEST_STRING, "batch_commit")
                    .putStringSet(KEY_TEST_SET, setOf("x", "y", "z"))
                    .commit()
                appendLog("put all + commit → $ok, counter=$counter")
            }

            SectionTitle("Editor — put + apply / remove / clear")
            ApiButton("put all types + apply") {
                sp.edit()
                    .putBoolean(KEY_TEST_BOOLEAN, false)
                    .putFloat(KEY_TEST_FLOAT, 2.718f)
                    .putInt(KEY_TEST_INT, 654321)
                    .putInt(KEY_TEST_XXX, counter++)
                    .putLong(KEY_TEST_LONG, 123456789L)
                    .putString(KEY_TEST_STRING, "batch_apply")
                    .putStringSet(KEY_TEST_SET, setOf("apply1", "apply2"))
                    .apply()
                appendLog("put all + apply (async save), counter=$counter")
            }
            ApiButton("remove(key) + commit") {
                val ok = sp.edit().remove(KEY_TEST_STRING).commit()
                appendLog("remove($KEY_TEST_STRING) + commit → $ok")
            }
            ApiButton("clear() + commit") {
                val ok = sp.edit().clear().commit()
                appendLog("clear + commit → $ok")
            }

            SectionTitle("OkSharedPreferences APIs")
            ApiButton("reload()") {
                sp.reload()
                appendLog("reload done, getAll size=${sp.getAll().size}")
            }
            ApiButton("registerOnSharedPreferenceChangeListener") {
                sp.registerOnSharedPreferenceChangeListener(demoListener)
                appendLog("registerOnSharedPreferenceChangeListener (duplicate register is safe)")
            }
            ApiButton("unregisterOnSharedPreferenceChangeListener") {
                sp.unregisterOnSharedPreferenceChangeListener(demoListener)
                appendLog("unregisterOnSharedPreferenceChangeListener")
            }
            ApiButton("clearOnSharedPreferenceChangeListener") {
                sp.clearOnSharedPreferenceChangeListener()
                appendLog("clearOnSharedPreferenceChangeListener (all listeners removed)")
            }

            SectionTitle("Cross-process (SharedPreferenceService)")
            ApiButton("remote: commit") {
                sendToService(messenger, SharedPreferenceService.SAVE_COMMIT, appendLog)
            }
            ApiButton("remote: apply") {
                sendToService(messenger, SharedPreferenceService.SAVE_APPLY, appendLog)
            }
            ApiButton("remote: clear") {
                sendToService(messenger, SharedPreferenceService.CLEAR, appendLog)
            }
            ApiButton("remote: show (logcat)") {
                sendToService(messenger, SharedPreferenceService.SHOW, appendLog)
            }
        }
    }
}

@Composable
private fun SectionTitle(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.titleSmall,
        modifier = Modifier.padding(start = 16.dp, top = 12.dp, end = 16.dp, bottom = 4.dp),
        color = MaterialTheme.colorScheme.primary
    )
}

@Composable
private fun ApiButton(label: String, onClick: () -> Unit) {
    Button(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp),
        onClick = onClick
    ) {
        Text(label)
    }
}

private fun sendToService(
    messenger: Messenger?,
    what: Int,
    appendLog: (String) -> Unit
) {
    if (messenger == null) {
        appendLog("Messenger not connected yet")
        return
    }
    messenger.send(Message.obtain(null, what))
    appendLog("sent message what=$what to SharedPreferenceService")
}

@Preview(showBackground = true)
@Composable
fun OkSpApiDemoPreview() {
    OkSharedPreferencesTheme {
        OkSpApiDemoScreen(messenger = null)
    }
}
