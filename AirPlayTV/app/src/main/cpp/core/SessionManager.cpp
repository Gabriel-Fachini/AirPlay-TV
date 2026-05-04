#include "SessionManager.h"
#include "network/network_utils.h"
#include <android/log.h>
#include <sys/socket.h>
#include <netinet/in.h>
#include <arpa/inet.h>
#include <unistd.h>
#include <cstring>

#define TAG "AirPlay:SessionManager"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, TAG, __VA_ARGS__)
#define LOGW(...) __android_log_print(ANDROID_LOG_WARN, TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, TAG, __VA_ARGS__)

SessionManager::SessionManager() : running_(false), serverSocket_(-1), port_(0) {}

SessionManager::~SessionManager() {
    stop();
}

bool SessionManager::start(int port) {
    if (running_) {
        LOGW("Server already running");
        return false;
    }

    port_ = port;
    
    serverSocket_ = socket(AF_INET, SOCK_STREAM, 0);
    if (serverSocket_ < 0) {
        LOGE("Failed to create socket");
        if (onError_) onError_("Failed to create socket");
        return false;
    }

    NetworkUtils::setSocketReuseAddr(serverSocket_);

    struct sockaddr_in addr;
    memset(&addr, 0, sizeof(addr));
    addr.sin_family = AF_INET;
    addr.sin_addr.s_addr = INADDR_ANY;
    addr.sin_port = htons(port_);

    if (bind(serverSocket_, (struct sockaddr*)&addr, sizeof(addr)) < 0) {
        LOGE("Failed to bind to port %d", port_);
        close(serverSocket_);
        serverSocket_ = -1;
        if (onError_) onError_("Failed to bind to port");
        return false;
    }

    if (listen(serverSocket_, 1) < 0) {
        LOGE("Failed to listen");
        close(serverSocket_);
        serverSocket_ = -1;
        if (onError_) onError_("Failed to listen");
        return false;
    }

    running_ = true;
    serverThread_ = std::thread(&SessionManager::serverThread, this);

    LOGI("SessionManager started on port %d", port_);
    return true;
}

void SessionManager::stop() {
    if (!running_) return;

    LOGI("Stopping SessionManager");
    running_ = false;

    if (serverSocket_ >= 0) {
        shutdown(serverSocket_, SHUT_RDWR);
        close(serverSocket_);
        serverSocket_ = -1;
    }

    if (serverThread_.joinable()) {
        serverThread_.join();
    }

    LOGI("SessionManager stopped");
}

void SessionManager::serverThread() {
    LOGI("Server thread started");

    while (running_) {
        struct sockaddr_in clientAddr;
        socklen_t clientLen = sizeof(clientAddr);

        int clientSocket = accept(serverSocket_, (struct sockaddr*)&clientAddr, &clientLen);
        
        if (clientSocket < 0) {
            if (running_) {
                LOGE("Accept failed");
            }
            break;
        }

        std::string clientIp = inet_ntoa(clientAddr.sin_addr);
        LOGI("Client connected from %s", clientIp.c_str());

        if (onClientConnected_) {
            onClientConnected_(clientSocket, clientIp);
        }

        close(clientSocket);
        
        if (onClientDisconnected_) {
            onClientDisconnected_();
        }
        
        LOGI("Client disconnected");
    }

    LOGI("Server thread ended");
}
