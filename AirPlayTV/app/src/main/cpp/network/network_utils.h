#ifndef NETWORK_UTILS_H
#define NETWORK_UTILS_H

#include <cstdint>

namespace NetworkUtils {

// Byte order conversion utilities
inline uint32_t readUint32BE(const uint8_t* data) {
    return (static_cast<uint32_t>(data[0]) << 24) |
           (static_cast<uint32_t>(data[1]) << 16) |
           (static_cast<uint32_t>(data[2]) << 8) |
           static_cast<uint32_t>(data[3]);
}

inline uint32_t readUint32LE(const uint8_t* data) {
    return static_cast<uint32_t>(data[0]) |
           (static_cast<uint32_t>(data[1]) << 8) |
           (static_cast<uint32_t>(data[2]) << 16) |
           (static_cast<uint32_t>(data[3]) << 24);
}

inline uint16_t readUint16BE(const uint8_t* data) {
    return static_cast<uint16_t>((data[0] << 8) | data[1]);
}

inline uint16_t readUint16LE(const uint8_t* data) {
    return static_cast<uint16_t>(data[0] | (data[1] << 8));
}

// Socket configuration utilities
bool setSocketTimeout(int socket, int seconds);
bool setSocketReuseAddr(int socket);

} // namespace NetworkUtils

#endif // NETWORK_UTILS_H
