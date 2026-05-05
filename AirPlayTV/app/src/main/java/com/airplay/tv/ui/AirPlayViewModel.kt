package com.airplay.tv.ui

import android.app.Application
import android.view.Surface
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.airplay.tv.network.NetworkUtils
import com.airplay.tv.network.mDNSModule
import com.airplay.tv.protocol.ProtocolHandler
import com.airplay.tv.service.AirPlayService
import com.airplay.tv.service.SessionManager
import com.airplay.tv.service.VideoOutputSize
import com.airplay.tv.util.Constants
import com.airplay.tv.util.Logger
import com.airplay.tv.util.TelemetryCollector
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * ViewModel principal da aplicação AirPlay TV
 */
class AirPlayViewModel(application: Application) : AndroidViewModel(application) {
    
    private val uiStateManager = UIStateManager()
    private val mdnsModule = mDNSModule(application.applicationContext)
    
    val uiState: StateFlow<UIStateManager.UIState> = uiStateManager.currentState
    val mdnsState: StateFlow<mDNSModule.ServiceState> = mdnsModule.serviceState
    
    private val serviceConnectionManager = ServiceConnectionManager(application)
    val telemetry: StateFlow<TelemetryCollector.Telemetry> = TelemetryFlowBinder.bind(
        serviceConnectionManager.serviceFlow.map { service -> service?.getTelemetry() }
    ).stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = TelemetryCollector.Telemetry()
    )
    val videoOutputSize: StateFlow<VideoOutputSize> = VideoOutputSizeFlowBinder.bind(
        serviceConnectionManager.serviceFlow.map { service -> service?.getVideoOutputSize() }
    ).stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = VideoOutputSize()
    )
    private val mediaStateTracker = MediaStateTracker(uiStateManager)

    init {
        // Inicializar com estado Startup
        // A transição para Idle acontecerá quando o serviço estiver pronto
        
        // Observar estado do mDNS
        viewModelScope.launch {
            mdnsModule.serviceState.collect { state ->
                when (state) {
                    is mDNSModule.ServiceState.Registered -> {
                        Logger.i(Logger.TAG_MDNS, "mDNS service registered: ${state.serviceName}")
                        // Transicionar para Idle quando o serviço estiver registrado
                        if (uiStateManager.isInState(UIStateManager.UIState.Startup::class)) {
                            uiStateManager.transitionTo(
                                UIStateManager.UIState.Idle("AirPlayTV")
                            )
                        }
                    }
                    is mDNSModule.ServiceState.Failed -> {
                        Logger.e(Logger.TAG_MDNS, "mDNS registration failed: ${state.message}")
                        // Se falhar durante startup, mostrar erro
                        if (uiStateManager.isInState(UIStateManager.UIState.Startup::class)) {
                            uiStateManager.transitionTo(
                                UIStateManager.UIState.Error("Falha ao iniciar serviço: ${state.message}")
                            )
                        }
                    }
                    else -> {}
                }
            }
        }
        
        // Observar mudanças no serviço
        viewModelScope.launch {
            serviceConnectionManager.serviceFlow.collectLatest { service ->
                if (service != null) {
                    observeServiceStates(service)
                }
            }
        }
    }
    
    /**
     * Observa estados do serviço AirPlay
     */
    private suspend fun observeServiceStates(service: AirPlayService) {
        coroutineScope {
            // Observar estado da sessão
            launch {
                service.getSessionState().collect { sessionState ->
                    handleSessionStateChange(sessionState)
                }
            }
            
            // Observar estado da conexão
            launch {
                service.getConnectionState().collect { connectionState ->
                    handleConnectionStateChange(connectionState)
                }
            }

            launch {
                service.getMediaPlaybackState().collect { mediaState ->
                    mediaStateTracker.handleMediaPlaybackStateChange(mediaState)
                }
            }
        }
    }
    
    /**
     * Trata mudanças de estado da sessão
     */
    private fun handleSessionStateChange(state: SessionManager.SessionState) {
        when (state) {
            is SessionManager.SessionState.Idle -> {
                uiStateManager.returnToIdle("AirPlayTV")
            }
            
            is SessionManager.SessionState.Active -> {
                uiStateManager.transitionTo(
                    UIStateManager.UIState.Mirroring(
                        clientIp = state.session.clientIp,
                        resolution = state.session.getResolutionString(),
                        sessionStartTime = state.session.startTime
                    )
                )
            }
            
            is SessionManager.SessionState.Timeout -> {
                Logger.w(Logger.TAG_UI, "Session state: Timeout")
                uiStateManager.transitionTo(
                    UIStateManager.UIState.Error("Conexão perdida (timeout)")
                )
            }
        }
    }
    
    /**
     * Trata mudanças de estado da conexão
     */
    private fun handleConnectionStateChange(state: ProtocolHandler.ConnectionState) {
        when (state) {
            is ProtocolHandler.ConnectionState.Idle -> {
                // Estado já tratado pelo SessionManager
            }
            
            is ProtocolHandler.ConnectionState.Connected -> {
                if (uiStateManager.isInState(UIStateManager.UIState.Mirroring::class)) {
                    return
                }
                uiStateManager.transitionTo(
                    UIStateManager.UIState.Connecting(state.clientIp)
                )
            }
            
            is ProtocolHandler.ConnectionState.Error -> {
                Logger.e(Logger.TAG_UI, "Connection state: Error")
                uiStateManager.transitionTo(
                    UIStateManager.UIState.Error(state.message)
                )
            }
        }
    }


    
    /**
     * Inicia o serviço AirPlay
     * - Verifica conexão Wi-Fi (ou permite emulador)
     * - Registra serviço mDNS
     * - Inicia servidor RTSP
     */
    fun startService() {
        viewModelScope.launch {
            Logger.i(Logger.TAG_SERVICE, "Starting AirPlay service")
            
            // Verificar conexão Wi-Fi (permitir emulador)
            val isWifiConnected = NetworkUtils.isWifiConnected(getApplication())
            if (!isWifiConnected) {
                // No emulador, pode não ter Wi-Fi, mas tem rede
                val localIp = NetworkUtils.getLocalIpAddress()
                if (localIp == null) {
                    Logger.w(Logger.TAG_SERVICE, "No network connection, cannot start service")
                    uiStateManager.transitionTo(
                        UIStateManager.UIState.Error("Sem conexão de rede. Conecte-se a uma rede.")
                    )
                    return@launch
                } else {
                    Logger.d(Logger.TAG_SERVICE, "Net ok ip=$localIp")
                }
            }
            
            // Obter IP local
            val localIp = NetworkUtils.getLocalIpAddress()
            if (localIp != null) {
                Logger.i(Logger.TAG_SERVICE, "Local IP: $localIp")
            }

            val rtspPort = NetworkUtils.findAvailablePort(
                preferredPort = Constants.RTSP_PORT,
                scanLimit = Constants.RTSP_PORT_SCAN_LIMIT
            )
            if (rtspPort == null) {
                Logger.e(Logger.TAG_SERVICE, "No available RTSP port found near ${Constants.RTSP_PORT}")
                uiStateManager.transitionTo(
                    UIStateManager.UIState.Error(
                        "Outra app de AirPlay parece estar usando as portas do receptor. Feche-a e tente novamente."
                    )
                )
                return@launch
            }
            if (rtspPort != Constants.RTSP_PORT) {
                Logger.w(
                    Logger.TAG_SERVICE,
                    "Default RTSP port ${Constants.RTSP_PORT} busy, falling back to $rtspPort"
                )
            }
            
            // Registrar serviço mDNS
            mdnsModule.registerService(
                deviceName = "AirPlayTV",
                port = rtspPort
            )
            
            serviceConnectionManager.startAndBindService(rtspPort)
            
            Logger.i(Logger.TAG_SERVICE, "AirPlay service started on port $rtspPort")
        }
    }

    /**
     * Para o serviço AirPlay
     * - Desregistra serviço mDNS
     * - Para servidor RTSP
     */
    fun stopService() {
        viewModelScope.launch {
            Logger.i(Logger.TAG_SERVICE, "Stopping AirPlay service")
            
            // Desregistrar serviço mDNS
            mdnsModule.unregisterService()
            
            serviceConnectionManager.unbindAndStopService()
            
        }
    }
    
    /**
     * Encerra sessão atual
     */
    fun endSession() {
        viewModelScope.launch {
            Logger.i(Logger.TAG_SESSION, "Ending session")
            
            serviceConnectionManager.endSession()
            
            // Retornar ao Idle
            uiStateManager.returnToIdle("AirPlayTV")
        }
    }

    fun setVideoSurface(surface: Surface) {
        serviceConnectionManager.setVideoSurface(surface)
    }

    fun clearVideoSurface() {
        serviceConnectionManager.clearVideoSurface()
    }
    
    /**
     * Simula transição para estado Connecting (para testes)
     */
    fun simulateConnecting(clientIp: String = "192.168.1.100") {
        uiStateManager.transitionTo(UIStateManager.UIState.Connecting(clientIp))
    }
    
    /**
     * Simula transição para estado Mirroring (para testes)
     */
    fun simulateMirroring(clientIp: String = "192.168.1.100", resolution: String = "1920x1080") {
        uiStateManager.transitionTo(
            UIStateManager.UIState.Mirroring(
                clientIp = clientIp,
                resolution = resolution,
                sessionStartTime = System.currentTimeMillis()
            )
        )
    }
    
    /**
     * Simula erro (para testes)
     */
    fun simulateError(message: String = "Erro de teste") {
        uiStateManager.transitionTo(UIStateManager.UIState.Error(message))
    }
    
    /**
     * Retorna ao estado Idle
     */
    fun returnToIdle() {
        uiStateManager.returnToIdle("AirPlayTV")
    }
    
    override fun onCleared() {
        super.onCleared()
        stopService()
        mdnsModule.cleanup()
    }

}
