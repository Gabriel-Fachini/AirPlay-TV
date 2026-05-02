package com.airplay.tv

import android.os.Bundle
import android.view.KeyEvent
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import com.airplay.tv.ui.AirPlayViewModel
import com.airplay.tv.ui.UIStateManager
import com.airplay.tv.ui.components.*
import com.airplay.tv.ui.theme.AirPlayTVTheme
import com.airplay.tv.util.Logger

/**
 * MainActivity - Versão de Produção
 * 
 * Activity principal do receptor AirPlay para Android TV.
 * Gerencia o ciclo de vida do serviço e navegação entre estados da UI.
 */
class MainActivity : ComponentActivity() {
    
    private val viewModel: AirPlayViewModel by viewModels()
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            AirPlayTVTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = Color.Black
                ) {
                    AirPlayScreen(viewModel)
                }
            }
        }
    }
    
    override fun onResume() {
        super.onResume()
        viewModel.startService()
    }
    
    override fun onPause() {
        super.onPause()
        viewModel.stopService()
    }
    
    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        // Interceptar botão BACK durante mirroring para encerrar sessão
        if (keyCode == KeyEvent.KEYCODE_BACK) {
            val currentState = viewModel.uiState.value
            if (currentState is UIStateManager.UIState.Mirroring) {
                Logger.i(Logger.TAG_UI, "BACK pressed during mirroring, ending session")
                viewModel.endSession()
                return true // Consumir evento, não fechar app
            }
        }
        return super.onKeyDown(keyCode, event)
    }
    
    override fun onDestroy() {
        super.onDestroy()
        // Garantir limpeza de recursos ao destruir Activity
        viewModel.stopService()
    }
}

/**
 * Composable principal que gerencia a navegação entre estados
 */
@Composable
fun AirPlayScreen(viewModel: AirPlayViewModel) {
    val uiState by viewModel.uiState.collectAsState()
    val telemetry by viewModel.telemetry.collectAsState()
    val mdnsState by viewModel.mdnsState.collectAsState()
    
    when (val state = uiState) {
        is UIStateManager.UIState.Startup -> {
            StartupScreen()
        }
        
        is UIStateManager.UIState.Idle -> {
            IdleScreen(
                deviceName = state.deviceName,
                mdnsState = mdnsState
            )
        }
        
        is UIStateManager.UIState.Connecting -> {
            ConnectingScreen(clientIp = state.clientIp)
        }
        
        is UIStateManager.UIState.Mirroring -> {
            MirroringScreen(
                clientIp = state.clientIp,
                resolution = state.resolution,
                telemetry = telemetry,
                onSurfaceReady = { surface ->
                    viewModel.setVideoSurface(surface)
                },
                onSurfaceReleased = {
                    viewModel.clearVideoSurface()
                }
            )
        }
        
        is UIStateManager.UIState.Error -> {
            ErrorScreen(
                errorMessage = state.message,
                onDismiss = {
                    viewModel.returnToIdle()
                }
            )
        }
    }
}
