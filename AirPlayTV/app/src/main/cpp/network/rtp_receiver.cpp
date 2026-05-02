#include "rtp_receiver.h"
#include "network_utils.h"
#include <android/log.h>
#include <sys/socket.h>
#include <netinet/in.h>
#include <arpa/inet.h>
#include <unistd.h>
#include <cstring>
#include <cerrno>

#define TAG "AirPlay:RTP"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, TAG, __VA_ARGS__)
#define LOGW(...) __android_log_print(ANDROID_LOG_WARN, TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, TAG, __VA_ARGS__)

RTPReceiver::RTPReceiver()
    : dataSocket_(-1)
    , controlSocket_(-1)
    , timingSocket_(-1)
    , running_(false)
{
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

    LOGI("RTP receiver stopped");
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
    uint8_t marker = (data[1] >> 7) & 0x01;
    uint8_t payloadType = data[1] & 0x7F;
    uint16_t sequenceNumber = (data[2] << 8) | data[3];
    uint32_t timestamp = (data[4] << 24) | (data[5] << 16) | (data[6] << 8) | data[7];
    uint32_t ssrc = (data[8] << 24) | (data[9] << 16) | (data[10] << 8) | data[11];

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

    // In this MVP mirroring flow, H.264 video arrives over the dedicated mirror TCP
    // channel. UDP RTP packets handled here are for the auxiliary audio stream.
    // Routing them as video based on payload type/size heuristics breaks iPhone
    // gallery video playback because AAC can be misclassified.
    if (onAudioData_) {
        onAudioData_(payload, payloadSize, timestamp);
    }
}
