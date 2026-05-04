package com.airplay.tv.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.airplay.tv.R
import com.airplay.tv.ui.theme.SfCompactText

internal val AirPlayBlue900 = Color(0xFF0A2E96)
internal val AirPlayBlue700 = Color(0xFF244DDA)
internal val AirPlayBlue500 = Color(0xFF63AEFF)
internal val AirPlayBlue300 = Color(0xFF9DCBFF)
internal val AirPlayViolet = Color(0xFF9D74FF)
internal val AirPlayWhiteSoft = Color(0xFFDDE7FF)
internal val AirPlayWhiteMuted = Color(0xFFAFC0F7)
internal val AirPlayGlass = Color(0x3399B5FF)
internal val AirPlayGlassStrong = Color(0x4A93AFFF)
internal val AirPlayGlassBorder = Color(0x55DDE9FF)
internal val AirPlayRed = Color(0xFFFF8A8A)
internal val AirPlayWarning = Color(0xFFFFD36D)
internal val AirPlayGreen = Color(0xFF7CDBA7)

@Composable
fun AirPlayBackdrop(
    topRightContent: (@Composable () -> Unit)? = null,
    content: @Composable BoxScope.() -> Unit
) {
    Box(
        modifier = Modifier.fillMaxSize()
    ) {
        Image(
            painter = painterResource(id = R.drawable.idle_background),
            contentDescription = null,
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.Crop
        )

        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            Color(0x12000000),
                            Color(0x22000000)
                        )
                    )
                )
        )

        AirPlayBrandLockup(
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(start = 62.dp, top = 46.dp)
        )

        Box(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(end = 60.dp, top = 50.dp)
        ) {
            topRightContent?.invoke()
        }

        Text(
            text = "Desenvolvido por Gabriel Fachini",
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 18.dp),
            color = AirPlayWhiteSoft.copy(alpha = 0.60f),
            style = MaterialTheme.typography.bodyLarge.copy(
                fontSize = 10.sp,
                lineHeight = 10.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 0.sp
            ),
            textAlign = TextAlign.Center
        )

        content()
    }
}

@Composable
fun AirPlayBrandLockup(modifier: Modifier = Modifier) {
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        AirPlayGlyph()

        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                text = "AirPlay",
                color = Color.White,
                style = MaterialTheme.typography.displayMedium.copy(
                    fontSize = 24.sp,
                    lineHeight = 24.sp,
                    fontWeight = FontWeight.SemiBold,
                    letterSpacing = (-0.37).sp
                )
            )
            Text(
                text = "Espelhe sem fio seu\ndispositivo Apple",
                color = Color.White.copy(alpha = 0.65f),
                style = MaterialTheme.typography.titleMedium.copy(
                    fontSize = 12.sp,
                    lineHeight = 14.sp,
                    fontWeight = FontWeight.Normal,
                    letterSpacing = 0.sp
                )
            )
        }
    }
}

@Composable
private fun AirPlayGlyph() {
    Text(
        text = String(Character.toChars(0x100461)),
        color = Color(0xFFA3B6E6),
        style = MaterialTheme.typography.displayMedium.copy(
            fontFamily = SfCompactText,
            fontSize = 38.sp,
            lineHeight = 38.sp,
            fontWeight = FontWeight.Normal
        )
    )
}

@Composable
fun AirPlayTopBadge(text: String, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(999.dp))
            .background(
                Brush.horizontalGradient(
                    colors = listOf(
                        Color.White.copy(alpha = 0.16f),
                        Color.White.copy(alpha = 0.09f)
                    )
                )
            )
            .border(
                BorderStroke(1.dp, Color.White.copy(alpha = 0.12f)),
                RoundedCornerShape(999.dp)
            )
            .padding(horizontal = 18.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Box(
            modifier = Modifier
                .size(12.dp)
                .clip(CircleShape)
                .background(Color(0xFF92C2FF))
        )
        Text(
            text = text,
            color = Color.White,
            style = MaterialTheme.typography.titleMedium.copy(
                fontSize = 12.sp,
                lineHeight = 12.sp,
                fontWeight = FontWeight.SemiBold
            )
        )
    }
}

@Composable
fun AirPlayGlassCard(
    modifier: Modifier = Modifier,
    padding: PaddingValues = PaddingValues(horizontal = 42.dp, vertical = 36.dp),
    content: @Composable BoxScope.() -> Unit
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(34.dp))
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        AirPlayGlassStrong,
                        AirPlayGlass
                    )
                )
            )
            .border(BorderStroke(1.2.dp, AirPlayGlassBorder), RoundedCornerShape(34.dp))
            .padding(padding),
        content = content
    )
}

@Composable
fun StatusPill(
    label: String,
    color: Color,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(999.dp))
            .background(color.copy(alpha = 0.18f))
            .border(BorderStroke(1.dp, color.copy(alpha = 0.42f)), RoundedCornerShape(999.dp))
            .padding(horizontal = 18.dp, vertical = 10.dp)
    ) {
        Text(
            text = label,
            color = Color.White,
            style = MaterialTheme.typography.labelLarge.copy(
                fontFamily = FontFamily.SansSerif,
                fontWeight = FontWeight.SemiBold,
                letterSpacing = 0.3.sp
            )
        )
    }
}
