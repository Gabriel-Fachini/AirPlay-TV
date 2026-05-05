#ifndef AIRPLAY_SERVER_H
#define AIRPLAY_SERVER_H

#include "SessionManager.h"
#include "MediaOrchestrator.h"
#include "../protocol/routes/RouteDispatcher.h"
#include <string>
#include <functional>
#include <memory>
#include <unordered_map>
#include <vector>

class RTSPHandler;
class FairPlayHandler;

/**
 * Servidor RTSP/AirPlay simplificado
 * Implementa handshake básico do protocolo AirPlay
 */
class AirPlayServer {
public:
    using AudioSessionConfig = MediaOrchestrator::AudioSessionConfig;

    // Callbacks para eventos
    using ConnectionCallback = std::function<void(const std::string& clientIp)>;
    using DisconnectionCallback = std::function<void()>;
    using ActivityCallback = std::function<void(const std::string& method)>;
    using VideoDataCallback = std::function<void(const uint8_t* data, size_t size, uint32_t timestamp)>;
    using AudioDataCallback = std::function<void(const uint8_t* data, size_t size, uint32_t timestamp)>;
    using AudioSyncCallback = std::function<void(uint32_t rtpSync, uint64_t remoteNtpUs, uint64_t localNtpUs, bool initial)>;
    using ErrorCallback = std::function<void(const std::string& error)>;
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

    AirPlayServer();
    ~AirPlayServer();

    // Controle do servidor
    bool start(int port);
    void stop();
    bool isRunning() const { return sessionManager_->isRunning(); }

    // Configurar callbacks
    void setConnectionCallback(ConnectionCallback callback) { onConnection_ = callback; }
    void setDisconnectionCallback(DisconnectionCallback callback) { sessionManager_->setClientDisconnectedCallback(callback); }
    void setActivityCallback(ActivityCallback callback) { onActivity_ = callback; }
    void setVideoDataCallback(VideoDataCallback callback) { mediaOrchestrator_->setVideoDataCallback(callback); }
    void setAudioDataCallback(AudioDataCallback callback) { mediaOrchestrator_->setAudioDataCallback(callback); }
    void setAudioSyncCallback(AudioSyncCallback callback) { mediaOrchestrator_->setAudioSyncCallback(callback); }
    void setErrorCallback(ErrorCallback callback) { 
        onError_ = callback; 
        mediaOrchestrator_->setErrorCallback(callback); 
        sessionManager_->setErrorCallback(callback); 
    }
    void setPhotoPlaybackCallback(PhotoPlaybackCallback callback) { routeDispatcher_->setPhotoPlaybackCallback(callback); }
    void setSlideshowPlaybackCallback(SlideshowPlaybackCallback callback) { routeDispatcher_->setSlideshowPlaybackCallback(callback); }
    void setMediaStopCallback(MediaStopCallback callback) { routeDispatcher_->setMediaStopCallback(callback); }
    void setMirroringVideoPacketCallback(std::function<void(int, const uint8_t*, size_t)> callback) { 
        mediaOrchestrator_->setMirroringVideoPacketCallback(callback); 
    }

    // Informações da sessão
    std::string getClientIp() const { return clientIp_; }
    int getVideoWidth() const { return videoWidth_; }
    int getVideoHeight() const { return videoHeight_; }
    int getAudioSampleRate() const { return mediaOrchestrator_->getAudioSampleRate(); }
    int getAudioChannels() const { return mediaOrchestrator_->getAudioChannels(); }
    AudioSessionConfig getAudioSessionConfig() const { return mediaOrchestrator_->getAudioSessionConfig(); }
    AudioSessionConfig prepareAudioSession(const AudioSessionConfig& config) { return mediaOrchestrator_->prepareAudioSession(config, clientIp_); }
    void updateAudioSessionConfig(const AudioSessionConfig& config) { mediaOrchestrator_->updateAudioSessionConfig(config); }
    void resetAudioSessionConfig() { mediaOrchestrator_->resetAudioSessionConfig(); }
    void resetSessionState();

    int startMirrorVideoServer() { return mediaOrchestrator_->startMirrorVideoServer(); }
    bool decryptFairPlayAesKey(const uint8_t* encryptedKey, size_t size, std::vector<uint8_t>* outKey);

private:
    void handleClient(int clientSocket, const std::string& clientIp);
    bool handleRTSPRequest(int socket, const std::string& request);
    bool handleMediaRequest(int socket, const std::string& request);
    
    // Handlers RTSP
    void handleInfo(int socket, const std::string& cseq);
    void handlePairSetup(int socket, const std::string& cseq, const std::string& request);
    void handlePairVerify(int socket, const std::string& cseq, const std::string& request);
    void handleFairPlaySetup(int socket, const std::string& cseq, const std::string& request);
    void handleOptions(int socket, const std::string& cseq);
    void handleSetup(int socket, const std::string& cseq, const std::string& request);
    void handleGetParameter(int socket, const std::string& cseq, const std::string& request);
    void handleSetParameter(int socket, const std::string& cseq, const std::string& request);
    void handleFeedback(int socket, const std::string& cseq);
    void handleFlush(int socket, const std::string& cseq, const std::string& request);
    void handleRecord(int socket, const std::string& cseq);
    void handleTeardown(int socket, const std::string& cseq);
    
    // Parsing
    std::string extractHeader(const std::string& request, const std::string& header);
    std::string extractBody(const std::string& request);
    void parseSetupParams(const std::string& request);
    void notifyActivity(const std::string& method);
    void resetMediaPlaybackState();
    
    std::unique_ptr<SessionManager> sessionManager_;
    std::unique_ptr<MediaOrchestrator> mediaOrchestrator_;
    std::unique_ptr<RTSPHandler> rtspHandler_;
    std::unique_ptr<FairPlayHandler> fairplayHandler_;
    std::unique_ptr<RouteDispatcher> routeDispatcher_;

    // Sessão
    std::string clientIp_;
    int videoWidth_ = 1920;
    int videoHeight_ = 1080;
    bool sessionAnnounced_ = false;
    std::vector<uint8_t> fairPlayKeyMessage_;
    
    // Callbacks
    ConnectionCallback onConnection_;
    ActivityCallback onActivity_;
    ErrorCallback onError_;
};

#endif // AIRPLAY_SERVER_H
