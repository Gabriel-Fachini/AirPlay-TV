#ifndef AIRPLAY_REQUEST_UTILS_H
#define AIRPLAY_REQUEST_UTILS_H

#include <sys/socket.h>
#include <sstream>
#include <string>
#include <vector>

namespace airplay::request {

inline std::string getRequestLine(const std::string& request) {
    const size_t lineEnd = request.find("\r\n");
    return request.substr(0, lineEnd);
}

inline std::string getRequestPath(const std::string& requestLine) {
    const size_t firstSpace = requestLine.find(' ');
    if (firstSpace == std::string::npos) {
        return "";
    }
    const size_t secondSpace = requestLine.find(' ', firstSpace + 1);
    if (secondSpace == std::string::npos) {
        return "";
    }
    return requestLine.substr(firstSpace + 1, secondSpace - firstSpace - 1);
}

inline std::string getPathWithoutQuery(const std::string& path) {
    const size_t queryStart = path.find('?');
    return queryStart == std::string::npos ? path : path.substr(0, queryStart);
}

inline std::string xmlEscape(const std::string& value) {
    std::string escaped;
    escaped.reserve(value.size());
    for (char ch : value) {
        switch (ch) {
            case '&': escaped += "&amp;"; break;
            case '<': escaped += "&lt;"; break;
            case '>': escaped += "&gt;"; break;
            case '"': escaped += "&quot;"; break;
            case '\'': escaped += "&apos;"; break;
            default: escaped.push_back(ch); break;
        }
    }
    return escaped;
}

inline void sendHttpResponse(
    int socket,
    int statusCode,
    const std::string& reason,
    const std::string& contentType,
    const std::string& body,
    const std::vector<std::string>& extraHeaders = {}) {
    std::ostringstream response;
    response << "HTTP/1.1 " << statusCode << ' ' << reason << "\r\n";
    if (!contentType.empty()) {
        response << "Content-Type: " << contentType << "\r\n";
    }
    for (const auto& header : extraHeaders) {
        response << header << "\r\n";
    }
    response << "Content-Length: " << body.size() << "\r\n";
    response << "\r\n";
    response << body;

    const std::string payload = response.str();
    send(socket, payload.c_str(), payload.length(), 0);
}

inline std::string parseXmlTagValue(const std::string& xml, const std::string& key) {
    const std::string keyToken = "<key>" + key + "</key>";
    const size_t keyPos = xml.find(keyToken);
    if (keyPos == std::string::npos) {
        return "";
    }

    const size_t stringStart = xml.find("<string>", keyPos);
    if (stringStart != std::string::npos) {
        const size_t valueStart = stringStart + 8;
        const size_t valueEnd = xml.find("</string>", valueStart);
        if (valueEnd != std::string::npos) {
            return xml.substr(valueStart, valueEnd - valueStart);
        }
    }

    const size_t integerStart = xml.find("<integer>", keyPos);
    if (integerStart != std::string::npos) {
        const size_t valueStart = integerStart + 9;
        const size_t valueEnd = xml.find("</integer>", valueStart);
        if (valueEnd != std::string::npos) {
            return xml.substr(valueStart, valueEnd - valueStart);
        }
    }

    return "";
}

}  // namespace airplay::request

#endif  // AIRPLAY_REQUEST_UTILS_H
