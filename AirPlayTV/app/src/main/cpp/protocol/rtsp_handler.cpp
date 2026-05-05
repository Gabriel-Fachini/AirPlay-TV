#include "rtsp_handler.h"
#include "protocol_constants.h"
#include <android/log.h>
#include <sys/socket.h>
#include <sstream>
#include <vector>

#define TAG "AirPlay:RTSP"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, TAG, __VA_ARGS__)
#define LOGW(...) __android_log_print(ANDROID_LOG_WARN, TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, TAG, __VA_ARGS__)

using namespace AirPlayProtocol;

RTSPHandler::RTSPHandler() {
}

RTSPHandler::~RTSPHandler() {
}

std::string RTSPHandler::extractHeader(const std::string& request, const std::string& header) {
    std::string searchStr = header + ": ";
    size_t pos = request.find(searchStr);
    
    if (pos == std::string::npos) {
        return "";
    }

    size_t start = pos + searchStr.length();
    size_t end = request.find("\r\n", start);
    
    if (end == std::string::npos) {
        return "";
    }

    return request.substr(start, end - start);
}

std::string RTSPHandler::extractBody(const std::string& request) {
    const size_t headersEnd = request.find("\r\n\r\n");
    if (headersEnd == std::string::npos) {
        return "";
    }
    return request.substr(headersEnd + 4);
}

void RTSPHandler::parseSetupParams(const std::string& request, int* videoWidth, int* videoHeight,
                                   int* audioSampleRate, int* audioChannels) {
    // Default values (1080p, 44.1kHz stereo)
    *videoWidth = 1920;
    *videoHeight = 1080;
    *audioSampleRate = 44100;
    *audioChannels = 2;
    
    LOGI("Parsed setup params: video=%dx%d, audio=%dHz %dch",
         *videoWidth, *videoHeight, *audioSampleRate, *audioChannels);
}

void RTSPHandler::handleInfo(int socket, const std::string& cseq) {
    std::ostringstream response;
    response << "RTSP/1.0 200 OK\r\n";
    response << "CSeq: " << cseq << "\r\n";
    response << "Content-Type: application/x-apple-binary-plist\r\n";
    response << "Content-Length: " << kInfoResponseBodySize << "\r\n";
    response << "Server: AirTunes/220.68\r\n";
    response << "\r\n";

    const std::string header = response.str();
    send(socket, header.c_str(), header.length(), 0);
    send(socket, kInfoResponseBody, kInfoResponseBodySize, 0);

}

void RTSPHandler::handleOptions(int socket, const std::string& cseq) {
    std::ostringstream response;
    response << "RTSP/1.0 200 OK\r\n";
    response << "CSeq: " << cseq << "\r\n";
    response << "Public: SETUP, RECORD, PAUSE, FLUSH, TEARDOWN, OPTIONS, GET_PARAMETER, SET_PARAMETER\r\n";
    response << "Server: AirPlay/220.68\r\n";
    response << "\r\n";

    std::string responseStr = response.str();
    send(socket, responseStr.c_str(), responseStr.length(), 0);
}

void RTSPHandler::handleSetup(int socket, const std::string& cseq, const std::string& request) {
    LOGI("Handling SETUP request");
    
    const std::string body = extractBody(request);
    bool ok = false;
    std::vector<uint8_t> responseBody;
    
    if (onSetup_) {
        responseBody = onSetup_(
            reinterpret_cast<const uint8_t*>(body.data()),
            body.size(),
            &ok);
    }

    if (!ok || responseBody.empty()) {
        LOGE("Java SETUP callback failed");
        std::ostringstream response;
        response << "RTSP/1.0 500 Internal Server Error\r\n";
        response << "CSeq: " << cseq << "\r\n";
        response << "Content-Length: 0\r\n";
        response << "Server: AirTunes/220.68\r\n";
        response << "\r\n";
        const std::string header = response.str();
        send(socket, header.c_str(), header.length(), 0);
        return;
    }

    std::ostringstream response;
    response << "RTSP/1.0 200 OK\r\n";
    response << "CSeq: " << cseq << "\r\n";
    response << "Content-Type: application/x-apple-binary-plist\r\n";
    response << "Content-Length: " << responseBody.size() << "\r\n";
    response << "Server: AirTunes/220.68\r\n";
    response << "\r\n";

    const std::string header = response.str();
    send(socket, header.c_str(), header.length(), 0);
    send(socket, responseBody.data(), responseBody.size(), 0);

}

void RTSPHandler::handleGetParameter(int socket, const std::string& cseq, const std::string& request) {
    const std::string contentType = extractHeader(request, "Content-Type");
    const std::string body = extractBody(request);
    std::string responseBody;
    std::string responseContentType;

    if (contentType == "text/parameters") {
        if (body.find("volume\r\n") != std::string::npos || body == "volume\n" || body == "volume") {
            responseContentType = "text/parameters";
            responseBody = "volume: 0.0\r\n";
        }
    } else if (!contentType.empty()) {
        LOGW("GET_PARAMETER with unsupported content type: %s", contentType.c_str());
    }

    std::ostringstream response;
    response << "RTSP/1.0 200 OK\r\n";
    response << "CSeq: " << cseq << "\r\n";
    if (!responseContentType.empty()) {
        response << "Content-Type: " << responseContentType << "\r\n";
    }
    response << "Content-Length: " << responseBody.size() << "\r\n";
    response << "Server: AirTunes/220.68\r\n";
    response << "\r\n";
    response << responseBody;

    const std::string responseStr = response.str();
    send(socket, responseStr.c_str(), responseStr.length(), 0);
}

void RTSPHandler::handleSetParameter(int socket, const std::string& cseq, const std::string& request) {
    const std::string contentType = extractHeader(request, "Content-Type");
    if (contentType != "text/parameters" &&
        contentType != "image/jpeg" &&
        contentType != "image/png" &&
        contentType != "application/x-dmap-tagged" &&
        !contentType.empty()) {
        LOGW("SET_PARAMETER with unsupported content type: %s", contentType.c_str());
    }

    std::ostringstream response;
    response << "RTSP/1.0 200 OK\r\n";
    response << "CSeq: " << cseq << "\r\n";
    response << "Content-Length: 0\r\n";
    response << "Server: AirTunes/220.68\r\n";
    response << "\r\n";

    const std::string responseStr = response.str();
    send(socket, responseStr.c_str(), responseStr.length(), 0);
}

void RTSPHandler::handleFeedback(int socket, const std::string& cseq) {
    std::ostringstream response;
    response << "RTSP/1.0 200 OK\r\n";
    response << "CSeq: " << cseq << "\r\n";
    response << "Content-Length: 0\r\n";
    response << "Server: AirTunes/220.68\r\n";
    response << "\r\n";

    const std::string responseStr = response.str();
    send(socket, responseStr.c_str(), responseStr.length(), 0);
}

void RTSPHandler::handleFlush(int socket, const std::string& cseq, const std::string& request) {
    int nextSequenceNumber = -1;
    const std::string rtpInfo = extractHeader(request, "RTP-Info");
    if (!rtpInfo.empty()) {
        const std::string prefix = "seq=";
        const size_t seqPos = rtpInfo.find(prefix);
        if (seqPos != std::string::npos) {
            nextSequenceNumber = std::stoi(rtpInfo.substr(seqPos + prefix.length()));
        }
    }

    std::ostringstream response;
    response << "RTSP/1.0 200 OK\r\n";
    response << "CSeq: " << cseq << "\r\n";
    response << "Content-Length: 0\r\n";
    response << "Server: AirTunes/220.68\r\n";
    response << "\r\n";

    const std::string responseStr = response.str();
    send(socket, responseStr.c_str(), responseStr.length(), 0);

    if (onFlush_) {
        onFlush_(nextSequenceNumber);
    }
}

void RTSPHandler::handleRecord(int socket, const std::string& cseq) {
    LOGI("Handling RECORD request - streaming started");

    std::ostringstream response;
    response << "RTSP/1.0 200 OK\r\n";
    response << "CSeq: " << cseq << "\r\n";
    response << "Audio-Latency: 11025\r\n";
    response << "Audio-Jack-Status: connected; type=analog\r\n";
    response << "\r\n";

    std::string responseStr = response.str();
    send(socket, responseStr.c_str(), responseStr.length(), 0);
    // Notify that streaming has started
    if (onRecord_) {
        onRecord_();
    }
}

void RTSPHandler::handleTeardown(int socket, const std::string& cseq) {
    LOGI("Handling TEARDOWN request");

    std::ostringstream response;
    response << "RTSP/1.0 200 OK\r\n";
    response << "CSeq: " << cseq << "\r\n";
    response << "\r\n";

    std::string responseStr = response.str();
    send(socket, responseStr.c_str(), responseStr.length(), 0);
}
