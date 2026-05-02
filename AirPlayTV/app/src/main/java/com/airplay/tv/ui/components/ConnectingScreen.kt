package com.airplay.tv.ui.components

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp

@Composable
fun ConnectingScreen(clientIp: String) {
    val infiniteTransition = rememberInfiniteTransition(label = "connecting")
    val scale by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 1.08f,
        animationSpec = infiniteRepeatable(
            animation = tween(1100, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulse"
    )

    AirPlayBackdrop(
        topRightContent = { AirPlayTopBadge(text = "Conexao em andamento") }
    ) {
        Row(
            modifier = Modifier
                .align(Alignment.Center)
                .width(1220.dp),
            horizontalArrangement = Arrangement.spacedBy(34.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(
                modifier = Modifier.width(430.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                StatusPill(label = "NEGOCIANDO", color = AirPlayBlue500)
                Text(
                    text = "Conectando o dispositivo",
                    style = MaterialTheme.typography.displayMedium,
                    color = Color.White,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "A TV ja recebeu a tentativa de conexao e esta finalizando o handshake AirPlay.",
                    style = MaterialTheme.typography.headlineSmall,
                    color = AirPlayWhiteSoft
                )
            }

            AirPlayGlassCard(
                modifier = Modifier
                    .width(700.dp)
                    .height(370.dp)
            ) {
                Column(
                    modifier = Modifier.align(Alignment.Center),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    CircularProgressIndicator(
                        modifier = Modifier
                            .size(78.dp)
                            .scale(scale),
                        color = AirPlayBlue300,
                        strokeWidth = 7.dp
                    )
                    Text(
                        text = "Aguardando confirmacao do cliente",
                        style = MaterialTheme.typography.displaySmall,
                        color = Color.White,
                        textAlign = TextAlign.Center,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = clientIp,
                        style = MaterialTheme.typography.headlineMedium,
                        color = AirPlayBlue300,
                        textAlign = TextAlign.Center
                    )
                    Text(
                        text = "Assim que o espelhamento iniciar, o video sera exibido em tela cheia.",
                        style = MaterialTheme.typography.titleMedium,
                        color = AirPlayWhiteMuted,
                        textAlign = TextAlign.Center
                    )
                }
            }
        }
    }
}
