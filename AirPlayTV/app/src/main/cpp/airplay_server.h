#ifndef AIRPLAY_SERVER_H
#define AIRPLAY_SERVER_H

#include <jni.h>
#include <string>
#include <thread>
#include <atomic>
#include <functional>

/**
 * Servidor RTSP/AirPlay simplificado
 * Implementa handshake básico do protocolo AirPlay
 */
class AirPlayServer {
public:
    // Callbacks para eventos
    using ConnectionCallback = std::function<void(const std::string& clientIp)>;
    using DisconnectionCallback = std::function<void()>;
    using VideoDataCallback = std::function<void(const uint8_t* data, size_t size, uint32_t timestamp)>;
    using AudioDataCallback = std::function<void(const uint8_t* data, size_t size, uint32_t timestamp)>;
    using ErrorCallback = std::function<void(const std::string& error)>;

    AirPlayServer();
    ~AirPlayServer();

    // Controle do servidor
    bool start(int port);
    void stop();
    bool isRunning() const { return running_; }

    // Configurar callbacks
    void setConnectionCallback(ConnectionCallback callback) { onConnection_ = callback; }
    void setDisconnectionCallback(DisconnectionCallback callback) { onDisconnection_ = callback; }
    void setVideoDataCallback(VideoDataCallback callback) { onVideoData_ = callback; }
    void setAudioDataCallback(AudioDataCallback callback) { onAudioData_ = callback; }
    void setErrorCallback(ErrorCallback callback) { onError_ = callback; }

    // Informações da sessão
    std::string getClientIp() const { return clientIp_; }
    int getVideoWidth() const { return videoWidth_; }
    int getVideoHeight() const { return videoHeight_; }
    int getAudioSampleRate() const { return audioSampleRate_; }
    int getAudioChannels() const { return audioChannels_; }

private:
    void serverThread();
    void handleClient(int clientSocket);
    bool handleRTSPRequest(int socket, const std::string& request);
    
    // Handlers RTSP
    void handleOptions(int socket, const std::string& cseq);
    void handleSetup(int socket, const std::string& cseq, const std::string& request);
    void handleRecord(int socket, const std::string& cseq);
    void handleTeardown(int socket, const std::string& cseq);
    
    // Parsing
    std::string extractHeader(const std::string& request, const std::string& header);
    void parseSetupParams(const std::string& request);
    
    // Thread e estado
    std::thread serverThread_;
    std::atomic<bool> running_;
    int serverSocket_;
    int port_;
    
    // Sessão
    std::string clientIp_;
    int videoWidth_;
    int videoHeight_;
    int audioSampleRate_;
    int audioChannels_;
    
    // Callbacks
    ConnectionCallback onConnection_;
    DisconnectionCallback onDisconnection_;
    VideoDataCallback onVideoData_;
    AudioDataCallback onAudioData_;
    ErrorCallback onError_;
};

#endif // AIRPLAY_SERVER_H
