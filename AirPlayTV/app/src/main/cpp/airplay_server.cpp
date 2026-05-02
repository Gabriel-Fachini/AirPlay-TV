#include "airplay_server.h"
#include <android/log.h>
#include <sys/socket.h>
#include <netinet/in.h>
#include <arpa/inet.h>
#include <unistd.h>
#include <cstring>
#include <sstream>

#define TAG "AirPlay:Server"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, TAG, __VA_ARGS__)
#define LOGW(...) __android_log_print(ANDROID_LOG_WARN, TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, TAG, __VA_ARGS__)

AirPlayServer::AirPlayServer()
    : running_(false)
    , serverSocket_(-1)
    , port_(0)
    , videoWidth_(1920)
    , videoHeight_(1080)
    , audioSampleRate_(44100)
    , audioChannels_(2)
{
    LOGI("AirPlayServer created");
}

AirPlayServer::~AirPlayServer() {
    stop();
    LOGI("AirPlayServer destroyed");
}

bool AirPlayServer::start(int port) {
    if (running_) {
        LOGW("Server already running");
        return false;
    }

    port_ = port;
    
    // Criar socket TCP
    serverSocket_ = socket(AF_INET, SOCK_STREAM, 0);
    if (serverSocket_ < 0) {
        LOGE("Failed to create socket");
        if (onError_) onError_("Failed to create socket");
        return false;
    }

    // Permitir reutilização de endereço
    int opt = 1;
    setsockopt(serverSocket_, SOL_SOCKET, SO_REUSEADDR, &opt, sizeof(opt));

    // Bind
    struct sockaddr_in addr;
    memset(&addr, 0, sizeof(addr));
    addr.sin_family = AF_INET;
    addr.sin_addr.s_addr = INADDR_ANY;
    addr.sin_port = htons(port_);

    if (bind(serverSocket_, (struct sockaddr*)&addr, sizeof(addr)) < 0) {
        LOGE("Failed to bind to port %d", port_);
        close(serverSocket_);
        serverSocket_ = -1;
        if (onError_) onError_("Failed to bind to port");
        return false;
    }

    // Listen
    if (listen(serverSocket_, 1) < 0) {
        LOGE("Failed to listen");
        close(serverSocket_);
        serverSocket_ = -1;
        if (onError_) onError_("Failed to listen");
        return false;
    }

    running_ = true;
    serverThread_ = std::thread(&AirPlayServer::serverThread, this);

    LOGI("RTSP server started on port %d", port_);
    return true;
}

void AirPlayServer::stop() {
    if (!running_) {
        return;
    }

    LOGI("Stopping RTSP server");
    running_ = false;

    // Fechar socket para desbloquear accept()
    if (serverSocket_ >= 0) {
        shutdown(serverSocket_, SHUT_RDWR);
        close(serverSocket_);
        serverSocket_ = -1;
    }

    // Aguardar thread terminar
    if (serverThread_.joinable()) {
        serverThread_.join();
    }

    LOGI("RTSP server stopped");
}

void AirPlayServer::serverThread() {
    LOGI("Server thread started");

    while (running_) {
        struct sockaddr_in clientAddr;
        socklen_t clientLen = sizeof(clientAddr);

        int clientSocket = accept(serverSocket_, (struct sockaddr*)&clientAddr, &clientLen);
        
        if (clientSocket < 0) {
            if (running_) {
                LOGE("Accept failed");
            }
            break;
        }

        clientIp_ = inet_ntoa(clientAddr.sin_addr);
        LOGI("Client connected from %s", clientIp_.c_str());

        if (onConnection_) {
            onConnection_(clientIp_);
        }

        handleClient(clientSocket);

        close(clientSocket);
        
        if (onDisconnection_) {
            onDisconnection_();
        }
        
        LOGI("Client disconnected");
    }

    LOGI("Server thread ended");
}

void AirPlayServer::handleClient(int clientSocket) {
    char buffer[4096];
    std::string requestBuffer;

    while (running_) {
        ssize_t bytesRead = recv(clientSocket, buffer, sizeof(buffer) - 1, 0);
        
        if (bytesRead <= 0) {
            LOGI("Client closed connection");
            break;
        }

        buffer[bytesRead] = '\0';
        requestBuffer += buffer;

        // Verificar se temos uma requisição completa (termina com \r\n\r\n)
        size_t endPos = requestBuffer.find("\r\n\r\n");
        if (endPos != std::string::npos) {
            std::string request = requestBuffer.substr(0, endPos + 4);
            requestBuffer = requestBuffer.substr(endPos + 4);

            LOGI("Received RTSP request:\n%s", request.c_str());

            if (!handleRTSPRequest(clientSocket, request)) {
                LOGW("Failed to handle RTSP request");
                break;
            }
        }
    }
}

bool AirPlayServer::handleRTSPRequest(int socket, const std::string& request) {
    std::string cseq = extractHeader(request, "CSeq");
    
    if (request.find("OPTIONS") == 0) {
        handleOptions(socket, cseq);
    } else if (request.find("SETUP") == 0) {
        handleSetup(socket, cseq, request);
    } else if (request.find("RECORD") == 0) {
        handleRecord(socket, cseq);
    } else if (request.find("TEARDOWN") == 0) {
        handleTeardown(socket, cseq);
        return false; // Encerrar conexão
    } else {
        LOGW("Unknown RTSP method");
        return false;
    }

    return true;
}

void AirPlayServer::handleOptions(int socket, const std::string& cseq) {
    LOGI("Handling OPTIONS request");

    std::ostringstream response;
    response << "RTSP/1.0 200 OK\r\n";
    response << "CSeq: " << cseq << "\r\n";
    response << "Public: SETUP, RECORD, PAUSE, FLUSH, TEARDOWN, OPTIONS, GET_PARAMETER, SET_PARAMETER\r\n";
    response << "Server: AirPlay/220.68\r\n";
    response << "\r\n";

    std::string responseStr = response.str();
    send(socket, responseStr.c_str(), responseStr.length(), 0);
    
    LOGI("Sent OPTIONS response");
}

void AirPlayServer::handleSetup(int socket, const std::string& cseq, const std::string& request) {
    LOGI("Handling SETUP request");

    parseSetupParams(request);

    std::ostringstream response;
    response << "RTSP/1.0 200 OK\r\n";
    response << "CSeq: " << cseq << "\r\n";
    response << "Transport: RTP/AVP/UDP;unicast;mode=record;server_port=6000-6001;control_port=6001;timing_port=6002\r\n";
    response << "Session: 1\r\n";
    response << "Audio-Jack-Status: connected\r\n";
    response << "\r\n";

    std::string responseStr = response.str();
    send(socket, responseStr.c_str(), responseStr.length(), 0);
    
    LOGI("Sent SETUP response (video: %dx%d, audio: %dHz %dch)", 
         videoWidth_, videoHeight_, audioSampleRate_, audioChannels_);
}

void AirPlayServer::handleRecord(int socket, const std::string& cseq) {
    LOGI("Handling RECORD request - streaming started");

    std::ostringstream response;
    response << "RTSP/1.0 200 OK\r\n";
    response << "CSeq: " << cseq << "\r\n";
    response << "Audio-Latency: 0\r\n";
    response << "\r\n";

    std::string responseStr = response.str();
    send(socket, responseStr.c_str(), responseStr.length(), 0);
    
    LOGI("Sent RECORD response - ready to receive media");
    
    // TODO: Iniciar recepção de pacotes RTP em portas UDP
    // Por enquanto, apenas simulação de callbacks
    // Em implementação completa, criar sockets UDP para receber RTP
}

void AirPlayServer::handleTeardown(int socket, const std::string& cseq) {
    LOGI("Handling TEARDOWN request");

    std::ostringstream response;
    response << "RTSP/1.0 200 OK\r\n";
    response << "CSeq: " << cseq << "\r\n";
    response << "\r\n";

    std::string responseStr = response.str();
    send(socket, responseStr.c_str(), responseStr.length(), 0);
    
    LOGI("Sent TEARDOWN response");
}

std::string AirPlayServer::extractHeader(const std::string& request, const std::string& header) {
    std::string searchStr = header + ": ";
    size_t pos = request.find(searchStr);
    
    if (pos == std::string::npos) {
        return "";
    }

    size_t start = pos + searchStr.length();
    size_t end = request.find("\r\n", start);
    
    if (end == std::string::npos) {
        return "";
    }

    return request.substr(start, end - start);
}

void AirPlayServer::parseSetupParams(const std::string& request) {
    // Extrair resolução do SDP (se presente)
    // Formato simplificado - em produção, parsear SDP completo
    
    // Valores padrão (1080p, 44.1kHz stereo)
    videoWidth_ = 1920;
    videoHeight_ = 1080;
    audioSampleRate_ = 44100;
    audioChannels_ = 2;
    
    // TODO: Parsear SDP real do Content-Length/body
    // Por enquanto, usar valores padrão
    
    LOGI("Parsed setup params: video=%dx%d, audio=%dHz %dch",
         videoWidth_, videoHeight_, audioSampleRate_, audioChannels_);
}
