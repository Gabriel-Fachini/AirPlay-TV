#include "PhotoRouteHandler.h"
#include "../../request_utils.h"
#include <android/log.h>

#define TAG "AirPlay:PhotoHandler"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, TAG, __VA_ARGS__)
#define LOGW(...) __android_log_print(ANDROID_LOG_WARN, TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, TAG, __VA_ARGS__)

using airplay::request::sendHttpResponse;
using airplay::request::parseXmlTagValue;

PhotoRouteHandler::PhotoRouteHandler() = default;

void PhotoRouteHandler::reset() {
    slideshowActive_ = false;
    slideshowTheme_.clear();
    slideshowDurationSeconds_ = 0;
    lastAssetKey_.clear();
    lastTransition_.clear();
    lastDisplayedPhoto_.clear();
    photoCache_.clear();
}

bool PhotoRouteHandler::handleRequest(int socket, const std::string& request, const std::string& routePath, const std::string& method, const std::string& sessionId, RTSPHandler* rtspHandler) {
    if (!rtspHandler) {
        LOGE("RTSPHandler is null");
        return false;
    }

    if (routePath == "/photo") {
        const std::string assetKey = rtspHandler->extractHeader(request, "X-Apple-AssetKey");
        const std::string assetAction = rtspHandler->extractHeader(request, "X-Apple-AssetAction");
        const std::string transition = rtspHandler->extractHeader(request, "X-Apple-Transition");
        const std::string body = rtspHandler->extractBody(request);
        std::vector<uint8_t> photoData;

        LOGI(
            "Photo request details: sessionId=%s action=%s assetKey=%s transition=%s slideshowActive=%s",
            sessionId.empty() ? "none" : sessionId.c_str(),
            assetAction.empty() ? "display" : assetAction.c_str(),
            assetKey.empty() ? "none" : assetKey.c_str(),
            transition.empty() ? "none" : transition.c_str(),
            slideshowActive_ ? "true" : "false");

        if (assetAction == "displayCached") {
            const auto cached = photoCache_.find(assetKey);
            if (cached == photoCache_.end()) {
                sendHttpResponse(socket, 412, "Precondition Failed", "", "");
                LOGW("Requested cached photo missing: sessionId=%s assetKey=%s",
                     sessionId.empty() ? "none" : sessionId.c_str(),
                     assetKey.c_str());
                if (onActivity_) onActivity_("PUT /photo");
                return true;
            }
            photoData = cached->second;
        } else {
            photoData.assign(body.begin(), body.end());
            if (assetAction == "cacheOnly" && !assetKey.empty()) {
                photoCache_[assetKey] = photoData;
                LOGI("Photo cached only: sessionId=%s assetKey=%s bytes=%zu cacheSize=%zu",
                     sessionId.empty() ? "none" : sessionId.c_str(),
                     assetKey.c_str(),
                     photoData.size(),
                     photoCache_.size());
            }
        }

        if (photoData.empty()) {
            sendHttpResponse(socket, 400, "Bad Request", "", "");
            LOGW("Photo request without usable JPEG data: sessionId=%s action=%s assetKey=%s",
                 sessionId.empty() ? "none" : sessionId.c_str(),
                 assetAction.empty() ? "display" : assetAction.c_str(),
                 assetKey.empty() ? "none" : assetKey.c_str());
            if (onActivity_) onActivity_("PUT /photo");
            return true;
        }

        if (!assetKey.empty() && assetAction != "displayCached") {
            photoCache_[assetKey] = photoData;
        }
        lastDisplayedPhoto_ = photoData;
        lastAssetKey_ = assetKey;
        lastTransition_ = transition;

        LOGI("Photo ready for playback: sessionId=%s bytes=%zu assetKey=%s cached=%zu slideshowActive=%s",
             sessionId.empty() ? "none" : sessionId.c_str(),
             photoData.size(),
             assetKey.empty() ? "none" : assetKey.c_str(),
             photoCache_.size(),
             slideshowActive_ ? "true" : "false");

        if (onPhotoPlayback_) {
            onPhotoPlayback_(
                clientIp_,
                sessionId,
                assetKey,
                transition,
                photoData,
                slideshowActive_);
        }

        sendHttpResponse(socket, 200, "OK", "", "");
        if (onActivity_) onActivity_("PUT /photo");
        return true;
    }

    if (routePath == "/slideshows/1") {
        const std::string body = rtspHandler->extractBody(request);
        const std::string state = parseXmlTagValue(body, "state");
        const std::string theme = parseXmlTagValue(body, "theme");
        const std::string slideDuration = parseXmlTagValue(body, "slideDuration");

        slideshowTheme_ = theme;
        slideshowDurationSeconds_ = slideDuration.empty() ? 0 : std::stoi(slideDuration);
        slideshowActive_ = state == "playing";

        LOGI("Slideshow request parsed: sessionId=%s state=%s theme=%s duration=%d cachedPhotos=%zu lastAsset=%s",
             sessionId.empty() ? "none" : sessionId.c_str(),
             state.empty() ? "playing" : state.c_str(),
             slideshowTheme_.empty() ? "none" : slideshowTheme_.c_str(),
             slideshowDurationSeconds_,
             photoCache_.size(),
             lastAssetKey_.empty() ? "none" : lastAssetKey_.c_str());

        if (onSlideshowPlayback_) {
            onSlideshowPlayback_(
                clientIp_,
                sessionId,
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
        if (onActivity_) onActivity_("PUT /slideshows/1");
        return true;
    }

    if (routePath == "/stop") {
        LOGI("Stopping media playback: sessionId=%s slideshowActive=%s lastAsset=%s",
             sessionId.empty() ? "none" : sessionId.c_str(),
             slideshowActive_ ? "true" : "false",
             lastAssetKey_.empty() ? "none" : lastAssetKey_.c_str());
        if (onMediaStop_) {
            onMediaStop_(sessionId);
        }
        reset();
        sendHttpResponse(socket, 200, "OK", "", "");
        if (onActivity_) onActivity_("POST /stop");
        return true;
    }

    return false;
}
