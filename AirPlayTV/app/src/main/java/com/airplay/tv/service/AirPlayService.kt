package com.airplay.tv.service

import android.app.Service
import android.content.Intent
import android.os.Binder
import android.os.IBinder
import com.airplay.tv.protocol.ProtocolHandler
import com.airplay.tv.util.Constants
import com.airplay.tv.util.Logger
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.StateFlow

/**
 * Serviço Android para gerenciar conexões AirPlay
 * Integra ProtocolHandler e SessionManager
 */
class AirPlayService : Service() {
    
    private val binder = LocalBinder()
    private val protocolHandler = ProtocolHandler()
    private val sessionManager = SessionManager()
    
    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    
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
     * Trata mudanças de estado da conexão
     */
    private fun handleConnectionStateChange(state: ProtocolHandler.ConnectionState) {
        when (state) {
            is ProtocolHandler.ConnectionState.Idle -> {
                Logger.i(Logger.TAG_SERVICE, "Connection state: Idle")
                // Encerrar sessão se existir
                if (sessionManager.isSessionActive()) {
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
                    
                    if (!success) {
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
                    sessionManager.endSession()
                }
            }
        }
    }
    
    /**
     * Encerra sessão manualmente
     */
    fun endSession() {
        Logger.i(Logger.TAG_SERVICE, "Manual session end requested")
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
     * Verifica se servidor está rodando
     */
    fun isServerRunning(): Boolean {
        return protocolHandler.isServerRunning()
    }
}

