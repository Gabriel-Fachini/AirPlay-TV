package com.airplay.tv.protocol

internal class ProtocolPlaybackStateStore {
    private var lastMediaImageData: ByteArray? = null
    private var lastMediaAssetKey: String? = null
    private var lastMediaTransition: String? = null

    fun reset() {
        lastMediaImageData = null
        lastMediaAssetKey = null
        lastMediaTransition = null
    }

    fun onPhotoPlayback(
        clientIp: String,
        sessionId: String,
        assetKey: String,
        transition: String?,
        imageData: ByteArray,
        isSlideshow: Boolean,
    ): ProtocolHandler.MediaPlaybackState {
        val imageCopy = imageData.copyOf()
        lastMediaImageData = imageCopy
        lastMediaAssetKey = assetKey
        lastMediaTransition = transition

        return if (isSlideshow) {
            ProtocolHandler.MediaPlaybackState.SlideshowPlaying(
                clientIp = clientIp,
                sessionId = sessionId,
                theme = null,
                slideDurationSeconds = 0,
                state = "playing",
                imageData = imageCopy.copyOf(),
                assetKey = assetKey,
                transition = transition,
            )
        } else {
            ProtocolHandler.MediaPlaybackState.PhotoDisplayed(
                clientIp = clientIp,
                sessionId = sessionId,
                assetKey = assetKey,
                transition = transition,
                imageData = imageCopy,
            )
        }
    }

    fun onSlideshowState(
        clientIp: String,
        sessionId: String,
        theme: String?,
        slideDurationSeconds: Int,
        state: String,
    ): ProtocolHandler.MediaPlaybackState {
        if (state.equals("stopped", ignoreCase = true)) {
            return ProtocolHandler.MediaPlaybackState.Idle
        }

        return ProtocolHandler.MediaPlaybackState.SlideshowPlaying(
            clientIp = clientIp,
            sessionId = sessionId,
            theme = theme,
            slideDurationSeconds = slideDurationSeconds,
            state = state,
            imageData = lastMediaImageData?.copyOf(),
            assetKey = lastMediaAssetKey,
            transition = lastMediaTransition,
        )
    }

    fun hasCachedImage(): Boolean = lastMediaImageData != null

    fun currentAssetKey(): String? = lastMediaAssetKey
}
