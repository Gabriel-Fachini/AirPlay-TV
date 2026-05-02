package com.airplay.tv.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.airplay.tv.R
import com.airplay.tv.network.mDNSModule

/**
 * Tela de estado Idle: aguardando conexão
 */
@Composable
fun IdleScreen(
    deviceName: String,
    mdnsState: mDNSModule.ServiceState = mDNSModule.ServiceState.Unregistered
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(48.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(24.dp)
        ) {
            // Título principal
            Text(
                text = stringResource(R.string.state_idle_title),
                fontSize = 48.sp,
                color = Color.White,
                textAlign = TextAlign.Center
            )
            
            // Subtítulo
            Text(
                text = stringResource(R.string.state_idle_subtitle),
                fontSize = 28.sp,
                color = Color.White.copy(alpha = 0.7f),
                textAlign = TextAlign.Center
            )
            
            Spacer(modifier = Modifier.height(32.dp))
            
            // Nome do dispositivo
            Text(
                text = stringResource(R.string.state_idle_device_name, deviceName),
                fontSize = 24.sp,
                color = Color.White.copy(alpha = 0.5f),
                textAlign = TextAlign.Center
            )
            
            Spacer(modifier = Modifier.height(16.dp))
            
            // Status do mDNS
            MDNSStatusIndicator(mdnsState)
        }
    }
}

/**
 * Indicador de status do serviço mDNS
 */
@Composable
fun MDNSStatusIndicator(state: mDNSModule.ServiceState) {
    val (statusText, statusColor) = when (state) {
        is mDNSModule.ServiceState.Unregistered -> {
            "⚪ Serviço não registrado" to Color.Gray
        }
        is mDNSModule.ServiceState.Registering -> {
            "🟡 Registrando serviço..." to Color.Yellow
        }
        is mDNSModule.ServiceState.Registered -> {
            "🟢 Visível na rede como: ${state.serviceName}" to Color.Green
        }
        is mDNSModule.ServiceState.Failed -> {
            "🔴 Erro: ${state.message}" to Color.Red
        }
    }
    
    Text(
        text = statusText,
        fontSize = 18.sp,
        color = statusColor,
        textAlign = TextAlign.Center
    )
}
