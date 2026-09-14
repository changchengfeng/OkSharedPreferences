#include "OkFileObserverThread.h"
#include <sys/inotify.h>
#include <unistd.h>
#include <errno.h>
#include <stdint.h>
#include "LOG.h"
#include "JniHelper.h"

namespace {
constexpr size_t kEventBufSize = 8192;
}

extern "C" JNIEXPORT jint JNICALL
Java_online_greatfeng_oksharedpreferences_fileobserver_OkFileObserverThread_nativeInit(
        JNIEnv *env, jobject thiz) {
    (void) env;
    (void) thiz;
    int fd = inotify_init1(IN_CLOEXEC);
    if (fd < 0) {
        LOGE("inotify_init1 failed: %d", errno);
    }
    return (jint) fd;
}

extern "C" JNIEXPORT void JNICALL
Java_online_greatfeng_oksharedpreferences_fileobserver_OkFileObserverThread_nativeObserve(
        JNIEnv *env, jobject thiz, jint fd) {
    if (fd < 0 || method_onEvent == nullptr) {
        LOGE("ok_fileobserver_observe() invalid fd or method");
        return;
    }

    char event_buf[kEventBufSize];
    struct inotify_event *event;

    while (1) {
        int event_pos = 0;
        int num_bytes = read(fd, event_buf, sizeof(event_buf));

        if (num_bytes < 0) {
            if (errno == EINTR) {
                continue;
            }
            LOGE("ok_fileobserver_observe() read failed errno=%d, retrying", errno);
            continue;
        }

        if (num_bytes < (int) sizeof(*event)) {
            LOGE("ok_fileobserver_observe() short read (%d bytes), continuing", num_bytes);
            continue;
        }

        while (num_bytes >= (int) sizeof(*event)) {
            int event_size;
            event = (struct inotify_event *) (event_buf + event_pos);

            jstring path = NULL;

            if (event->len > 0) {
                path = env->NewStringUTF(event->name);
            }
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

extern "C" JNIEXPORT void JNICALL
Java_online_greatfeng_oksharedpreferences_fileobserver_OkFileObserverThread_nativeStartWatching(
        JNIEnv *env, jobject thiz, jint fd, jobjectArray paths, jint mask, jintArray wfds) {
    (void) thiz;
    LOGD("ObserverThread_startWatching fd = %d", fd);
    if (fd < 0 || paths == nullptr || wfds == nullptr) {
        return;
    }
    jsize count = env->GetArrayLength(paths);
    jsize size = env->GetArrayLength(wfds);
    if (count <= 0 || size <= 0) {
        return;
    }
    jsize n = count < size ? count : size;
    jint *buffer = env->GetIntArrayElements(wfds, nullptr);
    if (buffer == nullptr) {
        return;
    }
    for (jsize i = 0; i < n; ++i) {
        buffer[i] = -1;
        jstring pathString = (jstring) env->GetObjectArrayElement(paths, i);
        if (pathString == nullptr) {
            continue;
        }
        const char *path = env->GetStringUTFChars(pathString, nullptr);
        if (path == nullptr) {
            env->DeleteLocalRef(pathString);
            continue;
        }
        LOGD("ObserverThread_startWatching path = %s", path);
        int wfd = inotify_add_watch(fd, path, (uint32_t) mask);
        if (wfd < 0) {
            LOGE("inotify_add_watch failed path=%s errno=%d", path, errno);
        }
        buffer[i] = wfd;
        env->ReleaseStringUTFChars(pathString, path);
        env->DeleteLocalRef(pathString);
    }
    env->ReleaseIntArrayElements(wfds, buffer, 0);
}

extern "C" JNIEXPORT void JNICALL
Java_online_greatfeng_oksharedpreferences_fileobserver_OkFileObserverThread_nativeStopWatching(
        JNIEnv *env, jobject thiz, jint fd, jintArray wfds) {
    (void) thiz;
    if (fd < 0 || wfds == nullptr) {
        return;
    }
    jsize count = env->GetArrayLength(wfds);
    jint *buffer = env->GetIntArrayElements(wfds, nullptr);
    if (buffer == nullptr) {
        return;
    }
    for (jsize i = 0; i < count; ++i) {
        if (buffer[i] >= 0) {
            inotify_rm_watch((int) fd, buffer[i]);
        }
    }
    env->ReleaseIntArrayElements(wfds, buffer, JNI_ABORT);
}
