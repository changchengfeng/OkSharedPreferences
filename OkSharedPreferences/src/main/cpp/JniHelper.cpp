#include "JniHelper.h"
#include "LOG.h"

jmethodID method_onEvent;

JNIEXPORT jint JNI_OnLoad(JavaVM *vm, void *reserved) {
    JNIEnv *env = nullptr;
    LOGD("JNI_OnLoad...");
    if (vm->GetEnv((void **) &env, JNI_VERSION_1_6) != JNI_OK || env == nullptr) {
        LOGE("JNI_OnLoad GetEnv Error");
        return JNI_ERR;
    }
    jclass clz = env->FindClass(
            "online/greatfeng/oksharedpreferences/fileobserver/OkFileObserverThread");
    if (clz == nullptr) {
        LOGE("JNI_OnLoad FindClass Error");
        return JNI_ERR;
    }
    method_onEvent = env->GetMethodID(clz, "onEvent", "(IILjava/lang/String;)V");
    env->DeleteLocalRef(clz);
    if (method_onEvent == nullptr) {
        LOGE("JNI_OnLoad GetMethodID Error");
        return JNI_ERR;
    }
    return JNI_VERSION_1_6;
}

JNIEXPORT void JNI_OnUnload(JavaVM *vm, void *reserved) {
}
