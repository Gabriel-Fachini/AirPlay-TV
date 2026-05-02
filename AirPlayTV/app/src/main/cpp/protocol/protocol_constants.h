#ifndef PROTOCOL_CONSTANTS_H
#define PROTOCOL_CONSTANTS_H

#include <cstddef>
#include <cstdint>

namespace AirPlayProtocol {

// FairPlay constants
constexpr size_t kFairPlaySetupRequestSize = 16;
constexpr size_t kFairPlayHandshakeRequestSize = 164;
constexpr size_t kFairPlaySetupResponseSize = 142;
constexpr size_t kFairPlayHandshakeResponseSize = 32;
constexpr size_t kMirrorHeaderSize = 128;

// FairPlay replies (4 modes)
extern const unsigned char kFairPlayReplies[4][kFairPlaySetupResponseSize];

// FairPlay header for handshake response
extern const unsigned char kFairPlayHeader[12];

// Info response body (binary plist)
extern const unsigned char kInfoResponseBody[];
extern const size_t kInfoResponseBodySize;

} // namespace AirPlayProtocol

#endif // PROTOCOL_CONSTANTS_H
