package com.airplay.tv.util

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Fonte única de telemetria consumida por service, media e ui.
 */
class TelemetryCollector {

    data class Telemetry(
        val fps: Float = 0f,
        val localLatencyMs: Int = 0,
        val bitrateKbps: Float = 0f,
        val resolutionWidth: Int = 0,
        val resolutionHeight: Int = 0,
        val droppedLocalFrames: Int = 0,
        val totalVideoFrames: Int = 0,
        val audioBufferMs: Int = 0
    )

    private val _telemetry = MutableStateFlow(Telemetry())
    val telemetry: StateFlow<Telemetry> = _telemetry.asStateFlow()

    fun updateFps(fps: Float) {
        _telemetry.value = _telemetry.value.copy(fps = fps)
    }

    fun updateVideoMetrics(
        fps: Float,
        localLatencyMs: Int,
        droppedLocalFrames: Int,
        totalVideoFrames: Int
    ) {
        _telemetry.value = _telemetry.value.copy(
            fps = fps,
            localLatencyMs = localLatencyMs,
            droppedLocalFrames = droppedLocalFrames,
            totalVideoFrames = totalVideoFrames
        )
    }

    fun updateBitrateKbps(bitrateKbps: Float) {
        _telemetry.value = _telemetry.value.copy(bitrateKbps = bitrateKbps)
    }

    fun updateResolution(width: Int, height: Int) {
        _telemetry.value = _telemetry.value.copy(
            resolutionWidth = width,
            resolutionHeight = height
        )
    }

    fun updateAudioBuffer(bufferMs: Int) {
        _telemetry.value = _telemetry.value.copy(audioBufferMs = bufferMs)
    }

    fun reset() {
        _telemetry.value = Telemetry()
    }

    fun logCurrentMetrics() {
        val t = _telemetry.value
        Logger.i(
            Logger.TAG_SERVICE,
            "Telemetry - FPS: ${t.fps}, Local latency: ${t.localLatencyMs}ms, " +
            "Resolution: ${t.resolutionWidth}x${t.resolutionHeight}, " +
            "Dropped local: ${t.droppedLocalFrames}/${t.totalVideoFrames}, " +
            "Bitrate: ${t.bitrateKbps} kbps"
        )
    }
}
