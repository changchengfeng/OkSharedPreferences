#include <jni.h>
#include <sys/inotify.h>
#include <unistd.h>
#include <errno.h>
#include "LOG.h"
#include "JniHelper.h"


extern "C"
JNIEXPORT jint JNICALL
Java_online_greatfeng_oksharedpreferences_fileobserver_OkFileObserver_00024ObserverThread_init(JNIEnv *env,
                                                                                   jobject thiz) {
    return (jint) inotify_init1(IN_CLOEXEC);
}
extern "C"
JNIEXPORT void JNICALL
Java_online_greatfeng_oksharedpreferences_fileobserver_OkFileObserver_00024ObserverThread_observe(JNIEnv *env,
                                                                                      jobject thiz,
                                                                                      jint fd) {

    char event_buf[512];
    struct inotify_event *event;

    while (1) {
        int event_pos = 0;
        int num_bytes = read(fd, event_buf, sizeof(event_buf));

        if (num_bytes < (int) sizeof(*event)) {
            if (errno == EINTR)
                continue;

            LOGE("***** ERROR! ok_fileobserver_observe() got a short event!");
            return;
        }

        while (num_bytes >= (int) sizeof(*event)) {
            int event_size;
            event = (struct inotify_event *) (event_buf + event_pos);

            jstring path = NULL;

            if (event->len > 0) {
                path = env->NewStringUTF(event->name);
            }
//            LOGD("CallVoidMethod... method_onEvent %d",method_onEvent);
            env->CallVoidMethod(thiz, method_onEvent, event->wd, event->mask, path);
            if (env->ExceptionCheck()) {
                env->ExceptionDescribe();
                env->ExceptionClear();
            }
            if (path != NULL) {
                env->DeleteLocalRef(path);
            }

            event_size = sizeof(*event) + event->len;
            num_bytes -= event_size;
            event_pos += event_size;
        }
    }
}
extern "C"
JNIEXPORT void JNICALL
Java_online_greatfeng_oksharedpreferences_fileobserver_OkFileObserver_00024ObserverThread_startWatching(
        JNIEnv *env, jobject thiz, jint fd, jobjectArray paths, jint mask, jintArray wfds) {
    LOGD("ObserverThread_startWatching fd = %d",fd);
    if (fd >= 0) {
        size_t count = env->GetArrayLength(paths);
        size_t size = env->GetArrayLength(wfds);
        jint buffer[size];
        for (jsize i = 0; i < count; ++i) {
            jstring pathString = (jstring) env->GetObjectArrayElement(paths, i);
            jboolean isCopy;
            const char *path = env->GetStringUTFChars(pathString, &isCopy);
            LOGD("ObserverThread_startWatching fd = %s",path);
            buffer[i] = inotify_add_watch(fd, reinterpret_cast<const char *>(path), mask);
            if (isCopy) {
                env->ReleaseStringUTFChars(pathString, path);
            }
        }
        env->SetIntArrayRegion(wfds, 0, size, buffer);
    }
}
extern "C"
JNIEXPORT void JNICALL
Java_online_greatfeng_oksharedpreferences_fileobserver_OkFileObserver_00024ObserverThread_stopWatching(
        JNIEnv *env, jobject thiz, jint fd, jintArray wfds) {
    size_t count = env->GetArrayLength(wfds);
    jboolean isCopy;
    jint *buffer = env->GetIntArrayElements(wfds, &isCopy);
    for (size_t i = 0; i < count; ++i) {
        inotify_rm_watch((int) fd, buffer[i]);
    }
    if (isCopy) {
        env->ReleaseIntArrayElements(wfds, buffer, JNI_ABORT);
    }
}