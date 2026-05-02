package com.airplay.tv.service

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Coletor de telemetria e métricas de performance
 * Agrega métricas de vídeo, áudio e rede
 */
class TelemetryCollector {
    
    /**
     * Dados de telemetria
     */
    data class Telemetry(
        val fps: Int = 0,
        val latencyMs: Int = 0,
        val bitrateMbps: Float = 0f,
        val resolution: String = "",
        val droppedFrames: Int = 0,
        val audioSampleRate: Int = 0,
        val audioChannels: Int = 0,
        val syncDriftMs: Int = 0,
        val packetLossPercent: Float = 0f
    )
    
    private val _telemetry = MutableStateFlow(Telemetry())
    val telemetry: StateFlow<Telemetry> = _telemetry.asStateFlow()
    
    // Métricas individuais
    private var fps = 0
    private var latencyMs = 0
    private var bitrateMbps = 0f
    private var resolution = ""
    private var droppedFrames = 0
    private var audioSampleRate = 0
    private var audioChannels = 0
    private var syncDriftMs = 0
    private var packetLossPercent = 0f
    
    /**
     * Atualiza métricas de vídeo
     */
    fun updateVideoMetrics(fps: Int, latencyMs: Int, droppedFrames: Int) {
        this.fps = fps
        this.latencyMs = latencyMs
        this.droppedFrames = droppedFrames
        emitTelemetry()
    }
    
    /**
     * Atualiza resolução de vídeo
     */
    fun updateResolution(width: Int, height: Int) {
        this.resolution = "${width}x${height}"
        emitTelemetry()
    }
    
    /**
     * Atualiza bitrate
     */
    fun updateBitrate(bitrateMbps: Float) {
        this.bitrateMbps = bitrateMbps
        emitTelemetry()
    }
    
    /**
     * Atualiza métricas de áudio
     */
    fun updateAudioMetrics(sampleRate: Int, channels: Int) {
        this.audioSampleRate = sampleRate
        this.audioChannels = channels
        emitTelemetry()
    }
    
    /**
     * Atualiza drift de sincronização A/V
     */
    fun updateSyncDrift(driftMs: Int) {
        this.syncDriftMs = driftMs
        emitTelemetry()
    }
    
    /**
     * Atualiza taxa de perda de pacotes
     */
    fun updatePacketLoss(lossPercent: Float) {
        this.packetLossPercent = lossPercent
        emitTelemetry()
    }
    
    /**
     * Emite telemetria atualizada
     */
    private fun emitTelemetry() {
        _telemetry.value = Telemetry(
            fps = fps,
            latencyMs = latencyMs,
            bitrateMbps = bitrateMbps,
            resolution = resolution,
            droppedFrames = droppedFrames,
            audioSampleRate = audioSampleRate,
            audioChannels = audioChannels,
            syncDriftMs = syncDriftMs,
            packetLossPercent = packetLossPercent
        )
    }
    
    /**
     * Reseta todas as métricas
     */
    fun reset() {
        fps = 0
        latencyMs = 0
        bitrateMbps = 0f
        resolution = ""
        droppedFrames = 0
        audioSampleRate = 0
        audioChannels = 0
        syncDriftMs = 0
        packetLossPercent = 0f
        emitTelemetry()
    }
}
