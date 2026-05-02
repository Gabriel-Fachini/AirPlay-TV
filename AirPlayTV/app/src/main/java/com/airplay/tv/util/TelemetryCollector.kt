package com.airplay.tv.util

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Coletor de métricas de telemetria para monitoramento de performance
 */
class TelemetryCollector {
    
    data class Telemetry(
        val fps: Float = 0f,
        val latencyMs: Int = 0,
        val bitrateKbps: Float = 0f,
        val resolutionWidth: Int = 0,
        val resolutionHeight: Int = 0,
        val droppedFrames: Int = 0,
        val totalFrames: Int = 0,
        val audioBufferMs: Int = 0
    )
    
    private val _telemetry = MutableStateFlow(Telemetry())
    val telemetry: StateFlow<Telemetry> = _telemetry.asStateFlow()
    
    fun updateFps(fps: Float) {
        _telemetry.value = _telemetry.value.copy(fps = fps)
    }
    
    fun updateLatency(latencyMs: Int) {
        _telemetry.value = _telemetry.value.copy(latencyMs = latencyMs)
    }
    
    fun updateBitrate(bitrateKbps: Float) {
        _telemetry.value = _telemetry.value.copy(bitrateKbps = bitrateKbps)
    }
    
    fun updateResolution(width: Int, height: Int) {
        _telemetry.value = _telemetry.value.copy(
            resolutionWidth = width,
            resolutionHeight = height
        )
    }
    
    fun incrementDroppedFrames() {
        _telemetry.value = _telemetry.value.copy(
            droppedFrames = _telemetry.value.droppedFrames + 1
        )
    }
    
    fun incrementTotalFrames() {
        _telemetry.value = _telemetry.value.copy(
            totalFrames = _telemetry.value.totalFrames + 1
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
        Logger.i(Logger.TAG_SERVICE, 
            "Telemetry - FPS: ${t.fps}, Latency: ${t.latencyMs}ms, " +
            "Resolution: ${t.resolutionWidth}x${t.resolutionHeight}, " +
            "Dropped: ${t.droppedFrames}/${t.totalFrames}, " +
            "Bitrate: ${t.bitrateKbps} kbps"
        )
    }
}
