package com.airplay.tv.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

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
        modifier = Modifier
            .fillMaxSize()
            .background(
                brush = Brush.linearGradient(
                    colors = listOf(
                        Color(0xFF103AA8),
                        Color(0xFF1A37AF),
                        Color(0xFF241E88)
                    ),
                    start = Offset.Zero,
                    end = Offset(1600f, 900f)
                )
            )
    ) {
        AmbientWaves(modifier = Modifier.fillMaxSize())

        AirPlayBrandLockup(
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(start = 56.dp, top = 42.dp)
        )

        Box(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(end = 56.dp, top = 42.dp)
        ) {
            topRightContent?.invoke()
        }

        Text(
            text = "Desenvolvido por Gabriel Fachini",
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 16.dp),
            color = AirPlayWhiteSoft.copy(alpha = 0.78f),
            style = MaterialTheme.typography.bodyLarge.copy(
                fontWeight = FontWeight.Medium,
                letterSpacing = 0.sp
            ),
            textAlign = TextAlign.Center
        )

        content()
    }
}

@Composable
private fun AmbientWaves(modifier: Modifier = Modifier) {
    Canvas(modifier = modifier) {
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(
                    Color(0x5538A2FF),
                    Color.Transparent
                ),
                center = Offset(size.width * 0.24f, size.height * 0.42f),
                radius = size.minDimension * 0.55f
            ),
            radius = size.minDimension * 0.55f,
            center = Offset(size.width * 0.24f, size.height * 0.42f)
        )

        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(
                    Color(0x558E58FF),
                    Color.Transparent
                ),
                center = Offset(size.width * 0.58f, size.height * 0.86f),
                radius = size.minDimension * 0.42f
            ),
            radius = size.minDimension * 0.42f,
            center = Offset(size.width * 0.58f, size.height * 0.86f)
        )

        val backCurve = Path().apply {
            moveTo(-size.width * 0.15f, size.height * 0.20f)
            cubicTo(
                size.width * 0.18f, size.height * 0.28f,
                size.width * 0.34f, size.height * 0.70f,
                size.width * 0.52f, size.height * 0.60f
            )
            cubicTo(
                size.width * 0.68f, size.height * 0.48f,
                size.width * 0.82f, size.height * 0.18f,
                size.width * 1.08f, size.height * 0.02f
            )
        }
        drawPath(
            path = backCurve,
            color = Color.White.copy(alpha = 0.12f),
            style = Stroke(width = size.minDimension * 0.0038f, cap = StrokeCap.Round)
        )

        val frontCurve = Path().apply {
            moveTo(-size.width * 0.08f, size.height * 0.72f)
            cubicTo(
                size.width * 0.16f, size.height * 0.55f,
                size.width * 0.33f, size.height * 0.40f,
                size.width * 0.46f, size.height * 0.58f
            )
            cubicTo(
                size.width * 0.60f, size.height * 0.75f,
                size.width * 0.70f, size.height * 0.88f,
                size.width * 1.05f, size.height * 0.77f
            )
        }
        drawPath(
            path = frontCurve,
            brush = Brush.linearGradient(
                colors = listOf(
                    Color.White.copy(alpha = 0.12f),
                    AirPlayViolet.copy(alpha = 0.28f),
                    AirPlayBlue300.copy(alpha = 0.16f)
                )
            ),
            style = Stroke(width = size.minDimension * 0.024f, cap = StrokeCap.Round)
        )

        drawRoundRect(
            brush = Brush.verticalGradient(
                colors = listOf(
                    Color.White.copy(alpha = 0.05f),
                    Color.Transparent
                )
            ),
            topLeft = Offset(size.width * 0.43f, size.height * 0.20f),
            size = Size(size.width * 0.52f, size.height * 0.66f),
            cornerRadius = androidx.compose.ui.geometry.CornerRadius(42f, 42f)
        )
    }
}

@Composable
fun AirPlayBrandLockup(modifier: Modifier = Modifier) {
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        AirPlayGlyph()

        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                text = "AirPlay",
                color = Color.White,
                style = MaterialTheme.typography.displayMedium.copy(
                    fontWeight = FontWeight.Bold,
                    letterSpacing = (-0.4).sp
                )
            )
            Text(
                text = "Espelhe sem fio seu\ndispositivo Apple",
                color = AirPlayWhiteSoft,
                style = MaterialTheme.typography.titleMedium.copy(
                    fontWeight = FontWeight.Normal,
                    lineHeight = 20.sp
                )
            )
        }
    }
}

@Composable
private fun AirPlayGlyph() {
    Box(modifier = Modifier.size(58.dp)) {
        Box(
            modifier = Modifier
                .width(46.dp)
                .height(32.dp)
                .clip(RoundedCornerShape(8.dp))
                .border(BorderStroke(2.5.dp, Color.White.copy(alpha = 0.82f)), RoundedCornerShape(8.dp))
                .align(Alignment.TopCenter)
        )

        Canvas(
            modifier = Modifier
                .size(26.dp)
                .align(Alignment.BottomCenter)
        ) {
            val path = Path().apply {
                moveTo(size.width / 2f, size.height * 0.08f)
                lineTo(size.width * 0.95f, size.height * 0.82f)
                lineTo(size.width * 0.05f, size.height * 0.82f)
                close()
            }
            drawPath(path = path, color = Color.White.copy(alpha = 0.84f))
        }
    }
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
            .padding(horizontal = 18.dp, vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Box(
            modifier = Modifier
                .size(10.dp)
                .clip(CircleShape)
                .background(AirPlayBlue300)
        )
        Text(
            text = text,
            color = Color.White,
            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Medium)
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
