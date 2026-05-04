#include <jni.h>
#include <string>
#include <vector>
#include <android/log.h>
#include "core/AirPlayServer.h"

#define TAG "AirPlay:JNI"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, TAG, __VA_ARGS__)

// Instância global do servidor (single session)
static AirPlayServer* g_server = nullptr;
static JavaVM* g_jvm = nullptr;
static jobject g_callbackObject = nullptr;

#include "JniBridge.h"

// JNI exports

// JNI exports
extern "C" {

JNIEXPORT jint JNI_OnLoad(JavaVM* vm, void* reserved) {
    JniBridge::init(vm);
    LOGI("Native library loaded");
    return JNI_VERSION_1_6;
}

JNIEXPORT jstring JNICALL
Java_com_airplay_tv_protocol_AirPlayJniBridge_getVersionFromJNI(
        JNIEnv* env,
        jobject /* this */) {
    std::string version = "AirPlay Native Library v1.0 (Fase 4)";
    LOGI("Version requested: %s", version.c_str());
    return env->NewStringUTF(version.c_str());
}

JNIEXPORT jboolean JNICALL
Java_com_airplay_tv_protocol_AirPlayJniBridge_startRTSPServerNative(
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
    g_server->setConnectionCallback(JniBridge::onConnectionCallback);
    g_server->setDisconnectionCallback(JniBridge::onDisconnectionCallback);
    g_server->setActivityCallback(JniBridge::onActivityCallback);
    g_server->setVideoDataCallback(JniBridge::onVideoDataCallback);
    g_server->setAudioDataCallback(JniBridge::onAudioDataCallback);
    g_server->setAudioSyncCallback(JniBridge::onAudioSyncCallback);
    g_server->setErrorCallback(JniBridge::onErrorCallback);
    g_server->setMirroringVideoPacketCallback(JniBridge::onMirroringVideoPacketCallback);
    g_server->setPhotoPlaybackCallback(JniBridge::onPhotoPlaybackCallback);
    g_server->setSlideshowPlaybackCallback(JniBridge::onSlideshowPlaybackCallback);
    g_server->setMediaStopCallback(JniBridge::onMediaStopCallback);
    
    // Salvar referência ao objeto Java para callbacks
    JniBridge::setCallbackObject(env, thiz);
    
    bool success = g_server->start(port);
    
    if (!success) {
        delete g_server;
        g_server = nullptr;
        JniBridge::clearCallbackObject(env);
        return JNI_FALSE;
    }
    
    LOGI("RTSP server started successfully");
    return JNI_TRUE;
}

JNIEXPORT void JNICALL
Java_com_airplay_tv_protocol_AirPlayJniBridge_stopRTSPServerNative(
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
    
    JniBridge::clearCallbackObject(env);
    
    LOGI("RTSP server stopped");
}

JNIEXPORT jboolean JNICALL
Java_com_airplay_tv_protocol_AirPlayJniBridge_isServerRunningNative(
        JNIEnv* env,
        jobject /* this */) {
    
    return (g_server != nullptr && g_server->isRunning()) ? JNI_TRUE : JNI_FALSE;
}

JNIEXPORT jstring JNICALL
Java_com_airplay_tv_protocol_AirPlayJniBridge_getClientIpNative(
        JNIEnv* env,
        jobject /* this */) {
    
    if (g_server == nullptr) {
        return env->NewStringUTF("");
    }
    
    std::string clientIp = g_server->getClientIp();
    return env->NewStringUTF(clientIp.c_str());
}

JNIEXPORT jintArray JNICALL
Java_com_airplay_tv_protocol_AirPlayJniBridge_getVideoResolutionNative(
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
Java_com_airplay_tv_protocol_AirPlayJniBridge_getAudioConfigNative(
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

JNIEXPORT void JNICALL
Java_com_airplay_tv_protocol_AirPlayJniBridge_updateAudioSessionConfigNative(
        JNIEnv*,
        jobject,
        jint compressionType,
        jint samplesPerFrame,
        jlong audioFormat,
        jint sampleRate,
        jint channels,
        jint remoteControlPort,
        jint localDataPort,
        jint localControlPort,
        jint localTimingPort,
        jboolean isMedia,
        jboolean usingScreen) {
    if (g_server == nullptr) {
        return;
    }

    AirPlayServer::AudioSessionConfig config;
    config.compressionType = compressionType;
    config.samplesPerFrame = samplesPerFrame;
    config.audioFormat = static_cast<uint64_t>(audioFormat);
    config.sampleRate = sampleRate;
    config.channels = channels;
    config.remoteControlPort = remoteControlPort;
    config.localDataPort = localDataPort;
    config.localControlPort = localControlPort;
    config.localTimingPort = localTimingPort;
    config.isMedia = isMedia == JNI_TRUE;
    config.usingScreen = usingScreen == JNI_TRUE;
    g_server->updateAudioSessionConfig(config);
}

JNIEXPORT void JNICALL
Java_com_airplay_tv_protocol_AirPlayJniBridge_resetAudioSessionConfigNative(
        JNIEnv*,
        jobject) {
    if (g_server != nullptr) {
        g_server->resetAudioSessionConfig();
    }
}

JNIEXPORT jbyteArray JNICALL
Java_com_airplay_tv_protocol_AirPlayJniBridge_decryptFairPlayAesKeyNative(
        JNIEnv* env,
        jobject /* this */,
        jbyteArray encryptedKey) {
    if (g_server == nullptr || encryptedKey == nullptr) {
        return nullptr;
    }

    const jsize length = env->GetArrayLength(encryptedKey);
    std::vector<uint8_t> input(static_cast<size_t>(length));
    env->GetByteArrayRegion(
            encryptedKey,
            0,
            length,
            reinterpret_cast<jbyte*>(input.data()));

    std::vector<uint8_t> output;
    if (!g_server->decryptFairPlayAesKey(input.data(), input.size(), &output) || output.empty()) {
        return nullptr;
    }

    jbyteArray result = env->NewByteArray(static_cast<jsize>(output.size()));
    env->SetByteArrayRegion(
            result,
            0,
            static_cast<jsize>(output.size()),
            reinterpret_cast<const jbyte*>(output.data()));
    return result;
}

JNIEXPORT jint JNICALL
Java_com_airplay_tv_protocol_AirPlayJniBridge_startMirrorVideoServerNative(
        JNIEnv* /* env */,
        jobject /* this */) {
    if (g_server == nullptr) {
        return -1;
    }

    return static_cast<jint>(g_server->startMirrorVideoServer());
}

} // extern "C"
