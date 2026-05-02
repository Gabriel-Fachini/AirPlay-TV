package com.airplay.tv.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.airplay.tv.network.mDNSModule

@Composable
fun IdleScreen(
    deviceName: String,
    mdnsState: mDNSModule.ServiceState = mDNSModule.ServiceState.Unregistered
) {
    val registeredName = (mdnsState as? mDNSModule.ServiceState.Registered)?.serviceName
    val displayName = registeredName ?: deviceName
    val networkBadge = when (mdnsState) {
        is mDNSModule.ServiceState.Registered -> "Rede: AirPlay disponivel"
        is mDNSModule.ServiceState.Registering -> "Rede: publicando receptor"
        is mDNSModule.ServiceState.Failed -> "Rede: falha no anuncio"
        is mDNSModule.ServiceState.Unregistered -> "Rede: inicializando"
    }

    AirPlayBackdrop(
        topRightContent = {
            AirPlayTopBadge(text = networkBadge)
        }
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(start = 54.dp, end = 54.dp, top = 136.dp, bottom = 42.dp),
            horizontalArrangement = Arrangement.spacedBy(28.dp),
            verticalAlignment = Alignment.Top
        ) {
            IdleHeroColumn(
                displayName = displayName,
                modifier = Modifier.width(470.dp)
            )

            AirPlayGlassCard(
                modifier = Modifier
                    .weight(1f)
                    .height(470.dp),
                padding = androidx.compose.foundation.layout.PaddingValues(
                    start = 24.dp,
                    top = 22.dp,
                    end = 24.dp,
                    bottom = 20.dp
                )
            ) {
                MirrorGuideCard(
                    deviceName = displayName,
                    mdnsState = mdnsState
                )
            }
        }
    }
}

@Composable
private fun IdleHeroColumn(
    displayName: String,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        Column {
            Text(
                text = "Pronto para\nespelhar",
                style = MaterialTheme.typography.displayLarge,
                color = Color.White,
                fontWeight = FontWeight.Bold,
                lineHeight = 52.sp
            )

            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = "Use o AirPlay para enviar video, fotos, musica e mais para esta TV.",
                style = MaterialTheme.typography.headlineSmall,
                color = AirPlayWhiteSoft,
                lineHeight = 24.sp,
                modifier = Modifier.widthIn(max = 400.dp)
            )

            Spacer(modifier = Modifier.height(18.dp))

            Text(
                text = displayName,
                style = MaterialTheme.typography.displaySmall,
                color = AirPlayBlue300,
                fontWeight = FontWeight.SemiBold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.widthIn(max = 420.dp)
            )
        }

        AirPlayGlassCard(
            modifier = Modifier.width(360.dp),
            padding = androidx.compose.foundation.layout.PaddingValues(18.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(14.dp),
                verticalAlignment = Alignment.Top
            ) {
                FauxQrCode()
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(
                        text = "Precisando de ajuda?",
                        style = MaterialTheme.typography.titleLarge,
                        color = Color.White,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        text = "Abra o guia completo para ver o fluxo detalhado de espelhamento.",
                        style = MaterialTheme.typography.bodyLarge,
                        color = AirPlayWhiteSoft,
                        lineHeight = 20.sp
                    )
                }
            }
        }
    }
}

@Composable
private fun MirrorGuideCard(
    deviceName: String,
    mdnsState: mDNSModule.ServiceState
) {
    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(18.dp)) {
            Text(
                text = "Como espelhar",
                style = MaterialTheme.typography.headlineLarge,
                color = Color.White,
                fontWeight = FontWeight.Bold
            )

            GuideStep(
                number = "1",
                badge = "CC",
                title = "Abra a Central de Controle",
                subtitle = "No seu iPhone, iPad ou Mac."
            )
            GuideStep(
                number = "2",
                badge = "AP",
                title = "Toque em Espelhar Tela",
                subtitle = "Selecione o icone AirPlay."
            )
            GuideStep(
                number = "3",
                badge = "TV",
                title = "Escolha esta TV",
                subtitle = "Selecione o nome da sua TV na lista."
            )
        }

        Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(1.dp)
                    .background(Color.White.copy(alpha = 0.12f))
            )

            Row(
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(58.dp)
                        .clip(CircleShape)
                        .background(
                            Brush.verticalGradient(
                                colors = listOf(
                                    Color.White.copy(alpha = 0.18f),
                                    Color.White.copy(alpha = 0.08f)
                                )
                            )
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "TV",
                        color = Color.White,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                }

                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(
                        text = deviceName,
                        style = MaterialTheme.typography.headlineMedium,
                        color = Color.White,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = mdnsAvailabilityText(mdnsState),
                        style = MaterialTheme.typography.titleMedium,
                        color = AirPlayBlue300
                    )
                }
            }
        }
    }
}

@Composable
private fun GuideStep(
    number: String,
    badge: String,
    title: String,
    subtitle: String
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.Top
    ) {
        Box(
            modifier = Modifier
                .size(36.dp)
                .clip(CircleShape)
                .background(Color(0xFF5EA8FF)),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = number,
                color = Color.White,
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold
            )
        }

        Box(
            modifier = Modifier
                .size(62.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(Color.White.copy(alpha = 0.12f))
                .border(BorderStroke(1.dp, Color.White.copy(alpha = 0.08f)), RoundedCornerShape(16.dp)),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = badge,
                color = Color.White,
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold
            )
        }

        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleLarge,
                color = Color.White,
                fontWeight = FontWeight.SemiBold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodyLarge,
                color = AirPlayWhiteMuted,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun FauxQrCode() {
    Column(
        modifier = Modifier
            .size(92.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(Color.White)
            .padding(7.dp),
        verticalArrangement = Arrangement.spacedBy(3.dp)
    ) {
        repeat(5) { rowIndex ->
            Row(
                horizontalArrangement = Arrangement.spacedBy(3.dp)
            ) {
                repeat(5) { columnIndex ->
                    val filled = (rowIndex + columnIndex) % 2 == 0 ||
                        (rowIndex == 0 && columnIndex < 2) ||
                        (columnIndex == 4 && rowIndex > 1)
                    Box(
                        modifier = Modifier
                            .size(12.dp)
                            .clip(RoundedCornerShape(3.dp))
                            .background(if (filled) Color(0xFF111111) else Color.Transparent)
                    )
                }
            }
        }
    }
}

private fun mdnsAvailabilityText(state: mDNSModule.ServiceState): String {
    return when (state) {
        is mDNSModule.ServiceState.Registered -> "Disponivel para AirPlay"
        is mDNSModule.ServiceState.Registering -> "Publicando na rede local"
        is mDNSModule.ServiceState.Failed -> "Falha no anuncio: ${state.message}"
        is mDNSModule.ServiceState.Unregistered -> "Iniciando receptor"
    }
}
