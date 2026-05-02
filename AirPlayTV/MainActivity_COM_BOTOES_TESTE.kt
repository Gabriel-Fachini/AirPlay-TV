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
        
        Logger.i(Logger.TAG_UI, "MainActivity created (versão com botões de teste)")
        
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
        Logger.i(Logger.TAG_UI, "MainActivity resumed")
        viewModel.startService()
    }
    
    override fun onPause() {
        super.onPause()
        Logger.i(Logger.TAG_UI, "MainActivity paused")
        viewModel.stopService()
    }
    
    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        // Interceptar botão BACK durante mirroring
        if (keyCode == KeyEvent.KEYCODE_BACK) {
            val currentState = viewModel.uiState.value
            if (currentState is UIStateManager.UIState.Mirroring) {
                Logger.i(Logger.TAG_UI, "BACK pressed during mirroring, ending session")
                viewModel.endSession()
                return true
            }
        }
        return super.onKeyDown(keyCode, event)
    }
}

/**
 * Composable principal COM BOTÕES DE TESTE
 */
@Composable
fun AirPlayScreenWithTestButtons(viewModel: AirPlayViewModel) {
    val uiState by viewModel.uiState.collectAsState()
    val telemetry by viewModel.telemetry.collectAsState()
    
    Box(modifier = Modifier.fillMaxSize()) {
        // UI principal (telas)
        when (val state = uiState) {
            is UIStateManager.UIState.Idle -> {
                IdleScreen(deviceName = state.deviceName)
            }
            
            is UIStateManager.UIState.Connecting -> {
                ConnectingScreen(clientIp = state.clientIp)
            }
            
            is UIStateManager.UIState.Mirroring -> {
                MirroringScreen(
                    clientIp = state.clientIp,
                    resolution = state.resolution,
                    telemetry = telemetry
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
 * Usa componentes TV-specific para suporte adequado a D-pad
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
            .background(Color.Black.copy(alpha = 0.8f))
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Título
        Text(
            text = "🧪 BOTÕES DE TESTE - Use as setas ← → do teclado",
            fontSize = 16.sp,
            color = Color.Yellow
        )
        
        // Linha 1: Estados principais (usando TvLazyRow para navegação)
        TvLazyRow(
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            contentPadding = PaddingValues(horizontal = 8.dp)
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
                    onClick = { viewModel.simulateError("Erro de teste simulado") }
                )
            }
        }
        
        // Linha 2: Variações
        TvLazyRow(
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            contentPadding = PaddingValues(horizontal = 8.dp)
        ) {
            item {
                TestButton(
                    text = "720p",
                    color = Color(0xFF9C27B0),
                    onClick = { viewModel.simulateMirroring("192.168.1.100", "1280x720") }
                )
            }
            
            item {
                TestButton(
                    text = "Erro Rede",
                    color = Color(0xFFE91E63),
                    onClick = { viewModel.simulateError("Conexão perdida (timeout)") }
                )
            }
        }
        
        // Hint
        Text(
            text = "↑ ↓ para mudar de linha | ← → para navegar | Enter para selecionar",
            fontSize = 12.sp,
            color = Color.White.copy(alpha = 0.6f)
        )
    }
}

/**
 * Botão de teste customizado com suporte a foco (D-pad/teclado)
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
            focusedScale = 1.1f
        ),
        modifier = Modifier
            .width(120.dp)
            .height(50.dp)
    ) {
        Text(
            text = text,
            fontSize = 14.sp,
            color = Color.White
        )
    }
}
