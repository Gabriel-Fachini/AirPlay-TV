package com.airplay.tv.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import android.view.SurfaceView
import com.airplay.tv.R
import com.airplay.tv.util.Constants
import com.airplay.tv.util.TelemetryCollector

/**
 * Tela de estado Mirroring: exibindo conteúdo espelhado
 */
@Composable
fun MirroringScreen(
    clientIp: String,
    resolution: String,
    telemetry: TelemetryCollector.Telemetry,
    onSurfaceViewCreated: (SurfaceView) -> Unit = {}
) {
    Box(
        modifier = Modifier.fillMaxSize()
    ) {
        // SurfaceView para renderização de vídeo (será usado na Fase 5)
        AndroidView(
            factory = { context ->
                SurfaceView(context).apply {
                    holder.setFixedSize(1920, 1080)
                    onSurfaceViewCreated(this)
                }
            },
            modifier = Modifier.fillMaxSize()
        )
        
        // Hint de controle (canto inferior)
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp),
            contentAlignment = Alignment.BottomCenter
        ) {
            Text(
                text = stringResource(R.string.state_mirroring_hint),
                fontSize = 18.sp,
                color = Color.White.copy(alpha = 0.6f)
            )
        }
        
        // Debug overlay (canto superior direito)
        if (Constants.DEBUG_OVERLAY_ENABLED) {
            DebugOverlay(
                telemetry = telemetry,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(16.dp)
            )
        }
    }
}

/**
 * Overlay de debug com métricas de performance
 */
@Composable
fun DebugOverlay(
    telemetry: TelemetryCollector.Telemetry,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .background(Color.Black.copy(alpha = 0.7f))
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Text(
            text = "FPS: %.1f".format(telemetry.fps),
            fontSize = 14.sp,
            color = if (telemetry.fps >= Constants.TARGET_FPS) Color.Green else Color.Yellow
        )
        
        Text(
            text = "Latência: ${telemetry.latencyMs} ms",
            fontSize = 14.sp,
            color = if (telemetry.latencyMs <= Constants.MAX_LATENCY_MS) Color.Green else Color.Red
        )
        
        Text(
            text = "Resolução: ${telemetry.resolutionWidth}x${telemetry.resolutionHeight}",
            fontSize = 14.sp,
            color = Color.White
        )
        
        Text(
            text = "Bitrate: %.1f Mbps".format(telemetry.bitrateKbps / 1000f),
            fontSize = 14.sp,
            color = Color.White
        )
        
        Text(
            text = "Dropped: ${telemetry.droppedFrames}/${telemetry.totalFrames}",
            fontSize = 14.sp,
            color = if (telemetry.droppedFrames == 0) Color.Green else Color.Yellow
        )
    }
}
