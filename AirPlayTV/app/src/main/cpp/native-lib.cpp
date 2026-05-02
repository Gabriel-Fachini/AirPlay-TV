#include <jni.h>
#include <string>
#include <android/log.h>

#define TAG "AirPlay:Native"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, TAG, __VA_ARGS__)

// Placeholder native library
// TODO: Fase 4 - Implementar bridge JNI para biblioteca AirPlay

extern "C" JNIEXPORT jstring JNICALL
Java_com_airplay_tv_protocol_ProtocolHandler_getVersionFromJNI(
        JNIEnv* env,
        jobject /* this */) {
    std::string version = "AirPlay Native Library - Stub (Fase 4)";
    LOGI("Native library loaded: %s", version.c_str());
    return env->NewStringUTF(version.c_str());
}
