#include "mirror_server.h"
#include "network_utils.h"
#include <android/log.h>
#include <sys/socket.h>
#include <netinet/in.h>
#include <arpa/inet.h>
#include <unistd.h>
#include <cstring>
#include <cerrno>
#include <inttypes.h>

#define TAG "AirPlay:Mirror"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, TAG, __VA_ARGS__)
#define LOGW(...) __android_log_print(ANDROID_LOG_WARN, TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, TAG, __VA_ARGS__)

constexpr size_t kMirrorHeaderSize = 128;

MirrorServer::MirrorServer()
    : socket_(-1)
    , port_(0)
    , running_(false)
{
    LOGI("MirrorServer created");
}

MirrorServer::~MirrorServer() {
    stop();
    LOGI("MirrorServer destroyed");
}

int MirrorServer::start() {
    if (running_) {
        LOGI("Mirror video server already running on port %d", port_);
        return port_;
    }

    socket_ = socket(AF_INET, SOCK_STREAM, 0);
    if (socket_ < 0) {
        LOGE("Failed to create mirror video socket: %s", strerror(errno));
        return -1;
    }

    NetworkUtils::setSocketReuseAddr(socket_);

    sockaddr_in addr {};
    addr.sin_family = AF_INET;
    addr.sin_addr.s_addr = INADDR_ANY;
    addr.sin_port = htons(0);  // Let OS assign port

    if (bind(socket_, reinterpret_cast<sockaddr*>(&addr), sizeof(addr)) < 0) {
        LOGE("Failed to bind mirror video socket: %s", strerror(errno));
        close(socket_);
        socket_ = -1;
        return -1;
    }

    socklen_t addrLen = sizeof(addr);
    if (getsockname(socket_, reinterpret_cast<sockaddr*>(&addr), &addrLen) < 0) {
        LOGE("Failed to read mirror video port: %s", strerror(errno));
        close(socket_);
        socket_ = -1;
        return -1;
    }

    if (listen(socket_, 1) < 0) {
        LOGE("Failed to listen on mirror video socket: %s", strerror(errno));
        close(socket_);
        socket_ = -1;
        return -1;
    }

    port_ = ntohs(addr.sin_port);
    running_ = true;
    thread_ = std::thread(&MirrorServer::serverThread, this);

    LOGI("Mirror video TCP server started on port %d", port_);
    return port_;
}

void MirrorServer::stop() {
    if (!running_ && socket_ < 0) {
        return;
    }

    running_ = false;

    if (socket_ >= 0) {
        shutdown(socket_, SHUT_RDWR);
        close(socket_);
        socket_ = -1;
    }

    if (thread_.joinable()) {
        thread_.join();
    }

    port_ = 0;
    LOGI("Mirror video TCP server stopped");
}

void MirrorServer::serverThread() {
    int streamSocket = -1;
    uint8_t header[kMirrorHeaderSize];
    std::vector<uint8_t> payload;
    uint64_t packetCount = 0;
    uint64_t videoPacketCount = 0;
    uint64_t heartbeatCount = 0;
    uint64_t reportPacketCount = 0;

    while (running_) {
        if (streamSocket < 0) {
            sockaddr_in clientAddr {};
            socklen_t clientLen = sizeof(clientAddr);
            LOGI("Waiting for mirror TCP client");
            streamSocket = accept(socket_, reinterpret_cast<sockaddr*>(&clientAddr), &clientLen);
            if (streamSocket < 0) {
                if (running_) {
                    LOGE("Mirror accept failed: %s", strerror(errno));
                }
                break;
            }
            LOGI("Mirror TCP client connected from %s:%d", 
                 inet_ntoa(clientAddr.sin_addr), ntohs(clientAddr.sin_port));
        }

        // Read header (128 bytes)
        size_t headerRead = 0;
        while (running_ && headerRead < kMirrorHeaderSize) {
            const ssize_t readCount = recv(streamSocket, header + headerRead, 
                                          kMirrorHeaderSize - headerRead, 0);
            if (readCount <= 0) {
                if (readCount < 0 && (errno == EAGAIN || errno == EWOULDBLOCK)) {
                    continue;
                }
                LOGW("Mirror TCP header read failed or closed");
                close(streamSocket);
                streamSocket = -1;
                break;
            }
            headerRead += static_cast<size_t>(readCount);
        }

        if (!running_ || streamSocket < 0) {
            continue;
        }

        // Parse header
        const uint32_t payloadSize = NetworkUtils::readUint32LE(header);
        const int payloadType = NetworkUtils::readUint16LE(header + 4) & 0x00FF;
        const uint16_t payloadOption = NetworkUtils::readUint16LE(header + 6);

        packetCount++;

        if (payloadType == 2) {
            heartbeatCount++;
            if (heartbeatCount <= 3) {
                LOGI("Mirror heartbeat #%" PRIu64 " received (option=0x%04x)", heartbeatCount, payloadOption);
            }
            continue;
        }

        if (payloadType == 1 || payloadType == 5 || packetCount <= 5) {
            LOGI("Mirror packet #%" PRIu64 " type=%d option=0x%04x payload=%u",
                 packetCount, payloadType, payloadOption, payloadSize);
        }

        if (payloadSize == 0 || payloadSize > (4 * 1024 * 1024)) {
            LOGW("Invalid mirror payload size: %u", payloadSize);
            close(streamSocket);
            streamSocket = -1;
            continue;
        }

        // Read payload
        payload.resize(payloadSize);
        size_t payloadRead = 0;
        while (running_ && payloadRead < payload.size()) {
            const ssize_t readCount = recv(streamSocket, payload.data() + payloadRead, 
                                          payload.size() - payloadRead, 0);
            if (readCount <= 0) {
                if (readCount < 0 && (errno == EAGAIN || errno == EWOULDBLOCK)) {
                    continue;
                }
                LOGW("Mirror TCP payload read failed or closed");
                close(streamSocket);
                streamSocket = -1;
                break;
            }
            payloadRead += static_cast<size_t>(readCount);
        }

        if (!running_ || streamSocket < 0) {
            continue;
        }

        // Call callback with received packet
        if (onPacket_) {
            if (payloadType == 0) {
                videoPacketCount++;
                if (videoPacketCount <= 3 || videoPacketCount % 120 == 0) {
                    LOGI("Forwarding mirror video packet #%" PRIu64 " (%u bytes)", videoPacketCount, payloadSize);
                }
            } else if (payloadType == 5) {
                reportPacketCount++;
                if (reportPacketCount <= 2) {
                    LOGI("Forwarding mirror report packet #%" PRIu64 " (%u bytes)", reportPacketCount, payloadSize);
                }
            }
            onPacket_(payloadType, payload.data(), payload.size());
        }
    }

    if (streamSocket >= 0) {
        close(streamSocket);
    }
}
