#include "ntp_client.h"
#include <android/log.h>
#include <sys/socket.h>
#include <netinet/in.h>
#include <arpa/inet.h>
#include <unistd.h>
#include <cstring>
#include <chrono>
#include <sys/time.h>

#define TAG "AirPlay:NTP"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, TAG, __VA_ARGS__)
#define LOGW(...) __android_log_print(ANDROID_LOG_WARN, TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, TAG, __VA_ARGS__)
#define LOGD(...) __android_log_print(ANDROID_LOG_DEBUG, TAG, __VA_ARGS__)

NTPClient::NTPClient()
    : socket_(-1)
    , clientPort_(7010)
    , running_(false)
    , sessionStartTime_(0)
{
}

NTPClient::~NTPClient() {
    stop();
}

bool NTPClient::start(const std::string& clientIp, int clientPort) {
    if (running_) {
        LOGW("NTP client already running");
        return true;
    }

    clientIp_ = clientIp;
    clientPort_ = clientPort;

    // Create UDP socket (same socket for send and receive)
    socket_ = socket(AF_INET, SOCK_DGRAM, 0);
    if (socket_ < 0) {
        LOGE("Failed to create NTP socket: %s", strerror(errno));
        return false;
    }

    // Set receive timeout to allow checking running_ flag
    struct timeval tv;
    tv.tv_sec = 0;
    tv.tv_usec = 300000; // 300ms timeout like RPiPlay
    if (setsockopt(socket_, SOL_SOCKET, SO_RCVTIMEO, &tv, sizeof(tv)) < 0) {
        LOGW("Failed to set socket timeout: %s", strerror(errno));
    }

    // Record session start time
    sessionStartTime_ = std::chrono::duration_cast<std::chrono::microseconds>(
        std::chrono::system_clock::now().time_since_epoch()
    ).count();

    running_ = true;
    thread_ = std::thread(&NTPClient::ntpThread, this);

    LOGI("NTP client started, sending to %s:%d every 3 seconds", 
         clientIp.c_str(), clientPort);
    return true;
}

void NTPClient::stop() {
    if (!running_) {
        return;
    }

    LOGI("Stopping NTP client");
    running_ = false;

    if (thread_.joinable()) {
        thread_.join();
    }

    if (socket_ >= 0) {
        close(socket_);
        socket_ = -1;
    }

    LOGI("NTP client stopped");
}

void NTPClient::ntpThread() {
    int requestCount = 0;
    int responseCount = 0;

    LOGI("NTP thread started");

    while (running_) {
        // Send NTP request
        sendNTPRequest();
        requestCount++;

        if (requestCount <= 3 || requestCount % 10 == 0) {
            LOGI("Sent NTP request #%d to %s:%d", 
                 requestCount, clientIp_.c_str(), clientPort_);
        }

        // Try to receive response (with timeout)
        uint8_t response[128];
        struct sockaddr_in fromAddr;
        socklen_t fromLen = sizeof(fromAddr);
        
        memset(&fromAddr, 0, sizeof(fromAddr));
        fromLen = sizeof(fromAddr);
        
        ssize_t received = recvfrom(socket_, response, sizeof(response), 0,
                                    (struct sockaddr*)&fromAddr, &fromLen);
        
        if (received > 0) {
            responseCount++;
            if (responseCount <= 3 || responseCount % 10 == 0) {
                LOGI("Received NTP response #%d (%zd bytes) from %s:%d", 
                     responseCount, received, inet_ntoa(fromAddr.sin_addr),
                     ntohs(fromAddr.sin_port));
            }
            LOGD("NTP response: first byte=0x%02x, second=0x%02x", response[0], response[1]);
        } else if (received < 0) {
            if (errno != EAGAIN && errno != EWOULDBLOCK) {
                LOGE("NTP receive error: %s (errno=%d)", strerror(errno), errno);
            } else {
                // Timeout - log occasionally
                if (requestCount <= 3 || requestCount % 10 == 0) {
                    LOGD("NTP receive timeout (normal, waiting for response)");
                }
            }
        }

        // Wait 3 seconds before next request
        for (int i = 0; i < 30 && running_; i++) {
            std::this_thread::sleep_for(std::chrono::milliseconds(100));
        }
    }

    LOGI("NTP thread ended (sent=%d, received=%d)", requestCount, responseCount);
}

void NTPClient::sendNTPRequest() {
    // NTP request packet format from RPiPlay
    // This is NOT standard NTP, it's Apple's custom format for AirPlay timing
    uint8_t request[32] = {
        0x80, 0xd2, 0x00, 0x07, 0x00, 0x00, 0x00, 0x00,
        0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00,
        0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00,
        0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00
    };

    // Put current timestamp at offset 24 (bytes 24-31)
    uint64_t timestamp = getTimestamp();
    putNTPTimestamp(request, 24, timestamp);

    // Send to client
    struct sockaddr_in addr;
    memset(&addr, 0, sizeof(addr));
    addr.sin_family = AF_INET;
    addr.sin_port = htons(clientPort_);
    inet_pton(AF_INET, clientIp_.c_str(), &addr.sin_addr);

    ssize_t sent = sendto(socket_, request, sizeof(request), 0,
                          (struct sockaddr*)&addr, sizeof(addr));
    
    if (sent < 0) {
        LOGE("Failed to send NTP request: %s", strerror(errno));
    } else {
        LOGD("Sent NTP packet: 0x%02x 0x%02x 0x%02x 0x%02x... (%zd bytes)", 
             request[0], request[1], request[2], request[3], sent);
    }
}

uint64_t NTPClient::getTimestamp() {
    // Get current time in microseconds
    struct timeval tv;
    gettimeofday(&tv, nullptr);
    return (uint64_t)tv.tv_sec * 1000000ULL + (uint64_t)tv.tv_usec;
}

void NTPClient::putNTPTimestamp(uint8_t* buffer, int offset, uint64_t microseconds) {
    // Convert microseconds to NTP timestamp format
    // NTP uses epoch of 1900, Unix uses 1970
    // Need to add the difference: 2208988800 seconds
    const uint64_t SECONDS_FROM_1900_TO_1970 = 2208988800ULL;
    
    uint64_t seconds = (microseconds / 1000000ULL) + SECONDS_FROM_1900_TO_1970;
    uint64_t fraction = ((microseconds % 1000000ULL) << 32) / 1000000ULL;

    // Write in big-endian (network byte order)
    buffer[offset + 0] = (seconds >> 24) & 0xFF;
    buffer[offset + 1] = (seconds >> 16) & 0xFF;
    buffer[offset + 2] = (seconds >> 8) & 0xFF;
    buffer[offset + 3] = seconds & 0xFF;
    buffer[offset + 4] = (fraction >> 24) & 0xFF;
    buffer[offset + 5] = (fraction >> 16) & 0xFF;
    buffer[offset + 6] = (fraction >> 8) & 0xFF;
    buffer[offset + 7] = fraction & 0xFF;
}
