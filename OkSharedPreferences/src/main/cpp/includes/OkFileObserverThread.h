#ifndef OKSHAREDPREFERENCES_OKFILEOBSERVERTHREAD_H
#define OKSHAREDPREFERENCES_OKFILEOBSERVERTHREAD_H

#include <jni.h>

#ifdef __cplusplus
extern "C" {
#endif

JNIEXPORT jint JNICALL
Java_online_greatfeng_oksharedpreferences_fileobserver_OkFileObserverThread_nativeInit(
        JNIEnv *env, jobject thiz);

JNIEXPORT void JNICALL
Java_online_greatfeng_oksharedpreferences_fileobserver_OkFileObserverThread_nativeObserve(
        JNIEnv *env, jobject thiz, jint fd);

JNIEXPORT void JNICALL
Java_online_greatfeng_oksharedpreferences_fileobserver_OkFileObserverThread_nativeStartWatching(
        JNIEnv *env, jobject thiz, jint fd, jobjectArray paths, jint mask, jintArray wfds);

JNIEXPORT void JNICALL
Java_online_greatfeng_oksharedpreferences_fileobserver_OkFileObserverThread_nativeStopWatching(
        JNIEnv *env, jobject thiz, jint fd, jintArray wfds);

#ifdef __cplusplus
}
#endif

#endif
