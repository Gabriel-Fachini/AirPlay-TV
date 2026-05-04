package com.airplay.tv.ui.components.idle

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
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
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.airplay.tv.network.mDNSModule
import com.airplay.tv.ui.theme.SfCompactText

@Composable
fun MirrorGuideCard(
    deviceName: String,
    mdnsState: mDNSModule.ServiceState,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(20.dp))
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        Color.White.copy(alpha = 0.12f),
                        Color.White.copy(alpha = 0.08f)
                    )
                )
            )
            .border(
                BorderStroke(1.dp, Color.White.copy(alpha = 0.18f)),
                RoundedCornerShape(20.dp)
            )
            .padding(horizontal = 32.dp, vertical = 24.dp)
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = "Como espelhar",
                style = MaterialTheme.typography.headlineLarge.copy(
                    fontSize = 18.sp,
                    lineHeight = 20.sp,
                    letterSpacing = (-0.32).sp,
                    fontWeight = FontWeight.SemiBold
                ),
                color = Color.White.copy(alpha = 0.9f)
            )

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(144.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically)
            ) {
                GuideStep(
                    number = "1",
                    title = "Abra a Central de Controle",
                    subtitle = "No seu iPhone, iPad ou Mac.",
                    icon = { SymbolGlyph(codePoint = 0x10070A, size = 20.sp) }
                )
                GuideStep(
                    number = "2",
                    title = "Toque em Espelhar Tela",
                    subtitle = "Selecione o icone AirPlay",
                    icon = { SymbolGlyph(codePoint = 0x100405, size = 20.sp) }
                )
                GuideStep(
                    number = "3",
                    title = "Escolha esta TV",
                    subtitle = "Selecione o nome da sua TV na lista.",
                    icon = { SymbolGlyph(codePoint = 0x100874, size = 20.sp) }
                )
            }

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(2.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(Color.White.copy(alpha = 0.15f))
            )

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 2.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.Top
            ) {
                DeviceBadge(
                    modifier = Modifier.padding(top = 2.dp),
                    icon = { SymbolGlyph(codePoint = 0x1003B2, size = 18.sp) }
                )

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 2.dp),
                    verticalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    Text(
                        text = deviceName,
                        style = MaterialTheme.typography.headlineMedium.copy(
                            fontSize = 14.sp,
                            lineHeight = 16.sp,
                            fontWeight = FontWeight.SemiBold
                        ),
                        color = Color.White,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = mdnsAvailabilityText(mdnsState),
                        style = MaterialTheme.typography.bodyLarge.copy(
                            fontSize = 9.sp,
                            lineHeight = 11.sp,
                            fontWeight = FontWeight.Medium
                        ),
                        color = Color.White.copy(alpha = 0.55f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}

@Composable
private fun GuideStep(
    number: String,
    title: String,
    subtitle: String,
    icon: @Composable () -> Unit
) {
    Row(
        modifier = Modifier.widthIn(max = 520.dp),
        horizontalArrangement = Arrangement.spacedBy(7.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        StepNumberBadge(number = number)
        DeviceBadge(icon = icon)

        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                text = title,
                style = MaterialTheme.typography.headlineMedium.copy(
                    fontSize = 16.sp,
                    lineHeight = 18.sp,
                    fontWeight = FontWeight.SemiBold
                ),
                color = Color.White,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodyLarge.copy(
                    fontSize = 9.sp,
                    lineHeight = 11.sp,
                    fontWeight = FontWeight.Medium
                ),
                color = Color.White.copy(alpha = 0.55f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun StepNumberBadge(number: String) {
    Box(
        modifier = Modifier
            .size(22.dp)
            .clip(CircleShape)
            .background(
                Brush.linearGradient(
                    colors = listOf(
                        Color(0xFF4A8EFF),
                        Color(0xFF3570E0)
                    )
                )
            ),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = number,
            color = Color.White,
            style = MaterialTheme.typography.titleLarge.copy(
                fontSize = 10.sp,
                lineHeight = 10.sp,
                fontWeight = FontWeight.Bold
            )
        )
    }
}

@Composable
private fun DeviceBadge(
    modifier: Modifier = Modifier,
    icon: @Composable () -> Unit
) {
    Box(
        modifier = modifier
            .size(40.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(Color.White.copy(alpha = 0.12f)),
        contentAlignment = Alignment.Center
    ) {
        icon()
    }
}

@Composable
private fun SymbolGlyph(
    codePoint: Int,
    size: androidx.compose.ui.unit.TextUnit,
    fontFamily: FontFamily = SfCompactText
) {
    Text(
        text = String(Character.toChars(codePoint)),
        color = Color.White,
        style = MaterialTheme.typography.titleMedium.copy(
            fontFamily = fontFamily,
            fontSize = size,
            lineHeight = size,
            fontWeight = FontWeight.Bold
        )
    )
}

private fun mdnsAvailabilityText(state: mDNSModule.ServiceState): String {
    return when (state) {
        is mDNSModule.ServiceState.Registered -> "Disponivel para AirPlay"
        is mDNSModule.ServiceState.Registering -> "Publicando na rede local"
        is mDNSModule.ServiceState.Failed -> "Falha no anuncio"
        is mDNSModule.ServiceState.Unregistered -> "Iniciando receptor"
    }
}
