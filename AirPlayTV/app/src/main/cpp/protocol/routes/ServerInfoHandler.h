#pragma once
#include "RouteHandler.h"

class ServerInfoHandler : public RouteHandler {
public:
    using ActivityCallback = std::function<void(const std::string& method)>;
    void setActivityCallback(ActivityCallback callback) { onActivity_ = std::move(callback); }

    bool handleRequest(int socket, const std::string& request, const std::string& routePath, const std::string& method, const std::string& sessionId, RTSPHandler* rtspHandler) override;

private:
    ActivityCallback onActivity_;
};
