#include <jni.h>
#include <android/log.h>

#include <memory>
#include <string>
#include <vector>

#include "JniBridge.h"
#include "ux_raop_core/include/UxRaopCore.h"

#define TAG "AirPlay:JNI"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, TAG, __VA_ARGS__)

namespace {

constexpr uint32_t kDefaultAudioSampleRate = 44100;
constexpr uint32_t kDefaultAudioChannels = 2;
constexpr const char* kReceiverName = "AirPlay TV";
constexpr const char* kReceiverId = "123456789ABC";

struct NativeAudioState {
    uint8_t compressionType = 0;
    uint16_t samplesPerFrame = 0;
    uint64_t audioFormat = 0;
    uint32_t sampleRate = kDefaultAudioSampleRate;
    uint32_t channels = kDefaultAudioChannels;
    bool isMedia = false;
    bool usingScreen = false;
    bool syncSeen = false;
    uint64_t accessUnitsSeen = 0;
};

struct NativeServerState {
    std::unique_ptr<UxRaopCore> core;
    std::string clientLabel;
    uint16_t width = 1920;
    uint16_t height = 1080;
    bool transportConnected = false;
    bool javaConnectionNotified = false;
    NativeAudioState audio;
};

std::unique_ptr<NativeServerState> g_state;

bool containsIdrNalUnit(const uint8_t* data, size_t size) {
    if (data == nullptr || size < 5) {
        return false;
    }

    for (size_t i = 0; i + 4 < size; ++i) {
        const bool startCode =
            data[i] == 0x00 &&
            data[i + 1] == 0x00 &&
            data[i + 2] == 0x00 &&
            data[i + 3] == 0x01;
        if (!startCode) {
            continue;
        }

        const uint8_t nalType = data[i + 4] & 0x1FU;
        if (nalType == 5) {
            return true;
        }
    }

    return false;
}

void maybeNotifyConnected(NativeServerState* state) {
    if (state == nullptr || !state->transportConnected || state->javaConnectionNotified) {
        return;
    }

    JniBridge::onConnectionCallback(state->clientLabel.empty() ? "airplay-client" : state->clientLabel);
    state->javaConnectionNotified = true;
}

void onSessionConnected(void* context, const char* clientLabel) {
    auto* state = static_cast<NativeServerState*>(context);
    if (state == nullptr) {
        return;
    }

    state->transportConnected = true;
    state->clientLabel = clientLabel != nullptr ? clientLabel : "airplay-client";
}

void onSessionDisconnected(void* context) {
    auto* state = static_cast<NativeServerState*>(context);
    if (state == nullptr) {
        return;
    }

    state->transportConnected = false;
    state->javaConnectionNotified = false;
    state->audio.syncSeen = false;
    JniBridge::onDisconnectionCallback();
}

void onProtocolError(void* /* context */, const char* message) {
    JniBridge::onErrorCallback(message != nullptr ? message : "ux_raop_core protocol error");
}

void onVideoConfig(
    void* context,
    uint16_t width,
    uint16_t height,
    const uint8_t* sps,
    size_t spsSize,
    const uint8_t* pps,
    size_t ppsSize) {
    auto* state = static_cast<NativeServerState*>(context);
    if (state == nullptr) {
        return;
    }

    state->width = width;
    state->height = height;
    JniBridge::onVideoConfigCallback(width, height, sps, spsSize, pps, ppsSize);
    maybeNotifyConnected(state);
}

void onAudioConfig(
    void* context,
    uint8_t compressionType,
    uint16_t samplesPerFrame,
    uint64_t audioFormat,
    uint32_t sampleRate,
    uint32_t channels,
    bool isMedia,
    bool usingScreen) {
    auto* state = static_cast<NativeServerState*>(context);
    if (state == nullptr) {
        return;
    }

    state->audio.compressionType = compressionType;
    state->audio.samplesPerFrame = samplesPerFrame;
    state->audio.audioFormat = audioFormat;
    state->audio.sampleRate = sampleRate > 0 ? sampleRate : kDefaultAudioSampleRate;
    state->audio.channels = channels > 0 ? channels : kDefaultAudioChannels;
    state->audio.isMedia = isMedia;
    state->audio.usingScreen = usingScreen;
    LOGI(
        "Native audio config ct=%u spf=%u fmt=0x%llx rate=%u ch=%u isMedia=%d usingScreen=%d",
        static_cast<unsigned>(compressionType),
        static_cast<unsigned>(samplesPerFrame),
        static_cast<unsigned long long>(audioFormat),
        state->audio.sampleRate,
        state->audio.channels,
        isMedia ? 1 : 0,
        usingScreen ? 1 : 0);

    JniBridge::onAudioConfigCallback(
        compressionType,
        samplesPerFrame,
        audioFormat,
        state->audio.sampleRate,
        state->audio.channels,
        isMedia,
        usingScreen);
    maybeNotifyConnected(state);
}

void onAudioAccessUnit(
    void* context,
    const uint8_t* data,
    size_t size,
    uint32_t rtpTimestamp,
    uint64_t ntpLocalUs,
    uint64_t ntpRemoteUs,
    int syncStatus) {
    auto* state = static_cast<NativeServerState*>(context);
    if (state == nullptr) {
        return;
    }

    const bool clockLocked = syncStatus > 0;
    state->audio.accessUnitsSeen++;
    const uint64_t presentationTimeUs =
        ntpLocalUs > 0 ? ntpLocalUs : (static_cast<uint64_t>(rtpTimestamp) * 1000000ULL) / state->audio.sampleRate;

    if (ntpLocalUs > 0 || ntpRemoteUs > 0) {
        JniBridge::onAudioSyncCallback(
            rtpTimestamp,
            ntpRemoteUs,
            ntpLocalUs,
            !state->audio.syncSeen);
        state->audio.syncSeen = true;
    }

    if (state->audio.accessUnitsSeen <= 5 || state->audio.accessUnitsSeen % 50 == 0) {
        LOGI(
            "Native audio AU#=%llu size=%zu rtp=%u ptsUs=%llu locked=%d sync=%d",
            static_cast<unsigned long long>(state->audio.accessUnitsSeen),
            size,
            rtpTimestamp,
            static_cast<unsigned long long>(presentationTimeUs),
            clockLocked ? 1 : 0,
            state->audio.syncSeen ? 1 : 0);
    }

    JniBridge::onAudioAccessUnitCallback(data, size, rtpTimestamp, presentationTimeUs, clockLocked);
    maybeNotifyConnected(state);
}

void onAudioFlush(void* /* context */) {
    LOGI("Native audio flush");
    JniBridge::onAudioFlushCallback(-1);
}

void onVideoFrame(
    void* context,
    const uint8_t* data,
    size_t size,
    uint64_t ntpLocalUs,
    uint64_t /* ntpRemoteUs */,
    bool /* isH265 */,
    int /* nalCount */) {
    auto* state = static_cast<NativeServerState*>(context);
    if (state == nullptr) {
        return;
    }

    JniBridge::onVideoPayloadCallback(
        data,
        size,
        ntpLocalUs,
        containsIdrNalUnit(data, size));
    maybeNotifyConnected(state);
}

void resetCachedSessionState(NativeServerState* state) {
    if (state == nullptr) {
        return;
    }

    state->clientLabel.clear();
    state->width = 1920;
    state->height = 1080;
    state->transportConnected = false;
    state->javaConnectionNotified = false;
    state->audio = NativeAudioState{};
}

} // namespace

extern "C" {

JNIEXPORT jint JNI_OnLoad(JavaVM* vm, void* /* reserved */) {
    JniBridge::init(vm);
    LOGI("Native library loaded");
    return JNI_VERSION_1_6;
}

JNIEXPORT jstring JNICALL
Java_com_airplay_tv_protocol_AirPlayJniBridge_getVersionFromJNI(
        JNIEnv* env,
        jobject /* this */) {
    const std::string version = "AirPlay Native Library v2.0 (ux_raop_core)";
    return env->NewStringUTF(version.c_str());
}

JNIEXPORT jboolean JNICALL
Java_com_airplay_tv_protocol_AirPlayJniBridge_startRTSPServerNative(
        JNIEnv* env,
        jobject thiz,
        jint port) {
    if (g_state != nullptr) {
        LOGE("Server already exists");
        return JNI_FALSE;
    }

    auto state = std::make_unique<NativeServerState>();
    state->core = std::make_unique<UxRaopCore>();

    UxRaopDisplayInfo displayInfo{};
    UxRaopCallbacks callbacks{};
    callbacks.context = state.get();
    callbacks.onSessionConnected = &onSessionConnected;
    callbacks.onSessionDisconnected = &onSessionDisconnected;
    callbacks.onProtocolError = &onProtocolError;
    callbacks.onVideoConfig = &onVideoConfig;
    callbacks.onAudioConfig = &onAudioConfig;
    callbacks.onAudioAccessUnit = &onAudioAccessUnit;
    callbacks.onAudioFlush = &onAudioFlush;
    callbacks.onVideoFrame = &onVideoFrame;

    JniBridge::setCallbackObject(env, thiz);

    if (!state->core->start(static_cast<unsigned short>(port), kReceiverName, kReceiverId, displayInfo, callbacks)) {
        JniBridge::clearCallbackObject(env);
        return JNI_FALSE;
    }

    g_state = std::move(state);
    LOGI("ux_raop_core started on port %d", port);
    return JNI_TRUE;
}

JNIEXPORT void JNICALL
Java_com_airplay_tv_protocol_AirPlayJniBridge_stopRTSPServerNative(
        JNIEnv* env,
        jobject /* this */) {
    if (g_state == nullptr) {
        return;
    }

    if (g_state->core != nullptr) {
        g_state->core->stop();
    }
    g_state.reset();
    JniBridge::clearCallbackObject(env);
}

JNIEXPORT jboolean JNICALL
Java_com_airplay_tv_protocol_AirPlayJniBridge_isServerRunningNative(
        JNIEnv* /* env */,
        jobject /* this */) {
    return (g_state != nullptr && g_state->core != nullptr && g_state->core->isRunning()) ? JNI_TRUE : JNI_FALSE;
}

JNIEXPORT jstring JNICALL
Java_com_airplay_tv_protocol_AirPlayJniBridge_getClientIpNative(
        JNIEnv* env,
        jobject /* this */) {
    if (g_state == nullptr || g_state->clientLabel.empty()) {
        return env->NewStringUTF("");
    }
    return env->NewStringUTF(g_state->clientLabel.c_str());
}

JNIEXPORT jintArray JNICALL
Java_com_airplay_tv_protocol_AirPlayJniBridge_getVideoResolutionNative(
        JNIEnv* env,
        jobject /* this */) {
    jint values[2] = {1920, 1080};
    if (g_state != nullptr) {
        values[0] = g_state->width;
        values[1] = g_state->height;
    }

    jintArray result = env->NewIntArray(2);
    env->SetIntArrayRegion(result, 0, 2, values);
    return result;
}

JNIEXPORT jintArray JNICALL
Java_com_airplay_tv_protocol_AirPlayJniBridge_getAudioConfigNative(
        JNIEnv* env,
        jobject /* this */) {
    jint values[2] = {static_cast<jint>(kDefaultAudioSampleRate), static_cast<jint>(kDefaultAudioChannels)};
    if (g_state != nullptr) {
        values[0] = static_cast<jint>(g_state->audio.sampleRate);
        values[1] = static_cast<jint>(g_state->audio.channels);
    }

    jintArray result = env->NewIntArray(2);
    env->SetIntArrayRegion(result, 0, 2, values);
    return result;
}

JNIEXPORT void JNICALL
Java_com_airplay_tv_protocol_AirPlayJniBridge_updateAudioSessionConfigNative(
        JNIEnv*,
        jobject,
        jint,
        jint,
        jlong,
        jint,
        jint,
        jint,
        jint,
        jint,
        jint,
        jboolean,
        jboolean) {
}

JNIEXPORT jintArray JNICALL
Java_com_airplay_tv_protocol_AirPlayJniBridge_prepareAudioSessionNative(
        JNIEnv* env,
        jobject,
        jint,
        jint,
        jlong,
        jint,
        jint,
        jint,
        jint,
        jint preferredDataPort,
        jint preferredControlPort,
        jint preferredTimingPort,
        jboolean,
        jboolean) {
    jintArray result = env->NewIntArray(3);
    jint ports[3] = {preferredDataPort, preferredControlPort, preferredTimingPort};
    env->SetIntArrayRegion(result, 0, 3, ports);
    return result;
}

JNIEXPORT void JNICALL
Java_com_airplay_tv_protocol_AirPlayJniBridge_resetAudioSessionConfigNative(
        JNIEnv*,
        jobject) {
    if (g_state != nullptr) {
        g_state->audio = NativeAudioState{};
    }
}

JNIEXPORT void JNICALL
Java_com_airplay_tv_protocol_AirPlayJniBridge_resetSessionStateNative(
        JNIEnv*,
        jobject) {
    if (g_state != nullptr) {
        resetCachedSessionState(g_state.get());
    }
}

JNIEXPORT jbyteArray JNICALL
Java_com_airplay_tv_protocol_AirPlayJniBridge_decryptFairPlayAesKeyNative(
        JNIEnv*,
        jobject,
        jbyteArray) {
    return nullptr;
}

JNIEXPORT jint JNICALL
Java_com_airplay_tv_protocol_AirPlayJniBridge_startMirrorVideoServerNative(
        JNIEnv*,
        jobject) {
    return -1;
}

} // extern "C"
