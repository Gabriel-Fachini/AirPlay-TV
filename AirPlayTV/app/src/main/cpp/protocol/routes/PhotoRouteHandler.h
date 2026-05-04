#pragma once
#include "RouteHandler.h"
#include "../../protocol/rtsp_handler.h"
#include <string>
#include <vector>
#include <unordered_map>
#include <functional>

class PhotoRouteHandler : public RouteHandler {
public:
    using ActivityCallback = std::function<void(const std::string& method)>;
    using PhotoPlaybackCallback = std::function<void(
        const std::string& clientIp,
        const std::string& sessionId,
        const std::string& assetKey,
        const std::string& transition,
        const std::vector<uint8_t>& imageData,
        bool isSlideshow)>;
    using SlideshowPlaybackCallback = std::function<void(
        const std::string& clientIp,
        const std::string& sessionId,
        const std::string& theme,
        int slideDurationSeconds,
        const std::string& state)>;
    using MediaStopCallback = std::function<void(const std::string& sessionId)>;

    PhotoRouteHandler();

    void setActivityCallback(ActivityCallback callback) { onActivity_ = std::move(callback); }
    void setPhotoPlaybackCallback(PhotoPlaybackCallback callback) { onPhotoPlayback_ = std::move(callback); }
    void setSlideshowPlaybackCallback(SlideshowPlaybackCallback callback) { onSlideshowPlayback_ = std::move(callback); }
    void setMediaStopCallback(MediaStopCallback callback) { onMediaStop_ = std::move(callback); }
    
    void setClientIp(const std::string& clientIp) { clientIp_ = clientIp; }

    bool handleRequest(int socket, const std::string& request, const std::string& routePath, const std::string& method, const std::string& sessionId, RTSPHandler* rtspHandler) override;

    void reset();

private:
    std::string clientIp_;
    bool slideshowActive_ = false;
    std::string slideshowTheme_;
    int slideshowDurationSeconds_ = 0;
    std::string lastAssetKey_;
    std::string lastTransition_;
    std::vector<uint8_t> lastDisplayedPhoto_;
    std::unordered_map<std::string, std::vector<uint8_t>> photoCache_;

    ActivityCallback onActivity_;
    PhotoPlaybackCallback onPhotoPlayback_;
    SlideshowPlaybackCallback onSlideshowPlayback_;
    MediaStopCallback onMediaStop_;
};
