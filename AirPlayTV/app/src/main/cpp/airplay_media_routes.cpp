#include "airplay_server.h"
#include "protocol/rtsp_handler.h"
#include "request_utils.h"
#include <android/log.h>

#define TAG "AirPlay:Server"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, TAG, __VA_ARGS__)
#define LOGW(...) __android_log_print(ANDROID_LOG_WARN, TAG, __VA_ARGS__)

using airplay::request::getPathWithoutQuery;
using airplay::request::getRequestLine;
using airplay::request::getRequestPath;
using airplay::request::parseXmlTagValue;
using airplay::request::sendHttpResponse;
using airplay::request::xmlEscape;

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

std::string AirPlayServer::extractHeader(const std::string& request, const std::string& header) {
    return rtspHandler_ ? rtspHandler_->extractHeader(request, header) : "";
}

std::string AirPlayServer::extractBody(const std::string& request) {
    return rtspHandler_ ? rtspHandler_->extractBody(request) : "";
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
