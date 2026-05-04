#include "RouteDispatcher.h"
#include "../../request_utils.h"
#include <android/log.h>

#define TAG "AirPlay:Dispatcher"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, TAG, __VA_ARGS__)
#define LOGW(...) __android_log_print(ANDROID_LOG_WARN, TAG, __VA_ARGS__)

using airplay::request::getPathWithoutQuery;
using airplay::request::getRequestLine;
using airplay::request::getRequestPath;
using airplay::request::sendHttpResponse;

RouteDispatcher::RouteDispatcher() {
    serverInfoHandler_ = std::make_unique<ServerInfoHandler>();
    photoHandler_ = std::make_unique<PhotoRouteHandler>();
    videoHandler_ = std::make_unique<VideoRouteHandler>();
}

void RouteDispatcher::setActivityCallback(ActivityCallback callback) {
    serverInfoHandler_->setActivityCallback(callback);
    photoHandler_->setActivityCallback(callback);
    videoHandler_->setActivityCallback(callback);
}

void RouteDispatcher::setPhotoPlaybackCallback(PhotoPlaybackCallback callback) {
    photoHandler_->setPhotoPlaybackCallback(std::move(callback));
}

void RouteDispatcher::setSlideshowPlaybackCallback(SlideshowPlaybackCallback callback) {
    photoHandler_->setSlideshowPlaybackCallback(std::move(callback));
}

void RouteDispatcher::setMediaStopCallback(MediaStopCallback callback) {
    photoHandler_->setMediaStopCallback(std::move(callback));
}

bool RouteDispatcher::dispatchMediaRequest(int socket, const std::string& request, RTSPHandler* rtspHandler) {
    const std::string requestLine = getRequestLine(request);
    const std::string requestPath = getRequestPath(requestLine);
    const std::string routePath = getPathWithoutQuery(requestPath);
    const std::string method = requestLine.substr(0, requestLine.find(' '));
    const std::string sessionId = rtspHandler ? rtspHandler->extractHeader(request, "X-Apple-Session-ID") : "";

    LOGI(
        "Media request: %s path=%s sessionId=%s",
        requestLine.c_str(),
        routePath.c_str(),
        sessionId.empty() ? "none" : sessionId.c_str());

    if (serverInfoHandler_->handleRequest(socket, request, routePath, method, sessionId, rtspHandler)) {
        return true;
    }

    if (photoHandler_->handleRequest(socket, request, routePath, method, sessionId, rtspHandler)) {
        return true;
    }

    if (videoHandler_->handleRequest(socket, request, routePath, method, sessionId, rtspHandler)) {
        return true;
    }

    LOGW("Unsupported media request path: method=%s path=%s sessionId=%s",
         method.c_str(),
         routePath.c_str(),
         sessionId.empty() ? "none" : sessionId.c_str());
    sendHttpResponse(socket, 404, "Not Found", "", "");
    return true;
}
