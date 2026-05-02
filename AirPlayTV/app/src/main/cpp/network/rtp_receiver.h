#ifndef RTP_RECEIVER_H
#define RTP_RECEIVER_H

#include <functional>
#include <thread>
#include <atomic>
#include <cstdint>
#include <cstddef>

/**
 * RTP/UDP receiver for AirPlay audio/video streams
 * Manages three UDP sockets: data, control, and timing
 */
class RTPReceiver {
public:
    using VideoDataCallback = std::function<void(const uint8_t* data, size_t size, uint32_t timestamp)>;
    using AudioDataCallback = std::function<void(const uint8_t* data, size_t size, uint32_t timestamp)>;
    using ErrorCallback = std::function<void(const std::string& error)>;

    RTPReceiver();
    ~RTPReceiver();

    // Start receiving on specified ports
    bool start(int dataPort, int controlPort, int timingPort);
    void stop();
    bool isRunning() const { return running_; }

    // Set callbacks for received data
    void setVideoDataCallback(VideoDataCallback callback) { onVideoData_ = callback; }
    void setAudioDataCallback(AudioDataCallback callback) { onAudioData_ = callback; }
    void setErrorCallback(ErrorCallback callback) { onError_ = callback; }

private:
    void receiveDataThread();
    void receiveControlThread();
    void receiveTimingThread();
    void processRTPPacket(const uint8_t* data, size_t size);
    bool bindUDPSocket(int socket, int port);

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
    ErrorCallback onError_;
};

#endif // RTP_RECEIVER_H
