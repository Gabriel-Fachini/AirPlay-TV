package com.airplay.tv.media

import com.airplay.tv.util.Constants
import com.airplay.tv.util.Logger
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlin.coroutines.coroutineContext
import kotlin.math.abs

/**
 * Gerenciador de sincronização áudio/vídeo
 * Mantém A/V sincronizados usando timestamps RTP
 */
class SyncManager(
    private val videoDecoder: VideoDecoder,
    private val audioDecoder: AudioDecoder
) {
    
    /**
     * Estado de sincronização
     */
    data class SyncState(
        val videoTimestampUs: Long = 0,
        val audioTimestampUs: Long = 0,
        val driftMs: Int = 0,
        val isSynced: Boolean = true
    )
    
    private val _syncState = MutableStateFlow(SyncState())
    val syncState: StateFlow<SyncState> = _syncState.asStateFlow()
    
    private var monitorJob: Job? = null
    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())
    
    // Timestamps de referência
    private var videoTimestampUs = 0L
    private var audioTimestampUs = 0L
    
    // Estatísticas
    private var syncAdjustments = 0
    private var maxDriftMs = 0
    
    /**
     * Inicia monitoramento de sincronização
     */
    fun start() {
        Logger.i(Logger.TAG_SESSION, "Starting A/V sync manager")
        
        monitorJob = scope.launch {
            monitorLoop()
        }
    }
    
    /**
     * Para monitoramento de sincronização
     */
    fun stop() {
        Logger.i(Logger.TAG_SESSION, "Stopping A/V sync manager")
        
        monitorJob?.cancel()
        monitorJob = null
        
        Logger.i(Logger.TAG_SESSION, 
            "Sync stats: adjustments=$syncAdjustments, maxDrift=${maxDriftMs}ms")
    }
    
    /**
     * Atualiza timestamp de vídeo
     * 
     * @param timestampUs Timestamp em microsegundos
     */
    fun updateVideoTimestamp(timestampUs: Long) {
        videoTimestampUs = timestampUs
    }
    
    /**
     * Atualiza timestamp de áudio
     * 
     * @param timestampUs Timestamp em microsegundos
     */
    fun updateAudioTimestamp(timestampUs: Long) {
        audioTimestampUs = timestampUs
    }
    
    /**
     * Loop de monitoramento de sincronização
     */
    private suspend fun monitorLoop() {
        Logger.i(Logger.TAG_SESSION, "Sync monitor loop started")
        
        while (currentCoroutineContext().isActive) {
            try {
                // Verificar sincronização a cada 100ms
                delay(100)
                
                checkSync()
                
            } catch (e: CancellationException) {
                break
            } catch (e: Exception) {
                Logger.e(Logger.TAG_SESSION, "Error in sync monitor loop", e)
            }
        }
        
        Logger.i(Logger.TAG_SESSION, "Sync monitor loop ended")
    }
    
    /**
     * Verifica e ajusta sincronização A/V
     */
    private fun checkSync() {
        if (!audioDecoder.hasSynchronizedClock()) {
            return
        }

        // Obter timestamps atuais
        val videoTs = videoDecoder.getLastRenderedPresentationTimeUs()
        val audioTs = audioDecoder.getLastRenderedPresentationTimeUs()
        
        if (videoTs == 0L || audioTs == 0L) {
            // Ainda não temos timestamps válidos
            return
        }
        
        // Calcular drift (diferença entre áudio e vídeo)
        val driftUs = audioTs - videoTs
        val driftMs = (driftUs / 1000).toInt()
        
        // Atualizar estatísticas
        if (abs(driftMs) > maxDriftMs) {
            maxDriftMs = abs(driftMs)
        }
        
        // Verificar se está fora de sincronização
        val isSynced = abs(driftMs) <= Constants.SYNC_THRESHOLD_MS
        
        // Atualizar estado
        _syncState.value = SyncState(
            videoTimestampUs = videoTs,
            audioTimestampUs = audioTs,
            driftMs = driftMs,
            isSynced = isSynced
        )
        
        // Ajustar se necessário
        if (!isSynced) {
            adjustSync(driftMs)
        }
    }
    
    /**
     * Ajusta sincronização quando drift excede threshold
     * 
     * @param driftMs Drift em milissegundos (positivo = áudio adiantado, negativo = áudio atrasado)
     */
    private fun adjustSync(driftMs: Int) {
        Logger.w(Logger.TAG_SESSION, "A/V out of sync: drift=${driftMs}ms")
        
        syncAdjustments++
        
        when {
            driftMs > Constants.SYNC_THRESHOLD_MS -> {
                // Áudio adiantado: desacelerar áudio
                val rate = calculatePlaybackRate(driftMs, slower = true)
                audioDecoder.setPlaybackRate(rate)
                
                Logger.i(Logger.TAG_SESSION, "Slowing audio playback to $rate (drift=${driftMs}ms)")
            }
            
            driftMs < -Constants.SYNC_THRESHOLD_MS -> {
                // Áudio atrasado: acelerar áudio
                val rate = calculatePlaybackRate(abs(driftMs), slower = false)
                audioDecoder.setPlaybackRate(rate)
                
                Logger.i(Logger.TAG_SESSION, "Speeding up audio playback to $rate (drift=${driftMs}ms)")
            }
            
            else -> {
                // Dentro do threshold: restaurar taxa normal
                audioDecoder.setPlaybackRate(1.0f)
            }
        }
    }
    
    /**
     * Calcula taxa de playback para correção de drift
     * 
     * @param driftMs Magnitude do drift em milissegundos
     * @param slower Se deve desacelerar (true) ou acelerar (false)
     * @return Taxa de playback ajustada
     */
    private fun calculatePlaybackRate(driftMs: Int, slower: Boolean): Float {
        // Ajuste proporcional ao drift
        // Drift pequeno (100-200ms): ajuste de 2-5%
        // Drift médio (200-500ms): ajuste de 5-10%
        // Drift grande (>500ms): ajuste de 10-15%
        
        val adjustmentPercent = when {
            driftMs < 200 -> 0.02f  // 2%
            driftMs < 500 -> 0.05f  // 5%
            else -> 0.10f           // 10%
        }
        
        return if (slower) {
            1.0f - adjustmentPercent  // Desacelerar
        } else {
            1.0f + adjustmentPercent  // Acelerar
        }
    }
    
    /**
     * Obtém drift atual em milissegundos
     */
    fun getCurrentDriftMs(): Int = _syncState.value.driftMs
    
    /**
     * Verifica se A/V estão sincronizados
     */
    fun isSynced(): Boolean = _syncState.value.isSynced
    
    /**
     * Obtém número de ajustes realizados
     */
    fun getSyncAdjustments(): Int = syncAdjustments
    
    /**
     * Obtém drift máximo observado
     */
    fun getMaxDriftMs(): Int = maxDriftMs
    
    /**
     * Reseta estatísticas
     */
    fun resetStats() {
        syncAdjustments = 0
        maxDriftMs = 0
        videoTimestampUs = 0
        audioTimestampUs = 0
        
        _syncState.value = SyncState()
        
        Logger.i(Logger.TAG_SESSION, "Sync stats reset")
    }
}
