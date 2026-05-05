#pragma once

#include "network/rtp_receiver.h"
#include "network/mirror_server.h"
#include "network/ntp_client.h"
#include <memory>
#include <functional>
#include <string>

class MediaOrchestrator {
public:
    struct AudioSessionConfig {
        int compressionType = 0;
        int samplesPerFrame = 0;
        uint64_t audioFormat = 0;
        int sampleRate = 44100;
        int channels = 2;
        int remoteControlPort = 0;
        int remoteTimingPort = 0;
        int localDataPort = 7100;
        int localControlPort = 6001;
        int localTimingPort = 7002;
        bool isMedia = false;
        bool usingScreen = false;
    };

    MediaOrchestrator();
    ~MediaOrchestrator();

    void startRTPReceiver();
    void stopRTPReceiver();
    bool isRTPReceiverRunning() const { return rtpRunning_; }

    int startMirrorVideoServer();
    void stopMirrorVideoServer();
    bool isMirrorVideoRunning() const { return mirrorVideoRunning_; }

    void startNTPClient(const std::string& clientIp);
    void stopNTPClient();

    AudioSessionConfig prepareAudioSession(const AudioSessionConfig& config, const std::string& clientIp);
    void flushAudio(uint16_t nextSequenceNumber, bool hasNextSequenceNumber);
    void updateAudioSessionConfig(const AudioSessionConfig& config);
    void resetAudioSessionConfig();
    AudioSessionConfig getAudioSessionConfig() const { return audioSessionConfig_; }
    int getAudioSampleRate() const { return audioSampleRate_; }
    int getAudioChannels() const { return audioChannels_; }

    // Setters for callbacks
    void setVideoDataCallback(std::function<void(const uint8_t*, size_t, uint32_t)> cb) { onVideoData_ = std::move(cb); }
    void setAudioDataCallback(std::function<void(const uint8_t*, size_t, uint32_t)> cb) { onAudioData_ = std::move(cb); }
    void setAudioSyncCallback(std::function<void(uint32_t, uint64_t, uint64_t, bool)> cb) { onAudioSync_ = std::move(cb); }
    void setErrorCallback(std::function<void(const std::string&)> cb) { onError_ = std::move(cb); }
    void setMirroringVideoPacketCallback(std::function<void(int, const uint8_t*, size_t)> cb) { onMirroringVideoPacket_ = std::move(cb); }

private:
    std::unique_ptr<RTPReceiver> rtpReceiver_;
    std::unique_ptr<MirrorServer> mirrorServer_;
    std::unique_ptr<NTPClient> ntpClient_;

    AudioSessionConfig audioSessionConfig_;
    int audioSampleRate_ = 44100;
    int audioChannels_ = 2;

    bool rtpRunning_ = false;
    bool mirrorVideoRunning_ = false;
    int mirrorVideoPort_ = 0;

    std::function<void(const uint8_t*, size_t, uint32_t)> onVideoData_;
    std::function<void(const uint8_t*, size_t, uint32_t)> onAudioData_;
    std::function<void(uint32_t, uint64_t, uint64_t, bool)> onAudioSync_;
    std::function<void(const std::string&)> onError_;
    std::function<void(int, const uint8_t*, size_t)> onMirroringVideoPacket_;
};
