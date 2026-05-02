#include <jni.h>
#include <string>
#include <android/log.h>
#include "airplay_server.h"

#define TAG "AirPlay:JNI"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, TAG, __VA_ARGS__)

// Instância global do servidor (single session)
static AirPlayServer* g_server = nullptr;
static JavaVM* g_jvm = nullptr;
static jobject g_callbackObject = nullptr;

// Helper para obter JNIEnv na thread atual
JNIEnv* getJNIEnv() {
    JNIEnv* env = nullptr;
    if (g_jvm->GetEnv((void**)&env, JNI_VERSION_1_6) == JNI_EDETACHED) {
        g_jvm->AttachCurrentThread(&env, nullptr);
    }
    return env;
}

// Callbacks do servidor para Java
void onConnectionCallback(const std::string& clientIp) {
    JNIEnv* env = getJNIEnv();
    if (!env || !g_callbackObject) return;

    jclass cls = env->GetObjectClass(g_callbackObject);
    jmethodID method = env->GetMethodID(cls, "onClientConnected", "(Ljava/lang/String;)V");
    
    if (method) {
        jstring jClientIp = env->NewStringUTF(clientIp.c_str());
        env->CallVoidMethod(g_callbackObject, method, jClientIp);
        env->DeleteLocalRef(jClientIp);
    }
    
    env->DeleteLocalRef(cls);
}

void onDisconnectionCallback() {
    JNIEnv* env = getJNIEnv();
    if (!env || !g_callbackObject) return;

    jclass cls = env->GetObjectClass(g_callbackObject);
    jmethodID method = env->GetMethodID(cls, "onClientDisconnected", "()V");
    
    if (method) {
        env->CallVoidMethod(g_callbackObject, method);
    }
    
    env->DeleteLocalRef(cls);
}

void onErrorCallback(const std::string& error) {
    JNIEnv* env = getJNIEnv();
    if (!env || !g_callbackObject) return;

    jclass cls = env->GetObjectClass(g_callbackObject);
    jmethodID method = env->GetMethodID(cls, "onError", "(Ljava/lang/String;)V");
    
    if (method) {
        jstring jError = env->NewStringUTF(error.c_str());
        env->CallVoidMethod(g_callbackObject, method, jError);
        env->DeleteLocalRef(jError);
    }
    
    env->DeleteLocalRef(cls);
}

// JNI exports
extern "C" {

JNIEXPORT jint JNI_OnLoad(JavaVM* vm, void* reserved) {
    g_jvm = vm;
    LOGI("Native library loaded");
    return JNI_VERSION_1_6;
}

JNIEXPORT jstring JNICALL
Java_com_airplay_tv_protocol_ProtocolHandler_getVersionFromJNI(
        JNIEnv* env,
        jobject /* this */) {
    std::string version = "AirPlay Native Library v1.0 (Fase 4)";
    LOGI("Version requested: %s", version.c_str());
    return env->NewStringUTF(version.c_str());
}

JNIEXPORT jboolean JNICALL
Java_com_airplay_tv_protocol_ProtocolHandler_startRTSPServerNative(
        JNIEnv* env,
        jobject thiz,
        jint port) {
    
    LOGI("Starting RTSP server on port %d", port);
    
    if (g_server != nullptr) {
        LOGE("Server already exists");
        return JNI_FALSE;
    }
    
    g_server = new AirPlayServer();
    
    // Configurar callbacks
    g_server->setConnectionCallback(onConnectionCallback);
    g_server->setDisconnectionCallback(onDisconnectionCallback);
    g_server->setErrorCallback(onErrorCallback);
    
    // Salvar referência ao objeto Java para callbacks
    g_callbackObject = env->NewGlobalRef(thiz);
    
    bool success = g_server->start(port);
    
    if (!success) {
        delete g_server;
        g_server = nullptr;
        env->DeleteGlobalRef(g_callbackObject);
        g_callbackObject = nullptr;
        return JNI_FALSE;
    }
    
    LOGI("RTSP server started successfully");
    return JNI_TRUE;
}

JNIEXPORT void JNICALL
Java_com_airplay_tv_protocol_ProtocolHandler_stopRTSPServerNative(
        JNIEnv* env,
        jobject /* this */) {
    
    LOGI("Stopping RTSP server");
    
    if (g_server == nullptr) {
        LOGE("Server not running");
        return;
    }
    
    g_server->stop();
    delete g_server;
    g_server = nullptr;
    
    if (g_callbackObject) {
        env->DeleteGlobalRef(g_callbackObject);
        g_callbackObject = nullptr;
    }
    
    LOGI("RTSP server stopped");
}

JNIEXPORT jboolean JNICALL
Java_com_airplay_tv_protocol_ProtocolHandler_isServerRunningNative(
        JNIEnv* env,
        jobject /* this */) {
    
    return (g_server != nullptr && g_server->isRunning()) ? JNI_TRUE : JNI_FALSE;
}

JNIEXPORT jstring JNICALL
Java_com_airplay_tv_protocol_ProtocolHandler_getClientIpNative(
        JNIEnv* env,
        jobject /* this */) {
    
    if (g_server == nullptr) {
        return env->NewStringUTF("");
    }
    
    std::string clientIp = g_server->getClientIp();
    return env->NewStringUTF(clientIp.c_str());
}

JNIEXPORT jintArray JNICALL
Java_com_airplay_tv_protocol_ProtocolHandler_getVideoResolutionNative(
        JNIEnv* env,
        jobject /* this */) {
    
    jintArray result = env->NewIntArray(2);
    
    if (g_server != nullptr) {
        jint resolution[2];
        resolution[0] = g_server->getVideoWidth();
        resolution[1] = g_server->getVideoHeight();
        env->SetIntArrayRegion(result, 0, 2, resolution);
    }
    
    return result;
}

JNIEXPORT jintArray JNICALL
Java_com_airplay_tv_protocol_ProtocolHandler_getAudioConfigNative(
        JNIEnv* env,
        jobject /* this */) {
    
    jintArray result = env->NewIntArray(2);
    
    if (g_server != nullptr) {
        jint config[2];
        config[0] = g_server->getAudioSampleRate();
        config[1] = g_server->getAudioChannels();
        env->SetIntArrayRegion(result, 0, 2, config);
    }
    
    return result;
}

} // extern "C"
