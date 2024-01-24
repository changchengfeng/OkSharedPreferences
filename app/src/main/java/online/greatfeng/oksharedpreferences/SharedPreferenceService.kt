package online.greatfeng.oksharedpreferences

import android.app.Service
import android.content.Intent
import android.content.SharedPreferences
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.Message
import android.os.Messenger
import android.util.Log

class SharedPreferenceService : Service() {
    var value = 100000
    val messenger = Messenger(object : Handler(Looper.getMainLooper()) {

        override fun handleMessage(msg: Message) {
            Log.d(TAG, "handleMessage() called with: msg = $msg")
            when (msg.what) {
                SAVE_COMMIT -> {
                    val okSharedPreferences = getOkSharedPreferences(OKSHAREDPREFERENCES_TEST_NAME)
                    val editor = okSharedPreferences.edit()
                    editor.putBoolean(KEY_TEST_BOOLEAN, true)
                    editor.putFloat(KEY_TEST_FLOAT, 3.1415926f)
                    editor.putInt(KEY_TEST_INT, 123456)
                    editor.putInt(KEY_TEST_XXX, value++)
                    editor.putLong(KEY_TEST_LONG, 987654321L)
                    editor.putString(
                        KEY_TEST_STRING,
                        "Stop watching for events. Some events may be in process, so events may continue to be reported even after this method completes. If monitoring is already stopped, this call has no effect."
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
                }

                SAVE_APPLY -> {
                    val okSharedPreferences = getOkSharedPreferences(OKSHAREDPREFERENCES_TEST_NAME)
                    val editor = okSharedPreferences.edit()
                    editor.putBoolean(KEY_TEST_BOOLEAN, true)
                    editor.putFloat(KEY_TEST_FLOAT, 3.1415926f)
                    editor.putInt(KEY_TEST_INT, 123456)
                    editor.putInt(KEY_TEST_XXX, value++)
                    editor.putLong(KEY_TEST_LONG, 987654321L)
                    editor.putString(
                        KEY_TEST_STRING,
                        "Stop watching for events. Some events may be in process, so events may continue to be reported even after this method completes. If monitoring is already stopped, this call has no effect."
                    )
                    editor.putStringSet(
                        KEY_TEST_SET, setOf(
                            "abc    这里的when表达式使用了没有参数的形式，而是直接在每个分支中写上条件。当yourValue小于80时，执行相应的代码块。如果需要执行其他比较，你可以根据实际情况添加相应的分支。d",
                            "efg    请注意，toByteArray() 返回的是字节数组，其中每个字节表示 Float 的不同部分，这包括符号位、指数位和尾数位。如果你需要更精确地控制字节的顺序（例如，大端序或小端序），你可能需要使用 ByteBuffer 类。",
                            "hjj    In the last line, (y = 5) is an assignment statement, and it cannot be used as part of the expression for the sum. If you need to modify a variable and use its value in an expression simultaneously, you should perform the assignment separately from the expression",
                            "zxc    在 Kotlin 中，你可以使用 toByteArray() 方法将 Float 转化为字节数组。这方法存在于 Float 类的扩展函数中。"
                        )
                    )
                    editor.apply()
                }

                CLEAR -> {
                    val okSharedPreferences = getOkSharedPreferences(OKSHAREDPREFERENCES_TEST_NAME)
                    okSharedPreferences.edit().clear().commit()
                }

                SHOW -> {
                    val okSharedPreferences = getOkSharedPreferences(OKSHAREDPREFERENCES_TEST_NAME)
                    val testBoolean = okSharedPreferences.getBoolean(KEY_TEST_BOOLEAN, false)
                    Log.d(TAG, "testBoolean: $testBoolean")

                    val testFloat = okSharedPreferences.getFloat(KEY_TEST_FLOAT, 0f)
                    Log.d(TAG, "testFloat: $testFloat")

                    val testInt = okSharedPreferences.getInt(KEY_TEST_INT, 0)
                    Log.d(TAG, "testInt: $testInt")

                    val testXXX = okSharedPreferences.getInt(KEY_TEST_XXX, 0)
                    Log.d(TAG, "testXXX: $testXXX")

                    val testLong = okSharedPreferences.getLong(KEY_TEST_LONG, 0L)
                    Log.d(TAG, "testLong: $testLong")


                    val testString = okSharedPreferences.getString(KEY_TEST_STRING, "")
                    Log.d(TAG, "testString: $testString")

                    val testSet = okSharedPreferences.getStringSet(KEY_TEST_SET, setOf())
                    Log.d(TAG, "testSet: $testSet")
                }
            }
        }
    })

    companion object {
        private const val TAG = "SharedPreferenceService"
        const val SAVE_COMMIT = 1
        const val SAVE_APPLY = 2
        const val CLEAR = 3
        const val SHOW = 4
    }


    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "onCreate() called")

        getOkSharedPreferences(OKSHAREDPREFERENCES_TEST_NAME).registerOnSharedPreferenceChangeListener(
            object : SharedPreferences.OnSharedPreferenceChangeListener {
                override fun onSharedPreferenceChanged(
                    sharedPreferences: SharedPreferences?,
                    key: String?
                ) {
                    sharedPreferences?.let {
                        key?.let {
                            when (it) {
                                KEY_TEST_BOOLEAN -> {
                                    Log.i(
                                        TAG,
                                        "onSharedPreferenceChanged() called with: sharedPreferences = ${
                                            sharedPreferences.getBoolean(
                                                it,
                                                false
                                            )
                                        }, key = $key"
                                    )
                                }

                                KEY_TEST_FLOAT -> {
                                    Log.i(
                                        TAG,
                                        "onSharedPreferenceChanged() called with: sharedPreferences = ${
                                            sharedPreferences.getFloat(
                                                it,
                                                0.0f
                                            )
                                        }, key = $key"
                                    )
                                }

                                KEY_TEST_INT -> {
                                    Log.i(
                                        TAG,
                                        "onSharedPreferenceChanged() called with: sharedPreferences = ${
                                            sharedPreferences.getInt(
                                                it,
                                                0
                                            )
                                        }, key = $key"
                                    )
                                }

                                KEY_TEST_XXX -> {
                                    Log.i(
                                        TAG,
                                        "onSharedPreferenceChanged() called with: sharedPreferences = ${
                                            sharedPreferences.getInt(
                                                it,
                                                0
                                            )
                                        }, key = $key"
                                    )
                                }

                                KEY_TEST_LONG -> {
                                    Log.i(
                                        TAG,
                                        "onSharedPreferenceChanged() called with: sharedPreferences = ${
                                            sharedPreferences.getLong(
                                                it,
                                                0L
                                            )
                                        }, key = $key"
                                    )
                                }

                                KEY_TEST_STRING -> {
                                    Log.i(
                                        TAG,
                                        "onSharedPreferenceChanged() called with: sharedPreferences = ${
                                            sharedPreferences.getString(
                                                it,
                                                null
                                            )
                                        }, key = $key"
                                    )
                                }

                                KEY_TEST_SET -> {
                                    Log.i(
                                        TAG,
                                        "onSharedPreferenceChanged() called with: sharedPreferences = ${
                                            sharedPreferences.getStringSet(
                                                it,
                                                null
                                            )
                                        }, key = $key"
                                    )
                                }

                                else -> {}
                            }
                        }
                    }


                }
            })
    }

    override fun onBind(intent: Intent): IBinder {
        return messenger.binder
    }
}