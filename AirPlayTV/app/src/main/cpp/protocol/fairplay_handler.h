#ifndef FAIRPLAY_HANDLER_H
#define FAIRPLAY_HANDLER_H

#include <string>
#include <vector>
#include <functional>
#include <cstdint>
#include <cstddef>

/**
 * Handles FairPlay and pairing protocol for AirPlay
 */
class FairPlayHandler {
public:
    using ByteArrayCallback = std::function<std::vector<uint8_t>(const uint8_t*, size_t, bool*)>;

    FairPlayHandler();
    ~FairPlayHandler();

    // RTSP handlers
    void handlePairSetup(int socket, const std::string& cseq, const std::string& request);
    void handlePairVerify(int socket, const std::string& cseq, const std::string& request);
    void handleFairPlaySetup(int socket, const std::string& cseq, const std::string& request);

    // FairPlay key decryption
    bool decryptAesKey(const uint8_t* encryptedKey, size_t size, std::vector<uint8_t>* outKey) const;

    // Set callback for Java pairing operations
    void setPairSetupCallback(ByteArrayCallback callback) { onPairSetup_ = callback; }
    void setPairVerifyCallback(ByteArrayCallback callback) { onPairVerify_ = callback; }

private:
    std::vector<uint8_t> fairPlayKeyMessage_;
    ByteArrayCallback onPairSetup_;
    ByteArrayCallback onPairVerify_;
};

#endif // FAIRPLAY_HANDLER_H
