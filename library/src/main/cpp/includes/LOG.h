//
// Created by cfeng25 on 1/14/24.
//




#ifndef OKSHAREDPREFERENCES_LOG_H
#define OKSHAREDPREFERENCES_LOG_H

#define MODULE_NAME  "OkSharedPreferences"



#include <android/log.h>

#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, MODULE_NAME, __VA_ARGS__)
#define LOGW(...) __android_log_print(ANDROID_LOG_WARN, MODULE_NAME, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, MODULE_NAME, __VA_ARGS__)
#define LOGF(...) __android_log_print(ANDROID_LOG_FATAL, MODULE_NAME, __VA_ARGS__)

#endif //OKSHAREDPREFERENCES_LOG_H


