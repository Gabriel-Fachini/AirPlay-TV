#pragma once

#include <cstddef>
#include <cstdint>
#include <memory>
#include <string>

struct UxRaopDisplayInfo {
    uint16_t width = 1920;
    uint16_t height = 1080;
    uint8_t refreshRate = 60;
    uint8_t maxFps = 30;
    uint8_t overscanned = 0;
};

struct UxRaopAudioFormat {
    uint8_t compressionType = 0;
    uint16_t samplesPerFrame = 0;
    uint64_t audioFormat = 0;
    bool isMedia = false;
    bool usingScreen = false;
};

struct UxRaopCallbacks {
    void* context = nullptr;
    void (*onSessionConnected)(void* context, const char* clientLabel) = nullptr;
    void (*onSessionDisconnected)(void* context) = nullptr;
    void (*onProtocolError)(void* context, const char* message) = nullptr;
    void (*onVideoConfig)(
        void* context,
        uint16_t width,
        uint16_t height,
        const uint8_t* sps,
        size_t spsSize,
        const uint8_t* pps,
        size_t ppsSize) = nullptr;
    void (*onAudioConfig)(
        void* context,
        uint8_t compressionType,
        uint16_t samplesPerFrame,
        uint64_t audioFormat,
        uint32_t sampleRate,
        uint32_t channels,
        bool isMedia,
        bool usingScreen) = nullptr;
    void (*onAudioAccessUnit)(
        void* context,
        const uint8_t* data,
        size_t size,
        uint32_t rtpTimestamp,
        uint64_t ntpLocalUs,
        uint64_t ntpRemoteUs,
        int syncStatus) = nullptr;
    void (*onAudioFlush)(void* context) = nullptr;
    void (*onVideoFrame)(
        void* context,
        const uint8_t* data,
        size_t size,
        uint64_t ntpLocalUs,
        uint64_t ntpRemoteUs,
        bool isH265,
        int nalCount) = nullptr;
};

class UxRaopCore {
public:
    UxRaopCore();
    ~UxRaopCore();

    bool start(
        unsigned short rtspPort,
        const std::string& receiverName,
        const std::string& receiverId,
        const UxRaopDisplayInfo& displayInfo,
        const UxRaopCallbacks& callbacks);
    void stop();
    bool isRunning() const;

    unsigned short port() const;
    const std::string& clientLabel() const;

private:
    struct Impl;
    std::unique_ptr<Impl> impl_;
};
