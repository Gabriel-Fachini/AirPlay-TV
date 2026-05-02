package com.airplay.tv.protocol

import com.airplay.tv.util.Constants
import com.airplay.tv.util.Logger
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Handler do protocolo AirPlay (RTSP/RTP)
 * Gerencia servidor RTSP e handshake com clientes
 */
class ProtocolHandler {
    
    // Estado da conexão
    sealed class ConnectionState {
        object Idle : ConnectionState()
        data class Connected(val clientIp: String) : ConnectionState()
        data class Error(val message: String) : ConnectionState()
    }
    
    private val _connectionState = MutableStateFlow<ConnectionState>(ConnectionState.Idle)
    val connectionState: StateFlow<ConnectionState> = _connectionState.asStateFlow()
    
    // Informações da sessão
    data class SessionInfo(
        val clientIp: String,
        val videoWidth: Int,
        val videoHeight: Int,
        val audioSampleRate: Int,
        val audioChannels: Int
    )
    
    private var currentSession: SessionInfo? = null
    
    companion object {
        init {
            try {
                System.loadLibrary("airplay-native")
                Logger.i(Logger.TAG_PROTOCOL, "Native library loaded successfully")
            } catch (e: UnsatisfiedLinkError) {
                Logger.e(Logger.TAG_PROTOCOL, "Failed to load native library", e)
            }
        }
    }
    
    // JNI functions
    external fun getVersionFromJNI(): String
    private external fun startRTSPServerNative(port: Int): Boolean
    private external fun stopRTSPServerNative()
    private external fun isServerRunningNative(): Boolean
    private external fun getClientIpNative(): String
    private external fun getVideoResolutionNative(): IntArray
    private external fun getAudioConfigNative(): IntArray
    
    /**
     * Inicia servidor RTSP
     */
    fun startRTSPServer(port: Int = Constants.RTSP_PORT): Boolean {
        Logger.i(Logger.TAG_PROTOCOL, "Starting RTSP server on port $port")
        
        if (isServerRunning()) {
            Logger.w(Logger.TAG_PROTOCOL, "Server already running")
            return false
        }
        
        val success = startRTSPServerNative(port)
        
        if (success) {
            Logger.i(Logger.TAG_PROTOCOL, "RTSP server started successfully")
            _connectionState.value = ConnectionState.Idle
        } else {
            Logger.e(Logger.TAG_PROTOCOL, "Failed to start RTSP server")
            _connectionState.value = ConnectionState.Error("Failed to start server")
        }
        
        return success
    }
    
    /**
     * Para servidor RTSP
     */
    fun stopRTSPServer() {
        Logger.i(Logger.TAG_PROTOCOL, "Stopping RTSP server")
        
        if (!isServerRunning()) {
            Logger.w(Logger.TAG_PROTOCOL, "Server not running")
            return
        }
        
        stopRTSPServerNative()
        currentSession = null
        _connectionState.value = ConnectionState.Idle
        
        Logger.i(Logger.TAG_PROTOCOL, "RTSP server stopped")
    }
    
    /**
     * Verifica se servidor está rodando
     */
    fun isServerRunning(): Boolean {
        return isServerRunningNative()
    }
    
    /**
     * Obtém informações da sessão atual
     */
    fun getSessionInfo(): SessionInfo? {
        if (!isServerRunning()) {
            return null
        }
        
        val clientIp = getClientIpNative()
        if (clientIp.isEmpty()) {
            return null
        }
        
        val videoRes = getVideoResolutionNative()
        val audioConfig = getAudioConfigNative()
        
        return SessionInfo(
            clientIp = clientIp,
            videoWidth = videoRes[0],
            videoHeight = videoRes[1],
            audioSampleRate = audioConfig[0],
            audioChannels = audioConfig[1]
        )
    }
    
    // Callbacks chamados do código nativo (JNI)
    @Suppress("unused")
    private fun onClientConnected(clientIp: String) {
        Logger.i(Logger.TAG_PROTOCOL, "Client connected: $clientIp")
        
        // Atualizar informações da sessão
        val sessionInfo = getSessionInfo()
        if (sessionInfo != null) {
            currentSession = sessionInfo
            _connectionState.value = ConnectionState.Connected(clientIp)
            
            Logger.i(Logger.TAG_PROTOCOL, 
                "Session established: ${sessionInfo.videoWidth}x${sessionInfo.videoHeight}, " +
                "${sessionInfo.audioSampleRate}Hz ${sessionInfo.audioChannels}ch")
        }
    }
    
    @Suppress("unused")
    private fun onClientDisconnected() {
        Logger.i(Logger.TAG_PROTOCOL, "Client disconnected")
        currentSession = null
        _connectionState.value = ConnectionState.Idle
    }
    
    @Suppress("unused")
    private fun onError(error: String) {
        Logger.e(Logger.TAG_PROTOCOL, "Protocol error: $error")
        _connectionState.value = ConnectionState.Error(error)
    }
}
