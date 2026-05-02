#include "network_utils.h"
#include <sys/socket.h>
#include <sys/time.h>
#include <android/log.h>

#define TAG "AirPlay:NetUtils"
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, TAG, __VA_ARGS__)

namespace NetworkUtils {

bool setSocketTimeout(int socket, int seconds) {
    struct timeval tv;
    tv.tv_sec = seconds;
    tv.tv_usec = 0;
    
    if (setsockopt(socket, SOL_SOCKET, SO_RCVTIMEO, &tv, sizeof(tv)) < 0) {
        LOGE("Failed to set socket timeout");
        return false;
    }
    
    return true;
}

bool setSocketReuseAddr(int socket) {
    int opt = 1;
    if (setsockopt(socket, SOL_SOCKET, SO_REUSEADDR, &opt, sizeof(opt)) < 0) {
        LOGE("Failed to set socket reuse address");
        return false;
    }
    
    return true;
}

} // namespace NetworkUtils
