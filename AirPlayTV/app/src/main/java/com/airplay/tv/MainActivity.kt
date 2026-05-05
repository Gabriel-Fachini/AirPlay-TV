package com.airplay.tv

import android.os.Bundle
import android.os.SystemClock
import android.view.KeyEvent
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
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
    private companion object {
        const val TELEMETRY_TOGGLE_DOUBLE_PRESS_WINDOW_MS = 400L
    }

    private val viewModel: AirPlayViewModel by viewModels()
    private var isTelemetryOverlayVisible by mutableStateOf(false)
    private var lastCenterPressUptimeMs = 0L
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            AirPlayTVTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = Color.Black
                ) {
                    AirPlayScreen(
                        viewModel = viewModel,
                        telemetryOverlayVisible = isTelemetryOverlayVisible
                    )
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
        val currentState = viewModel.uiState.value

        if (currentState is UIStateManager.UIState.Mirroring && isCenterEnterKey(keyCode)) {
            val now = SystemClock.uptimeMillis()
            if (now - lastCenterPressUptimeMs <= TELEMETRY_TOGGLE_DOUBLE_PRESS_WINDOW_MS) {
                isTelemetryOverlayVisible = !isTelemetryOverlayVisible
                lastCenterPressUptimeMs = 0L
                Logger.i(Logger.TAG_UI, "Telemetry overlay toggled visible=$isTelemetryOverlayVisible")
            } else {
                lastCenterPressUptimeMs = now
            }
            return true
        }

        // Interceptar botão BACK durante mirroring para encerrar sessão
        if (keyCode == KeyEvent.KEYCODE_BACK) {
            if (currentState is UIStateManager.UIState.Mirroring ||
                currentState is UIStateManager.UIState.MediaPlayback) {
                Logger.i(Logger.TAG_UI, "BACK pressed during active AirPlay session, ending session")
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

    private fun isCenterEnterKey(keyCode: Int): Boolean {
        return keyCode == KeyEvent.KEYCODE_DPAD_CENTER ||
            keyCode == KeyEvent.KEYCODE_ENTER ||
            keyCode == KeyEvent.KEYCODE_NUMPAD_ENTER
    }
}

/**
 * Composable principal que gerencia a navegação entre estados
 */
@Composable
fun AirPlayScreen(
    viewModel: AirPlayViewModel,
    telemetryOverlayVisible: Boolean
) {
    val uiState by viewModel.uiState.collectAsState()
    val telemetry by viewModel.telemetry.collectAsState()
    val videoOutputSize by viewModel.videoOutputSize.collectAsState()
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
                videoOutputSize = videoOutputSize,
                telemetry = telemetry,
                telemetryOverlayVisible = telemetryOverlayVisible,
                onSurfaceReady = { surface ->
                    viewModel.setVideoSurface(surface)
                },
                onSurfaceReleased = {
                    viewModel.clearVideoSurface()
                }
            )
        }

        is UIStateManager.UIState.MediaPlayback -> {
            MediaPlaybackScreen(
                kind = state.kind,
                clientIp = state.clientIp,
                sessionId = state.sessionId,
                imageData = state.imageData,
                assetKey = state.assetKey,
                transition = state.transition,
                theme = state.theme,
                slideDurationSeconds = state.slideDurationSeconds
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
