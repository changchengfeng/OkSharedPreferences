package online.greatfeng.oksharedpreferences

import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import online.greatfeng.library.getOkSharedPreferences
import online.greatfeng.oksharedpreferences.ui.theme.OkSharedPreferencesTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            OkSharedPreferencesTheme {
                // A surface container using the 'background' color from the theme
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    Greeting()
                }
            }
        }
    }
}

private const val TAG = "MainActivity"


const val KEY_TEST_BOOLEAN = "test_Boolean"
const val KEY_TEST_FLOAT = "test_Float"
const val KEY_TEST_INT = "test_Int"
const val KEY_TEST_LONG = "test_Long"
const val KEY_TEST_STRING = "test_String"
const val KEY_TEST_SET = "test_Set"

@Composable
fun Greeting() {
    val context = LocalContext.current
    Column(modifier = Modifier.fillMaxSize()) {
        Button(
            modifier = Modifier
                .wrapContentWidth()
                .wrapContentHeight()
                .padding(16.dp),
            onClick = { /* 按钮点击时执行的操作 */
                val okSharedPreferences = context.getOkSharedPreferences("test")
                val editor = okSharedPreferences.edit()
                editor.putBoolean(KEY_TEST_BOOLEAN, true)
                editor.putFloat(KEY_TEST_FLOAT, 3.1415926f)
                editor.putInt(KEY_TEST_INT, 123456)
                editor.putLong(KEY_TEST_LONG, 987654321L)
                editor.putString(
                    KEY_TEST_STRING,
                    "abc这里的when表达式使用了没有参数的形式，而是直接在每个分支中写上条件。当yourValue小于80时，执行相应的代码块。如果需要执行其他比较，你可以根据实际情况添加相应的分支。"
                )
                editor.putStringSet(
                    KEY_TEST_SET, setOf(
                        "abc    这里的when表达式使用了没有参数的形式，而是直接在每个分支中写上条件。当yourValue小于80时，执行相应的代码块。如果需要执行其他比较，你可以根据实际情况添加相应的分支。d",
                        "efg    请注意，toByteArray() 返回的是字节数组，其中每个字节表示 Float 的不同部分，这包括符号位、指数位和尾数位。如果你需要更精确地控制字节的顺序（例如，大端序或小端序），你可能需要使用 ByteBuffer 类。",
                        "hjj    In the last line, (y = 5) is an assignment statement, and it cannot be used as part of the expression for the sum. If you need to modify a variable and use its value in an expression simultaneously, you should perform the assignment separately from the expression",
                        "zxc    在 Kotlin 中，你可以使用 toByteArray() 方法将 Float 转化为字节数组。这方法存在于 Float 类的扩展函数中。"
                    )
                )
                editor.commit()
            },
            content = {
                Text("save")
            }
        )
        Button(
            modifier = Modifier
                .wrapContentWidth()
                .wrapContentHeight()
                .padding(16.dp),
            onClick = { /* 按钮点击时执行的操作 */
                val okSharedPreferences = context.getOkSharedPreferences("test")
                val testBoolean = okSharedPreferences.getBoolean(KEY_TEST_BOOLEAN, false)
                Log.d(TAG, "testBoolean: $testBoolean")

                val testFloat= okSharedPreferences.getFloat(KEY_TEST_FLOAT, 0f)
                Log.d(TAG, "testFloat: $testFloat")

                val testInt = okSharedPreferences.getInt(KEY_TEST_INT, 0)
                Log.d(TAG, "testInt: $testInt")

                val testLong = okSharedPreferences.getLong(KEY_TEST_LONG, 0L)
                Log.d(TAG, "testLong: $testLong")


                val testString = okSharedPreferences.getString(KEY_TEST_STRING, "")
                Log.d(TAG, "testString: $testString")

                val testSet = okSharedPreferences.getStringSet(KEY_TEST_SET, setOf())
                Log.d(TAG, "testSet: $testSet")
            },
            content = {
                Text("show")
            }
        )
    }

}

@Preview(showBackground = true)
@Composable
fun GreetingPreview() {
    OkSharedPreferencesTheme {
        Greeting()
    }
}