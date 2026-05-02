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

/**
 * Tela de estado Idle: aguardando conexão
 */
@Composable
fun IdleScreen(deviceName: String) {
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
        }
    }
}
