#include "fairplay_handler.h"
#include "protocol_constants.h"
#include <android/log.h>
#include <sys/socket.h>
#include <sstream>
#include <vector>

extern "C" {
#include "third_party/playfair/playfair.h"
}

#define TAG "AirPlay:FairPlay"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, TAG, __VA_ARGS__)

using namespace AirPlayProtocol;

FairPlayHandler::FairPlayHandler() {
    LOGI("FairPlayHandler created");
}

FairPlayHandler::~FairPlayHandler() {
    LOGI("FairPlayHandler destroyed");
}

void FairPlayHandler::handlePairSetup(int socket, const std::string& cseq, const std::string& request) {
    LOGI("Handling POST /pair-setup request");

    // Extract body
    size_t bodyStart = request.find("\r\n\r\n");
    if (bodyStart == std::string::npos) {
        LOGE("No body in pair-setup request");
        return;
    }
    bodyStart += 4;
    const std::string body = request.substr(bodyStart);

    // Call Java callback
    bool ok = false;
    std::vector<uint8_t> responseBody;
    if (onPairSetup_) {
        responseBody = onPairSetup_(
            reinterpret_cast<const uint8_t*>(body.data()),
            body.size(),
            &ok);
    }

    if (!ok) {
        LOGE("Java pair-setup callback failed");
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
    response << "Content-Type: application/octet-stream\r\n";
    response << "Content-Length: " << responseBody.size() << "\r\n";
    response << "Server: AirTunes/220.68\r\n";
    response << "\r\n";

    const std::string header = response.str();
    send(socket, header.c_str(), header.length(), 0);
    if (!responseBody.empty()) {
        send(socket, responseBody.data(), responseBody.size(), 0);
    }

    LOGI("Sent POST /pair-setup response (%zu bytes)", responseBody.size());
}

void FairPlayHandler::handlePairVerify(int socket, const std::string& cseq, const std::string& request) {
    LOGI("Handling POST /pair-verify request");

    // Extract body
    size_t bodyStart = request.find("\r\n\r\n");
    if (bodyStart == std::string::npos) {
        LOGE("No body in pair-verify request");
        return;
    }
    bodyStart += 4;
    const std::string body = request.substr(bodyStart);

    // Call Java callback
    bool ok = false;
    std::vector<uint8_t> responseBody;
    if (onPairVerify_) {
        responseBody = onPairVerify_(
            reinterpret_cast<const uint8_t*>(body.data()),
            body.size(),
            &ok);
    }

    if (!ok) {
        LOGE("Java pair-verify callback failed");
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
    response << "Content-Type: application/octet-stream\r\n";
    response << "Content-Length: " << responseBody.size() << "\r\n";
    response << "Server: AirTunes/220.68\r\n";
    response << "\r\n";

    const std::string header = response.str();
    send(socket, header.c_str(), header.length(), 0);
    if (!responseBody.empty()) {
        send(socket, responseBody.data(), responseBody.size(), 0);
    }

    LOGI("Sent POST /pair-verify response (%zu bytes)", responseBody.size());
}

void FairPlayHandler::handleFairPlaySetup(int socket, const std::string& cseq, const std::string& request) {
    LOGI("Handling POST /fp-setup request");

    // Extract body
    size_t bodyStart = request.find("\r\n\r\n");
    if (bodyStart == std::string::npos) {
        LOGE("No body in fp-setup request");
        return;
    }
    bodyStart += 4;
    const std::string body = request.substr(bodyStart);

    std::vector<uint8_t> responseBody;

    if (body.size() == kFairPlaySetupRequestSize) {
        const auto* requestBytes = reinterpret_cast<const unsigned char*>(body.data());
        if (requestBytes[4] != 0x03) {
            LOGE("Unsupported FairPlay version in setup message: %u", requestBytes[4]);
        } else if (requestBytes[14] > 3) {
            LOGE("Unsupported FairPlay mode in setup message: %u", requestBytes[14]);
        } else {
            responseBody.assign(
                kFairPlayReplies[requestBytes[14]],
                kFairPlayReplies[requestBytes[14]] + kFairPlaySetupResponseSize
            );
        }
    } else if (body.size() == kFairPlayHandshakeRequestSize) {
        const auto* requestBytes = reinterpret_cast<const unsigned char*>(body.data());
        if (requestBytes[4] != 0x03) {
            LOGE("Unsupported FairPlay version in handshake message: %u", requestBytes[4]);
        } else {
            fairPlayKeyMessage_.assign(requestBytes, requestBytes + body.size());
            responseBody.assign(kFairPlayHeader, kFairPlayHeader + sizeof(kFairPlayHeader));
            responseBody.insert(
                responseBody.end(),
                requestBytes + 144,
                requestBytes + 164
            );
        }
    } else {
        LOGE("Invalid FairPlay setup body size: %zu", body.size());
    }

    if (responseBody.empty()) {
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
    response << "Content-Type: application/octet-stream\r\n";
    response << "Content-Length: " << responseBody.size() << "\r\n";
    response << "Server: AirTunes/220.68\r\n";
    response << "\r\n";

    const std::string header = response.str();
    send(socket, header.c_str(), header.length(), 0);
    send(socket, responseBody.data(), responseBody.size(), 0);

    LOGI("Sent POST /fp-setup response (%zu bytes)", responseBody.size());
}

bool FairPlayHandler::decryptAesKey(
    const uint8_t* encryptedKey,
    size_t size,
    std::vector<uint8_t>* outKey) const {
    
    if (encryptedKey == nullptr || outKey == nullptr) {
        return false;
    }

    if (size != 72 || fairPlayKeyMessage_.size() != kFairPlayHandshakeRequestSize) {
        LOGE("Cannot decrypt FairPlay AES key: invalid input or missing fp-setup handshake");
        return false;
    }

    uint8_t keyOut[16] = {};
    playfair_decrypt(
        const_cast<unsigned char*>(fairPlayKeyMessage_.data()),
        const_cast<unsigned char*>(encryptedKey),
        keyOut);

    outKey->assign(keyOut, keyOut + sizeof(keyOut));
    return true;
}
