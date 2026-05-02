package com.airplay.tv.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
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
 * Tela de estado Error: exibindo erro
 */
@Composable
fun ErrorScreen(
    errorMessage: String,
    onDismiss: () -> Unit
) {
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
            // Título
            Text(
                text = stringResource(R.string.state_error_title),
                fontSize = 48.sp,
                color = Color(0xFFB00020),
                textAlign = TextAlign.Center
            )
            
            // Mensagem de erro
            Text(
                text = errorMessage,
                fontSize = 24.sp,
                color = Color.White.copy(alpha = 0.9f),
                textAlign = TextAlign.Center,
                modifier = Modifier.widthIn(max = 800.dp)
            )
            
            Spacer(modifier = Modifier.height(16.dp))
            
            // Botão OK
            Button(
                onClick = onDismiss,
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color(0xFF2196F3)
                ),
                modifier = Modifier
                    .width(200.dp)
                    .height(60.dp)
            ) {
                Text(
                    text = stringResource(R.string.state_error_button),
                    fontSize = 24.sp,
                    color = Color.White
                )
            }
        }
    }
}
