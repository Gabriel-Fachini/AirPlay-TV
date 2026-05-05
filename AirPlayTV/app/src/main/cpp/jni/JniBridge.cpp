#include "JniBridge.h"
#include <android/log.h>

#define TAG "AirPlay:JniBridge"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, TAG, __VA_ARGS__)

JavaVM* JniBridge::g_jvm = nullptr;
jobject JniBridge::g_callbackObject = nullptr;

void JniBridge::init(JavaVM* vm) {
    g_jvm = vm;
}

void JniBridge::setCallbackObject(JNIEnv* env, jobject callbackObject) {
    if (g_callbackObject) {
        env->DeleteGlobalRef(g_callbackObject);
    }
    g_callbackObject = env->NewGlobalRef(callbackObject);
}

void JniBridge::clearCallbackObject(JNIEnv* env) {
    if (g_callbackObject) {
        env->DeleteGlobalRef(g_callbackObject);
        g_callbackObject = nullptr;
    }
}

jobject JniBridge::getCallbackObject() {
    return g_callbackObject;
}

JNIEnv* JniBridge::getJNIEnv() {
    if (!g_jvm) return nullptr;
    JNIEnv* env = nullptr;
    if (g_jvm->GetEnv((void**)&env, JNI_VERSION_1_6) == JNI_EDETACHED) {
        g_jvm->AttachCurrentThread(&env, nullptr);
    }
    return env;
}

void JniBridge::onConnectionCallback(const std::string& clientIp) {
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

void JniBridge::onDisconnectionCallback() {
    JNIEnv* env = getJNIEnv();
    if (!env || !g_callbackObject) return;

    jclass cls = env->GetObjectClass(g_callbackObject);
    jmethodID method = env->GetMethodID(cls, "onClientDisconnected", "()V");
    
    if (method) {
        env->CallVoidMethod(g_callbackObject, method);
    }
    
    env->DeleteLocalRef(cls);
}

void JniBridge::onActivityCallback(const std::string& methodName) {
    JNIEnv* env = getJNIEnv();
    if (!env || !g_callbackObject) return;

    jclass cls = env->GetObjectClass(g_callbackObject);
    jmethodID method = env->GetMethodID(cls, "onControlRequestHandled", "(Ljava/lang/String;)V");

    if (method) {
        jstring jMethodName = env->NewStringUTF(methodName.c_str());
        env->CallVoidMethod(g_callbackObject, method, jMethodName);
        env->DeleteLocalRef(jMethodName);
    }

    env->DeleteLocalRef(cls);
}

void JniBridge::onAudioFlushCallback(int nextSequenceNumber) {
    JNIEnv* env = getJNIEnv();
    if (!env || !g_callbackObject) return;

    jclass cls = env->GetObjectClass(g_callbackObject);
    jmethodID method = env->GetMethodID(cls, "onAudioFlush", "(I)V");

    if (method) {
        env->CallVoidMethod(g_callbackObject, method, static_cast<jint>(nextSequenceNumber));
    }

    env->DeleteLocalRef(cls);
}

void JniBridge::onErrorCallback(const std::string& error) {
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

void JniBridge::onVideoConfigCallback(
        uint16_t width,
        uint16_t height,
        const uint8_t* sps,
        size_t spsSize,
        const uint8_t* pps,
        size_t ppsSize) {
    JNIEnv* env = getJNIEnv();
    if (!env || !g_callbackObject) return;

    jclass cls = env->GetObjectClass(g_callbackObject);
    jmethodID method = env->GetMethodID(cls, "onVideoConfig", "(II[B[B)V");

    if (method) {
        jbyteArray jSps = env->NewByteArray(static_cast<jsize>(spsSize));
        jbyteArray jPps = env->NewByteArray(static_cast<jsize>(ppsSize));
        env->SetByteArrayRegion(jSps, 0, static_cast<jsize>(spsSize), reinterpret_cast<const jbyte*>(sps));
        env->SetByteArrayRegion(jPps, 0, static_cast<jsize>(ppsSize), reinterpret_cast<const jbyte*>(pps));
        env->CallVoidMethod(
            g_callbackObject,
            method,
            static_cast<jint>(width),
            static_cast<jint>(height),
            jSps,
            jPps);
        env->DeleteLocalRef(jSps);
        env->DeleteLocalRef(jPps);
    }

    env->DeleteLocalRef(cls);
}

void JniBridge::onVideoPayloadCallback(const uint8_t* data, size_t size, uint64_t ptsUs, bool isKeyFrame) {
    JNIEnv* env = getJNIEnv();
    if (!env || !g_callbackObject) return;

    jclass cls = env->GetObjectClass(g_callbackObject);
    jmethodID method = env->GetMethodID(cls, "onVideoPayload", "([BJZ)V");

    if (method) {
        jbyteArray jData = env->NewByteArray(static_cast<jsize>(size));
        env->SetByteArrayRegion(jData, 0, static_cast<jsize>(size), reinterpret_cast<const jbyte*>(data));
        env->CallVoidMethod(
            g_callbackObject,
            method,
            jData,
            static_cast<jlong>(ptsUs),
            static_cast<jboolean>(isKeyFrame));
        env->DeleteLocalRef(jData);
    }

    env->DeleteLocalRef(cls);
}

void JniBridge::onAudioConfigCallback(
        uint8_t compressionType,
        uint16_t samplesPerFrame,
        uint64_t audioFormat,
        uint32_t sampleRate,
        uint32_t channels,
        bool isMedia,
        bool usingScreen) {
    JNIEnv* env = getJNIEnv();
    if (!env || !g_callbackObject) return;

    jclass cls = env->GetObjectClass(g_callbackObject);
    jmethodID method = env->GetMethodID(cls, "onAudioConfig", "(IIJIIZZ)V");

    if (method) {
        env->CallVoidMethod(
            g_callbackObject,
            method,
            static_cast<jint>(compressionType),
            static_cast<jint>(samplesPerFrame),
            static_cast<jlong>(audioFormat),
            static_cast<jint>(sampleRate),
            static_cast<jint>(channels),
            static_cast<jboolean>(isMedia),
            static_cast<jboolean>(usingScreen));
    }

    env->DeleteLocalRef(cls);
}

void JniBridge::onAudioAccessUnitCallback(
        const uint8_t* data,
        size_t size,
        uint32_t rtpTimestamp,
        uint64_t presentationTimeUs,
        bool clockLocked) {
    JNIEnv* env = getJNIEnv();
    if (!env || !g_callbackObject) return;

    jclass cls = env->GetObjectClass(g_callbackObject);
    jmethodID method = env->GetMethodID(cls, "onAudioAccessUnit", "([BJJZ)V");

    if (method) {
        jbyteArray jData = env->NewByteArray(static_cast<jsize>(size));
        env->SetByteArrayRegion(jData, 0, static_cast<jsize>(size), reinterpret_cast<const jbyte*>(data));
        env->CallVoidMethod(
            g_callbackObject,
            method,
            jData,
            static_cast<jlong>(rtpTimestamp),
            static_cast<jlong>(presentationTimeUs),
            static_cast<jboolean>(clockLocked));
        env->DeleteLocalRef(jData);
    }

    env->DeleteLocalRef(cls);
}

void JniBridge::onVideoDataCallback(const uint8_t* data, size_t size, uint32_t timestamp) {
    JNIEnv* env = getJNIEnv();
    if (!env || !g_callbackObject) return;

    jclass cls = env->GetObjectClass(g_callbackObject);
    jmethodID method = env->GetMethodID(cls, "onVideoData", "([BJ)V");
    
    if (method) {
        jbyteArray jData = env->NewByteArray(size);
        env->SetByteArrayRegion(jData, 0, size, (const jbyte*)data);
        env->CallVoidMethod(g_callbackObject, method, jData, (jlong)timestamp);
        env->DeleteLocalRef(jData);
    }
    
    env->DeleteLocalRef(cls);
}

void JniBridge::onAudioDataCallback(const uint8_t* data, size_t size, uint32_t timestamp) {
    JNIEnv* env = getJNIEnv();
    if (!env || !g_callbackObject) return;

    jclass cls = env->GetObjectClass(g_callbackObject);
    jmethodID method = env->GetMethodID(cls, "onAudioData", "([BJ)V");
    
    if (method) {
        jbyteArray jData = env->NewByteArray(size);
        env->SetByteArrayRegion(jData, 0, size, (const jbyte*)data);
        env->CallVoidMethod(g_callbackObject, method, jData, (jlong)timestamp);
        env->DeleteLocalRef(jData);
    }
    
    env->DeleteLocalRef(cls);
}

void JniBridge::onAudioSyncCallback(uint32_t rtpSync, uint64_t remoteNtpUs, uint64_t localNtpUs, bool initial) {
    JNIEnv* env = getJNIEnv();
    if (!env || !g_callbackObject) return;

    jclass cls = env->GetObjectClass(g_callbackObject);
    jmethodID method = env->GetMethodID(cls, "onAudioSync", "(JJJZ)V");

    if (method) {
        env->CallVoidMethod(
            g_callbackObject,
            method,
            static_cast<jlong>(rtpSync),
            static_cast<jlong>(remoteNtpUs),
            static_cast<jlong>(localNtpUs),
            static_cast<jboolean>(initial));
    }

    env->DeleteLocalRef(cls);
}

void JniBridge::onMirroringVideoPacketCallback(int payloadType, const uint8_t* data, size_t size) {
    JNIEnv* env = getJNIEnv();
    if (!env || !g_callbackObject) return;

    jclass cls = env->GetObjectClass(g_callbackObject);
    jmethodID method = env->GetMethodID(cls, "onMirroringVideoPacket", "(I[B)V");

    if (method) {
        jbyteArray jData = env->NewByteArray(static_cast<jsize>(size));
        env->SetByteArrayRegion(jData, 0, static_cast<jsize>(size), reinterpret_cast<const jbyte*>(data));
        env->CallVoidMethod(g_callbackObject, method, static_cast<jint>(payloadType), jData);
        env->DeleteLocalRef(jData);
    }

    env->DeleteLocalRef(cls);
}

void JniBridge::onPhotoPlaybackCallback(
        const std::string& clientIp,
        const std::string& sessionId,
        const std::string& assetKey,
        const std::string& transition,
        const std::vector<uint8_t>& imageData,
        bool isSlideshow) {
    JNIEnv* env = getJNIEnv();
    if (!env || !g_callbackObject) return;

    jclass cls = env->GetObjectClass(g_callbackObject);
    jmethodID method = env->GetMethodID(
        cls,
        "onPhotoPlaybackSession",
        "(Ljava/lang/String;Ljava/lang/String;Ljava/lang/String;Ljava/lang/String;[BZ)V");

    if (method) {
        jstring jClientIp = env->NewStringUTF(clientIp.c_str());
        jstring jSessionId = env->NewStringUTF(sessionId.c_str());
        jstring jAssetKey = env->NewStringUTF(assetKey.c_str());
        jstring jTransition = transition.empty() ? nullptr : env->NewStringUTF(transition.c_str());
        jbyteArray jData = env->NewByteArray(static_cast<jsize>(imageData.size()));
        env->SetByteArrayRegion(
            jData,
            0,
            static_cast<jsize>(imageData.size()),
            reinterpret_cast<const jbyte*>(imageData.data()));
        env->CallVoidMethod(
            g_callbackObject,
            method,
            jClientIp,
            jSessionId,
            jAssetKey,
            jTransition,
            jData,
            static_cast<jboolean>(isSlideshow));
        env->DeleteLocalRef(jClientIp);
        env->DeleteLocalRef(jSessionId);
        env->DeleteLocalRef(jAssetKey);
        if (jTransition != nullptr) {
            env->DeleteLocalRef(jTransition);
        }
        env->DeleteLocalRef(jData);
    }

    env->DeleteLocalRef(cls);
}

void JniBridge::onSlideshowPlaybackCallback(
        const std::string& clientIp,
        const std::string& sessionId,
        const std::string& theme,
        int slideDurationSeconds,
        const std::string& state) {
    JNIEnv* env = getJNIEnv();
    if (!env || !g_callbackObject) return;

    jclass cls = env->GetObjectClass(g_callbackObject);
    jmethodID method = env->GetMethodID(
        cls,
        "onSlideshowPlaybackState",
        "(Ljava/lang/String;Ljava/lang/String;Ljava/lang/String;ILjava/lang/String;)V");

    if (method) {
        jstring jClientIp = env->NewStringUTF(clientIp.c_str());
        jstring jSessionId = env->NewStringUTF(sessionId.c_str());
        jstring jTheme = theme.empty() ? nullptr : env->NewStringUTF(theme.c_str());
        jstring jState = env->NewStringUTF(state.c_str());
        env->CallVoidMethod(
            g_callbackObject,
            method,
            jClientIp,
            jSessionId,
            jTheme,
            static_cast<jint>(slideDurationSeconds),
            jState);
        env->DeleteLocalRef(jClientIp);
        env->DeleteLocalRef(jSessionId);
        if (jTheme != nullptr) {
            env->DeleteLocalRef(jTheme);
        }
        env->DeleteLocalRef(jState);
    }

    env->DeleteLocalRef(cls);
}

void JniBridge::onMediaStopCallback(const std::string& sessionId) {
    JNIEnv* env = getJNIEnv();
    if (!env || !g_callbackObject) return;

    jclass cls = env->GetObjectClass(g_callbackObject);
    jmethodID method = env->GetMethodID(cls, "onMediaPlaybackStopped", "(Ljava/lang/String;)V");

    if (method) {
        jstring jSessionId = env->NewStringUTF(sessionId.c_str());
        env->CallVoidMethod(g_callbackObject, method, jSessionId);
        env->DeleteLocalRef(jSessionId);
    }

    env->DeleteLocalRef(cls);
}

std::vector<uint8_t> JniBridge::invokeByteArrayCallback(
        const char* methodName,
        const uint8_t* data,
        size_t size,
        bool* ok) {
    *ok = false;

    JNIEnv* env = getJNIEnv();
    if (!env || !g_callbackObject) {
        LOGE("Cannot invoke %s: missing JNI environment or callback object", methodName);
        return {};
    }

    jclass cls = env->GetObjectClass(g_callbackObject);
    jmethodID method = env->GetMethodID(cls, methodName, "([B)[B");
    if (!method) {
        LOGE("Cannot find Java callback %s", methodName);
        env->DeleteLocalRef(cls);
        return {};
    }

    jbyteArray request = env->NewByteArray(static_cast<jsize>(size));
    if (request == nullptr) {
        LOGE("Failed to allocate byte array for %s", methodName);
        env->DeleteLocalRef(cls);
        return {};
    }

    env->SetByteArrayRegion(request, 0, static_cast<jsize>(size), reinterpret_cast<const jbyte*>(data));
    auto response = static_cast<jbyteArray>(env->CallObjectMethod(g_callbackObject, method, request));
    env->DeleteLocalRef(request);

    if (env->ExceptionCheck()) {
        env->ExceptionDescribe();
        env->ExceptionClear();
        LOGE("Java callback %s threw an exception", methodName);
        env->DeleteLocalRef(cls);
        return {};
    }

    if (response == nullptr) {
        LOGE("Java callback %s returned null", methodName);
        env->DeleteLocalRef(cls);
        return {};
    }

    std::vector<uint8_t> result;
    const jsize length = env->GetArrayLength(response);
    result.resize(static_cast<size_t>(length));
    if (length > 0) {
        env->GetByteArrayRegion(
                response,
                0,
                length,
                reinterpret_cast<jbyte*>(result.data()));
    }
    env->DeleteLocalRef(response);

    env->DeleteLocalRef(cls);
    *ok = true;
    return result;
}
