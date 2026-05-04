#include "MediaOrchestrator.h"
#include <android/log.h>

#define TAG "AirPlay:MediaOrchestrator"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, TAG, __VA_ARGS__)
#define LOGW(...) __android_log_print(ANDROID_LOG_WARN, TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, TAG, __VA_ARGS__)

MediaOrchestrator::MediaOrchestrator() {
    resetAudioSessionConfig();
}

MediaOrchestrator::~MediaOrchestrator() {
    stopRTPReceiver();
    stopMirrorVideoServer();
    stopNTPClient();
}

void MediaOrchestrator::startRTPReceiver() {
    if (rtpReceiver_ && rtpReceiver_->isRunning()) {
        LOGW("RTP receiver already running");
        return;
    }

    LOGI("Starting RTP receiver");

    rtpReceiver_ = std::make_unique<RTPReceiver>();

    rtpReceiver_->setVideoDataCallback(onVideoData_);
    rtpReceiver_->setAudioDataCallback(onAudioData_);
    rtpReceiver_->setAudioSyncCallback(onAudioSync_);
    rtpReceiver_->setErrorCallback(onError_);
    rtpReceiver_->setAudioConfig(audioSessionConfig_.compressionType, audioSessionConfig_.sampleRate);

    if (rtpReceiver_->start(
            audioSessionConfig_.localDataPort,
            audioSessionConfig_.localControlPort,
            audioSessionConfig_.localTimingPort)) {
        rtpRunning_ = true;
        LOGI("RTP receiver started successfully");
    } else {
        LOGE("Failed to start RTP receiver");
        rtpReceiver_.reset();
    }
}

void MediaOrchestrator::stopRTPReceiver() {
    if (!rtpRunning_) return;
    LOGI("Stopping RTP receiver");
    rtpRunning_ = false;
    if (rtpReceiver_) {
        rtpReceiver_->stop();
        rtpReceiver_.reset();
    }
}

int MediaOrchestrator::startMirrorVideoServer() {
    if (mirrorServer_ && mirrorServer_->isRunning()) {
        return mirrorServer_->getPort();
    }

    mirrorServer_ = std::make_unique<MirrorServer>();
    mirrorServer_->setPacketCallback(onMirroringVideoPacket_);

    int port = mirrorServer_->start();
    if (port > 0) {
        mirrorVideoPort_ = port;
        mirrorVideoRunning_ = true;
        LOGI("Mirror video server started on port %d", port);
    } else {
        mirrorServer_.reset();
    }

    return port;
}

void MediaOrchestrator::stopMirrorVideoServer() {
    if (!mirrorVideoRunning_) return;
    LOGI("Stopping mirror video server");
    mirrorVideoRunning_ = false;
    mirrorVideoPort_ = 0;
    if (mirrorServer_) {
        mirrorServer_->stop();
        mirrorServer_.reset();
    }
}

void MediaOrchestrator::startNTPClient(const std::string& clientIp) {
    if (!ntpClient_) {
        ntpClient_ = std::make_unique<NTPClient>();
    }
    if (!clientIp.empty() && !ntpClient_->isRunning()) {
        ntpClient_->start(clientIp, 7010);
    }
}

void MediaOrchestrator::stopNTPClient() {
    if (ntpClient_) {
        ntpClient_->stop();
        ntpClient_.reset();
    }
}

void MediaOrchestrator::updateAudioSessionConfig(const AudioSessionConfig& config) {
    audioSessionConfig_ = config;
    audioSampleRate_ = config.sampleRate;
    audioChannels_ = config.channels;
    if (rtpReceiver_) {
        rtpReceiver_->setAudioConfig(audioSessionConfig_.compressionType, audioSessionConfig_.sampleRate);
    }
}

void MediaOrchestrator::resetAudioSessionConfig() {
    audioSessionConfig_ = AudioSessionConfig();
    audioSampleRate_ = audioSessionConfig_.sampleRate;
    audioChannels_ = audioSessionConfig_.channels;
}
