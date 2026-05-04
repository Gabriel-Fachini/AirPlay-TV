package com.airplay.tv.service

import android.view.Surface
import com.airplay.tv.media.DecoderState
import com.airplay.tv.media.VideoDecoder
import com.airplay.tv.protocol.ProtocolHandler
import com.airplay.tv.util.Logger
import java.nio.ByteBuffer

internal class VideoDecoderConfigurator(
    private val videoDecoder: VideoDecoder
) {
    private data class AppliedCodecConfig(
        val width: Int,
        val height: Int,
        val sps: ByteArray,
        val pps: ByteArray,
    )

    private var pendingCodecConfig: ProtocolHandler.CodecConfig? = null
    private var appliedCodecConfig: AppliedCodecConfig? = null

    fun reset() {
        pendingCodecConfig = null
        appliedCodecConfig = null
    }

    fun handleCodecConfigReceived(config: ProtocolHandler.CodecConfig, videoSurface: Surface?) {
        val incomingConfig = AppliedCodecConfig(
            width = config.width,
            height = config.height,
            sps = config.sps.toByteArray(),
            pps = config.pps.toByteArray(),
        )
        pendingCodecConfig = config.copy(
            sps = config.sps.duplicate(),
            pps = config.pps.duplicate(),
        )
        Logger.i(
            Logger.TAG_SERVICE,
            "Handling codec config: ${config.width}x${config.height}, SPS=${incomingConfig.sps.size}B, PPS=${incomingConfig.pps.size}B"
        )

        if (videoSurface == null) {
            Logger.w(Logger.TAG_SERVICE, "Cannot configure decoder: no surface available")
            return
        }

        val currentConfig = appliedCodecConfig
        val decoderState = videoDecoder.state.value
        if (currentConfig != null && decoderState == DecoderState.Running) {
            val sameResolution = currentConfig.width == incomingConfig.width &&
                currentConfig.height == incomingConfig.height
            val sameSps = currentConfig.sps.contentEquals(incomingConfig.sps)
            val samePps = currentConfig.pps.contentEquals(incomingConfig.pps)

            if (sameResolution) {
                Logger.i(
                    Logger.TAG_SERVICE,
                    "Ignoring in-session codec config update without restart: " +
                        "sameResolution=true spsChanged=${!sameSps} ppsChanged=${!samePps}"
                )
                pendingCodecConfig = null
                return
            }

            Logger.i(
                Logger.TAG_SERVICE,
                "Reconfiguring video decoder due to resolution change: " +
                    "${currentConfig.width}x${currentConfig.height} -> ${incomingConfig.width}x${incomingConfig.height} " +
                    "(spsChanged=${!sameSps}, ppsChanged=${!samePps})"
            )
        }

        try {
            if (decoderState != DecoderState.Idle) {
                Logger.i(Logger.TAG_SERVICE, "Stopping existing decoder before reconfiguration")
                videoDecoder.stop()
            }

            val success = videoDecoder.configure(
                width = config.width,
                height = config.height,
                sps = config.sps,
                pps = config.pps,
                surface = videoSurface,
            )

            if (success) {
                videoDecoder.start()
                appliedCodecConfig = incomingConfig
                pendingCodecConfig = null
                Logger.i(Logger.TAG_SERVICE, "Video decoder configured and started with codec config")
            } else {
                Logger.e(Logger.TAG_SERVICE, "Failed to configure video decoder with codec config")
            }
        } catch (e: Exception) {
            Logger.e(Logger.TAG_SERVICE, "Error configuring decoder with codec config", e)
        }
    }

    fun applyPendingConfig(videoSurface: Surface?) {
        pendingCodecConfig?.let { config ->
            Logger.i(Logger.TAG_SERVICE, "Applying deferred codec config after surface became available")
            handleCodecConfigReceived(config, videoSurface)
        }
    }
}

internal fun ByteBuffer.toByteArray(): ByteArray {
    val duplicate = duplicate()
    val bytes = ByteArray(duplicate.remaining())
    duplicate.get(bytes)
    return bytes
}
