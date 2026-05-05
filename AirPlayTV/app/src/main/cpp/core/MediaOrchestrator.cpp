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

    int dataPort = audioSessionConfig_.localDataPort;
    int controlPort = audioSessionConfig_.localControlPort;
    int timingPort = audioSessionConfig_.localTimingPort;

    if (rtpReceiver_->start(&dataPort, &controlPort, &timingPort)) {
        audioSessionConfig_.localDataPort = dataPort;
        audioSessionConfig_.localControlPort = controlPort;
        audioSessionConfig_.localTimingPort = timingPort;
        rtpRunning_ = true;
        LOGI("RTP receiver started successfully data=%d control=%d timing=%d", dataPort, controlPort, timingPort);
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
        const int remoteTimingPort = audioSessionConfig_.remoteTimingPort > 0
            ? audioSessionConfig_.remoteTimingPort
            : audioSessionConfig_.localTimingPort;
        ntpClient_->start(clientIp, remoteTimingPort);
    }
}

void MediaOrchestrator::stopNTPClient() {
    if (ntpClient_) {
        ntpClient_->stop();
        ntpClient_.reset();
    }
}

MediaOrchestrator::AudioSessionConfig MediaOrchestrator::prepareAudioSession(
        const AudioSessionConfig& config,
        const std::string& clientIp) {
    audioSessionConfig_ = config;
    audioSampleRate_ = config.sampleRate;
    audioChannels_ = config.channels;

    if (!rtpReceiver_ || !rtpReceiver_->isRunning()) {
        startRTPReceiver();
    } else {
        rtpReceiver_->setAudioConfig(audioSessionConfig_.compressionType, audioSessionConfig_.sampleRate);
    }

    if (!clientIp.empty() && audioSessionConfig_.remoteTimingPort > 0) {
        stopNTPClient();
        startNTPClient(clientIp);
    }

    return audioSessionConfig_;
}

void MediaOrchestrator::flushAudio(uint16_t nextSequenceNumber, bool hasNextSequenceNumber) {
    if (rtpReceiver_) {
        rtpReceiver_->flushAudio(hasNextSequenceNumber ? nextSequenceNumber : 0, hasNextSequenceNumber);
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
