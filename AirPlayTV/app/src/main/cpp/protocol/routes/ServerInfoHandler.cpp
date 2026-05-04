#include "ServerInfoHandler.h"
#include "../../request_utils.h"

using airplay::request::sendHttpResponse;

bool ServerInfoHandler::handleRequest(int socket, const std::string& request, const std::string& routePath, const std::string& method, const std::string& sessionId, RTSPHandler* rtspHandler) {
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
        if (onActivity_) onActivity_("GET /server-info");
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
        if (onActivity_) onActivity_("GET /slideshow-features");
        return true;
    }

    return false;
}
