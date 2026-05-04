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
#include <sstream>
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

namespace {

std::string trimCopy(const std::string& value) {
    const auto start = value.find_first_not_of(" \t\r\n");
    if (start == std::string::npos) {
        return "";
    }
    const auto end = value.find_last_not_of(" \t\r\n");
    return value.substr(start, end - start + 1);
}

std::string getRequestLine(const std::string& request) {
    const size_t lineEnd = request.find("\r\n");
    return request.substr(0, lineEnd);
}

std::string getRequestPath(const std::string& requestLine) {
    const size_t firstSpace = requestLine.find(' ');
    if (firstSpace == std::string::npos) {
        return "";
    }
    const size_t secondSpace = requestLine.find(' ', firstSpace + 1);
    if (secondSpace == std::string::npos) {
        return "";
    }
    return requestLine.substr(firstSpace + 1, secondSpace - firstSpace - 1);
}

std::string getPathWithoutQuery(const std::string& path) {
    const size_t queryStart = path.find('?');
    return queryStart == std::string::npos ? path : path.substr(0, queryStart);
}

std::string xmlEscape(const std::string& value) {
    std::string escaped;
    escaped.reserve(value.size());
    for (char ch : value) {
        switch (ch) {
            case '&': escaped += "&amp;"; break;
            case '<': escaped += "&lt;"; break;
            case '>': escaped += "&gt;"; break;
            case '"': escaped += "&quot;"; break;
            case '\'': escaped += "&apos;"; break;
            default: escaped.push_back(ch); break;
        }
    }
    return escaped;
}

void sendHttpResponse(
    int socket,
    int statusCode,
    const std::string& reason,
    const std::string& contentType,
    const std::string& body,
    const std::vector<std::string>& extraHeaders = {}) {
    std::ostringstream response;
    response << "HTTP/1.1 " << statusCode << ' ' << reason << "\r\n";
    if (!contentType.empty()) {
        response << "Content-Type: " << contentType << "\r\n";
    }
    for (const auto& header : extraHeaders) {
        response << header << "\r\n";
    }
    response << "Content-Length: " << body.size() << "\r\n";
    response << "\r\n";
    response << body;

    const std::string payload = response.str();
    send(socket, payload.c_str(), payload.length(), 0);
}

std::string parseXmlTagValue(const std::string& xml, const std::string& key) {
    const std::string keyToken = "<key>" + key + "</key>";
    const size_t keyPos = xml.find(keyToken);
    if (keyPos == std::string::npos) {
        return "";
    }

    const size_t stringStart = xml.find("<string>", keyPos);
    if (stringStart != std::string::npos) {
        const size_t valueStart = stringStart + 8;
        const size_t valueEnd = xml.find("</string>", valueStart);
        if (valueEnd != std::string::npos) {
            return xml.substr(valueStart, valueEnd - valueStart);
        }
    }

    const size_t integerStart = xml.find("<integer>", keyPos);
    if (integerStart != std::string::npos) {
        const size_t valueStart = integerStart + 9;
        const size_t valueEnd = xml.find("</integer>", valueStart);
        if (valueEnd != std::string::npos) {
            return xml.substr(valueStart, valueEnd - valueStart);
        }
    }

    return "";
}

}  // namespace

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
    , mediaSessionAnnounced_(false)
    , slideshowActive_(false)
    , slideshowDurationSeconds_(0)
    , rtspHandler_(std::make_unique<RTSPHandler>())
    , fairplayHandler_(std::make_unique<FairPlayHandler>())
{
    LOGI("AirPlayServer created");
    resetAudioSessionConfig();
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
        mediaSessionAnnounced_ = false;
        resetMediaPlaybackState();
        resetAudioSessionConfig();
        LOGI("Client connected from %s", clientIp_.c_str());

        handleClient(clientSocket);

        close(clientSocket);
        stopMirrorVideoServer();
        stopRTPReceiver();
        
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
                const std::string requestLine = getRequestLine(request);
                if (requestLine.rfind("TEARDOWN", 0) == 0) {
                    LOGI("Client requested normal session teardown");
                } else {
                    LOGW("Failed to handle RTSP request: %s", requestLine.c_str());
                }
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

    const std::string requestLine = getRequestLine(request);
    const std::string requestPath = getRequestPath(requestLine);
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

bool AirPlayServer::handleMediaRequest(int socket, const std::string& request) {
    const std::string requestLine = getRequestLine(request);
    const std::string requestPath = getRequestPath(requestLine);
    const std::string routePath = getPathWithoutQuery(requestPath);
    const std::string method = requestLine.substr(0, requestLine.find(' '));
    const std::string sessionId = extractHeader(request, "X-Apple-Session-ID");
    const std::string body = extractBody(request);

    LOGI(
        "Media request: %s path=%s sessionId=%s bodyBytes=%zu",
        requestLine.c_str(),
        routePath.c_str(),
        sessionId.empty() ? "none" : sessionId.c_str(),
        body.size());

    mediaSessionId_ = sessionId;
    mediaSessionAnnounced_ = true;

    if (routePath == "/server-info") {
        static const char* kServerInfoBody =
            "<?xml version=\"1.0\" encoding=\"UTF-8\"?>"
            "<!DOCTYPE plist PUBLIC \"-//Apple//DTD PLIST 1.0//EN\" "
            "\"http://www.apple.com/DTDs/PropertyList-1.0.dtd\">"
            "<plist version=\"1.0\"><dict>"
            "<key>deviceid</key><string>50:0F:A5:7F:57:FA</string>"
            "<key>features</key><integer>129964703714</integer>"
            "<key>model</key><string>AppleTV3,2</string>"
            "<key>protovers</key><string>1.0</string>"
            "<key>srcvers</key><string>220.68</string>"
            "</dict></plist>";
        sendHttpResponse(socket, 200, "OK", "text/x-apple-plist+xml", kServerInfoBody);
        notifyActivity("GET /server-info");
        return true;
    }

    if (routePath == "/slideshow-features") {
        static const char* kSlideshowFeaturesBody =
            "<?xml version=\"1.0\" encoding=\"UTF-8\"?>"
            "<!DOCTYPE plist PUBLIC \"-//Apple//DTD PLIST 1.0//EN\" "
            "\"http://www.apple.com/DTDs/PropertyList-1.0.dtd\">"
            "<plist version=\"1.0\"><dict><key>themes</key><array>"
            "<dict><key>key</key><string>None</string><key>name</key><string>None</string></dict>"
            "<dict><key>key</key><string>Dissolve</string><key>name</key><string>Dissolve</string></dict>"
            "<dict><key>key</key><string>Classic</string><key>name</key><string>Classic</string></dict>"
            "</array></dict></plist>";
        sendHttpResponse(socket, 200, "OK", "text/x-apple-plist+xml", kSlideshowFeaturesBody);
        notifyActivity("GET /slideshow-features");
        return true;
    }

    if (routePath == "/photo") {
        const std::string assetKey = extractHeader(request, "X-Apple-AssetKey");
        const std::string assetAction = extractHeader(request, "X-Apple-AssetAction");
        const std::string transition = extractHeader(request, "X-Apple-Transition");
        std::vector<uint8_t> photoData;

        LOGI(
            "Photo request details: sessionId=%s action=%s assetKey=%s transition=%s slideshowActive=%s",
            mediaSessionId_.empty() ? "none" : mediaSessionId_.c_str(),
            assetAction.empty() ? "display" : assetAction.c_str(),
            assetKey.empty() ? "none" : assetKey.c_str(),
            transition.empty() ? "none" : transition.c_str(),
            slideshowActive_ ? "true" : "false");

        if (assetAction == "displayCached") {
            const auto cached = photoCache_.find(assetKey);
            if (cached == photoCache_.end()) {
                sendHttpResponse(socket, 412, "Precondition Failed", "", "");
                LOGW("Requested cached photo missing: sessionId=%s assetKey=%s",
                     mediaSessionId_.empty() ? "none" : mediaSessionId_.c_str(),
                     assetKey.c_str());
                notifyActivity("PUT /photo");
                return true;
            }
            photoData = cached->second;
        } else {
            photoData.assign(body.begin(), body.end());
            if (assetAction == "cacheOnly" && !assetKey.empty()) {
                photoCache_[assetKey] = photoData;
                LOGI("Photo cached only: sessionId=%s assetKey=%s bytes=%zu cacheSize=%zu",
                     mediaSessionId_.empty() ? "none" : mediaSessionId_.c_str(),
                     assetKey.c_str(),
                     photoData.size(),
                     photoCache_.size());
            }
        }

        if (photoData.empty()) {
            sendHttpResponse(socket, 400, "Bad Request", "", "");
            LOGW("Photo request without usable JPEG data: sessionId=%s action=%s assetKey=%s",
                 mediaSessionId_.empty() ? "none" : mediaSessionId_.c_str(),
                 assetAction.empty() ? "display" : assetAction.c_str(),
                 assetKey.empty() ? "none" : assetKey.c_str());
            notifyActivity("PUT /photo");
            return true;
        }

        if (!assetKey.empty() && assetAction != "displayCached") {
            photoCache_[assetKey] = photoData;
        }
        lastDisplayedPhoto_ = photoData;
        lastAssetKey_ = assetKey;
        lastTransition_ = transition;

        LOGI("Photo ready for playback: sessionId=%s bytes=%zu assetKey=%s cached=%zu slideshowActive=%s",
             mediaSessionId_.empty() ? "none" : mediaSessionId_.c_str(),
             photoData.size(),
             assetKey.empty() ? "none" : assetKey.c_str(),
             photoCache_.size(),
             slideshowActive_ ? "true" : "false");

        if (onPhotoPlayback_) {
            onPhotoPlayback_(
                clientIp_,
                mediaSessionId_,
                assetKey,
                transition,
                photoData,
                slideshowActive_);
        }

        sendHttpResponse(socket, 200, "OK", "", "");
        notifyActivity("PUT /photo");
        return true;
    }

    if (routePath == "/slideshows/1") {
        const std::string state = parseXmlTagValue(body, "state");
        const std::string theme = parseXmlTagValue(body, "theme");
        const std::string slideDuration = parseXmlTagValue(body, "slideDuration");

        slideshowTheme_ = theme;
        slideshowDurationSeconds_ = slideDuration.empty() ? 0 : std::stoi(slideDuration);
        slideshowActive_ = state == "playing";

        LOGI("Slideshow request parsed: sessionId=%s state=%s theme=%s duration=%d cachedPhotos=%zu lastAsset=%s",
             mediaSessionId_.empty() ? "none" : mediaSessionId_.c_str(),
             state.empty() ? "playing" : state.c_str(),
             slideshowTheme_.empty() ? "none" : slideshowTheme_.c_str(),
             slideshowDurationSeconds_,
             photoCache_.size(),
             lastAssetKey_.empty() ? "none" : lastAssetKey_.c_str());

        if (onSlideshowPlayback_) {
            onSlideshowPlayback_(
                clientIp_,
                mediaSessionId_,
                slideshowTheme_,
                slideshowDurationSeconds_,
                state.empty() ? "playing" : state);
        }

        sendHttpResponse(
            socket,
            200,
            "OK",
            "text/x-apple-plist+xml",
            "<?xml version=\"1.0\" encoding=\"UTF-8\"?>"
            "<!DOCTYPE plist PUBLIC \"-//Apple//DTD PLIST 1.0//EN\" "
            "\"http://www.apple.com/DTDs/PropertyList-1.0.dtd\">"
            "<plist version=\"1.0\"><dict/></plist>");
        notifyActivity("PUT /slideshows/1");
        return true;
    }

    if (routePath == "/stop") {
        LOGI("Stopping media playback: sessionId=%s slideshowActive=%s lastAsset=%s",
             mediaSessionId_.empty() ? "none" : mediaSessionId_.c_str(),
             slideshowActive_ ? "true" : "false",
             lastAssetKey_.empty() ? "none" : lastAssetKey_.c_str());
        if (onMediaStop_) {
            onMediaStop_(mediaSessionId_);
        }
        resetMediaPlaybackState();
        sendHttpResponse(socket, 200, "OK", "", "");
        notifyActivity("POST /stop");
        return true;
    }

    if (routePath == "/play" ||
        routePath == "/scrub" ||
        routePath == "/rate" ||
        routePath == "/playback-info" ||
        routePath == "/event" ||
        routePath == "/setProperty") {
        sendHttpResponse(socket, 501, "Not Implemented", "", "");
        LOGW("Video HTTP playback request gracefully rejected: method=%s path=%s sessionId=%s",
             method.c_str(),
             routePath.c_str(),
             mediaSessionId_.empty() ? "none" : mediaSessionId_.c_str());
        notifyActivity(routePath);
        return true;
    }

    LOGW("Unsupported media request path: method=%s path=%s sessionId=%s",
         method.c_str(),
         routePath.c_str(),
         mediaSessionId_.empty() ? "none" : mediaSessionId_.c_str());
    sendHttpResponse(socket, 404, "Not Found", "", "");
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
    if ((!sessionAnnounced_ && !mediaSessionAnnounced_) || !onActivity_) {
        return;
    }
    onActivity_(method);
}

void AirPlayServer::resetMediaPlaybackState() {
    mediaSessionId_.clear();
    slideshowActive_ = false;
    slideshowTheme_.clear();
    slideshowDurationSeconds_ = 0;
    lastAssetKey_.clear();
    lastTransition_.clear();
    lastDisplayedPhoto_.clear();
    photoCache_.clear();
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
    rtpReceiver_->setAudioSyncCallback(onAudioSync_);
    rtpReceiver_->setErrorCallback(onError_);
    rtpReceiver_->setAudioConfig(audioSessionConfig_.compressionType, audioSessionConfig_.sampleRate);

    if (rtpReceiver_->start(
            audioSessionConfig_.localDataPort,
            audioSessionConfig_.localControlPort,
            audioSessionConfig_.localTimingPort)) {
        rtpRunning_ = true;
        LOGI("RTP receiver started successfully");
    } else {
        LOGE("Failed to start RTP receiver");
        rtpReceiver_.reset();
    }
}

void AirPlayServer::updateAudioSessionConfig(const AudioSessionConfig& config) {
    audioSessionConfig_ = config;
    audioSampleRate_ = config.sampleRate;
    audioChannels_ = config.channels;
    if (rtpReceiver_) {
        rtpReceiver_->setAudioConfig(audioSessionConfig_.compressionType, audioSessionConfig_.sampleRate);
    }
}

void AirPlayServer::resetAudioSessionConfig() {
    audioSessionConfig_ = AudioSessionConfig();
    audioSampleRate_ = audioSessionConfig_.sampleRate;
    audioChannels_ = audioSessionConfig_.channels;
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
