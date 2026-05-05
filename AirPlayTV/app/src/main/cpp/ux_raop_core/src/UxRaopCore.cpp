#include "UxRaopCore.h"

#include "dnssd.h"
#include "logger.h"
#include "raop.h"

#include <android/log.h>

#include <array>
#include <cstring>
#include <functional>
#include <vector>
#include <utility>

#define TAG "AirPlay:UxRaopCore"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, TAG, __VA_ARGS__)
#define LOGW(...) __android_log_print(ANDROID_LOG_WARN, TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, TAG, __VA_ARGS__)

namespace {

std::array<unsigned char, 6> buildHwAddr(const std::string& receiverId) {
    const auto hashValue = std::hash<std::string>{}(receiverId);
    std::array<unsigned char, 6> result{};
    result[0] = 0x12;
    result[1] = 0x34;
    result[2] = static_cast<unsigned char>((hashValue >> 24U) & 0xFFU);
    result[3] = static_cast<unsigned char>((hashValue >> 16U) & 0xFFU);
    result[4] = static_cast<unsigned char>((hashValue >> 8U) & 0xFFU);
    result[5] = static_cast<unsigned char>(hashValue & 0xFFU);
    return result;
}

void logCallback(void* /* cls */, int level, const char* msg) {
    switch (level) {
        case LOGGER_ERR:
            LOGE("%s", msg);
            break;
        case LOGGER_WARNING:
            LOGW("%s", msg);
            break;
        default:
            LOGI("%s", msg);
            break;
    }
}

bool startsWithNalStartCode(const uint8_t* data, size_t offset, size_t size) {
    return offset + 4 <= size &&
        data[offset] == 0x00 &&
        data[offset + 1] == 0x00 &&
        data[offset + 2] == 0x00 &&
        data[offset + 3] == 0x01;
}

bool extractH264CodecConfig(
    const uint8_t* data,
    size_t size,
    std::vector<uint8_t>* spsOut,
    std::vector<uint8_t>* ppsOut) {
    if (data == nullptr || spsOut == nullptr || ppsOut == nullptr || size < 8) {
        return false;
    }

    for (size_t offset = 0; offset + 4 < size; ++offset) {
        if (!startsWithNalStartCode(data, offset, size)) {
            continue;
        }

        const size_t nalStart = offset + 4;
        size_t nextOffset = nalStart;
        while (nextOffset < size && !startsWithNalStartCode(data, nextOffset, size)) {
            ++nextOffset;
        }

        const size_t nalSize = nextOffset - nalStart;
        if (nalSize == 0) {
            continue;
        }

        const uint8_t nalType = data[nalStart] & 0x1FU;
        if (nalType == 7 && spsOut->empty()) {
            spsOut->assign(data + nalStart, data + nalStart + nalSize);
        } else if (nalType == 8 && ppsOut->empty()) {
            ppsOut->assign(data + nalStart, data + nalStart + nalSize);
        }

        if (!spsOut->empty() && !ppsOut->empty()) {
            return true;
        }

        offset = nextOffset > 0 ? nextOffset - 1 : nextOffset;
    }

    return false;
}

} // namespace

struct UxRaopCore::Impl {
    raop_t* raop = nullptr;
    dnssd_t* dnssd = nullptr;
    UxRaopCallbacks callbacks{};
    UxRaopDisplayInfo displayInfo{};
    UxRaopAudioFormat lastAudioFormat{};
    std::string clientLabel = "airplay-client";
    uint16_t width = 1920;
    uint16_t height = 1080;
    bool running = false;
    bool notifiedConnected = false;
    bool emittedAudioConfig = false;
    bool emittedVideoConfig = false;
    unsigned short listeningPort = 0;
    uint64_t audioAccessUnitsSeen = 0;
    uint64_t videoFramesSeen = 0;
    float lastVolume = 0.0f;

    static Impl* self(void* context) {
        return static_cast<Impl*>(context);
    }

    static void resetConnectionScopedState(Impl* impl, bool keepClientLabel) {
        if (impl == nullptr) {
            return;
        }
        impl->notifiedConnected = false;
        impl->emittedAudioConfig = false;
        impl->emittedVideoConfig = false;
        impl->audioAccessUnitsSeen = 0;
        impl->videoFramesSeen = 0;
        impl->lastVolume = 0.0f;
        impl->lastAudioFormat = UxRaopAudioFormat{};
        impl->width = impl->displayInfo.width;
        impl->height = impl->displayInfo.height;
        if (!keepClientLabel) {
            impl->clientLabel = "airplay-client";
        }
    }

    static void connInit(void* context) {
        auto* impl = self(context);
        const bool hadResidualState =
            impl->notifiedConnected ||
            impl->emittedAudioConfig ||
            impl->emittedVideoConfig ||
            impl->audioAccessUnitsSeen > 0 ||
            impl->videoFramesSeen > 0;
        if (hadResidualState) {
            LOGW(
                "conn_init resetting stale session state audioCfg=%d videoCfg=%d audioAu=%llu videoFrames=%llu",
                impl->emittedAudioConfig ? 1 : 0,
                impl->emittedVideoConfig ? 1 : 0,
                static_cast<unsigned long long>(impl->audioAccessUnitsSeen),
                static_cast<unsigned long long>(impl->videoFramesSeen));
        }
        resetConnectionScopedState(impl, true);
        if (impl->callbacks.onSessionConnected != nullptr) {
            impl->callbacks.onSessionConnected(impl->callbacks.context, impl->clientLabel.c_str());
            impl->notifiedConnected = true;
        }
    }

    static void connDestroy(void* context) {
        auto* impl = self(context);
        resetConnectionScopedState(impl, false);
        if (impl->callbacks.onSessionDisconnected != nullptr) {
            impl->callbacks.onSessionDisconnected(impl->callbacks.context);
        }
    }

    static void connReset(void* context, int reason) {
        auto* impl = self(context);
        if (impl->callbacks.onProtocolError != nullptr) {
            const auto message = std::string("RAOP reset reason=") + std::to_string(reason);
            impl->callbacks.onProtocolError(impl->callbacks.context, message.c_str());
        }
    }

    static void audioFlush(void* context) {
        auto* impl = self(context);
        LOGI("audio_flush accessUnitsSeen=%llu", static_cast<unsigned long long>(impl->audioAccessUnitsSeen));
        if (impl->callbacks.onAudioFlush != nullptr) {
            impl->callbacks.onAudioFlush(impl->callbacks.context);
        }
    }

    static void audioGetFormat(
        void* context,
        unsigned char* ct,
        unsigned short* spf,
        bool* usingScreen,
        bool* isMedia,
        uint64_t* audioFormat) {
        auto* impl = self(context);
        LOGI(
            "audio_get_format ct=%u spf=%u fmt=0x%llx isMedia=%d usingScreen=%d",
            static_cast<unsigned>(*ct),
            static_cast<unsigned>(*spf),
            static_cast<unsigned long long>(*audioFormat),
            *isMedia ? 1 : 0,
            *usingScreen ? 1 : 0);
        impl->lastAudioFormat.compressionType = *ct;
        impl->lastAudioFormat.samplesPerFrame = *spf;
        impl->lastAudioFormat.audioFormat = *audioFormat;
        impl->lastAudioFormat.isMedia = *isMedia;
        impl->lastAudioFormat.usingScreen = *usingScreen;
    }

    static void audioProcess(void* context, raop_ntp_t* /* ntp */, audio_decode_struct* data) {
        auto* impl = self(context);
        impl->audioAccessUnitsSeen++;
        impl->lastAudioFormat.compressionType = data->ct;
        if (!impl->emittedAudioConfig && impl->callbacks.onAudioConfig != nullptr) {
            LOGI(
                "emit_audio_config ct=%u spf=%u fmt=0x%llx rate=44100 ch=2 isMedia=%d usingScreen=%d",
                static_cast<unsigned>(impl->lastAudioFormat.compressionType),
                static_cast<unsigned>(impl->lastAudioFormat.samplesPerFrame),
                static_cast<unsigned long long>(impl->lastAudioFormat.audioFormat),
                impl->lastAudioFormat.isMedia ? 1 : 0,
                impl->lastAudioFormat.usingScreen ? 1 : 0);
            impl->callbacks.onAudioConfig(
                impl->callbacks.context,
                impl->lastAudioFormat.compressionType,
                impl->lastAudioFormat.samplesPerFrame,
                impl->lastAudioFormat.audioFormat,
                44100,
                2,
                impl->lastAudioFormat.isMedia,
                impl->lastAudioFormat.usingScreen);
            impl->emittedAudioConfig = true;
        }
        if (impl->audioAccessUnitsSeen <= 5 || impl->audioAccessUnitsSeen % 50 == 0) {
            LOGI(
                "audio_process au#=%llu len=%d rtp=%u sync=%d ntpLocal=%llu ntpRemote=%llu ct=%u",
                static_cast<unsigned long long>(impl->audioAccessUnitsSeen),
                data->data_len,
                data->rtp_time,
                data->sync_status,
                static_cast<unsigned long long>(data->ntp_time_local),
                static_cast<unsigned long long>(data->ntp_time_remote),
                static_cast<unsigned>(data->ct));
        }
        if (impl->callbacks.onAudioAccessUnit != nullptr) {
            impl->callbacks.onAudioAccessUnit(
                impl->callbacks.context,
                data->data,
                static_cast<size_t>(data->data_len),
                data->rtp_time,
                data->ntp_time_local,
                data->ntp_time_remote,
                data->sync_status);
        }
    }

    static void videoProcess(void* context, raop_ntp_t* /* ntp */, video_decode_struct* data) {
        auto* impl = self(context);
        impl->videoFramesSeen++;
        if (!data->is_h265 && !impl->emittedVideoConfig && impl->callbacks.onVideoConfig != nullptr) {
            std::vector<uint8_t> sps;
            std::vector<uint8_t> pps;
            if (extractH264CodecConfig(data->data, static_cast<size_t>(data->data_len), &sps, &pps)) {
                impl->callbacks.onVideoConfig(
                    impl->callbacks.context,
                    impl->width,
                    impl->height,
                    sps.data(),
                    sps.size(),
                    pps.data(),
                    pps.size());
                impl->emittedVideoConfig = true;
            }
        }
        if (impl->callbacks.onVideoFrame != nullptr) {
            impl->callbacks.onVideoFrame(
                impl->callbacks.context,
                data->data,
                static_cast<size_t>(data->data_len),
                data->ntp_time_local,
                data->ntp_time_remote,
                data->is_h265,
                data->nal_count);
        }
    }

    static void reportClientRequest(
        void* context,
        char* /* deviceid */,
        char* /* model */,
        char* name,
        bool* admit) {
        auto* impl = self(context);
        if (name != nullptr && std::strlen(name) > 0) {
            impl->clientLabel = name;
        }
        if (admit != nullptr) {
            *admit = true;
        }
    }

    static double audioSetClientVolume(void* /* context */) {
        return 0.0;
    }

    static void noopVoid(void* /* context */) {}
    static void noopReset(void* /* context */, reset_type_t /* resetType */) {}
    static void noopSetVolume(void* context, float volume) {
        auto* impl = self(context);
        impl->lastVolume = volume;
        LOGI("audio_set_volume volume=%0.2f", volume);
    }
    static void noopSetMetadata(void* /* context */, const void* /* buffer */, int /* buflen */) {}
    static void noopSetCoverArt(void* /* context */, const void* /* buffer */, int /* buflen */) {}
    static void noopSetProgress(void* /* context */, uint32_t* /* start */, uint32_t* /* curr */, uint32_t* /* end */) {}
    static void reportSize(void* context, float* /* ws */, float* /* hs */, float* w, float* h) {
        auto* impl = self(context);
        if (w != nullptr && *w > 0.0f) {
            impl->width = static_cast<uint16_t>(*w);
        }
        if (h != nullptr && *h > 0.0f) {
            impl->height = static_cast<uint16_t>(*h);
        }
    }
    static void noopDisplayPin(void* /* context */, char* /* pin */) {}
    static void noopRegisterClient(void* /* context */, const char* /* deviceId */, const char* /* pk */, const char* /* name */) {}
    static bool noopCheckRegister(void* /* context */, const char* /* pk */) { return false; }
    static const char* noopPassword(void* /* context */, int* len) {
        if (len != nullptr) {
            *len = 0;
        }
        return nullptr;
    }
    static void noopExportDacp(void* /* context */, const char* /* activeRemote */, const char* /* dacpId */) {}
    static int videoSetCodec(void* /* context */, video_codec_t codec) {
        return codec == VIDEO_CODEC_H265 ? -1 : 0;
    }
    static void noopOnVideoPlay(void* /* context */, const char* /* location */, float /* startPosition */) {}
    static void noopOnVideoScrub(void* /* context */, float /* position */) {}
    static void noopOnVideoRate(void* /* context */, float /* rate */) {}
    static void noopOnVideoStop(void* /* context */) {}
    static void noopOnVideoAcquirePlaybackInfo(void* /* context */, playback_info_t* playbackInfo) {
        if (playbackInfo != nullptr) {
            std::memset(playbackInfo, 0, sizeof(*playbackInfo));
        }
    }
    static float noopOnPlaylistRemove(void* /* context */) { return 0.0f; }
};

UxRaopCore::UxRaopCore() : impl_(std::make_unique<Impl>()) {}

UxRaopCore::~UxRaopCore() {
    stop();
}

bool UxRaopCore::start(
    unsigned short rtspPort,
    const std::string& receiverName,
    const std::string& receiverId,
    const UxRaopDisplayInfo& displayInfo,
    const UxRaopCallbacks& callbacks) {
    stop();

    impl_->callbacks = callbacks;
    impl_->displayInfo = displayInfo;
    impl_->clientLabel = "airplay-client";
    impl_->width = displayInfo.width;
    impl_->height = displayInfo.height;
    impl_->notifiedConnected = false;
    impl_->emittedAudioConfig = false;
    impl_->emittedVideoConfig = false;
    impl_->audioAccessUnitsSeen = 0;
    impl_->videoFramesSeen = 0;
    impl_->lastVolume = 0.0f;

    raop_callbacks_t raopCallbacks{};
    raopCallbacks.cls = impl_.get();
    raopCallbacks.audio_process = &Impl::audioProcess;
    raopCallbacks.video_process = &Impl::videoProcess;
    raopCallbacks.video_pause = &Impl::noopVoid;
    raopCallbacks.video_resume = &Impl::noopVoid;
    raopCallbacks.conn_feedback = &Impl::noopVoid;
    raopCallbacks.conn_reset = &Impl::connReset;
    raopCallbacks.video_reset = &Impl::noopReset;
    raopCallbacks.conn_init = &Impl::connInit;
    raopCallbacks.conn_destroy = &Impl::connDestroy;
    raopCallbacks.audio_flush = &Impl::audioFlush;
    raopCallbacks.video_flush = &Impl::noopVoid;
    raopCallbacks.audio_set_client_volume = &Impl::audioSetClientVolume;
    raopCallbacks.audio_set_volume = &Impl::noopSetVolume;
    raopCallbacks.audio_set_metadata = &Impl::noopSetMetadata;
    raopCallbacks.audio_set_coverart = &Impl::noopSetCoverArt;
    raopCallbacks.audio_stop_coverart_rendering = &Impl::noopVoid;
    raopCallbacks.audio_set_progress = &Impl::noopSetProgress;
    raopCallbacks.audio_get_format = &Impl::audioGetFormat;
    raopCallbacks.video_report_size = &Impl::reportSize;
    raopCallbacks.report_client_request = &Impl::reportClientRequest;
    raopCallbacks.display_pin = &Impl::noopDisplayPin;
    raopCallbacks.register_client = &Impl::noopRegisterClient;
    raopCallbacks.check_register = &Impl::noopCheckRegister;
    raopCallbacks.passwd = &Impl::noopPassword;
    raopCallbacks.export_dacp = &Impl::noopExportDacp;
    raopCallbacks.video_set_codec = &Impl::videoSetCodec;
    raopCallbacks.on_video_play = &Impl::noopOnVideoPlay;
    raopCallbacks.on_video_scrub = &Impl::noopOnVideoScrub;
    raopCallbacks.on_video_rate = &Impl::noopOnVideoRate;
    raopCallbacks.on_video_stop = &Impl::noopOnVideoStop;
    raopCallbacks.on_video_playlist_remove = &Impl::noopOnPlaylistRemove;
    raopCallbacks.on_video_acquire_playback_info = &Impl::noopOnVideoAcquirePlaybackInfo;

    impl_->raop = raop_init(&raopCallbacks);
    if (impl_->raop == nullptr) {
        LOGE("raop_init failed");
        return false;
    }

    raop_set_log_callback(impl_->raop, logCallback, nullptr);
    raop_set_log_level(impl_->raop, LOGGER_INFO);
    if (raop_init2(impl_->raop, 0, receiverId.c_str(), "") != 0) {
        LOGE("raop_init2 failed");
        stop();
        return false;
    }

    raop_set_plist(impl_->raop, "width", displayInfo.width);
    raop_set_plist(impl_->raop, "height", displayInfo.height);
    raop_set_plist(impl_->raop, "refreshRate", displayInfo.refreshRate);
    raop_set_plist(impl_->raop, "maxFPS", displayInfo.maxFps);
    raop_set_plist(impl_->raop, "overscanned", displayInfo.overscanned);

    auto hwAddr = buildHwAddr(receiverId);
    int dnssdError = DNSSD_ERROR_NOERROR;
    impl_->dnssd = dnssd_init(
        receiverName.c_str(),
        static_cast<int>(receiverName.size()),
        reinterpret_cast<const char*>(hwAddr.data()),
        static_cast<int>(hwAddr.size()),
        &dnssdError,
        0);
    if (impl_->dnssd == nullptr || dnssdError != DNSSD_ERROR_NOERROR) {
        LOGE("dnssd_init failed: %d", dnssdError);
        stop();
        return false;
    }

    raop_set_dnssd(impl_->raop, impl_->dnssd);

    std::array<unsigned short, 3> tcpPorts{0, 0, 0};
    std::array<unsigned short, 3> udpPorts{0, 0, 0};
    raop_set_tcp_ports(impl_->raop, tcpPorts.data());
    raop_set_udp_ports(impl_->raop, udpPorts.data());

    unsigned short requestedPort = rtspPort;
    if (raop_start_httpd(impl_->raop, &requestedPort) < 0) {
        LOGE("raop_start_httpd failed");
        stop();
        return false;
    }

    raop_set_port(impl_->raop, requestedPort);
    dnssd_register_raop(impl_->dnssd, requestedPort);
    dnssd_register_airplay(impl_->dnssd, requestedPort);

    impl_->listeningPort = requestedPort;
    impl_->running = true;
    LOGI("Ux RAOP core started on port %u", requestedPort);
    return true;
}

void UxRaopCore::stop() {
    if (!impl_) {
        return;
    }

    impl_->running = false;
    impl_->notifiedConnected = false;
    impl_->emittedAudioConfig = false;
    impl_->emittedVideoConfig = false;
    impl_->listeningPort = 0;

    if (impl_->raop != nullptr) {
        raop_destroy(impl_->raop);
        impl_->raop = nullptr;
    }

    if (impl_->dnssd != nullptr) {
        dnssd_destroy(impl_->dnssd);
        impl_->dnssd = nullptr;
    }
}

bool UxRaopCore::isRunning() const {
    return impl_ != nullptr && impl_->running && impl_->raop != nullptr && raop_is_running(impl_->raop);
}

unsigned short UxRaopCore::port() const {
    return impl_ != nullptr ? impl_->listeningPort : 0;
}

const std::string& UxRaopCore::clientLabel() const {
    return impl_->clientLabel;
}
