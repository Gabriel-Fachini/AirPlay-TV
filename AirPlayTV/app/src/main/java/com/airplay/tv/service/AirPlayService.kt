package com.airplay.tv.service

import android.app.Service
import android.content.Intent
import android.os.Binder
import android.os.IBinder
import android.view.Surface
import com.airplay.tv.media.AudioDecoder
import com.airplay.tv.media.SyncManager
import com.airplay.tv.media.VideoDecoder
import com.airplay.tv.protocol.ProtocolHandler
import com.airplay.tv.util.Constants
import com.airplay.tv.util.Logger
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.StateFlow
import java.nio.ByteBuffer

/**
 * Serviço Android para gerenciar conexões AirPlay
 * Integra ProtocolHandler, SessionManager e pipeline de mídia
 */
class AirPlayService : Service() {
    data class VideoOutputSize(
        val width: Int = 0,
        val height: Int = 0
    )
    
    private val binder = LocalBinder()
    private val sessionManager = SessionManager()
    private val telemetryCollector = TelemetryCollector()
    private val videoOutputSize = kotlinx.coroutines.flow.MutableStateFlow(VideoOutputSize())
    
    // Decoders de mídia
    private val videoDecoder = VideoDecoder(telemetryCollector) { width, height ->
        handleVideoOutputSizeChanged(width, height)
    }
    private val audioDecoder = AudioDecoder()
    private var syncManager: SyncManager? = null
    
    // Protocol handler com referências aos decoders
    private val protocolHandler = ProtocolHandler(videoDecoder, audioDecoder)
    
    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    
    // Surface para renderização de vídeo (será configurado pela UI)
    private var videoSurface: Surface? = null
    private var pendingSessionInfo: ProtocolHandler.SessionInfo? = null
    private var pendingCodecConfig: ProtocolHandler.CodecConfig? = null
    private var mediaPipelineStarted = false
    
    inner class LocalBinder : Binder() {
        fun getService(): AirPlayService = this@AirPlayService
    }
    
    override fun onCreate() {
        super.onCreate()
        Logger.i(Logger.TAG_SERVICE, "AirPlayService created")
        
        // Observar mudanças de estado do protocolo
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
        
        // Observar codec config recebido
        scope.launch {
            protocolHandler.codecConfigReceived.collect { config ->
                Logger.i(Logger.TAG_SERVICE, "Codec config received: ${config.width}x${config.height}")
                handleCodecConfigReceived(config)
            }
        }
        
    }
    
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Logger.i(Logger.TAG_SERVICE, "AirPlayService started")
        
        // Iniciar servidor RTSP
        val success = protocolHandler.startRTSPServer(Constants.RTSP_PORT)
        
        if (!success) {
            Logger.e(Logger.TAG_SERVICE, "Failed to start RTSP server")
            stopSelf()
            return START_NOT_STICKY
        }
        
        Logger.i(Logger.TAG_SERVICE, "RTSP server started on port ${Constants.RTSP_PORT}")
        
        return START_STICKY
    }
    
    override fun onDestroy() {
        super.onDestroy()
        Logger.i(Logger.TAG_SERVICE, "AirPlayService destroyed")
        
        // Task 6.2: Para pipeline de mídia com tratamento de erros
        try {
            stopMediaPipeline()
        } catch (e: Exception) {
            Logger.e(Logger.TAG_SERVICE, "Error stopping media pipeline on destroy", e)
        }
        
        // Parar servidor RTSP
        try {
            protocolHandler.stopRTSPServer()
        } catch (e: Exception) {
            Logger.e(Logger.TAG_SERVICE, "Error stopping RTSP server on destroy", e)
        }
        
        // Encerrar sessão
        try {
            sessionManager.endSession()
        } catch (e: Exception) {
            Logger.e(Logger.TAG_SERVICE, "Error ending session on destroy", e)
        }
        
        // Limpar recursos
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
    
    override fun onBind(intent: Intent?): IBinder {
        return binder
    }
    
    /**
     * Configura Surface para renderização de vídeo
     * Deve ser chamado pela UI antes de iniciar sessão
     */
    fun setVideoSurface(surface: Surface) {
        Logger.i(Logger.TAG_SERVICE, "Video surface configured")
        videoSurface = surface

        val pendingSession = pendingSessionInfo
        if (pendingSession != null && !mediaPipelineStarted) {
            Logger.i(Logger.TAG_SERVICE, "Starting deferred media pipeline after surface became available")
            startMediaPipeline(pendingSession)
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
    
    /**
     * Task 6.2: Trata mudanças de estado da conexão com tratamento robusto de erros
     */
    private fun handleConnectionStateChange(state: ProtocolHandler.ConnectionState) {
        when (state) {
            is ProtocolHandler.ConnectionState.Idle -> {
                Logger.i(Logger.TAG_SERVICE, "Connection state: Idle")
                // Encerrar sessão se existir
                if (sessionManager.isSessionActive()) {
                    try {
                        stopMediaPipeline()
                        sessionManager.endSession()
                    } catch (e: Exception) {
                        Logger.e(Logger.TAG_SERVICE, "Error ending session on Idle state", e)
                    }
                }
            }
            
            is ProtocolHandler.ConnectionState.Connected -> {
                Logger.i(Logger.TAG_SERVICE, "Connection state: Connected (${state.clientIp})")
                
                try {
                    // Obter informações da sessão
                    val sessionInfo = protocolHandler.getSessionInfo()
                    
                    if (sessionInfo != null) {
                        // Iniciar sessão
                        val success = sessionManager.startSession(
                            clientIp = sessionInfo.clientIp,
                            videoWidth = sessionInfo.videoWidth,
                            videoHeight = sessionInfo.videoHeight,
                            audioSampleRate = sessionInfo.audioSampleRate,
                            audioChannels = sessionInfo.audioChannels
                        )
                        
                        if (success) {
                            sessionManager.updateActivity()
                            if (videoSurface == null) {
                                Logger.i(Logger.TAG_SERVICE, "Deferring media pipeline until UI surface is ready")
                                pendingSessionInfo = sessionInfo
                            } else {
                                startMediaPipeline(sessionInfo)
                            }
                        } else {
                            Logger.w(Logger.TAG_SERVICE, "Failed to start session (already active)")
                        }
                    } else {
                        Logger.e(Logger.TAG_SERVICE, "Failed to get session info")
                    }
                } catch (e: Exception) {
                    Logger.e(Logger.TAG_SERVICE, "Error handling connection", e)
                    // Limpar estado parcial
                    try {
                        stopMediaPipeline()
                        sessionManager.endSession()
                    } catch (cleanupError: Exception) {
                        Logger.e(Logger.TAG_SERVICE, "Error during cleanup", cleanupError)
                    }
                }
            }
            
            is ProtocolHandler.ConnectionState.Error -> {
                Logger.e(Logger.TAG_SERVICE, "Connection state: Error (${state.message})")
                // Encerrar sessão em caso de erro
                if (sessionManager.isSessionActive()) {
                    try {
                        stopMediaPipeline()
                        sessionManager.endSession()
                    } catch (e: Exception) {
                        Logger.e(Logger.TAG_SERVICE, "Error ending session on Error state", e)
                    }
                }
            }
        }
    }
    
    /**
     * Handles codec config received from mirroring session
     * Reconfigures video decoder with SPS/PPS
     */
    private fun handleCodecConfigReceived(config: ProtocolHandler.CodecConfig) {
        pendingCodecConfig = config.copy(
            sps = config.sps.duplicate(),
            pps = config.pps.duplicate()
        )
        Logger.i(Logger.TAG_SERVICE, "Handling codec config: ${config.width}x${config.height}, SPS=${config.sps.remaining()}B, PPS=${config.pps.remaining()}B")
        
        val surface = videoSurface
        if (surface == null) {
            Logger.w(Logger.TAG_SERVICE, "Cannot configure decoder: no surface available")
            return
        }
        
        try {
            // Stop decoder if running
            if (videoDecoder.state.value != VideoDecoder.DecoderState.Idle) {
                Logger.i(Logger.TAG_SERVICE, "Stopping existing decoder before reconfiguration")
                videoDecoder.stop()
            }
            
            // Configure with SPS/PPS
            val success = videoDecoder.configure(
                width = config.width,
                height = config.height,
                sps = config.sps,
                pps = config.pps,
                surface = surface
            )
            
            if (success) {
                // Start decoder
                videoDecoder.start()
                pendingCodecConfig = null
                Logger.i(Logger.TAG_SERVICE, "Video decoder configured and started with codec config")
            } else {
                Logger.e(Logger.TAG_SERVICE, "Failed to configure video decoder with codec config")
            }
        } catch (e: Exception) {
            Logger.e(Logger.TAG_SERVICE, "Error configuring decoder with codec config", e)
        }
    }

    private fun handleVideoOutputSizeChanged(width: Int, height: Int) {
        if (width <= 0 || height <= 0) {
            return
        }

        Logger.i(Logger.TAG_SERVICE, "Video output size updated: ${width}x${height}")
        videoOutputSize.value = VideoOutputSize(width, height)
    }
    
    /**
     * Task 6.2: Inicia pipeline de mídia com tratamento robusto de erros
     */
    private fun startMediaPipeline(sessionInfo: ProtocolHandler.SessionInfo) {
        Logger.i(Logger.TAG_SERVICE, "Starting media pipeline")
        
        try {
            val surface = videoSurface
            if (surface == null) {
                Logger.e(Logger.TAG_SERVICE, "Video surface not configured")
                pendingSessionInfo = sessionInfo
                return
            }
            
            // Video decoder will be configured when codec config (SPS/PPS) is received
            // This happens via handleCodecConfigReceived() when type 1 packet arrives
            Logger.i(Logger.TAG_SERVICE, "Waiting for codec config to configure video decoder")
            
            // Configurar audio decoder
            // TODO: Obter AudioSpecificConfig real do handshake RTSP
            // Por enquanto, usar buffer vazio (será configurado quando receber primeiro frame)
            val aacConfig = ByteBuffer.allocate(0)
            
            val audioSuccess = audioDecoder.configure(
                sampleRate = sessionInfo.audioSampleRate,
                channels = sessionInfo.audioChannels,
                aacConfig = aacConfig
            )
            
            if (!audioSuccess) {
                Logger.e(Logger.TAG_SERVICE, "Failed to configure audio decoder")
                return
            }
            
            // Iniciar audio decoder
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
            
            // Iniciar sync manager
            try {
                syncManager = SyncManager(videoDecoder, audioDecoder).also {
                    it.start()
                }
            } catch (e: Exception) {
                Logger.e(Logger.TAG_SERVICE, "Failed to start sync manager", e)
                // Continuar sem sync (não é crítico)
                syncManager = null
            }
            
            // Atualizar telemetria
            telemetryCollector.updateResolution(sessionInfo.videoWidth, sessionInfo.videoHeight)
            telemetryCollector.updateAudioMetrics(sessionInfo.audioSampleRate, sessionInfo.audioChannels)
            pendingSessionInfo = null
            mediaPipelineStarted = true
            
            Logger.i(Logger.TAG_SERVICE, "Media pipeline started successfully")
            
        } catch (e: Exception) {
            Logger.e(Logger.TAG_SERVICE, "Unexpected error starting media pipeline", e)
            // Limpar tudo
            try {
                stopMediaPipeline()
            } catch (cleanupError: Exception) {
                Logger.e(Logger.TAG_SERVICE, "Error during cleanup", cleanupError)
            }
        }
    }
    
    /**
     * Task 6.2: Para pipeline de mídia com tratamento robusto de erros
     */
    private fun stopMediaPipeline() {
        Logger.i(Logger.TAG_SERVICE, "Stopping media pipeline")
        mediaPipelineStarted = false
        pendingSessionInfo = null
        pendingCodecConfig = null
        
        // Parar sync manager
        try {
            syncManager?.stop()
        } catch (e: Exception) {
            Logger.e(Logger.TAG_SERVICE, "Error stopping sync manager", e)
        } finally {
            syncManager = null
        }
        
        // Parar video decoder
        try {
            videoDecoder.stop()
        } catch (e: Exception) {
            Logger.e(Logger.TAG_SERVICE, "Error stopping video decoder", e)
        }
        
        // Parar audio decoder
        try {
            audioDecoder.stop()
        } catch (e: Exception) {
            Logger.e(Logger.TAG_SERVICE, "Error stopping audio decoder", e)
        }
        
        // Resetar telemetria
        try {
            telemetryCollector.reset()
        } catch (e: Exception) {
            Logger.e(Logger.TAG_SERVICE, "Error resetting telemetry", e)
        }
        videoOutputSize.value = VideoOutputSize()
        
        Logger.i(Logger.TAG_SERVICE, "Media pipeline stopped")
    }
    
    /**
     * Task 6.1 & 6.2: Encerra sessão manualmente com tratamento robusto de erros
     */
    fun endSession() {
        Logger.i(Logger.TAG_SERVICE, "Manual session end requested")
        
        try {
            stopMediaPipeline()
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
            protocolHandler.startRTSPServer(Constants.RTSP_PORT)
        } catch (e: Exception) {
            Logger.e(Logger.TAG_SERVICE, "Error restarting RTSP server", e)
        }
    }
    
    /**
     * Obtém estado da sessão
     */
    fun getSessionState(): StateFlow<SessionManager.SessionState> {
        return sessionManager.sessionState
    }
    
    /**
     * Obtém estado da conexão
     */
    fun getConnectionState(): StateFlow<ProtocolHandler.ConnectionState> {
        return protocolHandler.connectionState
    }
    
    /**
     * Obtém telemetria
     */
    fun getTelemetry(): StateFlow<TelemetryCollector.Telemetry> {
        return telemetryCollector.telemetry
    }

    fun getVideoOutputSize(): StateFlow<VideoOutputSize> {
        return videoOutputSize
    }
    
    /**
     * Verifica se servidor está rodando
     */
    fun isServerRunning(): Boolean {
        return protocolHandler.isServerRunning()
    }
}
