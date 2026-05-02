package com.airplay.tv.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.airplay.tv.R
import com.airplay.tv.network.mDNSModule

/**
 * Tela de estado Idle: aguardando conexão
 * Design refinado e profissional para Android TV
 */
@Composable
fun IdleScreen(
    deviceName: String,
    mdnsState: mDNSModule.ServiceState = mDNSModule.ServiceState.Unregistered
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                brush = Brush.verticalGradient(
                    colors = listOf(
                        Color(0xFF000000),
                        Color(0xFF0A0A0A),
                        Color(0xFF000000)
                    )
                )
            )
            .padding(64.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier.fillMaxWidth()
        ) {
            // Ícone/Logo AirPlay
            Text(
                text = "📱",
                fontSize = 96.sp,
                modifier = Modifier.padding(bottom = 32.dp)
            )
            
            // Título principal
            Text(
                text = "Pronto para Receber",
                fontSize = 56.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White,
                textAlign = TextAlign.Center,
                letterSpacing = 1.sp
            )
            
            Spacer(modifier = Modifier.height(16.dp))
            
            // Subtítulo
            Text(
                text = stringResource(R.string.state_idle_subtitle),
                fontSize = 28.sp,
                fontWeight = FontWeight.Light,
                color = Color.White.copy(alpha = 0.7f),
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(horizontal = 48.dp)
            )
            
            Spacer(modifier = Modifier.height(64.dp))
            
            // Card com informações do dispositivo
            Box(
                modifier = Modifier
                    .background(
                        color = Color.White.copy(alpha = 0.05f),
                        shape = RoundedCornerShape(16.dp)
                    )
                    .padding(horizontal = 48.dp, vertical = 32.dp)
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    // Label "Dispositivo"
                    Text(
                        text = "Dispositivo",
                        fontSize = 22.sp,
                        fontWeight = FontWeight.Light,
                        color = Color.White.copy(alpha = 0.6f),
                        textAlign = TextAlign.Center
                    )
                    
                    // Nome do dispositivo (destaque)
                    Text(
                        text = deviceName,
                        fontSize = 36.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF2196F3),
                        textAlign = TextAlign.Center
                    )
                    
                    // Divisor sutil
                    Box(
                        modifier = Modifier
                            .width(200.dp)
                            .height(1.dp)
                            .background(Color.White.copy(alpha = 0.2f))
                    )
                    
                    // Status do mDNS
                    MDNSStatusIndicator(mdnsState)
                }
            }
            
            Spacer(modifier = Modifier.height(48.dp))
            
            // Instrução adicional (sutil)
            Text(
                text = "Abra o Centro de Controle no seu dispositivo Apple",
                fontSize = 20.sp,
                fontWeight = FontWeight.Light,
                color = Color.White.copy(alpha = 0.4f),
                textAlign = TextAlign.Center,
                modifier = Modifier.alpha(0.7f)
            )
        }
    }
}

/**
 * Indicador de status do serviço mDNS
 * Design refinado com ícones e cores apropriadas
 */
@Composable
fun MDNSStatusIndicator(state: mDNSModule.ServiceState) {
    val (icon, statusText, statusColor) = when (state) {
        is mDNSModule.ServiceState.Unregistered -> {
            Triple("⚪", "Inicializando serviço...", Color(0xFF9E9E9E))
        }
        is mDNSModule.ServiceState.Registering -> {
            Triple("🟡", "Registrando na rede...", Color(0xFFFFC107))
        }
        is mDNSModule.ServiceState.Registered -> {
            Triple("🟢", "Pronto para receber conexões", Color(0xFF4CAF50))
        }
        is mDNSModule.ServiceState.Failed -> {
            Triple("🔴", "Erro: ${state.message}", Color(0xFFF44336))
        }
    }
    
    Row(
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.padding(8.dp)
    ) {
        Text(
            text = icon,
            fontSize = 24.sp,
            modifier = Modifier.padding(end = 12.dp)
        )
        
        Text(
            text = statusText,
            fontSize = 22.sp,
            fontWeight = FontWeight.Medium,
            color = statusColor,
            textAlign = TextAlign.Center
        )
    }
    
    // Mostrar nome do serviço quando registrado
    if (state is mDNSModule.ServiceState.Registered) {
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = state.serviceName,
            fontSize = 18.sp,
            fontWeight = FontWeight.Light,
            color = Color.White.copy(alpha = 0.6f),
            textAlign = TextAlign.Center
        )
    }
}
