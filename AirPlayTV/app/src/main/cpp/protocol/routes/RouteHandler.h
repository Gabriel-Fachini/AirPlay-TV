#pragma once

#include <string>
#include <functional>
#include <memory>
#include <unordered_map>
#include <vector>

class RTSPHandler;

class RouteHandler {
public:
    virtual ~RouteHandler() = default;
    virtual bool handleRequest(int socket, const std::string& request, const std::string& routePath, const std::string& method, const std::string& sessionId, RTSPHandler* rtspHandler) = 0;
};
