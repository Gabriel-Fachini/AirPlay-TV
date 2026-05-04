package com.airplay.tv.service

import android.view.Surface
import com.airplay.tv.media.AudioDecoder
import com.airplay.tv.media.DecoderState
import com.airplay.tv.media.SyncManager
import com.airplay.tv.media.VideoDecoder
import com.airplay.tv.protocol.ProtocolHandler
import com.airplay.tv.util.Logger
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.nio.ByteBuffer

internal class MediaPipelineController(
    private val telemetryCollector: TelemetryCollector,
) {
    private data class AppliedCodecConfig(
        val width: Int,
        val height: Int,
        val sps: ByteArray,
        val pps: ByteArray,
    )

    val videoDecoder = VideoDecoder(telemetryCollector) { width, height ->
        handleVideoOutputSizeChanged(width, height)
    }
    val audioDecoder = AudioDecoder()

    private val videoOutputSize = MutableStateFlow(VideoOutputSize())
    private var syncManager: SyncManager? = null
    private var videoSurface: Surface? = null
    private var pendingSessionInfo: ProtocolHandler.SessionInfo? = null
    private var pendingCodecConfig: ProtocolHandler.CodecConfig? = null
    private var appliedCodecConfig: AppliedCodecConfig? = null
    private var mediaPipelineStarted = false

    fun getVideoOutputSize(): StateFlow<VideoOutputSize> = videoOutputSize

    fun setVideoSurface(surface: Surface) {
        Logger.i(Logger.TAG_SERVICE, "Video surface configured")
        videoSurface = surface

        pendingSessionInfo?.takeIf { !mediaPipelineStarted }?.let { sessionInfo ->
            Logger.i(Logger.TAG_SERVICE, "Starting deferred media pipeline after surface became available")
            startMediaPipeline(sessionInfo)
        }

        pendingCodecConfig?.let { config ->
            Logger.i(Logger.TAG_SERVICE, "Applying deferred codec config after surface became available")
            handleCodecConfigReceived(config)
        }
    }

    fun clearVideoSurface() {
        Logger.i(Logger.TAG_SERVICE, "Video surface cleared")
        videoSurface = null
    }

    fun handleCodecConfigReceived(config: ProtocolHandler.CodecConfig) {
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

        val surface = videoSurface
        if (surface == null) {
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
                surface = surface,
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

    fun startMediaPipeline(sessionInfo: ProtocolHandler.SessionInfo) {
        Logger.i(Logger.TAG_SERVICE, "Starting media pipeline")

        try {
            val surface = videoSurface
            if (surface == null) {
                Logger.e(Logger.TAG_SERVICE, "Video surface not configured")
                pendingSessionInfo = sessionInfo
                return
            }

            Logger.i(Logger.TAG_SERVICE, "Waiting for codec config to configure video decoder")

            val audioStreamConfig = sessionInfo.audioStreamConfig
            if (audioStreamConfig.isSupportedAac) {
                val aacConfig = audioStreamConfig.buildCodecSpecificData()
                if (aacConfig == null) {
                    Logger.e(
                        Logger.TAG_SERVICE,
                        "Negotiated AAC codec ${audioStreamConfig.codecLabel} but codec specific data was unavailable"
                    )
                    return
                }

                val audioSuccess = audioDecoder.configure(
                    sampleRate = sessionInfo.audioSampleRate,
                    channels = sessionInfo.audioChannels,
                    aacConfig = aacConfig,
                )
                if (!audioSuccess) {
                    Logger.e(Logger.TAG_SERVICE, "Failed to configure audio decoder")
                    return
                }

                try {
                    audioDecoder.start()
                } catch (e: Exception) {
                    Logger.e(Logger.TAG_SERVICE, "Failed to start audio decoder", e)
                    try {
                        audioDecoder.stop()
                    } catch (cleanupError: Exception) {
                        Logger.e(Logger.TAG_SERVICE, "Error stopping audio decoder during cleanup", cleanupError)
                    }
                    return
                }

                try {
                    syncManager = SyncManager(videoDecoder, audioDecoder).also { it.start() }
                } catch (e: Exception) {
                    Logger.e(Logger.TAG_SERVICE, "Failed to start sync manager", e)
                    syncManager = null
                }
            } else {
                Logger.w(
                    Logger.TAG_SERVICE,
                    "Audio codec unsupported for current session: ${audioStreamConfig.codecLabel}. Video will continue without audio."
                )
                syncManager = null
            }

            telemetryCollector.updateResolution(sessionInfo.videoWidth, sessionInfo.videoHeight)
            telemetryCollector.updateAudioMetrics(sessionInfo.audioSampleRate, sessionInfo.audioChannels)
            pendingSessionInfo = null
            mediaPipelineStarted = true

            Logger.i(Logger.TAG_SERVICE, "Media pipeline started successfully")
        } catch (e: Exception) {
            Logger.e(Logger.TAG_SERVICE, "Unexpected error starting media pipeline", e)
            try {
                stopMediaPipeline()
            } catch (cleanupError: Exception) {
                Logger.e(Logger.TAG_SERVICE, "Error during cleanup", cleanupError)
            }
        }
    }

    fun stopMediaPipeline() {
        Logger.i(Logger.TAG_SERVICE, "Stopping media pipeline")
        mediaPipelineStarted = false
        pendingSessionInfo = null
        pendingCodecConfig = null
        appliedCodecConfig = null

        try {
            syncManager?.stop()
        } catch (e: Exception) {
            Logger.e(Logger.TAG_SERVICE, "Error stopping sync manager", e)
        } finally {
            syncManager = null
        }

        try {
            videoDecoder.stop()
        } catch (e: Exception) {
            Logger.e(Logger.TAG_SERVICE, "Error stopping video decoder", e)
        }

        try {
            audioDecoder.stop()
        } catch (e: Exception) {
            Logger.e(Logger.TAG_SERVICE, "Error stopping audio decoder", e)
        }

        try {
            telemetryCollector.reset()
        } catch (e: Exception) {
            Logger.e(Logger.TAG_SERVICE, "Error resetting telemetry", e)
        }
        videoOutputSize.value = VideoOutputSize()

        Logger.i(Logger.TAG_SERVICE, "Media pipeline stopped")
    }

    private fun handleVideoOutputSizeChanged(width: Int, height: Int) {
        if (width <= 0 || height <= 0) {
            return
        }

        Logger.i(Logger.TAG_SERVICE, "Video output size updated: ${width}x${height}")
        videoOutputSize.value = VideoOutputSize(width, height)
    }
}

private fun ByteBuffer.toByteArray(): ByteArray {
    val duplicate = duplicate()
    val bytes = ByteArray(duplicate.remaining())
    duplicate.get(bytes)
    return bytes
}
