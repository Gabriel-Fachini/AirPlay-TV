package com.airplay.tv.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.airplay.tv.util.Constants
import com.airplay.tv.util.Logger
import com.airplay.tv.util.TelemetryCollector
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

/**
 * ViewModel principal da aplicação AirPlay TV
 */
class AirPlayViewModel : ViewModel() {
    
    private val uiStateManager = UIStateManager()
    private val telemetryCollector = TelemetryCollector()
    
    val uiState: StateFlow<UIStateManager.UIState> = uiStateManager.currentState
    val telemetry: StateFlow<TelemetryCollector.Telemetry> = telemetryCollector.telemetry
    
    init {
        Logger.i(Logger.TAG_UI, "AirPlayViewModel initialized")
        // Inicializar com estado Idle
        uiStateManager.returnToIdle(Constants.DEFAULT_DEVICE_NAME)
    }
    
    /**
     * Inicia o serviço AirPlay (stub - será implementado na Fase 3)
     */
    fun startService() {
        viewModelScope.launch {
            Logger.i(Logger.TAG_SERVICE, "Starting AirPlay service (stub)")
            // TODO: Implementar na Fase 3
            // - Registrar serviço mDNS
            // - Iniciar servidor RTSP
        }
    }
    
    /**
     * Para o serviço AirPlay (stub - será implementado na Fase 3)
     */
    fun stopService() {
        viewModelScope.launch {
            Logger.i(Logger.TAG_SERVICE, "Stopping AirPlay service (stub)")
            // TODO: Implementar na Fase 3
            // - Desregistrar serviço mDNS
            // - Parar servidor RTSP
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
    }
}
