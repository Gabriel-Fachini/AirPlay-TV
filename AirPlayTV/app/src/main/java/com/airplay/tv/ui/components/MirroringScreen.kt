package com.airplay.tv.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import android.view.Gravity
import android.view.Surface
import android.view.SurfaceHolder
import android.view.SurfaceView
import android.widget.FrameLayout
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
    onSurfaceReady: (Surface) -> Unit = {},
    onSurfaceReleased: () -> Unit = {}
) {
    DisposableEffect(Unit) {
        onDispose {
            onSurfaceReleased()
        }
    }

    Box(
        modifier = Modifier.fillMaxSize()
    ) {
        val resolvedVideoSize = remember(telemetry.resolutionWidth, telemetry.resolutionHeight, resolution) {
            if (telemetry.resolutionWidth > 0 && telemetry.resolutionHeight > 0) {
                Pair(telemetry.resolutionWidth, telemetry.resolutionHeight)
            } else {
                parseResolutionLabel(resolution) ?: Pair(1920, 1080)
            }
        }

        BoxWithConstraints(
            modifier = Modifier.fillMaxSize()
        ) {
            val videoAspectRatio = resolvedVideoSize.first.toFloat() / resolvedVideoSize.second.toFloat()
            val containerAspectRatio = maxWidth.value / maxHeight.value
            val surfaceModifier = if (videoAspectRatio > containerAspectRatio) {
                Modifier
                    .fillMaxWidth()
                    .aspectRatio(videoAspectRatio)
            } else {
                Modifier
                    .fillMaxHeight()
                    .aspectRatio(videoAspectRatio)
            }

            AndroidView(
                factory = { context ->
                    SurfaceView(context).apply {
                        holder.addCallback(object : SurfaceHolder.Callback {
                            override fun surfaceCreated(holder: SurfaceHolder) {
                                onSurfaceReady(holder.surface)
                            }

                            override fun surfaceChanged(
                                holder: SurfaceHolder,
                                format: Int,
                                width: Int,
                                height: Int
                            ) {
                                onSurfaceReady(holder.surface)
                            }

                            override fun surfaceDestroyed(holder: SurfaceHolder) {
                                onSurfaceReleased()
                            }
                        })
                    }
                },
                update = { surfaceView ->
                    surfaceView.holder.setFixedSize(resolvedVideoSize.first, resolvedVideoSize.second)
                    (surfaceView.layoutParams as? FrameLayout.LayoutParams)?.apply {
                        gravity = Gravity.CENTER
                    }
                    surfaceView.holder.surface?.let { surface ->
                        if (surface.isValid) {
                            onSurfaceReady(surface)
                        }
                    }
                },
                modifier = surfaceModifier.align(Alignment.Center)
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

private fun parseResolutionLabel(resolution: String): Pair<Int, Int>? {
    val parts = resolution.split("x")
    if (parts.size != 2) {
        return null
    }

    val width = parts[0].trim().toIntOrNull() ?: return null
    val height = parts[1].trim().toIntOrNull() ?: return null
    if (width <= 0 || height <= 0) {
        return null
    }

    return Pair(width, height)
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
