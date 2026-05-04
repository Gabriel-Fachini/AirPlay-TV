package com.airplay.tv.ui

import android.app.Application
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.IBinder
import android.view.Surface
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.airplay.tv.network.NetworkUtils
import com.airplay.tv.network.mDNSModule
import com.airplay.tv.protocol.ProtocolHandler
import com.airplay.tv.service.AirPlayService
import com.airplay.tv.service.SessionManager
import com.airplay.tv.util.Constants
import com.airplay.tv.util.Logger
import com.airplay.tv.util.TelemetryCollector
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

/**
 * ViewModel principal da aplicação AirPlay TV
 */
class AirPlayViewModel(application: Application) : AndroidViewModel(application) {
    
    private val uiStateManager = UIStateManager()
    private val telemetryCollector = TelemetryCollector()
    private val mdnsModule = mDNSModule(application.applicationContext)
    
    val uiState: StateFlow<UIStateManager.UIState> = uiStateManager.currentState
    val telemetry: StateFlow<TelemetryCollector.Telemetry> = telemetryCollector.telemetry
    val mdnsState: StateFlow<mDNSModule.ServiceState> = mdnsModule.serviceState
    
    // Service binding
    private var airPlayService: AirPlayService? = null
    private var serviceBound = false
    private var pendingVideoSurface: Surface? = null
    
    private val serviceConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
            val binder = service as AirPlayService.LocalBinder
            airPlayService = binder.getService()
            serviceBound = true

            pendingVideoSurface?.let { surface ->
                airPlayService?.setVideoSurface(surface)
            }
            
            // Observar estados do serviço
            observeServiceStates()
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            airPlayService = null
            serviceBound = false
        }
    }

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
                                UIStateManager.UIState.Idle(Constants.DEFAULT_DEVICE_NAME)
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
    }
    
    /**
     * Observa estados do serviço AirPlay
     */
    private fun observeServiceStates() {
        val service = airPlayService ?: return
        
        // Observar estado da sessão
        viewModelScope.launch {
            service.getSessionState().collect { sessionState ->
                handleSessionStateChange(sessionState)
            }
        }
        
        // Observar estado da conexão
        viewModelScope.launch {
            service.getConnectionState().collect { connectionState ->
                handleConnectionStateChange(connectionState)
            }
        }

        viewModelScope.launch {
            service.getMediaPlaybackState().collect { mediaState ->
                handleMediaPlaybackStateChange(mediaState)
            }
        }

        viewModelScope.launch {
            service.getTelemetry().collect { serviceTelemetry ->
                telemetryCollector.updateFps(serviceTelemetry.fps.toFloat())
                telemetryCollector.updateLatency(serviceTelemetry.latencyMs)
                telemetryCollector.updateBitrate(serviceTelemetry.bitrateMbps * 1000f)
                telemetryCollector.updateFrameStats(
                    droppedFrames = serviceTelemetry.droppedFrames,
                    totalFrames = serviceTelemetry.totalFrames
                )
            }
        }

        viewModelScope.launch {
            service.getVideoOutputSize().collect { videoOutputSize ->
                if (videoOutputSize.width > 0 && videoOutputSize.height > 0) {
                    telemetryCollector.updateResolution(videoOutputSize.width, videoOutputSize.height)
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
                uiStateManager.returnToIdle(Constants.DEFAULT_DEVICE_NAME)
                telemetryCollector.reset()
            }
            
            is SessionManager.SessionState.Active -> {
                uiStateManager.transitionTo(
                    UIStateManager.UIState.Mirroring(
                        clientIp = state.session.clientIp,
                        resolution = state.session.getResolutionString(),
                        sessionStartTime = state.session.startTime
                    )
                )
                
                // Atualizar telemetria
                telemetryCollector.updateResolution(
                    state.session.videoWidth,
                    state.session.videoHeight
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

    private fun handleMediaPlaybackStateChange(state: ProtocolHandler.MediaPlaybackState) {
        when (state) {
            is ProtocolHandler.MediaPlaybackState.Idle -> {
                Logger.i(Logger.TAG_UI, "UI media playback state: Idle")
                if (!uiStateManager.isInState(UIStateManager.UIState.Mirroring::class)) {
                    uiStateManager.returnToIdle(Constants.DEFAULT_DEVICE_NAME)
                }
            }

            is ProtocolHandler.MediaPlaybackState.PhotoDisplayed -> {
                Logger.i(
                    Logger.TAG_UI,
                    "UI entering photo playback: sessionId=${state.sessionId.ifEmpty { "none" }} " +
                        "asset=${state.assetKey.ifEmpty { "none" }} bytes=${state.imageData.size}"
                )
                telemetryCollector.reset()
                uiStateManager.transitionTo(
                    UIStateManager.UIState.MediaPlayback(
                        clientIp = state.clientIp,
                        kind = "photo",
                        sessionId = state.sessionId,
                        imageData = state.imageData.copyOf(),
                        assetKey = state.assetKey,
                        transition = state.transition,
                        theme = null,
                        slideDurationSeconds = 0
                    )
                )
            }

            is ProtocolHandler.MediaPlaybackState.SlideshowPlaying -> {
                Logger.i(
                    Logger.TAG_UI,
                    "UI entering slideshow playback: sessionId=${state.sessionId.ifEmpty { "none" }} " +
                        "theme=${state.theme ?: "none"} duration=${state.slideDurationSeconds}s " +
                        "hasImage=${state.imageData != null}"
                )
                telemetryCollector.reset()
                uiStateManager.transitionTo(
                    UIStateManager.UIState.MediaPlayback(
                        clientIp = state.clientIp,
                        kind = "slideshow",
                        sessionId = state.sessionId,
                        imageData = state.imageData?.copyOf(),
                        assetKey = state.assetKey,
                        transition = state.transition,
                        theme = state.theme,
                        slideDurationSeconds = state.slideDurationSeconds
                    )
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
            
            // Obter SSID da rede (pode não estar disponível no emulador)
            val ssid = NetworkUtils.getWifiSsid(getApplication())
            if (ssid != null) {
                Logger.d(Logger.TAG_SERVICE, "SSID=$ssid")
            }
            
            // Registrar serviço mDNS
            mdnsModule.registerService(
                deviceName = Constants.DEFAULT_DEVICE_NAME,
                port = Constants.RTSP_PORT
            )
            
            // Iniciar AirPlayService
            val intent = Intent(getApplication(), AirPlayService::class.java)
            getApplication<Application>().startService(intent)
            getApplication<Application>().bindService(intent, serviceConnection, Context.BIND_AUTO_CREATE)
            
            Logger.i(Logger.TAG_SERVICE, "AirPlay service started")
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
            
            // Parar AirPlayService
            if (serviceBound) {
                getApplication<Application>().unbindService(serviceConnection)
                serviceBound = false
            }
            
            val intent = Intent(getApplication(), AirPlayService::class.java)
            getApplication<Application>().stopService(intent)
            
        }
    }
    
    /**
     * Encerra sessão atual
     */
    fun endSession() {
        viewModelScope.launch {
            Logger.i(Logger.TAG_SESSION, "Ending session")
            
            airPlayService?.endSession()
            
            // Retornar ao Idle
            uiStateManager.returnToIdle(Constants.DEFAULT_DEVICE_NAME)
            telemetryCollector.reset()
        }
    }

    fun setVideoSurface(surface: Surface) {
        pendingVideoSurface = surface
        airPlayService?.setVideoSurface(surface)
    }

    fun clearVideoSurface() {
        pendingVideoSurface = null
        airPlayService?.clearVideoSurface()
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
        
        // Simular algumas métricas
        telemetryCollector.updateResolution(1920, 1080)
        telemetryCollector.updateFps(30f)
        telemetryCollector.updateLatency(250)
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
        uiStateManager.returnToIdle(Constants.DEFAULT_DEVICE_NAME)
        telemetryCollector.reset()
    }
    
    override fun onCleared() {
        super.onCleared()
        stopService()
        mdnsModule.cleanup()
    }
}
