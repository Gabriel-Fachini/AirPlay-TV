package com.airplay.tv.ui.components

import android.view.Gravity
import android.view.Surface
import android.view.SurfaceHolder
import android.view.SurfaceView
import android.widget.FrameLayout
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.airplay.tv.service.VideoOutputSize
import com.airplay.tv.util.Constants
import com.airplay.tv.util.TelemetryCollector
import kotlinx.coroutines.delay
import kotlin.math.abs

private const val VIDEO_MODE_HINT_DURATION_MS = 5000L
private const val VIDEO_MODE_ACTIVE_BADGE_DURATION_MS = 3000L
private const val TARGET_VIDEO_ASPECT_RATIO = 16f / 9f
private const val VIDEO_MODE_HINT_ASPECT_TOLERANCE = 0.05f

enum class MirroringPresentationMode {
    FIT,
    VIDEO_CROP_16_9,
}

internal enum class MirroringLayoutStrategy {
    FIT_WIDTH,
    FIT_HEIGHT,
    CROP_FILL,
}

/**
 * Tela de estado Mirroring: exibindo conteúdo espelhado
 */
@Composable
fun MirroringScreen(
    clientIp: String,
    resolution: String,
    videoOutputSize: VideoOutputSize,
    presentationMode: MirroringPresentationMode,
    telemetry: TelemetryCollector.Telemetry,
    telemetryOverlayVisible: Boolean,
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
        val resolvedVideoSize = remember(
            videoOutputSize.width,
            videoOutputSize.height,
            telemetry.resolutionWidth,
            telemetry.resolutionHeight,
            resolution
        ) {
            resolveVideoSize(videoOutputSize, telemetry, resolution)
        }
        val videoModeHintEligible = remember(resolvedVideoSize) {
            shouldShowVideoModeHint(resolvedVideoSize)
        }
        var showVideoModeHint by remember(clientIp) { mutableStateOf(false) }
        var showVideoModeActiveBadge by remember(clientIp) { mutableStateOf(false) }
        var hasShownHintForCurrentEligibleGeometry by remember(clientIp) { mutableStateOf(false) }

        LaunchedEffect(videoModeHintEligible, presentationMode, clientIp) {
            if (!videoModeHintEligible) {
                showVideoModeHint = false
                hasShownHintForCurrentEligibleGeometry = false
                return@LaunchedEffect
            }

            if (presentationMode != MirroringPresentationMode.FIT) {
                showVideoModeHint = false
                return@LaunchedEffect
            }

            if (!hasShownHintForCurrentEligibleGeometry) {
                hasShownHintForCurrentEligibleGeometry = true
                showVideoModeHint = true
                delay(VIDEO_MODE_HINT_DURATION_MS)
                showVideoModeHint = false
            }
        }

        LaunchedEffect(presentationMode, clientIp) {
            if (presentationMode != MirroringPresentationMode.VIDEO_CROP_16_9) {
                showVideoModeActiveBadge = false
                return@LaunchedEffect
            }

            showVideoModeActiveBadge = true
            delay(VIDEO_MODE_ACTIVE_BADGE_DURATION_MS)
            showVideoModeActiveBadge = false
        }

        BoxWithConstraints(
            modifier = Modifier.fillMaxSize()
        ) {
            val videoAspectRatio = resolvedVideoSize.first.toFloat() / resolvedVideoSize.second.toFloat()
            val containerAspectRatio = maxWidth.value / maxHeight.value
            val layoutStrategy = resolveMirroringLayoutStrategy(
                presentationMode = presentationMode,
                videoAspectRatio = videoAspectRatio,
                containerAspectRatio = containerAspectRatio
            )
            val surfaceModifier = surfaceModifierFor(layoutStrategy, videoAspectRatio)
            val videoModeScale = resolveVideoModeScale(
                presentationMode = presentationMode,
                videoAspectRatio = videoAspectRatio,
                containerAspectRatio = containerAspectRatio
            )

            Box(
                modifier = Modifier.fillMaxSize()
            ) {
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
                        surfaceView.holder.setSizeFromLayout()
                        (surfaceView.layoutParams as? FrameLayout.LayoutParams)?.apply {
                            gravity = Gravity.CENTER
                        }
                        surfaceView.pivotX = surfaceView.width / 2f
                        surfaceView.pivotY = surfaceView.height / 2f
                        surfaceView.scaleX = videoModeScale.first
                        surfaceView.scaleY = videoModeScale.second
                        surfaceView.holder.surface?.let { surface ->
                            if (surface.isValid) {
                                onSurfaceReady(surface)
                            }
                        }
                    },
                    modifier = surfaceModifier.align(Alignment.Center)
                )
            }
        }

        Column(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            horizontalAlignment = Alignment.End
        ) {
            if (showVideoModeHint) {
                AirPlayTopBadge(
                    text = "Clique longo no OK para Modo video",
                    showIndicator = false
                )
            }

            if (showVideoModeActiveBadge) {
                AirPlayTopBadge(
                    text = "Modo video ativo",
                    showIndicator = false
                )
            }

            if (telemetryOverlayVisible) {
                DebugOverlay(telemetry = telemetry)
            }
        }
    }
}

internal fun resolveVideoSize(
    videoOutputSize: VideoOutputSize,
    telemetry: TelemetryCollector.Telemetry,
    resolution: String
): Pair<Int, Int> {
    if (videoOutputSize.width > 0 && videoOutputSize.height > 0) {
        return Pair(videoOutputSize.width, videoOutputSize.height)
    }

    if (telemetry.resolutionWidth > 0 && telemetry.resolutionHeight > 0) {
        return Pair(telemetry.resolutionWidth, telemetry.resolutionHeight)
    }

    return parseResolutionLabel(resolution) ?: Pair(1920, 1080)
}

internal fun parseResolutionLabel(resolution: String): Pair<Int, Int>? {
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

internal fun shouldShowVideoModeHint(
    resolvedVideoSize: Pair<Int, Int>
): Boolean {
    val width = resolvedVideoSize.first
    val height = resolvedVideoSize.second
    if (width <= 0 || height <= 0 || width <= height) {
        return false
    }

    val aspectRatio = width.toFloat() / height.toFloat()
    return aspectRatio > TARGET_VIDEO_ASPECT_RATIO + VIDEO_MODE_HINT_ASPECT_TOLERANCE
}

internal fun resolveMirroringLayoutStrategy(
    presentationMode: MirroringPresentationMode,
    videoAspectRatio: Float,
    containerAspectRatio: Float
): MirroringLayoutStrategy {
    return when (presentationMode) {
        MirroringPresentationMode.FIT -> {
            if (videoAspectRatio > containerAspectRatio) {
                MirroringLayoutStrategy.FIT_WIDTH
            } else {
                MirroringLayoutStrategy.FIT_HEIGHT
            }
        }

        MirroringPresentationMode.VIDEO_CROP_16_9 -> {
            MirroringLayoutStrategy.CROP_FILL
        }
    }
}

private fun surfaceModifierFor(
    layoutStrategy: MirroringLayoutStrategy,
    videoAspectRatio: Float
): Modifier {
    return when (layoutStrategy) {
        MirroringLayoutStrategy.FIT_WIDTH -> {
            Modifier
                .fillMaxWidth()
                .aspectRatio(videoAspectRatio)
        }

        MirroringLayoutStrategy.FIT_HEIGHT -> {
            Modifier
                .fillMaxHeight()
                .aspectRatio(videoAspectRatio)
        }

        MirroringLayoutStrategy.CROP_FILL -> Modifier.fillMaxSize()
    }
}

internal fun resolveVideoModeScale(
    presentationMode: MirroringPresentationMode,
    videoAspectRatio: Float,
    containerAspectRatio: Float
): Pair<Float, Float> {
    if (presentationMode != MirroringPresentationMode.VIDEO_CROP_16_9) {
        return 1f to 1f
    }

    if (abs(videoAspectRatio - containerAspectRatio) < 0.0001f) {
        return 1f to 1f
    }

    return if (videoAspectRatio > containerAspectRatio) {
        (videoAspectRatio / containerAspectRatio) to 1f
    } else {
        1f to (containerAspectRatio / videoAspectRatio)
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
            text = "Latencia local: ${telemetry.localLatencyMs} ms",
            fontSize = 14.sp,
            color = if (telemetry.localLatencyMs <= Constants.MAX_LATENCY_MS) Color.Green else Color.Red
        )

        Text(
            text = "Resolucao: ${telemetry.resolutionWidth}x${telemetry.resolutionHeight}",
            fontSize = 14.sp,
            color = Color.White
        )

        Text(
            text = "Bitrate: %.1f Mbps".format(telemetry.bitrateKbps / 1000f),
            fontSize = 14.sp,
            color = Color.White
        )

        Text(
            text = "Dropped local: ${telemetry.droppedLocalFrames}/${telemetry.totalVideoFrames}",
            fontSize = 14.sp,
            color = if (telemetry.droppedLocalFrames == 0) Color.Green else Color.Yellow
        )
    }
}
