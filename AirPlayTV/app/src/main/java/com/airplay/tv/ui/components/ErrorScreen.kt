package com.airplay.tv.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp

@OptIn(androidx.tv.material3.ExperimentalTvMaterial3Api::class)
@Composable
fun ErrorScreen(
    errorMessage: String,
    onDismiss: () -> Unit
) {
    AirPlayBackdrop(
        topRightContent = { AirPlayTopBadge(text = "Atencao no receptor") }
    ) {
        Row(
            modifier = Modifier
                .align(Alignment.Center)
                .width(1220.dp),
            horizontalArrangement = Arrangement.spacedBy(34.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(
                modifier = Modifier.width(410.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                StatusPill(label = "ERRO", color = AirPlayRed)
                Text(
                    text = "Algo interrompeu o espelhamento",
                    style = MaterialTheme.typography.displayMedium,
                    color = Color.White,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "Revise a mensagem ao lado, confirme a rede local e tente novamente.",
                    style = MaterialTheme.typography.headlineSmall,
                    color = AirPlayWhiteSoft
                )
            }

            AirPlayGlassCard(
                modifier = Modifier.width(700.dp)
            ) {
                Column(
                    modifier = Modifier.align(Alignment.Center),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(18.dp)
                ) {
                    Text(
                        text = "Falha de conexao",
                        style = MaterialTheme.typography.displaySmall,
                        color = AirPlayRed,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center
                    )
                    Text(
                        text = errorMessage,
                        style = MaterialTheme.typography.headlineSmall,
                        color = Color.White,
                        textAlign = TextAlign.Center
                    )
                    androidx.tv.material3.Button(
                        onClick = onDismiss,
                        colors = androidx.tv.material3.ButtonDefaults.colors(
                            containerColor = Color.White.copy(alpha = 0.18f),
                            focusedContainerColor = AirPlayBlue500
                        ),
                        modifier = Modifier.width(220.dp)
                    ) {
                        Text(
                            text = "Voltar ao inicio",
                            style = MaterialTheme.typography.titleMedium,
                            color = Color.White
                        )
                    }
                }
            }
        }
    }
}
