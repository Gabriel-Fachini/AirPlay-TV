package com.airplay.tv.service

import com.airplay.tv.util.Constants
import com.airplay.tv.util.Logger
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Gerenciador de sessões AirPlay
 * Controla ciclo de vida da sessão (single-session MVP)
 */
class SessionManager {
    
    // Estado da sessão
    sealed class SessionState {
        object Idle : SessionState()
        data class Active(val session: Session) : SessionState()
        data class Timeout(val session: Session) : SessionState()
    }
    
    // Dados da sessão
    data class Session(
        val clientIp: String,
        val startTime: Long,
        val videoWidth: Int,
        val videoHeight: Int,
        val audioSampleRate: Int,
        val audioChannels: Int
    ) {
        fun getDurationMs(): Long = System.currentTimeMillis() - startTime
        
        fun getResolutionString(): String = "${videoWidth}x${videoHeight}"
        
        fun getAudioConfigString(): String = "${audioSampleRate}Hz ${audioChannels}ch"
    }
    
    private val _sessionState = MutableStateFlow<SessionState>(SessionState.Idle)
    val sessionState: StateFlow<SessionState> = _sessionState.asStateFlow()
    
    private var currentSession: Session? = null
    private var timeoutJob: Job? = null
    @Volatile
    private var isActive = false
    
    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())
    
    /**
     * Inicia nova sessão
     * @return true se sessão foi criada, false se já existe sessão ativa
     */
    fun startSession(
        clientIp: String,
        videoWidth: Int,
        videoHeight: Int,
        audioSampleRate: Int,
        audioChannels: Int
    ): Boolean {
        Logger.i(Logger.TAG_SESSION, "Starting session for client: $clientIp")
        
        // Verificar se já existe sessão ativa (single-session MVP)
        if (isActive) {
            Logger.w(Logger.TAG_SESSION, "Session already active, rejecting new connection")
            return false
        }
        
        // Criar nova sessão
        val session = Session(
            clientIp = clientIp,
            startTime = System.currentTimeMillis(),
            videoWidth = videoWidth,
            videoHeight = videoHeight,
            audioSampleRate = audioSampleRate,
            audioChannels = audioChannels
        )
        
        currentSession = session
        isActive = true
        _sessionState.value = SessionState.Active(session)
        
        // Iniciar monitoramento de timeout
        startTimeoutMonitoring()
        
        Logger.i(Logger.TAG_SESSION, 
            "Session started: ${session.getResolutionString()}, ${session.getAudioConfigString()}")
        
        return true
    }
    
    /**
     * Encerra sessão atual
     */
    fun endSession() {
        val session = currentSession
        
        if (session == null) {
            Logger.w(Logger.TAG_SESSION, "No active session to end")
            return
        }
        
        Logger.i(Logger.TAG_SESSION, 
            "Ending session for ${session.clientIp} (duration: ${session.getDurationMs()}ms)")
        
        // Cancelar monitoramento de timeout
        timeoutJob?.cancel()
        timeoutJob = null
        
        // Limpar estado
        currentSession = null
        isActive = false
        _sessionState.value = SessionState.Idle
        
        Logger.i(Logger.TAG_SESSION, "Session ended")
    }
    
    /**
     * Verifica se existe sessão ativa
     */
    fun isSessionActive(): Boolean {
        return isActive
    }
    
    /**
     * Obtém sessão atual (se existir)
     */
    fun getCurrentSession(): Session? {
        return currentSession
    }
    
    /**
     * Atualiza timestamp de atividade (resetar timeout)
     */
    fun updateActivity() {
        if (!isActive) {
            return
        }
        
        // Reiniciar monitoramento de timeout
        timeoutJob?.cancel()
        startTimeoutMonitoring()
    }
    
    /**
     * Inicia monitoramento de timeout
     * Se não receber pacotes por SESSION_TIMEOUT_MS, marca sessão como timeout
     */
    private fun startTimeoutMonitoring() {
        timeoutJob = scope.launch {
            delay(Constants.SESSION_TIMEOUT_MS)
            
            val session = currentSession
            if (session != null && isActive) {
                Logger.w(Logger.TAG_SESSION, 
                    "Session timeout detected (no activity for ${Constants.SESSION_TIMEOUT_MS}ms)")
                
                _sessionState.value = SessionState.Timeout(session)
                
                // Aguardar um pouco antes de encerrar automaticamente
                delay(2000)
                endSession()
            }
        }
    }
    
    /**
     * Libera recursos
     */
    fun cleanup() {
        Logger.i(Logger.TAG_SESSION, "Cleaning up SessionManager")
        
        timeoutJob?.cancel()
        endSession()
        scope.cancel()
    }
}

