package com.airplay.tv.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val DarkColorScheme = darkColorScheme(
    primary = Color(0xFF63AEFF),
    secondary = Color(0xFF9DCBFF),
    tertiary = Color(0xFF9D74FF),
    background = Color(0xFF103AA8),
    surface = Color(0x332E57C7),
    error = Color(0xFFFF8A8A),
    onPrimary = Color.White,
    onSecondary = Color(0xFF04110A),
    onTertiary = Color(0xFF04110A),
    onBackground = Color.White,
    onSurface = Color.White,
    onError = Color.White
)

@Composable
fun AirPlayTVTheme(
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = DarkColorScheme,
        typography = Typography,
        content = content
    )
}
