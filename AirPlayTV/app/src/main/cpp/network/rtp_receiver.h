#ifndef RTP_RECEIVER_H
#define RTP_RECEIVER_H

#include <functional>
#include <thread>
#include <atomic>
#include <cstdint>
#include <cstddef>
#include <array>
#include <vector>

/**
 * RTP/UDP receiver for AirPlay audio/video streams
 * Manages three UDP sockets: data, control, and timing
 */
class RTPReceiver {
public:
    using VideoDataCallback = std::function<void(const uint8_t* data, size_t size, uint32_t timestamp)>;
    using AudioDataCallback = std::function<void(const uint8_t* data, size_t size, uint32_t timestamp)>;
    using AudioSyncCallback = std::function<void(uint32_t rtpSync, uint64_t remoteNtpUs, uint64_t localNtpUs, bool initial)>;
    using ErrorCallback = std::function<void(const std::string& error)>;

    RTPReceiver();
    ~RTPReceiver();

    // Start receiving on specified ports
    bool start(int dataPort, int controlPort, int timingPort);
    void stop();
    bool isRunning() const { return running_; }
    void setAudioConfig(int compressionType, int sampleRate);

    // Set callbacks for received data
    void setVideoDataCallback(VideoDataCallback callback) { onVideoData_ = callback; }
    void setAudioDataCallback(AudioDataCallback callback) { onAudioData_ = callback; }
    void setAudioSyncCallback(AudioSyncCallback callback) { onAudioSync_ = callback; }
    void setErrorCallback(ErrorCallback callback) { onError_ = callback; }

private:
    void receiveDataThread();
    void receiveControlThread();
    void receiveTimingThread();
    void processRTPPacket(const uint8_t* data, size_t size);
    void enqueueAudioPacket(uint16_t sequenceNumber, uint32_t timestamp, const uint8_t* payload, size_t payloadSize);
    void drainAudioPackets();
    void resetAudioPacketBuffer();
    bool bindUDPSocket(int socket, int port);
    static short seqnumCmp(uint16_t left, uint16_t right);

    struct AudioPacketEntry {
        bool filled = false;
        uint16_t sequenceNumber = 0;
        uint32_t timestamp = 0;
        std::vector<uint8_t> payload;
    };

    // Sockets
    int dataSocket_;
    int controlSocket_;
    int timingSocket_;

    // Threads
    std::thread dataThread_;
    std::thread controlThread_;
    std::thread timingThread_;
    std::atomic<bool> running_;

    // Callbacks
    VideoDataCallback onVideoData_;
    AudioDataCallback onAudioData_;
    AudioSyncCallback onAudioSync_;
    ErrorCallback onError_;
    bool hasReceivedAudioSync_;
    int compressionType_;
    int sampleRate_;
    bool audioBufferEmpty_;
    uint16_t firstAudioSequence_;
    uint16_t lastAudioSequence_;
    uint64_t bufferedAudioPackets_;
    uint64_t duplicateAudioPackets_;
    uint64_t droppedAudioPackets_;
    std::array<AudioPacketEntry, 32> audioPacketBuffer_;

    static uint64_t convertNtpToUnixMicros(uint64_t ntpTimestamp);
    static uint64_t getCurrentUnixMicros();
};

#endif // RTP_RECEIVER_H
