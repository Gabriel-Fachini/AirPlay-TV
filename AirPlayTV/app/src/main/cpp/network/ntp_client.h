#ifndef NTP_CLIENT_H
#define NTP_CLIENT_H

#include <string>
#include <thread>
#include <atomic>
#include <cstdint>

/**
 * Simple NTP client for AirPlay time synchronization
 * Sends NTP requests to client at 3 second intervals on port 7010
 * Receives NTP responses from client on port 7011
 */
class NTPClient {
public:
    NTPClient();
    ~NTPClient();

    // Start NTP client (send requests + receive responses)
    bool start(const std::string& clientIp, int clientPort = 7010);
    void stop();
    bool isRunning() const { return running_; }

private:
    void sendThread();
    void receiveThread();
    void sendNTPRequest();
    void receiveNTPResponse();
    uint64_t getTimestamp();

    int sendSocket_;
    int receiveSocket_;
    std::string clientIp_;
    int clientPort_;
    int serverPort_;  // Port 7011 for receiving responses
    std::thread sendThread_;
    std::thread receiveThread_;
    std::atomic<bool> running_;
    uint64_t sessionStartTime_;
};

#endif // NTP_CLIENT_H
