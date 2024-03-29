//
// Created by cfeng25 on 1/14/24.
//


#include "JniHelper.h"
#include "LOG.h"

jmethodID method_onEvent;
JNIEnv *env;

JNIEXPORT jint JNI_OnLoad(JavaVM *vm, void *reserved) {

    LOGD("JNI_OnLoad...");
    if (vm->GetEnv((void **) &env, JNI_VERSION_1_6) != JNI_OK) {
        LOGE("JNI_OnLoad GetEnv Error");
        return JNI_ERR;
    }
    jclass clz = env->FindClass(
            "online/greatfeng/oksharedpreferences/fileobserver/OkFileObserver$ObserverThread");
    method_onEvent = env->GetMethodID(clz, "onEvent", "(IILjava/lang/String;)V");
    LOGD("JNI_OnLoad clz = %d", clz);
    LOGD("JNI_OnLoad method_onEvent = %d", method_onEvent);
    return JNI_VERSION_1_6;
}

JNIEXPORT void JNI_OnUnload(JavaVM *vm, void *reserved) {

}
