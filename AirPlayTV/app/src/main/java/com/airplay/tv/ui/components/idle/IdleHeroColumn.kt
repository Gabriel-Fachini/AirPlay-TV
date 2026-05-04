package com.airplay.tv.ui.components.idle

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun IdleHeroColumn(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(15.dp)
    ) {
        Text(
            text = "Pronto para\nespelhar",
            style = MaterialTheme.typography.displayLarge.copy(
                fontSize = 32.sp,
                lineHeight = 36.sp,
                letterSpacing = (-0.58).sp,
                fontWeight = FontWeight.SemiBold
            ),
            color = Color.White
        )

        Text(
            text = "Use o AirPlay para enviar video, fotos, musica e mais para esta TV.",
            style = MaterialTheme.typography.headlineSmall.copy(
                fontSize = 14.sp,
                lineHeight = 18.sp,
                letterSpacing = (-0.16).sp,
                fontWeight = FontWeight.Normal
            ),
            color = Color.White.copy(alpha = 0.72f),
            modifier = Modifier.width(272.dp)
        )
    }
}
