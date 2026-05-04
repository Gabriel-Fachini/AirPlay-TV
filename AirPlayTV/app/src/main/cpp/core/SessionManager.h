#pragma once

#include <thread>
#include <atomic>
#include <string>
#include <functional>

class SessionManager {
public:
    SessionManager();
    ~SessionManager();

    bool start(int port);
    void stop();
    bool isRunning() const { return running_; }

    void setClientConnectedCallback(std::function<void(int socket, const std::string& ip)> cb) { onClientConnected_ = std::move(cb); }
    void setClientDisconnectedCallback(std::function<void()> cb) { onClientDisconnected_ = std::move(cb); }
    void setErrorCallback(std::function<void(const std::string&)> cb) { onError_ = std::move(cb); }

private:
    void serverThread();

    std::thread serverThread_;
    std::atomic<bool> running_;
    int serverSocket_;
    int port_;

    std::function<void(int, const std::string&)> onClientConnected_;
    std::function<void()> onClientDisconnected_;
    std::function<void(const std::string&)> onError_;
};
