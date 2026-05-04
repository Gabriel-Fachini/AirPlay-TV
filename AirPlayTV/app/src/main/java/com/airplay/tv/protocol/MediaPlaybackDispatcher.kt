package com.airplay.tv.protocol

import com.airplay.tv.util.Logger
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow

internal class MediaPlaybackDispatcher(
    private val playbackStateStore: ProtocolPlaybackStateStore,
    private val mediaPlaybackState: MutableStateFlow<ProtocolHandler.MediaPlaybackState>,
    private val sessionActivity: MutableSharedFlow<String>
) {
    fun onControlRequestHandled(method: String) {
        if (
            method == "GET /server-info" ||
            method == "GET /slideshow-features" ||
            method == "PUT /photo" ||
            method == "PUT /slideshows/1" ||
            method == "POST /stop" ||
            method == "/play" ||
            method == "/scrub" ||
            method == "/rate" ||
            method == "/playback-info" ||
            method == "/event" ||
            method == "/setProperty"
        ) {
            Logger.i(Logger.TAG_PROTOCOL, "Native media control handled: $method")
        }
        sessionActivity.tryEmit(method)
    }

    fun onPhotoPlaybackSession(
        clientIp: String,
        sessionId: String,
        assetKey: String,
        transition: String?,
        imageData: ByteArray,
        isSlideshow: Boolean
    ) {
        Logger.i(
            Logger.TAG_PROTOCOL,
            "Media callback photo: client=$clientIp sessionId=${sessionId.ifEmpty { "none" }} " +
                "bytes=${imageData.size} assetKey=${assetKey.ifEmpty { "none" }} " +
                "transition=${transition ?: "none"} slideshow=$isSlideshow"
        )

        sessionActivity.tryEmit(if (isSlideshow) "SLIDESHOW_PHOTO" else "PHOTO")
        mediaPlaybackState.value = playbackStateStore.onPhotoPlayback(
            clientIp = clientIp,
            sessionId = sessionId,
            assetKey = assetKey,
            transition = transition,
            imageData = imageData,
            isSlideshow = isSlideshow,
        )
    }

    fun onSlideshowPlaybackState(
        clientIp: String,
        sessionId: String,
        theme: String?,
        slideDurationSeconds: Int,
        state: String
    ) {
        Logger.i(
            Logger.TAG_PROTOCOL,
                "Media callback slideshow: client=$clientIp sessionId=${sessionId.ifEmpty { "none" }} " +
                "state=$state theme=${theme ?: "none"} duration=${slideDurationSeconds}s " +
                "hasImage=${playbackStateStore.hasCachedImage()}"
        )
        sessionActivity.tryEmit("SLIDESHOW_STATE")
        mediaPlaybackState.value = playbackStateStore.onSlideshowState(
            clientIp = clientIp,
            sessionId = sessionId,
            theme = theme,
            slideDurationSeconds = slideDurationSeconds,
            state = state,
        )
    }

    fun onMediaPlaybackStopped(sessionId: String) {
        Logger.i(
            Logger.TAG_PROTOCOL,
            "Media callback stop: sessionId=${sessionId.ifEmpty { "none" }} " +
                "lastAsset=${playbackStateStore.currentAssetKey() ?: "none"} hadImage=${playbackStateStore.hasCachedImage()}"
        )
        playbackStateStore.reset()
        sessionActivity.tryEmit("MEDIA_STOP")
        mediaPlaybackState.value = ProtocolHandler.MediaPlaybackState.Idle
    }
}
