/**
 * AirPlay Server - Refactored Version
 * 
 * This file now delegates to specialized modules:
 * - protocol/rtsp_handler: RTSP protocol handling
 * - protocol/fairplay_handler: FairPlay and pairing
 * - network/rtp_receiver: RTP/UDP packet reception
 * - network/mirror_server: TCP mirroring server
 */

#include "airplay_server.h"
#include "protocol/rtsp_handler.h"
#include "protocol/fairplay_handler.h"
#include "network/rtp_receiver.h"
#include "network/mirror_server.h"
#include "network/ntp_client.h"
#include "network/network_utils.h"
#include <android/log.h>
#include <sys/socket.h>
#include <netinet/in.h>
#include <arpa/inet.h>
#include <unistd.h>
#include <cstring>
#include <memory>

#define TAG "AirPlay:Server"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, TAG, __VA_ARGS__)
#define LOGW(...) __android_log_print(ANDROID_LOG_WARN, TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, TAG, __VA_ARGS__)

// Forward declaration for JNI callback
std::vector<uint8_t> invokeByteArrayCallback(
    const char* methodName,
    const uint8_t* data,
    size_t size,
    bool* ok);

AirPlayServer::AirPlayServer()
    : running_(false)
    , serverSocket_(-1)
    , port_(0)
    , dataSocket_(-1)
    , controlSocket_(-1)
    , timingSocket_(-1)
    , rtpRunning_(false)
    , mirrorVideoSocket_(-1)
    , mirrorVideoPort_(0)
    , mirrorVideoRunning_(false)
    , videoWidth_(1920)
    , videoHeight_(1080)
    , audioSampleRate_(44100)
    , audioChannels_(2)
    , sessionAnnounced_(false)
    , rtspHandler_(std::make_unique<RTSPHandler>())
    , fairplayHandler_(std::make_unique<FairPlayHandler>())
{
    LOGI("AirPlayServer created");
}

AirPlayServer::~AirPlayServer() {
    stop();
    LOGI("AirPlayServer destroyed");
}

bool AirPlayServer::start(int port) {
    if (running_) {
        LOGW("Server already running");
        return false;
    }

    port_ = port;
    
    // Create TCP socket
    serverSocket_ = socket(AF_INET, SOCK_STREAM, 0);
    if (serverSocket_ < 0) {
        LOGE("Failed to create socket");
        if (onError_) onError_("Failed to create socket");
        return false;
    }

    // Allow address reuse
    NetworkUtils::setSocketReuseAddr(serverSocket_);

    // Bind
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

    // Listen
    if (listen(serverSocket_, 1) < 0) {
        LOGE("Failed to listen");
        close(serverSocket_);
        serverSocket_ = -1;
        if (onError_) onError_("Failed to listen");
        return false;
    }

    running_ = true;
    serverThread_ = std::thread(&AirPlayServer::serverThread, this);

    LOGI("RTSP server started on port %d", port_);
    return true;
}

void AirPlayServer::stop() {
    if (!running_) {
        return;
    }

    LOGI("Stopping RTSP server");
    running_ = false;

    // Stop RTP receiver and mirror server first
    stopRTPReceiver();
    stopMirrorVideoServer();

    // Close socket to unblock accept()
    if (serverSocket_ >= 0) {
        shutdown(serverSocket_, SHUT_RDWR);
        close(serverSocket_);
        serverSocket_ = -1;
    }

    // Wait for thread to finish
    if (serverThread_.joinable()) {
        serverThread_.join();
    }

    LOGI("RTSP server stopped");
}

void AirPlayServer::serverThread() {
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

        clientIp_ = inet_ntoa(clientAddr.sin_addr);
        sessionAnnounced_ = false;
        LOGI("Client connected from %s", clientIp_.c_str());

        handleClient(clientSocket);

        close(clientSocket);
        
        if (onDisconnection_) {
            onDisconnection_();
        }
        
        LOGI("Client disconnected");
    }

    LOGI("Server thread ended");
}

void AirPlayServer::handleClient(int clientSocket) {
    char buffer[4096];
    std::string requestBuffer;

    // Reset session-scoped protocol state for each client connection.
    rtspHandler_ = std::make_unique<RTSPHandler>();
    fairplayHandler_ = std::make_unique<FairPlayHandler>();

    // Setup callbacks for handlers
    rtspHandler_->setSetupCallback([this](const uint8_t* data, size_t size, bool* ok) {
        return invokeByteArrayCallback("onSetupRequest", data, size, ok);
    });

    rtspHandler_->setRecordCallback([this]() {
        if (!sessionAnnounced_ && onConnection_) {
            sessionAnnounced_ = true;
            onConnection_(clientIp_);
        }
        startRTPReceiver();
        
        // Start NTP client for time synchronization (required for mirroring)
        if (!ntpClient_) {
            ntpClient_ = std::make_unique<NTPClient>();
        }
        if (!clientIp_.empty() && !ntpClient_->isRunning()) {
            ntpClient_->start(clientIp_, 7010);
        }
    });

    fairplayHandler_->setPairSetupCallback([](const uint8_t* data, size_t size, bool* ok) {
        return invokeByteArrayCallback("onPairSetup", data, size, ok);
    });

    fairplayHandler_->setPairVerifyCallback([](const uint8_t* data, size_t size, bool* ok) {
        return invokeByteArrayCallback("onPairVerify", data, size, ok);
    });

    while (running_) {
        ssize_t bytesRead = recv(clientSocket, buffer, sizeof(buffer), 0);
        
        if (bytesRead <= 0) {
            LOGI("Client closed connection");
            break;
        }

        // Append binary data to buffer
        requestBuffer.append(buffer, bytesRead);
        
        // Process all complete requests in buffer
        while (true) {
            // Check if we have complete headers (ends with \r\n\r\n)
            size_t headersEnd = requestBuffer.find("\r\n\r\n");
            if (headersEnd == std::string::npos) {
                // Incomplete headers, continue reading
                break;
            }
            
            size_t bodyStart = headersEnd + 4;
            std::string headers = requestBuffer.substr(0, bodyStart);
            
            // Extract Content-Length from headers
            std::string contentLengthStr = rtspHandler_->extractHeader(headers, "Content-Length");
            int contentLength = contentLengthStr.empty() ? 0 : std::stoi(contentLengthStr);
            
            // Check if we have complete body in buffer
            size_t availableBodySize = requestBuffer.length() - bodyStart;
            
            if (contentLength > 0 && availableBodySize < (size_t)contentLength) {
                // Incomplete body, continue reading
                break;
            }
            
            // We have complete request (headers + body if any)
            std::string request = requestBuffer.substr(0, bodyStart + contentLength);
            requestBuffer = requestBuffer.substr(bodyStart + contentLength);

            if (!handleRTSPRequest(clientSocket, request)) {
                LOGW("Failed to handle RTSP request");
                return;
            }
            
            // Continue processing if there are more requests in buffer
            if (requestBuffer.empty()) {
                break;
            }
        }
    }
}

bool AirPlayServer::handleRTSPRequest(int socket, const std::string& request) {
    if (!rtspHandler_ || !fairplayHandler_) {
        LOGE("Protocol handlers are not initialized for current client session");
        return false;
    }

    std::string cseq = rtspHandler_->extractHeader(request, "CSeq");
    std::string userAgent = rtspHandler_->extractHeader(request, "User-Agent");
    
    // Log request details for debugging iPhone connection issues
    LOGI("RTSP Request: %s (CSeq: %s, User-Agent: %s)", 
         request.substr(0, request.find('\r')).c_str(), 
         cseq.c_str(), 
         userAgent.empty() ? "unknown" : userAgent.c_str());
    
    if (request.rfind("GET /info", 0) == 0) {
        handleInfo(socket, cseq);
        notifyActivity("GET /info");
    } else if (request.find("POST /pair-setup") != std::string::npos) {
        LOGI("Handling pair-setup request");
        handlePairSetup(socket, cseq, request);
        notifyActivity("POST /pair-setup");
    } else if (request.find("POST /pair-verify") != std::string::npos) {
        LOGI("Handling pair-verify request");
        handlePairVerify(socket, cseq, request);
        notifyActivity("POST /pair-verify");
    } else if (request.find("POST /fp-setup") != std::string::npos) {
        LOGI("Handling fp-setup request");
        handleFairPlaySetup(socket, cseq, request);
        notifyActivity("POST /fp-setup");
    } else if (request.find("OPTIONS") == 0) {
        handleOptions(socket, cseq);
        notifyActivity("OPTIONS");
    } else if (request.find("SETUP") == 0) {
        LOGI("Handling SETUP request");
        handleSetup(socket, cseq, request);
        notifyActivity("SETUP");
    } else if (request.find("GET_PARAMETER") == 0) {
        handleGetParameter(socket, cseq, request);
        notifyActivity("GET_PARAMETER");
    } else if (request.find("SET_PARAMETER") == 0) {
        handleSetParameter(socket, cseq, request);
        notifyActivity("SET_PARAMETER");
    } else if (request.find("POST /feedback") != std::string::npos || request.find("FEEDBACK") == 0) {
        handleFeedback(socket, cseq);
        notifyActivity("FEEDBACK");
    } else if (request.find("RECORD") == 0) {
        LOGI("Handling RECORD request");
        handleRecord(socket, cseq);
        notifyActivity("RECORD");
    } else if (request.find("TEARDOWN") == 0) {
        LOGI("Handling TEARDOWN request");
        handleTeardown(socket, cseq);
        notifyActivity("TEARDOWN");
        return false; // Close connection
    } else {
        LOGW("Unknown RTSP method: %s", request.substr(0, 100).c_str());
        return false;
    }

    return true;
}

// ============================================================================
// RTSP Handlers - Delegate to RTSPHandler
// ============================================================================

void AirPlayServer::handleInfo(int socket, const std::string& cseq) {
    if (rtspHandler_) {
        rtspHandler_->handleInfo(socket, cseq);
    }
}

void AirPlayServer::handleOptions(int socket, const std::string& cseq) {
    if (rtspHandler_) {
        rtspHandler_->handleOptions(socket, cseq);
    }
}

void AirPlayServer::handleSetup(int socket, const std::string& cseq, const std::string& request) {
    if (rtspHandler_) {
        rtspHandler_->handleSetup(socket, cseq, request);
        rtspHandler_->parseSetupParams(request, &videoWidth_, &videoHeight_, &audioSampleRate_, &audioChannels_);
    }
}

void AirPlayServer::handleGetParameter(int socket, const std::string& cseq, const std::string& request) {
    if (rtspHandler_) {
        rtspHandler_->handleGetParameter(socket, cseq, request);
    }
}

void AirPlayServer::handleSetParameter(int socket, const std::string& cseq, const std::string& request) {
    if (rtspHandler_) {
        rtspHandler_->handleSetParameter(socket, cseq, request);
    }
}

void AirPlayServer::handleFeedback(int socket, const std::string& cseq) {
    if (rtspHandler_) {
        rtspHandler_->handleFeedback(socket, cseq);
    }
}

void AirPlayServer::handleRecord(int socket, const std::string& cseq) {
    if (rtspHandler_) {
        rtspHandler_->handleRecord(socket, cseq);
    }
}

void AirPlayServer::handleTeardown(int socket, const std::string& cseq) {
    if (rtspHandler_) {
        rtspHandler_->handleTeardown(socket, cseq);
    }
}

// ============================================================================
// FairPlay Handlers - Delegate to FairPlayHandler
// ============================================================================

void AirPlayServer::handlePairSetup(int socket, const std::string& cseq, const std::string& request) {
    if (fairplayHandler_) {
        fairplayHandler_->handlePairSetup(socket, cseq, request);
    }
}

void AirPlayServer::handlePairVerify(int socket, const std::string& cseq, const std::string& request) {
    if (fairplayHandler_) {
        fairplayHandler_->handlePairVerify(socket, cseq, request);
    }
}

void AirPlayServer::handleFairPlaySetup(int socket, const std::string& cseq, const std::string& request) {
    if (fairplayHandler_) {
        fairplayHandler_->handleFairPlaySetup(socket, cseq, request);
    }
}

// ============================================================================
// Utility Methods - Delegate to RTSPHandler
// ============================================================================

std::string AirPlayServer::extractHeader(const std::string& request, const std::string& header) {
    return rtspHandler_ ? rtspHandler_->extractHeader(request, header) : "";
}

std::string AirPlayServer::extractBody(const std::string& request) {
    return rtspHandler_ ? rtspHandler_->extractBody(request) : "";
}

void AirPlayServer::parseSetupParams(const std::string& request) {
    if (rtspHandler_) {
        rtspHandler_->parseSetupParams(request, &videoWidth_, &videoHeight_, &audioSampleRate_, &audioChannels_);
    }
}

void AirPlayServer::notifyActivity(const std::string& method) {
    if (!sessionAnnounced_ || !onActivity_) {
        return;
    }
    onActivity_(method);
}

// ============================================================================
// RTP Receiver - Delegate to RTPReceiver
// ============================================================================

void AirPlayServer::startRTPReceiver() {
    if (rtpReceiver_ && rtpReceiver_->isRunning()) {
        LOGW("RTP receiver already running");
        return;
    }

    LOGI("Starting RTP receiver");

    rtpReceiver_ = std::make_unique<RTPReceiver>();

    // Setup callbacks
    rtpReceiver_->setVideoDataCallback(onVideoData_);
    rtpReceiver_->setAudioDataCallback(onAudioData_);
    rtpReceiver_->setErrorCallback(onError_);

    // Start receiver (hardcoded ports for now)
    if (rtpReceiver_->start(7100, 6001, 7002)) {
        rtpRunning_ = true;
        LOGI("RTP receiver started successfully");
    } else {
        LOGE("Failed to start RTP receiver");
        rtpReceiver_.reset();
    }
}

void AirPlayServer::stopRTPReceiver() {
    if (!rtpRunning_) {
        return;
    }

    LOGI("Stopping RTP receiver");
    rtpRunning_ = false;
    if (rtpReceiver_) {
        rtpReceiver_->stop();
        rtpReceiver_.reset();
    }
    
    // Stop NTP client
    if (ntpClient_) {
        ntpClient_->stop();
        ntpClient_.reset();
    }
}

// These methods are now handled by RTPReceiver module
void AirPlayServer::receiveDataThread() { /* Handled by RTPReceiver */ }
void AirPlayServer::receiveControlThread() { /* Handled by RTPReceiver */ }
void AirPlayServer::receiveTimingThread() { /* Handled by RTPReceiver */ }
bool AirPlayServer::bindUDPSocket(int socket, int port) { return false; }
void AirPlayServer::processRTPPacket(const uint8_t* data, size_t size) { /* Handled by RTPReceiver */ }

// ============================================================================
// Mirror Server - Delegate to MirrorServer
// ============================================================================

int AirPlayServer::startMirrorVideoServer() {
    if (mirrorServer_ && mirrorServer_->isRunning()) {
        return mirrorServer_->getPort();
    }

    mirrorServer_ = std::make_unique<MirrorServer>();
    mirrorServer_->setPacketCallback(onMirroringVideoPacket_);

    int port = mirrorServer_->start();
    if (port > 0) {
        mirrorVideoPort_ = port;
        mirrorVideoRunning_ = true;
        LOGI("Mirror video server started on port %d", port);
    } else {
        mirrorServer_.reset();
    }

    return port;
}

void AirPlayServer::stopMirrorVideoServer() {
    if (!mirrorVideoRunning_) {
        return;
    }

    LOGI("Stopping mirror video server");
    mirrorVideoRunning_ = false;
    mirrorVideoPort_ = 0;
    if (mirrorServer_) {
        mirrorServer_->stop();
        mirrorServer_.reset();
    }
}

// This method is now handled by MirrorServer module
void AirPlayServer::mirrorVideoThread() { /* Handled by MirrorServer */ }

// ============================================================================
// FairPlay Decryption - Delegate to FairPlayHandler
// ============================================================================

bool AirPlayServer::decryptFairPlayAesKey(const uint8_t* encryptedKey, size_t size,
                                         std::vector<uint8_t>* outKey) {
    if (!fairplayHandler_) {
        LOGE("Cannot decrypt FairPlay AES key: FairPlay handler is not initialized");
        return false;
    }
    return fairplayHandler_->decryptAesKey(encryptedKey, size, outKey);
}
