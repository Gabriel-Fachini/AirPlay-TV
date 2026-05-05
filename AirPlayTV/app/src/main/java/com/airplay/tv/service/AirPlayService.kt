package com.airplay.tv.service

import android.app.Service
import android.content.Intent
import android.os.Binder
import android.os.IBinder
import android.view.Surface
import com.airplay.tv.protocol.ProtocolHandler
import com.airplay.tv.util.Constants
import com.airplay.tv.util.Logger
import com.airplay.tv.util.TelemetryCollector
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

/**
 * Serviço Android para gerenciar conexões AirPlay
 * Integra ProtocolHandler, SessionManager e pipeline de mídia
 */
class AirPlayService : Service() {
    private val binder = LocalBinder()
    private val sessionManager = SessionManager()
    private val telemetryCollector = TelemetryCollector()
    private val mediaPipelineController = MediaPipelineController(telemetryCollector)
    private val protocolHandler = ProtocolHandler(
        mediaPipelineController.videoDecoder,
        mediaPipelineController.audioDecoder,
    )
    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private var currentRtspPort: Int = Constants.RTSP_PORT

    inner class LocalBinder : Binder() {
        fun getService(): AirPlayService = this@AirPlayService
    }

    override fun onCreate() {
        super.onCreate()
        Logger.i(Logger.TAG_SERVICE, "AirPlayService created")

        scope.launch {
            protocolHandler.connectionState.collect { state ->
                handleConnectionStateChange(state)
            }
        }

        scope.launch {
            protocolHandler.sessionActivity.collect {
                if (sessionManager.isSessionActive()) {
                    sessionManager.updateActivity()
                }
            }
        }

        scope.launch {
            protocolHandler.codecConfigReceived.collect { config ->
                Logger.i(Logger.TAG_SERVICE, "Codec config received: ${config.width}x${config.height}")
                mediaPipelineController.handleCodecConfigReceived(config)
            }
        }

        scope.launch {
            protocolHandler.audioConfigReceived.collect { event ->
                Logger.i(
                    Logger.TAG_SERVICE,
                    "Audio config received source=${event.source} codec=${event.streamConfig.codecLabel} " +
                        "rate=${event.streamConfig.sampleRate} ch=${event.streamConfig.channels}"
                )
                val activeSession = sessionManager.getCurrentSession()
                val sessionInfo = protocolHandler.getSessionInfo()
                if (activeSession == null || sessionInfo == null) {
                    Logger.i(Logger.TAG_SERVICE, "Deferring audio pipeline start until session is active")
                    return@collect
                }
                val started = mediaPipelineController.ensureAudioPipelineStarted(sessionInfo)
                Logger.i(
                    Logger.TAG_SERVICE,
                    "Deferred audio pipeline result started=$started activeClient=${activeSession.clientIp}"
                )
            }
        }

        scope.launch {
            protocolHandler.mediaPlaybackState.collect { state ->
                if (state is ProtocolHandler.MediaPlaybackState.Idle) {
                    Logger.i(Logger.TAG_SERVICE, "Media playback state: Idle")
                    if (!sessionManager.isSessionActive()) {
                        telemetryCollector.reset()
                    }
                } else {
                    val description = when (state) {
                        is ProtocolHandler.MediaPlaybackState.PhotoDisplayed ->
                            "photo sessionId=${state.sessionId.ifEmpty { "none" }} asset=${state.assetKey.ifEmpty { "none" }}"
                        is ProtocolHandler.MediaPlaybackState.SlideshowPlaying ->
                            "slideshow sessionId=${state.sessionId.ifEmpty { "none" }} theme=${state.theme ?: "none"} duration=${state.slideDurationSeconds}s"
                        is ProtocolHandler.MediaPlaybackState.Idle -> "idle"
                    }
                    Logger.i(Logger.TAG_SERVICE, "Media playback state active: $description")
                    if (sessionManager.isSessionActive()) {
                        Logger.i(Logger.TAG_SERVICE, "Ending active mirroring session because HTTP media playback took over")
                        sessionManager.endSession()
                    }
                    Logger.i(Logger.TAG_SERVICE, "Stopping media pipeline for HTTP media playback mode")
                    mediaPipelineController.stopMediaPipeline()
                }
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Logger.i(Logger.TAG_SERVICE, "AirPlayService started")

        currentRtspPort = intent?.getIntExtra(Constants.EXTRA_RTSP_PORT, Constants.RTSP_PORT)
            ?: Constants.RTSP_PORT

        val success = protocolHandler.startRTSPServer(currentRtspPort)
        if (!success) {
            Logger.e(Logger.TAG_SERVICE, "Failed to start RTSP server on port $currentRtspPort")
            stopSelf()
            return START_NOT_STICKY
        }

        Logger.i(Logger.TAG_SERVICE, "RTSP server started on port $currentRtspPort")
        return START_STICKY
    }

    override fun onDestroy() {
        super.onDestroy()
        Logger.i(Logger.TAG_SERVICE, "AirPlayService destroyed")

        try {
            mediaPipelineController.stopMediaPipeline()
        } catch (e: Exception) {
            Logger.e(Logger.TAG_SERVICE, "Error stopping media pipeline on destroy", e)
        }

        try {
            protocolHandler.stopRTSPServer()
        } catch (e: Exception) {
            Logger.e(Logger.TAG_SERVICE, "Error stopping RTSP server on destroy", e)
        }

        try {
            sessionManager.endSession()
        } catch (e: Exception) {
            Logger.e(Logger.TAG_SERVICE, "Error ending session on destroy", e)
        }

        try {
            sessionManager.cleanup()
        } catch (e: Exception) {
            Logger.e(Logger.TAG_SERVICE, "Error cleaning up session manager", e)
        }

        try {
            scope.cancel()
        } catch (e: Exception) {
            Logger.e(Logger.TAG_SERVICE, "Error canceling coroutine scope", e)
        }

        Logger.i(Logger.TAG_SERVICE, "AirPlayService cleanup complete")
    }

    override fun onBind(intent: Intent?): IBinder = binder

    fun setVideoSurface(surface: Surface) {
        mediaPipelineController.setVideoSurface(surface)
    }

    fun clearVideoSurface() {
        mediaPipelineController.clearVideoSurface()
    }

    private fun handleConnectionStateChange(state: ProtocolHandler.ConnectionState) {
        when (state) {
            is ProtocolHandler.ConnectionState.Idle -> {
                Logger.i(Logger.TAG_SERVICE, "Connection state: Idle")
                if (sessionManager.isSessionActive()) {
                    try {
                        mediaPipelineController.stopMediaPipeline()
                        sessionManager.endSession()
                    } catch (e: Exception) {
                        Logger.e(Logger.TAG_SERVICE, "Error ending session on Idle state", e)
                    }
                }
            }

            is ProtocolHandler.ConnectionState.Connected -> {
                Logger.i(Logger.TAG_SERVICE, "Connection state: Connected (${state.clientIp})")
                try {
                    val sessionInfo = protocolHandler.getSessionInfo()
                    if (sessionInfo != null) {
                        val success = sessionManager.startSession(
                            clientIp = sessionInfo.clientIp,
                            videoWidth = sessionInfo.videoWidth,
                            videoHeight = sessionInfo.videoHeight,
                            audioSampleRate = sessionInfo.audioSampleRate,
                            audioChannels = sessionInfo.audioChannels,
                        )

                        if (success) {
                            sessionManager.updateActivity()
                            mediaPipelineController.startMediaPipeline(sessionInfo)
                        } else {
                            Logger.w(Logger.TAG_SERVICE, "Failed to start session (already active)")
                        }
                    } else {
                        Logger.e(Logger.TAG_SERVICE, "Failed to get session info")
                    }
                } catch (e: Exception) {
                    Logger.e(Logger.TAG_SERVICE, "Error handling connection", e)
                    try {
                        mediaPipelineController.stopMediaPipeline()
                        sessionManager.endSession()
                    } catch (cleanupError: Exception) {
                        Logger.e(Logger.TAG_SERVICE, "Error during cleanup", cleanupError)
                    }
                }
            }

            is ProtocolHandler.ConnectionState.Error -> {
                Logger.e(Logger.TAG_SERVICE, "Connection state: Error (${state.message})")
                if (sessionManager.isSessionActive()) {
                    try {
                        mediaPipelineController.stopMediaPipeline()
                        sessionManager.endSession()
                    } catch (e: Exception) {
                        Logger.e(Logger.TAG_SERVICE, "Error ending session on Error state", e)
                    }
                }
            }
        }
    }

    fun endSession() {
        Logger.i(Logger.TAG_SERVICE, "Manual session end requested")

        try {
            mediaPipelineController.stopMediaPipeline()
        } catch (e: Exception) {
            Logger.e(Logger.TAG_SERVICE, "Error stopping media pipeline", e)
        }

        try {
            sessionManager.endSession()
        } catch (e: Exception) {
            Logger.e(Logger.TAG_SERVICE, "Error ending session", e)
        }

        try {
            protocolHandler.stopRTSPServer()
        } catch (e: Exception) {
            Logger.e(Logger.TAG_SERVICE, "Error stopping RTSP server", e)
        }

        try {
            protocolHandler.startRTSPServer(currentRtspPort)
        } catch (e: Exception) {
            Logger.e(Logger.TAG_SERVICE, "Error restarting RTSP server", e)
        }
    }

    fun getSessionState(): StateFlow<SessionManager.SessionState> = sessionManager.sessionState

    fun getConnectionState(): StateFlow<ProtocolHandler.ConnectionState> = protocolHandler.connectionState

    fun getMediaPlaybackState(): StateFlow<ProtocolHandler.MediaPlaybackState> = protocolHandler.mediaPlaybackState

    fun getTelemetry(): StateFlow<TelemetryCollector.Telemetry> = telemetryCollector.telemetry

    fun getVideoOutputSize(): StateFlow<VideoOutputSize> = mediaPipelineController.getVideoOutputSize()

    fun isServerRunning(): Boolean = protocolHandler.isServerRunning()
}
