#include "ntp_client.h"
#include <android/log.h>
#include <sys/socket.h>
#include <netinet/in.h>
#include <arpa/inet.h>
#include <unistd.h>
#include <cstring>
#include <chrono>

#define TAG "AirPlay:NTP"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, TAG, __VA_ARGS__)
#define LOGW(...) __android_log_print(ANDROID_LOG_WARN, TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, TAG, __VA_ARGS__)
#define LOGD(...) __android_log_print(ANDROID_LOG_DEBUG, TAG, __VA_ARGS__)

NTPClient::NTPClient()
    : sendSocket_(-1)
    , receiveSocket_(-1)
    , clientPort_(7010)
    , serverPort_(7011)
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

    // Create UDP socket for sending requests
    sendSocket_ = socket(AF_INET, SOCK_DGRAM, 0);
    if (sendSocket_ < 0) {
        LOGE("Failed to create NTP send socket: %s", strerror(errno));
        return false;
    }

    // Create UDP socket for receiving responses
    receiveSocket_ = socket(AF_INET, SOCK_DGRAM, 0);
    if (receiveSocket_ < 0) {
        LOGE("Failed to create NTP receive socket: %s", strerror(errno));
        close(sendSocket_);
        sendSocket_ = -1;
        return false;
    }

    // Bind receive socket to port 7011
    struct sockaddr_in serverAddr;
    memset(&serverAddr, 0, sizeof(serverAddr));
    serverAddr.sin_family = AF_INET;
    serverAddr.sin_addr.s_addr = INADDR_ANY;
    serverAddr.sin_port = htons(serverPort_);

    if (bind(receiveSocket_, (struct sockaddr*)&serverAddr, sizeof(serverAddr)) < 0) {
        LOGE("Failed to bind NTP receive socket to port %d: %s", serverPort_, strerror(errno));
        close(sendSocket_);
        close(receiveSocket_);
        sendSocket_ = -1;
        receiveSocket_ = -1;
        return false;
    }

    // Record session start time
    sessionStartTime_ = std::chrono::duration_cast<std::chrono::microseconds>(
        std::chrono::system_clock::now().time_since_epoch()
    ).count();

    running_ = true;
    sendThread_ = std::thread(&NTPClient::sendThread, this);
    receiveThread_ = std::thread(&NTPClient::receiveThread, this);

    LOGI("NTP client started: sending to %s:%d, receiving on port %d", 
         clientIp.c_str(), clientPort, serverPort_);
    return true;
}

void NTPClient::stop() {
    if (!running_) {
        return;
    }

    LOGI("Stopping NTP client");
    running_ = false;

    if (sendThread_.joinable()) {
        sendThread_.join();
    }

    if (receiveThread_.joinable()) {
        receiveThread_.join();
    }

    if (sendSocket_ >= 0) {
        close(sendSocket_);
        sendSocket_ = -1;
    }

    if (receiveSocket_ >= 0) {
        close(receiveSocket_);
        receiveSocket_ = -1;
    }

    LOGI("NTP client stopped");
}

void NTPClient::sendThread() {
    int requestCount = 0;

    while (running_) {
        sendNTPRequest();
        requestCount++;

        if (requestCount <= 3 || requestCount % 10 == 0) {
            LOGI("Sent NTP request #%d to %s:%d", 
                 requestCount, clientIp_.c_str(), clientPort_);
        }

        // Wait 3 seconds before next request
        for (int i = 0; i < 30 && running_; i++) {
            std::this_thread::sleep_for(std::chrono::milliseconds(100));
        }
    }

    LOGI("NTP send thread ended");
}

void NTPClient::receiveThread() {
    uint8_t buffer[48];
    struct sockaddr_in clientAddr;
    socklen_t clientLen = sizeof(clientAddr);
    int responseCount = 0;

    LOGI("NTP receive thread started, listening on port %d", serverPort_);

    while (running_) {
        // Set timeout for recvfrom to allow checking running_ flag
        struct timeval tv;
        tv.tv_sec = 1;
        tv.tv_usec = 0;
        setsockopt(receiveSocket_, SOL_SOCKET, SO_RCVTIMEO, &tv, sizeof(tv));

        ssize_t received = recvfrom(receiveSocket_, buffer, sizeof(buffer), 0,
                                    (struct sockaddr*)&clientAddr, &clientLen);
        
        if (received > 0) {
            responseCount++;
            if (responseCount <= 3 || responseCount % 10 == 0) {
                LOGI("Received NTP response #%d (%zd bytes) from %s", 
                     responseCount, received, inet_ntoa(clientAddr.sin_addr));
            }
            LOGD("NTP response: flags=0x%02x stratum=%d", buffer[0], buffer[1]);
        } else if (received < 0 && errno != EAGAIN && errno != EWOULDBLOCK) {
            if (running_) {
                LOGE("NTP receive error: %s", strerror(errno));
            }
        }
    }

    LOGI("NTP receive thread ended (received %d responses)", responseCount);
}

void NTPClient::sendNTPRequest() {
    // NTP packet structure (48 bytes)
    uint8_t packet[48];
    memset(packet, 0, sizeof(packet));

    // NTP header according to documentation:
    // Flags: 0x23 (version 4, mode 3 = client)
    packet[0] = 0x23;
    
    // Stratum: 0 (unspecified)
    packet[1] = 0x00;
    
    // Poll interval: 0
    packet[2] = 0x00;
    
    // Precision: 0
    packet[3] = 0x00;

    // Root delay, root dispersion, reference ID: all zeros (already set by memset)

    // Transmit timestamp (last 8 bytes): time since session start
    // Documentation says: "The reference date for the timestamps is the beginning of the mirroring session"
    uint64_t timestamp = getTimestamp();
    
    // NTP timestamp format: seconds in first 4 bytes, fraction in last 4 bytes
    // Convert microseconds to NTP format
    uint32_t seconds = static_cast<uint32_t>(timestamp / 1000000);
    uint32_t fraction = static_cast<uint32_t>((timestamp % 1000000) * 4294.967296); // Convert to 2^32 fraction

    // Write timestamp in big-endian (network byte order)
    packet[40] = (seconds >> 24) & 0xFF;
    packet[41] = (seconds >> 16) & 0xFF;
    packet[42] = (seconds >> 8) & 0xFF;
    packet[43] = seconds & 0xFF;
    packet[44] = (fraction >> 24) & 0xFF;
    packet[45] = (fraction >> 16) & 0xFF;
    packet[46] = (fraction >> 8) & 0xFF;
    packet[47] = fraction & 0xFF;

    // Send to client
    struct sockaddr_in addr;
    memset(&addr, 0, sizeof(addr));
    addr.sin_family = AF_INET;
    addr.sin_port = htons(clientPort_);
    inet_pton(AF_INET, clientIp_.c_str(), &addr.sin_addr);

    ssize_t sent = sendto(sendSocket_, packet, sizeof(packet), 0,
                          (struct sockaddr*)&addr, sizeof(addr));
    
    if (sent < 0) {
        LOGE("Failed to send NTP request: %s", strerror(errno));
    }
}

uint64_t NTPClient::getTimestamp() {
    // Get current time
    uint64_t now = std::chrono::duration_cast<std::chrono::microseconds>(
        std::chrono::system_clock::now().time_since_epoch()
    ).count();

    // Return time since session start (in microseconds)
    return now - sessionStartTime_;
}
