package com.airplay.tv.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.airplay.tv.R

/**
 * Tela de estado Error: exibindo erro
 * Design refinado com visual mais amigável
 */
@OptIn(androidx.tv.material3.ExperimentalTvMaterial3Api::class)
@Composable
fun ErrorScreen(
    errorMessage: String,
    onDismiss: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                brush = Brush.radialGradient(
                    colors = listOf(
                        Color(0xFFB00020).copy(alpha = 0.2f),
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
            // Ícone de erro
            Text(
                text = "⚠️",
                fontSize = 96.sp,
                modifier = Modifier.padding(bottom = 32.dp)
            )
            
            // Card com informações do erro
            Box(
                modifier = Modifier
                    .background(
                        color = Color.White.copy(alpha = 0.05f),
                        shape = RoundedCornerShape(16.dp)
                    )
                    .padding(horizontal = 48.dp, vertical = 40.dp)
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(24.dp)
                ) {
                    // Título
                    Text(
                        text = stringResource(R.string.state_error_title),
                        fontSize = 52.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFFF44336),
                        textAlign = TextAlign.Center
                    )
                    
                    // Mensagem de erro
                    Text(
                        text = errorMessage,
                        fontSize = 28.sp,
                        fontWeight = FontWeight.Normal,
                        color = Color.White.copy(alpha = 0.9f),
                        textAlign = TextAlign.Center,
                        modifier = Modifier
                            .widthIn(max = 900.dp)
                            .padding(horizontal = 24.dp)
                    )
                    
                    Spacer(modifier = Modifier.height(16.dp))
                    
                    // Botão OK com estilo TV
                    androidx.tv.material3.Button(
                        onClick = onDismiss,
                        colors = androidx.tv.material3.ButtonDefaults.colors(
                            containerColor = Color(0xFF2196F3),
                            focusedContainerColor = Color(0xFF1976D2)
                        ),
                        scale = androidx.tv.material3.ButtonDefaults.scale(
                            focusedScale = 1.1f
                        ),
                        modifier = Modifier
                            .width(240.dp)
                            .height(72.dp)
                    ) {
                        Text(
                            text = stringResource(R.string.state_error_button),
                            fontSize = 28.sp,
                            fontWeight = FontWeight.Medium,
                            color = Color.White
                        )
                    }
                }
            }
        }
    }
}
