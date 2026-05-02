package com.airplay.tv

import android.os.Bundle
import android.view.KeyEvent
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.foundation.lazy.list.TvLazyRow
import com.airplay.tv.network.mDNSModule
import com.airplay.tv.ui.AirPlayViewModel
import com.airplay.tv.ui.UIStateManager
import com.airplay.tv.ui.components.*
import com.airplay.tv.ui.theme.AirPlayTVTheme
import com.airplay.tv.util.Logger

/**
 * MainActivity COM BOTÕES DE TESTE
 * 
 * Esta versão inclui botões na parte inferior para navegar entre os estados.
 * Use esta versão para testar no emulador.
 * 
 * INSTRUÇÕES:
 * 1. Substitua o arquivo MainActivity.kt por este
 * 2. Recompile: ./gradlew assembleDebug
 * 3. Rode no emulador
 * 4. Use os botões na parte inferior para navegar entre estados
 * 
 * IMPORTANTE: Esta é uma versão de TESTE. 
 * Para produção, use a MainActivity.kt original (sem botões).
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
                    AirPlayScreenWithTestButtons(viewModel)
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
        // Task 6.1: Interceptar botão BACK durante mirroring
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
        // Task 6.2: Garantir limpeza de recursos ao destruir Activity
        viewModel.stopService()
    }
}

/**
 * Composable principal COM BOTÕES DE TESTE
 */
@Composable
fun AirPlayScreenWithTestButtons(viewModel: AirPlayViewModel) {
    val uiState by viewModel.uiState.collectAsState()
    val telemetry by viewModel.telemetry.collectAsState()
    val mdnsState by viewModel.mdnsState.collectAsState()
    
    Box(modifier = Modifier.fillMaxSize()) {
        // UI principal (telas)
        when (val state = uiState) {
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
        
        // BOTÕES DE TESTE (parte inferior)
        TestButtonsOverlay(
            viewModel = viewModel,
            modifier = Modifier.align(Alignment.BottomCenter)
        )
    }
}

/**
 * Overlay com botões de teste para navegar entre estados
 * Versão compacta para não obstruir a tela
 */
@OptIn(androidx.tv.foundation.ExperimentalTvFoundationApi::class)
@Composable
fun TestButtonsOverlay(
    viewModel: AirPlayViewModel,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(Color.Black.copy(alpha = 0.7f))
            .padding(8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        // Título compacto
        Text(
            text = "🧪 TESTE (use ← → Enter)",
            fontSize = 12.sp,
            color = Color.Yellow
        )
        
        // Botões em uma linha (usando TvLazyRow para navegação)
        TvLazyRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            contentPadding = PaddingValues(horizontal = 4.dp)
        ) {
            item {
                TestButton(
                    text = "Idle",
                    color = Color(0xFF2196F3),
                    onClick = { viewModel.returnToIdle() }
                )
            }
            
            item {
                TestButton(
                    text = "Connecting",
                    color = Color(0xFFFF9800),
                    onClick = { viewModel.simulateConnecting("192.168.1.100") }
                )
            }
            
            item {
                TestButton(
                    text = "Mirroring",
                    color = Color(0xFF4CAF50),
                    onClick = { viewModel.simulateMirroring("192.168.1.100", "1920x1080") }
                )
            }
            
            item {
                TestButton(
                    text = "Error",
                    color = Color(0xFFF44336),
                    onClick = { viewModel.simulateError("Erro de teste") }
                )
            }
        }
    }
}

/**
 * Botão de teste customizado com suporte a foco (D-pad/teclado)
 * Versão compacta
 */
@OptIn(androidx.tv.material3.ExperimentalTvMaterial3Api::class)
@Composable
fun TestButton(
    text: String,
    color: Color,
    onClick: () -> Unit
) {
    androidx.tv.material3.Button(
        onClick = onClick,
        colors = androidx.tv.material3.ButtonDefaults.colors(
            containerColor = color,
            focusedContainerColor = color.copy(alpha = 0.8f)
        ),
        scale = androidx.tv.material3.ButtonDefaults.scale(
            focusedScale = 1.05f
        ),
        modifier = Modifier
            .width(90.dp)
            .height(40.dp)
    ) {
        Text(
            text = text,
            fontSize = 12.sp,
            color = Color.White
        )
    }
}
