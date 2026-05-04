#include "VideoRouteHandler.h"
#include "../../request_utils.h"
#include <android/log.h>

#define TAG "AirPlay:VideoHandler"
#define LOGW(...) __android_log_print(ANDROID_LOG_WARN, TAG, __VA_ARGS__)

using airplay::request::sendHttpResponse;

bool VideoRouteHandler::handleRequest(int socket, const std::string& request, const std::string& routePath, const std::string& method, const std::string& sessionId, RTSPHandler* rtspHandler) {
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
             sessionId.empty() ? "none" : sessionId.c_str());
        if (onActivity_) onActivity_(routePath);
        return true;
    }

    return false;
}
