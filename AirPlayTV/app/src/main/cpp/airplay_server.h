#ifndef AIRPLAY_SERVER_H
#define AIRPLAY_SERVER_H

#include <jni.h>
#include <string>
#include <thread>
#include <atomic>
#include <functional>
#include <memory>
#include <vector>

class RTSPHandler;
class FairPlayHandler;
class RTPReceiver;
class MirrorServer;

/**
 * Servidor RTSP/AirPlay simplificado
 * Implementa handshake básico do protocolo AirPlay
 */
class AirPlayServer {
public:
    // Callbacks para eventos
    using ConnectionCallback = std::function<void(const std::string& clientIp)>;
    using DisconnectionCallback = std::function<void()>;
    using ActivityCallback = std::function<void(const std::string& method)>;
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
    void setActivityCallback(ActivityCallback callback) { onActivity_ = callback; }
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
    void handleInfo(int socket, const std::string& cseq);
    void handlePairSetup(int socket, const std::string& cseq, const std::string& request);
    void handlePairVerify(int socket, const std::string& cseq, const std::string& request);
    void handleFairPlaySetup(int socket, const std::string& cseq, const std::string& request);
    void handleOptions(int socket, const std::string& cseq);
    void handleSetup(int socket, const std::string& cseq, const std::string& request);
    void handleGetParameter(int socket, const std::string& cseq, const std::string& request);
    void handleSetParameter(int socket, const std::string& cseq, const std::string& request);
    void handleFeedback(int socket, const std::string& cseq);
    void handleRecord(int socket, const std::string& cseq);
    void handleTeardown(int socket, const std::string& cseq);
    
    // RTP/UDP
    void startRTPReceiver();
    void stopRTPReceiver();
    void receiveDataThread();
    void receiveControlThread();
    void receiveTimingThread();
    bool bindUDPSocket(int socket, int port);
    void processRTPPacket(const uint8_t* data, size_t size);

    // Mirroring TCP
    void stopMirrorVideoServer();
    void mirrorVideoThread();
    
    // Parsing
    std::string extractHeader(const std::string& request, const std::string& header);
    std::string extractBody(const std::string& request);
    void parseSetupParams(const std::string& request);
    void notifyActivity(const std::string& method);
    
    // Thread e estado
    std::thread serverThread_;
    std::atomic<bool> running_;
    int serverSocket_;
    int port_;
    
    // RTP/UDP sockets e threads
    int dataSocket_;
    int controlSocket_;
    int timingSocket_;
    std::thread dataThread_;
    std::thread controlThread_;
    std::thread timingThread_;
    std::atomic<bool> rtpRunning_;

    // Mirroring TCP
    int mirrorVideoSocket_;
    int mirrorVideoPort_;
    std::thread mirrorVideoThread_;
    std::atomic<bool> mirrorVideoRunning_;
    
    // Sessão
    std::string clientIp_;
    int videoWidth_;
    int videoHeight_;
    int audioSampleRate_;
    int audioChannels_;
    bool sessionAnnounced_;
    std::vector<uint8_t> fairPlayKeyMessage_;
    
    // Callbacks
    ConnectionCallback onConnection_;
    DisconnectionCallback onDisconnection_;
    ActivityCallback onActivity_;
    VideoDataCallback onVideoData_;
    AudioDataCallback onAudioData_;
    ErrorCallback onError_;
    std::function<void(int, const uint8_t*, size_t)> onMirroringVideoPacket_;

public:
    void setMirroringVideoPacketCallback(std::function<void(int, const uint8_t*, size_t)> callback) {
        onMirroringVideoPacket_ = std::move(callback);
    }

    int startMirrorVideoServer();
    bool decryptFairPlayAesKey(const uint8_t* encryptedKey, size_t size, std::vector<uint8_t>* outKey);

private:
    std::unique_ptr<RTSPHandler> rtspHandler_;
    std::unique_ptr<FairPlayHandler> fairplayHandler_;
    std::unique_ptr<RTPReceiver> rtpReceiver_;
    std::unique_ptr<MirrorServer> mirrorServer_;
};

#endif // AIRPLAY_SERVER_H
