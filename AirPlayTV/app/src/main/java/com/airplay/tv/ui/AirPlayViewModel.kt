package com.airplay.tv.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.airplay.tv.network.NetworkUtils
import com.airplay.tv.network.mDNSModule
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
    
    init {
        Logger.i(Logger.TAG_UI, "AirPlayViewModel initialized")
        // Inicializar com estado Idle
        uiStateManager.returnToIdle(Constants.DEFAULT_DEVICE_NAME)
        
        // Observar estado do mDNS
        viewModelScope.launch {
            mdnsModule.serviceState.collect { state ->
                when (state) {
                    is mDNSModule.ServiceState.Registered -> {
                        Logger.i(Logger.TAG_MDNS, "mDNS service registered: ${state.serviceName}")
                    }
                    is mDNSModule.ServiceState.Failed -> {
                        Logger.e(Logger.TAG_MDNS, "mDNS registration failed: ${state.message}")
                    }
                    else -> {}
                }
            }
        }
    }
    
    /**
     * Inicia o serviço AirPlay
     * - Verifica conexão Wi-Fi (ou permite emulador)
     * - Registra serviço mDNS
     * - Prepara para receber conexões
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
                    Logger.i(Logger.TAG_SERVICE, "No Wi-Fi but has network (emulator?), proceeding...")
                }
            }
            
            // Obter IP local
            val localIp = NetworkUtils.getLocalIpAddress()
            if (localIp == null) {
                Logger.w(Logger.TAG_SERVICE, "Could not determine local IP address")
            } else {
                Logger.i(Logger.TAG_SERVICE, "Local IP: $localIp")
            }
            
            // Obter SSID da rede (pode não estar disponível no emulador)
            val ssid = NetworkUtils.getWifiSsid(getApplication())
            if (ssid != null) {
                Logger.i(Logger.TAG_SERVICE, "Connected to Wi-Fi: $ssid")
            } else {
                Logger.d(Logger.TAG_SERVICE, "Wi-Fi SSID not available (emulator?)")
            }
            
            // Registrar serviço mDNS
            mdnsModule.registerService(
                deviceName = Constants.DEFAULT_DEVICE_NAME,
                port = Constants.RTSP_PORT
            )
            
            // TODO: Fase 4 - Iniciar servidor RTSP
            Logger.d(Logger.TAG_SERVICE, "RTSP server will be started in Phase 4")
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
            
            // TODO: Fase 4 - Parar servidor RTSP
            Logger.d(Logger.TAG_SERVICE, "RTSP server will be stopped in Phase 4")
        }
    }
    
    /**
     * Encerra sessão atual (stub - será implementado na Fase 4)
     */
    fun endSession() {
        viewModelScope.launch {
            Logger.i(Logger.TAG_SESSION, "Ending session (stub)")
            // TODO: Implementar na Fase 4
            // - Enviar TEARDOWN
            // - Parar decoders
            // - Liberar recursos
            
            // Por enquanto, apenas retorna ao Idle
            uiStateManager.returnToIdle(Constants.DEFAULT_DEVICE_NAME)
            telemetryCollector.reset()
        }
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
        Logger.i(Logger.TAG_UI, "AirPlayViewModel cleared")
        stopService()
        mdnsModule.cleanup()
    }
}
