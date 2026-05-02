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
    
    private val binder = LocalBinder()
    private val sessionManager = SessionManager()
    private val telemetryCollector = TelemetryCollector()
    
    // Decoders de mídia
    private val videoDecoder = VideoDecoder(telemetryCollector)
    private val audioDecoder = AudioDecoder()
    private var syncManager: SyncManager? = null
    
    // Protocol handler com referências aos decoders
    private val protocolHandler = ProtocolHandler(videoDecoder, audioDecoder)
    
    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    
    // Surface para renderização de vídeo (será configurado pela UI)
    private var videoSurface: Surface? = null
    
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
        
        // Observar mudanças de estado dos decoders
        scope.launch {
            videoDecoder.state.collect { state ->
                Logger.d(Logger.TAG_SERVICE, "Video decoder state: $state")
            }
        }
        
        scope.launch {
            audioDecoder.state.collect { state ->
                Logger.d(Logger.TAG_SERVICE, "Audio decoder state: $state")
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
        
        // Parar pipeline de mídia
        stopMediaPipeline()
        
        // Parar servidor RTSP
        protocolHandler.stopRTSPServer()
        
        // Encerrar sessão
        sessionManager.endSession()
        
        // Limpar recursos
        sessionManager.cleanup()
        scope.cancel()
        
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
    }
    
    /**
     * Trata mudanças de estado da conexão
     */
    private fun handleConnectionStateChange(state: ProtocolHandler.ConnectionState) {
        when (state) {
            is ProtocolHandler.ConnectionState.Idle -> {
                Logger.i(Logger.TAG_SERVICE, "Connection state: Idle")
                // Encerrar sessão se existir
                if (sessionManager.isSessionActive()) {
                    stopMediaPipeline()
                    sessionManager.endSession()
                }
            }
            
            is ProtocolHandler.ConnectionState.Connected -> {
                Logger.i(Logger.TAG_SERVICE, "Connection state: Connected (${state.clientIp})")
                
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
                        // Iniciar pipeline de mídia
                        startMediaPipeline(sessionInfo)
                    } else {
                        Logger.w(Logger.TAG_SERVICE, "Failed to start session (already active)")
                    }
                } else {
                    Logger.e(Logger.TAG_SERVICE, "Failed to get session info")
                }
            }
            
            is ProtocolHandler.ConnectionState.Error -> {
                Logger.e(Logger.TAG_SERVICE, "Connection state: Error (${state.message})")
                // Encerrar sessão em caso de erro
                if (sessionManager.isSessionActive()) {
                    stopMediaPipeline()
                    sessionManager.endSession()
                }
            }
        }
    }
    
    /**
     * Inicia pipeline de mídia (decoders + sync)
     */
    private fun startMediaPipeline(sessionInfo: ProtocolHandler.SessionInfo) {
        Logger.i(Logger.TAG_SERVICE, "Starting media pipeline")
        
        val surface = videoSurface
        if (surface == null) {
            Logger.e(Logger.TAG_SERVICE, "Video surface not configured")
            return
        }
        
        // Configurar video decoder
        // TODO: Obter SPS/PPS reais do handshake RTSP
        // Por enquanto, usar buffers vazios (será configurado quando receber primeiro frame)
        val sps = ByteBuffer.allocate(0)
        val pps = ByteBuffer.allocate(0)
        
        val videoSuccess = videoDecoder.configure(
            width = sessionInfo.videoWidth,
            height = sessionInfo.videoHeight,
            sps = sps,
            pps = pps,
            surface = surface
        )
        
        if (!videoSuccess) {
            Logger.e(Logger.TAG_SERVICE, "Failed to configure video decoder")
            return
        }
        
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
        
        // Iniciar decoders
        videoDecoder.start()
        audioDecoder.start()
        
        // Iniciar sync manager
        syncManager = SyncManager(videoDecoder, audioDecoder).also {
            it.start()
        }
        
        // Atualizar telemetria
        telemetryCollector.updateResolution(sessionInfo.videoWidth, sessionInfo.videoHeight)
        telemetryCollector.updateAudioMetrics(sessionInfo.audioSampleRate, sessionInfo.audioChannels)
        
        Logger.i(Logger.TAG_SERVICE, "Media pipeline started successfully")
    }
    
    /**
     * Para pipeline de mídia
     */
    private fun stopMediaPipeline() {
        Logger.i(Logger.TAG_SERVICE, "Stopping media pipeline")
        
        syncManager?.stop()
        syncManager = null
        
        videoDecoder.stop()
        audioDecoder.stop()
        
        telemetryCollector.reset()
        
        Logger.i(Logger.TAG_SERVICE, "Media pipeline stopped")
    }
    
    /**
     * Encerra sessão manualmente
     */
    fun endSession() {
        Logger.i(Logger.TAG_SERVICE, "Manual session end requested")
        stopMediaPipeline()
        sessionManager.endSession()
        protocolHandler.stopRTSPServer()
        protocolHandler.startRTSPServer(Constants.RTSP_PORT)
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
    
    /**
     * Verifica se servidor está rodando
     */
    fun isServerRunning(): Boolean {
        return protocolHandler.isServerRunning()
    }
}
