#include "rtp_receiver.h"
#include "network_utils.h"
#include <android/log.h>
#include <sys/socket.h>
#include <netinet/in.h>
#include <arpa/inet.h>
#include <unistd.h>
#include <cstring>
#include <cerrno>
#include <chrono>

#define TAG "AirPlayRTP"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, TAG, __VA_ARGS__)
#define LOGW(...) __android_log_print(ANDROID_LOG_WARN, TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, TAG, __VA_ARGS__)

RTPReceiver::RTPReceiver()
    : dataSocket_(-1)
    , controlSocket_(-1)
    , timingSocket_(-1)
    , running_(false)
    , hasReceivedAudioSync_(false)
    , compressionType_(0)
    , sampleRate_(44100)
    , audioBufferEmpty_(true)
    , firstAudioSequence_(0)
    , lastAudioSequence_(0)
    , bufferedAudioPackets_(0)
    , duplicateAudioPackets_(0)
    , droppedAudioPackets_(0)
{
    resetAudioPacketBuffer();
    LOGI("RTPReceiver created");
}

RTPReceiver::~RTPReceiver() {
    stop();
    LOGI("RTPReceiver destroyed");
}

bool RTPReceiver::bindUDPSocket(int socket, int port) {
    struct sockaddr_in addr;
    memset(&addr, 0, sizeof(addr));
    addr.sin_family = AF_INET;
    addr.sin_addr.s_addr = INADDR_ANY;
    addr.sin_port = htons(port);

    if (bind(socket, (struct sockaddr*)&addr, sizeof(addr)) < 0) {
        LOGE("Failed to bind UDP socket to port %d: %s", port, strerror(errno));
        return false;
    }

    LOGI("UDP socket bound to port %d", port);
    return true;
}

bool RTPReceiver::start(int dataPort, int controlPort, int timingPort) {
    if (running_) {
        LOGW("RTP receiver already running");
        return false;
    }

    hasReceivedAudioSync_ = false;
    resetAudioPacketBuffer();

    LOGI("Starting RTP receiver (data=%d, control=%d, timing=%d)", 
         dataPort, controlPort, timingPort);

    // Create UDP sockets
    dataSocket_ = socket(AF_INET, SOCK_DGRAM, 0);
    if (dataSocket_ < 0) {
        LOGE("Failed to create data socket: %s", strerror(errno));
        if (onError_) onError_("Failed to create data socket");
        return false;
    }

    controlSocket_ = socket(AF_INET, SOCK_DGRAM, 0);
    if (controlSocket_ < 0) {
        LOGE("Failed to create control socket: %s", strerror(errno));
        close(dataSocket_);
        dataSocket_ = -1;
        if (onError_) onError_("Failed to create control socket");
        return false;
    }

    timingSocket_ = socket(AF_INET, SOCK_DGRAM, 0);
    if (timingSocket_ < 0) {
        LOGE("Failed to create timing socket: %s", strerror(errno));
        close(dataSocket_);
        close(controlSocket_);
        dataSocket_ = -1;
        controlSocket_ = -1;
        if (onError_) onError_("Failed to create timing socket");
        return false;
    }

    // Set timeout to avoid blocking indefinitely
    NetworkUtils::setSocketTimeout(dataSocket_, 1);
    NetworkUtils::setSocketTimeout(controlSocket_, 1);
    NetworkUtils::setSocketTimeout(timingSocket_, 1);

    // Bind to ports
    if (!bindUDPSocket(dataSocket_, dataPort)) {
        close(dataSocket_);
        close(controlSocket_);
        close(timingSocket_);
        dataSocket_ = -1;
        controlSocket_ = -1;
        timingSocket_ = -1;
        if (onError_) onError_("Failed to bind data socket");
        return false;
    }

    if (!bindUDPSocket(controlSocket_, controlPort)) {
        close(dataSocket_);
        close(controlSocket_);
        close(timingSocket_);
        dataSocket_ = -1;
        controlSocket_ = -1;
        timingSocket_ = -1;
        if (onError_) onError_("Failed to bind control socket");
        return false;
    }

    if (!bindUDPSocket(timingSocket_, timingPort)) {
        close(dataSocket_);
        close(controlSocket_);
        close(timingSocket_);
        dataSocket_ = -1;
        controlSocket_ = -1;
        timingSocket_ = -1;
        if (onError_) onError_("Failed to bind timing socket");
        return false;
    }

    // Start receiver threads
    running_ = true;
    dataThread_ = std::thread(&RTPReceiver::receiveDataThread, this);
    controlThread_ = std::thread(&RTPReceiver::receiveControlThread, this);
    timingThread_ = std::thread(&RTPReceiver::receiveTimingThread, this);

    LOGI("RTP receiver started successfully");
    return true;
}

void RTPReceiver::stop() {
    if (!running_) {
        return;
    }

    LOGI("Stopping RTP receiver");
    running_ = false;

    // Close sockets to unblock recv()
    if (dataSocket_ >= 0) {
        shutdown(dataSocket_, SHUT_RDWR);
        close(dataSocket_);
        dataSocket_ = -1;
    }

    if (controlSocket_ >= 0) {
        shutdown(controlSocket_, SHUT_RDWR);
        close(controlSocket_);
        controlSocket_ = -1;
    }

    if (timingSocket_ >= 0) {
        shutdown(timingSocket_, SHUT_RDWR);
        close(timingSocket_);
        timingSocket_ = -1;
    }

    // Wait for threads to finish
    if (dataThread_.joinable()) {
        dataThread_.join();
    }

    if (controlThread_.joinable()) {
        controlThread_.join();
    }

    if (timingThread_.joinable()) {
        timingThread_.join();
    }

    if (bufferedAudioPackets_ > 0 || duplicateAudioPackets_ > 0 || droppedAudioPackets_ > 0) {
        LOGI("Audio buf valid=%llu dup=%llu drop=%llu",
             (unsigned long long)bufferedAudioPackets_,
             (unsigned long long)duplicateAudioPackets_,
             (unsigned long long)droppedAudioPackets_);
    }

    LOGI("RTP receiver stopped");
}

void RTPReceiver::setAudioConfig(int compressionType, int sampleRate) {
    compressionType_ = compressionType;
    sampleRate_ = sampleRate > 0 ? sampleRate : 44100;
}

void RTPReceiver::receiveDataThread() {
    uint8_t buffer[2048];
    uint64_t totalPackets = 0;
    uint64_t totalBytes = 0;
    
    struct sockaddr_in senderAddr;
    socklen_t senderLen = sizeof(senderAddr);

    while (running_) {
        ssize_t bytesRead = recvfrom(dataSocket_, buffer, sizeof(buffer), 0,
                                     (struct sockaddr*)&senderAddr, &senderLen);
        
        if (bytesRead < 0) {
            if (errno == EAGAIN || errno == EWOULDBLOCK) {
                // Timeout, continue
                continue;
            }
            if (running_) {
                LOGE("Data socket recv error: %s", strerror(errno));
            }
            break;
        }

        if (bytesRead > 0) {
            totalPackets++;
            totalBytes += bytesRead;
            
            // Log first packet to see source
            if (totalPackets == 1) {
                LOGI("First RTP packet received from %s:%d (%zd bytes)",
                     inet_ntoa(senderAddr.sin_addr),
                     ntohs(senderAddr.sin_port),
                     bytesRead);
            }
            
            processRTPPacket(buffer, bytesRead);
        }
    }

    if (totalPackets > 0) {
        LOGI("Data thread ended (%llu packets, %llu bytes)",
             (unsigned long long)totalPackets,
             (unsigned long long)totalBytes);
    }
}

void RTPReceiver::receiveControlThread() {
    uint8_t buffer[512];
    struct sockaddr_in senderAddr;
    socklen_t senderLen = sizeof(senderAddr);
    bool firstPacket = true;

    while (running_) {
        ssize_t bytesRead = recvfrom(controlSocket_, buffer, sizeof(buffer), 0,
                                     (struct sockaddr*)&senderAddr, &senderLen);
        
        if (bytesRead < 0) {
            if (errno == EAGAIN || errno == EWOULDBLOCK) {
                continue;
            }
            if (running_) {
                LOGE("Control socket recv error: %s", strerror(errno));
            }
            break;
        }

        if (bytesRead > 0) {
            if (firstPacket) {
                LOGI("First RTCP packet received from %s:%d (%zd bytes)",
                     inet_ntoa(senderAddr.sin_addr),
                     ntohs(senderAddr.sin_port),
                     bytesRead);
                firstPacket = false;
            }

            const uint8_t packetType = buffer[1] & 0x7F;
            if (packetType == 0x54 && bytesRead >= 20 && onAudioSync_) {
                const bool initial = !hasReceivedAudioSync_;
                hasReceivedAudioSync_ = true;

                const uint32_t rtpSync =
                    (static_cast<uint32_t>(buffer[4]) << 24) |
                    (static_cast<uint32_t>(buffer[5]) << 16) |
                    (static_cast<uint32_t>(buffer[6]) << 8) |
                    static_cast<uint32_t>(buffer[7]);
                const uint64_t remoteNtpRaw =
                    (static_cast<uint64_t>(buffer[8]) << 56) |
                    (static_cast<uint64_t>(buffer[9]) << 48) |
                    (static_cast<uint64_t>(buffer[10]) << 40) |
                    (static_cast<uint64_t>(buffer[11]) << 32) |
                    (static_cast<uint64_t>(buffer[12]) << 24) |
                    (static_cast<uint64_t>(buffer[13]) << 16) |
                    (static_cast<uint64_t>(buffer[14]) << 8) |
                    static_cast<uint64_t>(buffer[15]);

                onAudioSync_(
                    rtpSync,
                    convertNtpToUnixMicros(remoteNtpRaw),
                    getCurrentUnixMicros(),
                    initial);
            }
        }
    }

    if (!firstPacket) {
        LOGI("Control thread ended");
    }
}

void RTPReceiver::receiveTimingThread() {
    uint8_t buffer[512];
    struct sockaddr_in senderAddr;
    socklen_t senderLen = sizeof(senderAddr);
    bool firstPacket = true;

    while (running_) {
        ssize_t bytesRead = recvfrom(timingSocket_, buffer, sizeof(buffer), 0,
                                     (struct sockaddr*)&senderAddr, &senderLen);
        
        if (bytesRead < 0) {
            if (errno == EAGAIN || errno == EWOULDBLOCK) {
                continue;
            }
            if (running_) {
                LOGE("Timing socket recv error: %s", strerror(errno));
            }
            break;
        }

        if (bytesRead > 0) {
            if (firstPacket) {
                LOGI("First timing packet received from %s:%d (%zd bytes)",
                     inet_ntoa(senderAddr.sin_addr),
                     ntohs(senderAddr.sin_port),
                     bytesRead);
                firstPacket = false;
            }
        }
    }

    if (!firstPacket) {
        LOGI("Timing thread ended");
    }
}

void RTPReceiver::processRTPPacket(const uint8_t* data, size_t size) {
    // Check minimum size (RTP header = 12 bytes)
    if (size < 12) {
        LOGW("RTP packet too small: %zu bytes", size);
        return;
    }

    // Parse RTP header (RFC 3550)
    uint8_t version = (data[0] >> 6) & 0x03;
    uint8_t padding = (data[0] >> 5) & 0x01;
    uint8_t extension = (data[0] >> 4) & 0x01;
    uint8_t csrcCount = data[0] & 0x0F;
    uint16_t sequenceNumber = (data[2] << 8) | data[3];
    uint32_t timestamp = (data[4] << 24) | (data[5] << 16) | (data[6] << 8) | data[7];

    if (version != 2) {
        droppedAudioPackets_++;
        return;
    }

    // Calculate payload offset
    size_t headerSize = 12 + (csrcCount * 4);
    
    if (extension) {
        if (size < headerSize + 4) {
            LOGW("RTP packet with extension too small");
            return;
        }
        uint16_t extLength = (data[headerSize + 2] << 8) | data[headerSize + 3];
        headerSize += 4 + (extLength * 4);
    }

    if (size < headerSize) {
        LOGW("RTP packet header size mismatch");
        return;
    }

    const uint8_t* payload = data + headerSize;
    size_t payloadSize = size - headerSize;

    // Remove padding if present
    if (padding && payloadSize > 0) {
        uint8_t paddingLength = payload[payloadSize - 1];
        if (paddingLength <= payloadSize) {
            payloadSize -= paddingLength;
        }
    }

    if (payloadSize == 0) {
        return;
    }

    if (compressionType_ == 8 &&
        payloadSize == 4 &&
        payload[0] == 0x00 &&
        payload[1] == 0x68 &&
        payload[2] == 0x34 &&
        payload[3] == 0x00) {
        if (onAudioData_) {
            onAudioData_(payload, payloadSize, timestamp);
        }
        return;
    }

    enqueueAudioPacket(sequenceNumber, timestamp, payload, payloadSize);
}

void RTPReceiver::enqueueAudioPacket(
        uint16_t sequenceNumber,
        uint32_t timestamp,
        const uint8_t* payload,
        size_t payloadSize) {
    if (audioBufferEmpty_) {
        firstAudioSequence_ = sequenceNumber;
        lastAudioSequence_ = sequenceNumber;
        audioBufferEmpty_ = false;
    } else {
        if (seqnumCmp(sequenceNumber, firstAudioSequence_) < 0) {
            droppedAudioPackets_++;
            return;
        }

        while (seqnumCmp(sequenceNumber, static_cast<uint16_t>(firstAudioSequence_ + audioPacketBuffer_.size())) >= 0) {
            auto& firstEntry = audioPacketBuffer_[firstAudioSequence_ % audioPacketBuffer_.size()];
            if (firstEntry.filled && firstEntry.sequenceNumber == firstAudioSequence_) {
                firstEntry.filled = false;
                firstEntry.payload.clear();
            } else {
                droppedAudioPackets_++;
            }

            if (firstAudioSequence_ == lastAudioSequence_) {
                audioBufferEmpty_ = true;
                break;
            }
            firstAudioSequence_++;
        }

        if (audioBufferEmpty_) {
            firstAudioSequence_ = sequenceNumber;
            lastAudioSequence_ = sequenceNumber;
            audioBufferEmpty_ = false;
        } else if (seqnumCmp(sequenceNumber, lastAudioSequence_) > 0) {
            lastAudioSequence_ = sequenceNumber;
        }
    }

    auto& entry = audioPacketBuffer_[sequenceNumber % audioPacketBuffer_.size()];
    if (entry.filled && entry.sequenceNumber == sequenceNumber) {
        duplicateAudioPackets_++;
        return;
    }

    entry.filled = true;
    entry.sequenceNumber = sequenceNumber;
    entry.timestamp = timestamp;
    entry.payload.assign(payload, payload + payloadSize);
    drainAudioPackets();
}

void RTPReceiver::drainAudioPackets() {
    while (!audioBufferEmpty_) {
        auto& entry = audioPacketBuffer_[firstAudioSequence_ % audioPacketBuffer_.size()];
        if (!entry.filled || entry.sequenceNumber != firstAudioSequence_) {
            return;
        }

        bufferedAudioPackets_++;
        if (onAudioData_) {
            onAudioData_(entry.payload.data(), entry.payload.size(), entry.timestamp);
        }

        entry.filled = false;
        entry.payload.clear();
        if (firstAudioSequence_ == lastAudioSequence_) {
            audioBufferEmpty_ = true;
            return;
        }
        firstAudioSequence_++;
    }
}

void RTPReceiver::resetAudioPacketBuffer() {
    audioBufferEmpty_ = true;
    firstAudioSequence_ = 0;
    lastAudioSequence_ = 0;
    bufferedAudioPackets_ = 0;
    duplicateAudioPackets_ = 0;
    droppedAudioPackets_ = 0;
    for (auto& entry : audioPacketBuffer_) {
        entry.filled = false;
        entry.sequenceNumber = 0;
        entry.timestamp = 0;
        entry.payload.clear();
    }
}

short RTPReceiver::seqnumCmp(uint16_t left, uint16_t right) {
    return static_cast<short>(left - right);
}

uint64_t RTPReceiver::convertNtpToUnixMicros(uint64_t ntpTimestamp) {
    static constexpr uint64_t kNtpToUnixEpochSeconds = 2208988800ULL;

    const uint64_t seconds = ntpTimestamp >> 32;
    const uint64_t fraction = ntpTimestamp & 0xFFFFFFFFULL;
    const uint64_t unixSeconds = seconds > kNtpToUnixEpochSeconds
        ? seconds - kNtpToUnixEpochSeconds
        : 0ULL;
    const uint64_t microsFraction = (fraction * 1000000ULL) >> 32;
    return (unixSeconds * 1000000ULL) + microsFraction;
}

uint64_t RTPReceiver::getCurrentUnixMicros() {
    const auto now = std::chrono::system_clock::now().time_since_epoch();
    return static_cast<uint64_t>(
        std::chrono::duration_cast<std::chrono::microseconds>(now).count());
}
