#ifndef MIRROR_SERVER_H
#define MIRROR_SERVER_H

#include <functional>
#include <thread>
#include <atomic>
#include <vector>
#include <cstdint>
#include <cstddef>

/**
 * TCP server for AirPlay mirroring video stream
 * Receives video packets with 128-byte headers
 */
class MirrorServer {
public:
    using PacketCallback = std::function<void(int payloadType, const uint8_t* data, size_t size)>;

    MirrorServer();
    ~MirrorServer();

    // Start server and return allocated port (or -1 on error)
    int start();
    void stop();
    bool isRunning() const { return running_; }
    int getPort() const { return port_; }

    // Set callback for received packets
    void setPacketCallback(PacketCallback callback) { onPacket_ = callback; }

private:
    void serverThread();

    int socket_;
    int port_;
    std::thread thread_;
    std::atomic<bool> running_;
    PacketCallback onPacket_;
};

#endif // MIRROR_SERVER_H
