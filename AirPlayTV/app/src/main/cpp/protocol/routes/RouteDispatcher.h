#pragma once

#include "ServerInfoHandler.h"
#include "PhotoRouteHandler.h"
#include "VideoRouteHandler.h"
#include "../../protocol/rtsp_handler.h"
#include <memory>
#include <string>
#include <vector>

class RouteDispatcher {
public:
    using ActivityCallback = std::function<void(const std::string& method)>;
    using PhotoPlaybackCallback = PhotoRouteHandler::PhotoPlaybackCallback;
    using SlideshowPlaybackCallback = PhotoRouteHandler::SlideshowPlaybackCallback;
    using MediaStopCallback = PhotoRouteHandler::MediaStopCallback;

    RouteDispatcher();

    void setActivityCallback(ActivityCallback callback);
    void setPhotoPlaybackCallback(PhotoPlaybackCallback callback);
    void setSlideshowPlaybackCallback(SlideshowPlaybackCallback callback);
    void setMediaStopCallback(MediaStopCallback callback);

    void setClientIp(const std::string& clientIp) {
        photoHandler_->setClientIp(clientIp);
    }

    bool dispatchMediaRequest(int socket, const std::string& request, RTSPHandler* rtspHandler);

    void resetSessionState() {
        photoHandler_->reset();
    }

private:
    std::unique_ptr<ServerInfoHandler> serverInfoHandler_;
    std::unique_ptr<PhotoRouteHandler> photoHandler_;
    std::unique_ptr<VideoRouteHandler> videoHandler_;
};
