/**
 * AirPlay Server - Refactored Version
 * 
 * This file now delegates to specialized modules:
 * - protocol/rtsp_handler: RTSP protocol handling
 * - protocol/fairplay_handler: FairPlay and pairing
 * - network/rtp_receiver: RTP/UDP packet reception
 * - network/mirror_server: TCP mirroring server
 */

#include "AirPlayServer.h"
#include "protocol/rtsp_handler.h"
#include "protocol/fairplay_handler.h"
#include "network/rtp_receiver.h"
#include "network/mirror_server.h"
#include "network/ntp_client.h"
#include "network/network_utils.h"
#include "request_utils.h"
#include <android/log.h>
#include <sys/socket.h>
#include <netinet/in.h>
#include <arpa/inet.h>
#include <unistd.h>
#include <cstring>
#include <sstream>
#include <memory>

#define TAG "AirPlay:Server"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, TAG, __VA_ARGS__)
#define LOGW(...) __android_log_print(ANDROID_LOG_WARN, TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, TAG, __VA_ARGS__)

#include "jni/JniBridge.h"

AirPlayServer::AirPlayServer()
    : sessionManager_(std::make_unique<SessionManager>())
    , mediaOrchestrator_(std::make_unique<MediaOrchestrator>())
    , rtspHandler_(std::make_unique<RTSPHandler>())
    , fairplayHandler_(std::make_unique<FairPlayHandler>())
    , routeDispatcher_(std::make_unique<RouteDispatcher>())
{
    LOGI("AirPlayServer created");
    
    sessionManager_->setClientConnectedCallback([this](int socket, const std::string& ip) {
        handleClient(socket, ip);
    });
}

AirPlayServer::~AirPlayServer() {
    stop();
    LOGI("AirPlayServer destroyed");
}

bool AirPlayServer::start(int port) {
    return sessionManager_->start(port);
}

void AirPlayServer::stop() {
    sessionManager_->stop();
    resetSessionState();
}

void AirPlayServer::handleClient(int clientSocket, const std::string& clientIp) {
    clientIp_ = clientIp;
    sessionAnnounced_ = false;
    routeDispatcher_->resetSessionState();
    routeDispatcher_->setClientIp(clientIp);
    resetAudioSessionConfig();
    
    char buffer[4096];
    std::string requestBuffer;

    // Reset session-scoped protocol state for each client connection.
    rtspHandler_ = std::make_unique<RTSPHandler>();
    fairplayHandler_ = std::make_unique<FairPlayHandler>();

    // Setup callbacks for handlers
    rtspHandler_->setSetupCallback([this](const uint8_t* data, size_t size, bool* ok) {
        return JniBridge::invokeByteArrayCallback("onSetupRequest", data, size, ok);
    });

    rtspHandler_->setRecordCallback([this]() {
        if (!sessionAnnounced_ && onConnection_) {
            sessionAnnounced_ = true;
            onConnection_(clientIp_);
        }
    });

    rtspHandler_->setFlushCallback([this](int nextSequenceNumber) {
        mediaOrchestrator_->flushAudio(
            static_cast<uint16_t>(nextSequenceNumber >= 0 ? nextSequenceNumber : 0),
            nextSequenceNumber >= 0);
        JniBridge::onAudioFlushCallback(nextSequenceNumber);
    });

    fairplayHandler_->setPairSetupCallback([](const uint8_t* data, size_t size, bool* ok) {
        return JniBridge::invokeByteArrayCallback("onPairSetup", data, size, ok);
    });

    fairplayHandler_->setPairVerifyCallback([](const uint8_t* data, size_t size, bool* ok) {
        return JniBridge::invokeByteArrayCallback("onPairVerify", data, size, ok);
    });

    while (sessionManager_->isRunning()) {
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
                const std::string requestLine = airplay::request::getRequestLine(request);
                if (requestLine.rfind("TEARDOWN", 0) == 0) {
                    LOGI("Client requested normal session teardown");
                } else {
                    LOGW("Failed to handle RTSP request: %s", requestLine.c_str());
                }
                resetSessionState();
                return;
            }
            
            // Continue processing if there are more requests in buffer
            if (requestBuffer.empty()) {
                break;
            }
        }
    }

    resetSessionState();
}

void AirPlayServer::resetSessionState() {
    mediaOrchestrator_->stopRTPReceiver();
    mediaOrchestrator_->stopMirrorVideoServer();
    mediaOrchestrator_->stopNTPClient();
    mediaOrchestrator_->resetAudioSessionConfig();
    routeDispatcher_->resetSessionState();
    routeDispatcher_->setClientIp("");
    fairPlayKeyMessage_.clear();
    clientIp_.clear();
    videoWidth_ = 1920;
    videoHeight_ = 1080;
    sessionAnnounced_ = false;
}

bool AirPlayServer::handleRTSPRequest(int socket, const std::string& request) {
    if (!rtspHandler_ || !fairplayHandler_) {
        LOGE("Protocol handlers are not initialized for current client session");
        return false;
    }

    const std::string requestLine = airplay::request::getRequestLine(request);
    const std::string requestPath = airplay::request::getRequestPath(requestLine);
    std::string cseq = rtspHandler_->extractHeader(request, "CSeq");
    std::string userAgent = rtspHandler_->extractHeader(request, "User-Agent");
    const std::string sessionId = extractHeader(request, "X-Apple-Session-ID");
    
    LOGI("Control request: %s path=%s cseq=%s sessionId=%s ua=%s",
         requestLine.c_str(),
         requestPath.c_str(),
         cseq.c_str(), 
         sessionId.empty() ? "none" : sessionId.c_str(),
         userAgent.empty() ? "unknown" : userAgent.c_str());
    
    if (request.rfind("GET /server-info", 0) == 0 ||
        request.rfind("GET /slideshow-features", 0) == 0 ||
        request.rfind("GET /scrub", 0) == 0 ||
        request.rfind("GET /playback-info", 0) == 0 ||
        request.rfind("PUT /photo", 0) == 0 ||
        request.rfind("PUT /setProperty", 0) == 0 ||
        request.rfind("PUT /slideshows/1", 0) == 0 ||
        request.rfind("POST /play", 0) == 0 ||
        request.rfind("POST /scrub", 0) == 0 ||
        request.rfind("POST /rate", 0) == 0 ||
        request.rfind("POST /event", 0) == 0 ||
        request.rfind("POST /stop", 0) == 0) {
        return handleMediaRequest(socket, request);
    }

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
    } else if (request.find("FLUSH") == 0) {
        handleFlush(socket, cseq, request);
        notifyActivity("FLUSH");
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
        LOGW("Unknown control request, closing client connection: %s", requestLine.c_str());
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
        int audioSampleRate = mediaOrchestrator_->getAudioSampleRate();
        int audioChannels = mediaOrchestrator_->getAudioChannels();
        rtspHandler_->parseSetupParams(request, &videoWidth_, &videoHeight_, &audioSampleRate, &audioChannels);
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

void AirPlayServer::handleFlush(int socket, const std::string& cseq, const std::string& request) {
    if (rtspHandler_) {
        rtspHandler_->handleFlush(socket, cseq, request);
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

void AirPlayServer::parseSetupParams(const std::string& request) {
    if (rtspHandler_) {
        int audioSampleRate = mediaOrchestrator_->getAudioSampleRate();
        int audioChannels = mediaOrchestrator_->getAudioChannels();
        rtspHandler_->parseSetupParams(request, &videoWidth_, &videoHeight_, &audioSampleRate, &audioChannels);
    }
}

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

std::string AirPlayServer::extractHeader(const std::string& request, const std::string& header) {
    return rtspHandler_ ? rtspHandler_->extractHeader(request, header) : "";
}

std::string AirPlayServer::extractBody(const std::string& request) {
    return rtspHandler_ ? rtspHandler_->extractBody(request) : "";
}

void AirPlayServer::notifyActivity(const std::string& method) {
    if (!sessionAnnounced_ || !onActivity_) {
        return;
    }
    onActivity_(method);
}

bool AirPlayServer::handleMediaRequest(int socket, const std::string& request) {
    return routeDispatcher_->dispatchMediaRequest(socket, request, rtspHandler_.get());
}
