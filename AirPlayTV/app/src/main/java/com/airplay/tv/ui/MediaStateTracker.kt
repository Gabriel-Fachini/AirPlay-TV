package com.airplay.tv.ui

import com.airplay.tv.protocol.ProtocolHandler
import com.airplay.tv.util.Constants
import com.airplay.tv.util.Logger

class MediaStateTracker(
    private val uiStateManager: UIStateManager
) {
    fun handleMediaPlaybackStateChange(state: ProtocolHandler.MediaPlaybackState) {
        when (state) {
            is ProtocolHandler.MediaPlaybackState.Idle -> {
                Logger.i(Logger.TAG_UI, "UI media playback state: Idle")
                if (!uiStateManager.isInState(UIStateManager.UIState.Mirroring::class)) {
                    uiStateManager.returnToIdle(Constants.DEFAULT_DEVICE_NAME)
                }
            }

            is ProtocolHandler.MediaPlaybackState.PhotoDisplayed -> {
                Logger.i(
                    Logger.TAG_UI,
                    "UI entering photo playback: sessionId=${state.sessionId.ifEmpty { "none" }} " +
                        "asset=${state.assetKey.ifEmpty { "none" }} bytes=${state.imageData.size}"
                )
                uiStateManager.transitionTo(
                    UIStateManager.UIState.MediaPlayback(
                        clientIp = state.clientIp,
                        kind = "photo",
                        sessionId = state.sessionId,
                        imageData = state.imageData.copyOf(),
                        assetKey = state.assetKey,
                        transition = state.transition,
                        theme = null,
                        slideDurationSeconds = 0
                    )
                )
            }

            is ProtocolHandler.MediaPlaybackState.SlideshowPlaying -> {
                Logger.i(
                    Logger.TAG_UI,
                    "UI entering slideshow playback: sessionId=${state.sessionId.ifEmpty { "none" }} " +
                        "theme=${state.theme ?: "none"} duration=${state.slideDurationSeconds}s " +
                        "hasImage=${state.imageData != null}"
                )
                uiStateManager.transitionTo(
                    UIStateManager.UIState.MediaPlayback(
                        clientIp = state.clientIp,
                        kind = "slideshow",
                        sessionId = state.sessionId,
                        imageData = state.imageData?.copyOf(),
                        assetKey = state.assetKey,
                        transition = state.transition,
                        theme = state.theme,
                        slideDurationSeconds = state.slideDurationSeconds
                    )
                )
            }
        }
    }
}
