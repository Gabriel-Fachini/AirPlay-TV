package com.airplay.tv.service

import android.view.Surface
import com.airplay.tv.media.AudioDecoder
import com.airplay.tv.media.DecoderState
import com.airplay.tv.media.SyncManager
import com.airplay.tv.media.VideoDecoder
import com.airplay.tv.media.VideoInputQueue
import com.airplay.tv.media.VideoPerformanceTracker
import com.airplay.tv.protocol.ProtocolHandler
import com.airplay.tv.util.Logger
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

internal class MediaPipelineController(
    private val telemetryCollector: TelemetryCollector,
) {


    val performanceTracker = VideoPerformanceTracker(telemetryCollector)
    val inputQueue = VideoInputQueue()
    val videoDecoder = VideoDecoder(telemetryCollector, performanceTracker, inputQueue) { width, height ->
        handleVideoOutputSizeChanged(width, height)
    }
    val audioDecoder = AudioDecoder()

    private val videoOutputSize = MutableStateFlow(VideoOutputSize())
    private var syncManager: SyncManager? = null
    private var videoSurface: Surface? = null
    private var pendingSessionInfo: ProtocolHandler.SessionInfo? = null
    private var mediaPipelineStarted = false

    private val videoConfigurator = VideoDecoderConfigurator(videoDecoder)
    private val audioConfigurator = AudioPipelineConfigurator(audioDecoder)

    fun getVideoOutputSize(): StateFlow<VideoOutputSize> = videoOutputSize

    fun setVideoSurface(surface: Surface) {
        Logger.i(Logger.TAG_SERVICE, "Video surface configured")
        videoSurface = surface

        pendingSessionInfo?.takeIf { !mediaPipelineStarted }?.let { sessionInfo ->
            Logger.i(Logger.TAG_SERVICE, "Starting deferred media pipeline after surface became available")
            startMediaPipeline(sessionInfo)
        }

        videoConfigurator.applyPendingConfig(surface)
    }

    fun clearVideoSurface() {
        Logger.i(Logger.TAG_SERVICE, "Video surface cleared")
        videoSurface = null
    }

    fun handleCodecConfigReceived(config: ProtocolHandler.CodecConfig) {
        videoConfigurator.handleCodecConfigReceived(config, videoSurface)
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

            if (audioConfigurator.startAudioPipeline(sessionInfo)) {
                ensureSyncManagerStarted()
            } else {
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

    fun ensureAudioPipelineStarted(sessionInfo: ProtocolHandler.SessionInfo): Boolean {
        return when (audioDecoder.state.value) {
            DecoderState.Running -> {
                Logger.i(Logger.TAG_SERVICE, "Audio pipeline already running")
                true
            }
            DecoderState.Configured -> {
                Logger.i(Logger.TAG_SERVICE, "Audio decoder configured but not running; starting now")
                try {
                    audioDecoder.start()
                    ensureSyncManagerStarted()
                    true
                } catch (e: Exception) {
                    Logger.e(Logger.TAG_SERVICE, "Failed to start configured audio decoder", e)
                    false
                }
            }
            else -> {
                Logger.i(
                    Logger.TAG_SERVICE,
                    "Starting deferred audio pipeline codec=${sessionInfo.audioStreamConfig.codecLabel} " +
                        "rate=${sessionInfo.audioSampleRate} ch=${sessionInfo.audioChannels}"
                )
                val started = audioConfigurator.startAudioPipeline(sessionInfo)
                if (started) {
                    ensureSyncManagerStarted()
                }
                started
            }
        }
    }

    fun stopMediaPipeline() {
        Logger.i(Logger.TAG_SERVICE, "Stopping media pipeline")
        mediaPipelineStarted = false
        pendingSessionInfo = null
        videoConfigurator.reset()

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

    private fun ensureSyncManagerStarted() {
        if (syncManager != null) {
            return
        }
        try {
            syncManager = SyncManager(videoDecoder, audioDecoder).also { it.start() }
        } catch (e: Exception) {
            Logger.e(Logger.TAG_SERVICE, "Failed to start sync manager", e)
            syncManager = null
        }
    }
}
