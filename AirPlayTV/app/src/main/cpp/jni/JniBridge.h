#pragma once

#include <jni.h>
#include <string>
#include <vector>
#include <cstdint>

// Gerenciador de Callbacks para JNI
class JniBridge {
public:
    static void init(JavaVM* vm);
    static void setCallbackObject(JNIEnv* env, jobject callbackObject);
    static void clearCallbackObject(JNIEnv* env);
    static jobject getCallbackObject();

    static void onConnectionCallback(const std::string& clientIp);
    static void onDisconnectionCallback();
    static void onActivityCallback(const std::string& methodName);
    static void onErrorCallback(const std::string& error);
    static void onVideoDataCallback(const uint8_t* data, size_t size, uint32_t timestamp);
    static void onAudioDataCallback(const uint8_t* data, size_t size, uint32_t timestamp);
    static void onAudioSyncCallback(uint32_t rtpSync, uint64_t remoteNtpUs, uint64_t localNtpUs, bool initial);
    static void onMirroringVideoPacketCallback(int payloadType, const uint8_t* data, size_t size);
    
    static void onPhotoPlaybackCallback(
        const std::string& clientIp,
        const std::string& sessionId,
        const std::string& assetKey,
        const std::string& transition,
        const std::vector<uint8_t>& imageData,
        bool isSlideshow);
        
    static void onSlideshowPlaybackCallback(
        const std::string& clientIp,
        const std::string& sessionId,
        const std::string& theme,
        int slideDurationSeconds,
        const std::string& state);
        
    static void onMediaStopCallback(const std::string& sessionId);
    
    static std::vector<uint8_t> invokeByteArrayCallback(
        const char* methodName,
        const uint8_t* data,
        size_t size,
        bool* ok);

private:
    static JNIEnv* getJNIEnv();

    static JavaVM* g_jvm;
    static jobject g_callbackObject;
};
