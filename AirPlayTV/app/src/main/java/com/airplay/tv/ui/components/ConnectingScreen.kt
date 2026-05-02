package com.airplay.tv.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.material3.CircularProgressIndicator
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
 * Tela de estado Connecting: estabelecendo conexão
 */
@Composable
fun ConnectingScreen(clientIp: String) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(48.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(32.dp)
        ) {
            // Spinner
            CircularProgressIndicator(
                modifier = Modifier.size(80.dp),
                color = Color.White,
                strokeWidth = 6.dp
            )
            
            // Título
            Text(
                text = stringResource(R.string.state_connecting_title),
                fontSize = 42.sp,
                color = Color.White,
                textAlign = TextAlign.Center
            )
            
            // Subtítulo
            Text(
                text = stringResource(R.string.state_connecting_subtitle),
                fontSize = 24.sp,
                color = Color.White.copy(alpha = 0.7f),
                textAlign = TextAlign.Center
            )
            
            // IP do cliente
            Text(
                text = clientIp,
                fontSize = 20.sp,
                color = Color.White.copy(alpha = 0.5f),
                textAlign = TextAlign.Center
            )
        }
    }
}
