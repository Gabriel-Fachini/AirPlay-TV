#ifndef NTP_CLIENT_H
#define NTP_CLIENT_H

#include <string>
#include <thread>
#include <atomic>
#include <cstdint>

/**
 * NTP client for AirPlay time synchronization
 * Based on RPiPlay implementation
 * Sends timing requests to Mac and receives responses on the same socket
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
    void ntpThread();
    void sendNTPRequest();
    uint64_t getTimestamp();
    void putNTPTimestamp(uint8_t* buffer, int offset, uint64_t microseconds);

    int socket_;  // Single socket for both send and receive
    std::string clientIp_;
    int clientPort_;
    std::thread thread_;
    std::atomic<bool> running_;
    uint64_t sessionStartTime_;
};

#endif // NTP_CLIENT_H
