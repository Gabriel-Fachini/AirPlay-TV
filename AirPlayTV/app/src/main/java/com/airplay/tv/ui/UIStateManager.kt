package com.airplay.tv.ui

import com.airplay.tv.util.Logger
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Gerenciador de estados da UI com validação de transições
 */
class UIStateManager {
    
    /**
     * Estados possíveis da aplicação
     */
    sealed class UIState {
        /**
         * Estado inicial: aguardando conexão
         */
        data class Idle(val deviceName: String) : UIState()
        
        /**
         * Conectando com cliente AirPlay
         */
        data class Connecting(val clientIp: String) : UIState()
        
        /**
         * Sessão ativa de mirroring
         */
        data class Mirroring(
            val clientIp: String,
            val resolution: String,
            val sessionStartTime: Long
        ) : UIState()
        
        /**
         * Erro ocorreu
         */
        data class Error(val message: String, val throwable: Throwable? = null) : UIState()
    }
    
    private val _currentState = MutableStateFlow<UIState>(UIState.Idle(""))
    val currentState: StateFlow<UIState> = _currentState.asStateFlow()
    
    /**
     * Transições permitidas entre estados
     */
    private val allowedTransitions = mapOf(
        UIState.Idle::class to setOf(
            UIState.Connecting::class,
            UIState.Error::class
        ),
        UIState.Connecting::class to setOf(
            UIState.Mirroring::class,
            UIState.Error::class,
            UIState.Idle::class
        ),
        UIState.Mirroring::class to setOf(
            UIState.Idle::class,
            UIState.Error::class
        ),
        UIState.Error::class to setOf(
            UIState.Idle::class
        )
    )
    
    /**
     * Transiciona para novo estado com validação
     */
    fun transitionTo(newState: UIState) {
        val currentStateClass = _currentState.value::class
        val newStateClass = newState::class
        
        val allowed = allowedTransitions[currentStateClass]?.contains(newStateClass) ?: false
        
        if (!allowed) {
            Logger.w(
                Logger.TAG_UI,
                "Invalid state transition: ${currentStateClass.simpleName} -> ${newStateClass.simpleName}"
            )
            return
        }
        
        Logger.i(
            Logger.TAG_UI,
            "State transition: ${currentStateClass.simpleName} -> ${newStateClass.simpleName}"
        )
        
        _currentState.value = newState
    }
    
    /**
     * Retorna ao estado Idle
     */
    fun returnToIdle(deviceName: String) {
        transitionTo(UIState.Idle(deviceName))
    }
    
    /**
     * Verifica se está em estado específico
     */
    fun isInState(stateClass: kotlin.reflect.KClass<out UIState>): Boolean {
        return _currentState.value::class == stateClass
    }
}
