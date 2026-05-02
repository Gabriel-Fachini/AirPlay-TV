#ifndef RTSP_HANDLER_H
#define RTSP_HANDLER_H

#include <string>
#include <functional>
#include <cstdint>

/**
 * Handles RTSP protocol requests for AirPlay
 */
class RTSPHandler {
public:
    using SetupCallback = std::function<std::vector<uint8_t>(const uint8_t*, size_t, bool*)>;
    using ConnectionCallback = std::function<void()>;

    RTSPHandler();
    ~RTSPHandler();

    // RTSP method handlers
    void handleInfo(int socket, const std::string& cseq);
    void handleOptions(int socket, const std::string& cseq);
    void handleSetup(int socket, const std::string& cseq, const std::string& request);
    void handleGetParameter(int socket, const std::string& cseq, const std::string& request);
    void handleSetParameter(int socket, const std::string& cseq, const std::string& request);
    void handleFeedback(int socket, const std::string& cseq);
    void handleRecord(int socket, const std::string& cseq);
    void handleTeardown(int socket, const std::string& cseq);

    // Utility methods
    std::string extractHeader(const std::string& request, const std::string& header);
    std::string extractBody(const std::string& request);
    void parseSetupParams(const std::string& request, int* videoWidth, int* videoHeight,
                         int* audioSampleRate, int* audioChannels);

    // Set callbacks
    void setSetupCallback(SetupCallback callback) { onSetup_ = callback; }
    void setRecordCallback(ConnectionCallback callback) { onRecord_ = callback; }

private:
    SetupCallback onSetup_;
    ConnectionCallback onRecord_;
};

#endif // RTSP_HANDLER_H
