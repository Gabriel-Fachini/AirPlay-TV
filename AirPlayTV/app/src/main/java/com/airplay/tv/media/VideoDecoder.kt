package com.airplay.tv.media

import android.media.MediaCodec
import android.media.MediaFormat
import android.view.Surface
import com.airplay.tv.service.TelemetryCollector
import com.airplay.tv.util.Constants
import com.airplay.tv.util.Logger
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.nio.ByteBuffer
import java.util.concurrent.LinkedBlockingQueue
import kotlin.coroutines.coroutineContext

internal fun buildCodecSpecificDataBuffer(nalUnit: ByteBuffer): ByteBuffer {
    val source = nalUnit.duplicate()
    val bytes = ByteArray(source.remaining())
    source.get(bytes)

    val hasStartCode = bytes.size >= 4 &&
        bytes[0] == 0.toByte() &&
        bytes[1] == 0.toByte() &&
        bytes[2] == 0.toByte() &&
        bytes[3] == 1.toByte()

    val outputBytes = if (hasStartCode) {
        bytes
    } else {
        byteArrayOf(0, 0, 0, 1) + bytes
    }

    return ByteBuffer.allocateDirect(outputBytes.size).apply {
        put(outputBytes)
        flip()
    }
}

/**
 * Decodificador de vídeo H.264 usando MediaCodec
 * Renderiza frames em SurfaceView
 */
class VideoDecoder(
    private val telemetryCollector: TelemetryCollector,
    private val onVideoSizeChanged: (width: Int, height: Int) -> Unit = { _, _ -> }
) {
    
    /**
     * Estado do decoder
     */
    sealed class DecoderState {
        object Idle : DecoderState()
        object Configured : DecoderState()
        object Running : DecoderState()
        data class Error(val message: String) : DecoderState()
    }
    
    private val _state = MutableStateFlow<DecoderState>(DecoderState.Idle)
    val state: StateFlow<DecoderState> = _state.asStateFlow()
    
    private var codec: MediaCodec? = null
    private var decoderJob: Job? = null
    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())
    
    // Buffer de entrada (fila de payloads H.264) - tamanho dinâmico
    private var bufferSize = Constants.JITTER_BUFFER_FRAMES * 6
    private var inputQueue = LinkedBlockingQueue<H264Frame>(bufferSize)
    
    data class H264Frame(
        val data: ByteArray,
        val timestamp: Long,
        val isKeyFrame: Boolean = false
    ) {
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (javaClass != other?.javaClass) return false
            
            other as H264Frame
            
            if (!data.contentEquals(other.data)) return false
            if (timestamp != other.timestamp) return false
            if (isKeyFrame != other.isKeyFrame) return false
            
            return true
        }
        
        override fun hashCode(): Int {
            var result = data.contentHashCode()
            result = 31 * result + timestamp.hashCode()
            result = 31 * result + isKeyFrame.hashCode()
            return result
        }
    }
    
    // Métricas
    private var framesDecoded = 0L
    private var framesDropped = 0L
    private var lastFpsTime = System.currentTimeMillis()
    private var fpsCounter = 0
    private var currentFps = 0
    private var submittedInputFrames = 0L
    private var sessionStartTimeUs = 0L  // Tempo de início da sessão (para cálculo de latência)
    private var bytesSubmittedSinceLastBitrateSample = 0L
    private var lastBitrateSampleTimeMs = System.currentTimeMillis()
    private var lastRenderedPresentationTimeUs = 0L
    
    // Monitoramento de performance
    private var lowFpsCounter = 0
    private var highDropRateCounter = 0
    private val performanceCheckInterval = 1000L // 1 segundo
    private var lastPerformanceCheck = System.currentTimeMillis()
    
    /**
     * Configura decoder com parâmetros de vídeo
     * 
     * @param width Largura do vídeo
     * @param height Altura do vídeo
     * @param sps Sequence Parameter Set (H.264)
     * @param pps Picture Parameter Set (H.264)
     * @param surface Surface para renderização
     */
    fun configure(
        width: Int,
        height: Int,
        sps: ByteBuffer,
        pps: ByteBuffer,
        surface: Surface
    ): Boolean {
        try {
            Logger.i(Logger.TAG_VIDEO, "Configuring video decoder: ${width}x${height}")
            
            // Criar decoder H.264
            codec = MediaCodec.createDecoderByType(Constants.VIDEO_CODEC_MIME)
            
            // Configurar formato
            val format = MediaFormat.createVideoFormat(Constants.VIDEO_CODEC_MIME, width, height).apply {
                // SPS/PPS são opcionais - podem ser enviados como primeiro frame
                if (sps.hasRemaining()) {
                    setByteBuffer("csd-0", buildCodecSpecificDataBuffer(sps)) // SPS
                }
                if (pps.hasRemaining()) {
                    setByteBuffer("csd-1", buildCodecSpecificDataBuffer(pps)) // PPS
                }
                setInteger(MediaFormat.KEY_MAX_INPUT_SIZE, Constants.MAX_INPUT_BUFFER_SIZE)
                setInteger(MediaFormat.KEY_FRAME_RATE, Constants.TARGET_FPS)
                
                // Configurações para melhorar qualidade de decodificação
                // Low latency mode: reduz buffering interno do decoder
                try {
                    setInteger(MediaFormat.KEY_LOW_LATENCY, 1)
                } catch (e: Exception) {
                    Logger.d(Logger.TAG_VIDEO, "KEY_LOW_LATENCY not supported on this device")
                }
                
                // Operating rate: hint para o decoder sobre a taxa de frames esperada
                try {
                    setInteger(MediaFormat.KEY_OPERATING_RATE, Constants.TARGET_FPS)
                } catch (e: Exception) {
                    Logger.d(Logger.TAG_VIDEO, "KEY_OPERATING_RATE not supported on this device")
                }
                
                // Priority: alta prioridade para decodificação de vídeo
                try {
                    setInteger(MediaFormat.KEY_PRIORITY, 0) // 0 = realtime priority
                } catch (e: Exception) {
                    Logger.d(Logger.TAG_VIDEO, "KEY_PRIORITY not supported on this device")
                }
            }
            
            codec?.configure(format, surface, null, 0)
            
            _state.value = DecoderState.Configured
            Logger.i(Logger.TAG_VIDEO, "Video decoder configured successfully with low latency optimizations")
            
            return true
            
        } catch (e: Exception) {
            Logger.e(Logger.TAG_VIDEO, "Failed to configure video decoder", e)
            _state.value = DecoderState.Error(e.message ?: "Configuration failed")
            return false
        }
    }
    
    /**
     * Inicia decodificação
     */
    fun start() {
        if (_state.value != DecoderState.Configured) {
            Logger.w(Logger.TAG_VIDEO, "Cannot start: decoder not configured")
            return
        }
        
        try {
            codec?.start()
            _state.value = DecoderState.Running
            
            // Resetar métricas
            resetMetrics()
            
            // Inicializar tempo de início da sessão (em microsegundos)
            sessionStartTimeUs = System.currentTimeMillis() * 1000L
            
            // Iniciar thread de decodificação com prioridade alta
            decoderJob = scope.launch {
                // Aumentar prioridade da thread para decodificação de vídeo
                android.os.Process.setThreadPriority(android.os.Process.THREAD_PRIORITY_DISPLAY)
                decoderLoop()
            }
            
            Logger.i(Logger.TAG_VIDEO, "Video decoder started with high priority")
            
        } catch (e: Exception) {
            Logger.e(Logger.TAG_VIDEO, "Failed to start video decoder", e)
            _state.value = DecoderState.Error(e.message ?: "Start failed")
        }
    }
    
    /**
     * Para decodificação
     */
    fun stop() {
        Logger.i(Logger.TAG_VIDEO, "Stopping video decoder")
        _state.value = DecoderState.Idle
        
        // Cancelar job de decodificação
        decoderJob?.cancel()
        decoderJob = null
        
        // Liberar recursos do codec com tratamento de erro robusto
        try {
            codec?.let { c ->
                try {
                    c.stop()
                } catch (e: IllegalStateException) {
                    Logger.w(Logger.TAG_VIDEO, "Codec already stopped or in invalid state", e)
                } catch (e: Exception) {
                    Logger.e(Logger.TAG_VIDEO, "Error stopping codec", e)
                }
                
                try {
                    c.release()
                } catch (e: Exception) {
                    Logger.e(Logger.TAG_VIDEO, "Error releasing codec", e)
                }
            }
            codec = null
        } catch (e: Exception) {
            Logger.e(Logger.TAG_VIDEO, "Unexpected error during codec cleanup", e)
        }
        
        // Limpar fila de entrada
        inputQueue.clear()
        lastRenderedPresentationTimeUs = 0L
        _state.value = DecoderState.Idle
        
        Logger.i(Logger.TAG_VIDEO, "Video decoder stopped (decoded=$framesDecoded, dropped=$framesDropped, fps=$currentFps)")
    }
    
    /**
     * Enfileira frame H.264 para decodificação
     * 
     * @param data Payload H.264 (NAL units)
     * @param timestamp Timestamp RTP (em unidades de 90kHz)
     * @param isKeyFrame Se é um keyframe (IDR)
     */
    fun queueFrame(data: ByteArray, timestamp: Long, isKeyFrame: Boolean = false) {
        if (_state.value is DecoderState.Error) {
            Logger.w(Logger.TAG_VIDEO, "Cannot queue frame: decoder is in error state")
            return
        }
        
        if (data.isEmpty()) {
            Logger.w(Logger.TAG_VIDEO, "Ignoring empty frame (keyframe=$isKeyFrame, timestamp=$timestamp)")
            return
        }
        
        // Validar frame antes de enfileirar
        if (!isValidH264Frame(data)) {
            framesDropped++
            if (framesDropped % 10 == 0L) {
                Logger.w(Logger.TAG_VIDEO, "Dropping invalid H.264 frame (total dropped=$framesDropped)")
            }
            return
        }
        
        val frame = H264Frame(data, timestamp, isKeyFrame)
        
        if (inputQueue.offer(frame)) {
            return
        }

        if (isKeyFrame) {
            val evictedFrames = inputQueue.size
            inputQueue.clear()
            framesDropped += evictedFrames.toLong()
            if (evictedFrames > 0) {
                Logger.i(
                    Logger.TAG_VIDEO,
                    "Dropped $evictedFrames stale queued frames to preserve latest keyframe"
                )
            }
            if (inputQueue.offer(frame)) {
                return
            }
        } else {
            val evictedFrame = if (submittedInputFrames == 0L && dropOldestQueuedNonKeyFrame()) {
                true
            } else {
                inputQueue.poll()
                true
            }
            if (evictedFrame) {
                framesDropped++
            }
            if (inputQueue.offer(frame)) {
                return
            }
        }

        framesDropped++

        // Log apenas a cada 10 frames dropados para não poluir logs
        if (framesDropped % 10 == 0L) {
            Logger.w(Logger.TAG_VIDEO, "Input queue full, dropping frames (total dropped=$framesDropped)")
        }
    }
    
    /**
     * Valida se um frame H.264 está bem formado
     * Verifica se contém pelo menos um NAL unit válido com start code
     */
    private fun isValidH264Frame(data: ByteArray): Boolean {
        if (data.size < 5) { // Mínimo: start code (4 bytes) + NAL header (1 byte)
            return false
        }
        
        // Procurar por start code (0x00 0x00 0x00 0x01)
        var hasValidNal = false
        var i = 0
        while (i <= data.size - 4) {
            if (data[i] == 0.toByte() && 
                data[i + 1] == 0.toByte() && 
                data[i + 2] == 0.toByte() && 
                data[i + 3] == 1.toByte()) {
                
                // Verificar se há NAL header após o start code
                if (i + 4 < data.size) {
                    val nalHeader = data[i + 4].toInt() and 0xFF
                    val nalType = nalHeader and 0x1F
                    
                    // NAL types válidos: 1-5 (VCL), 6-12 (non-VCL), 7 (SPS), 8 (PPS)
                    if (nalType in 1..12) {
                        hasValidNal = true
                        break
                    }
                }
            }
            i++
        }
        
        return hasValidNal
    }
    
    /**
     * Loop principal de decodificação
     */
    private suspend fun decoderLoop() {
        val timeoutUs = 10000L // 10ms
        
        while (currentCoroutineContext().isActive && _state.value == DecoderState.Running) {
            try {
                // Processar entrada (enfileirar frames)
                processInput(timeoutUs)
                
                // Processar saída (renderizar frames)
                processOutput(timeoutUs)
                
                // Yield para não bloquear thread
                yield()
                
            } catch (e: CancellationException) {
                break
            } catch (e: IllegalStateException) {
                if (_state.value == DecoderState.Idle || !currentCoroutineContext().isActive) {
                    break
                }
                Logger.e(Logger.TAG_VIDEO, "Error in decoder loop", e)
                _state.value = DecoderState.Error(e.message ?: "Decoder error")
                break
            } catch (e: Exception) {
                Logger.e(Logger.TAG_VIDEO, "Error in decoder loop", e)
                _state.value = DecoderState.Error(e.message ?: "Decoder error")
                break
            }
        }
        
    }
    
    /**
     * Processa entrada (enfileira frames para decodificação)
     */
    private fun processInput(timeoutUs: Long) {
        val codec = this.codec ?: return

        // Nunca dequeuar um input buffer sem frame pronto, ou o slot fica perdido dentro do codec.
        if (inputQueue.peek() == null) {
            return
        }

        // Obter buffer de entrada disponível
        val inputIndex = codec.dequeueInputBuffer(timeoutUs)
        if (inputIndex < 0) {
            return // Nenhum buffer disponível
        }

        // Obter próximo frame da fila. Antes do primeiro submit, o decoder precisa começar em um IDR.
        val frame = pollFrameForCodec() ?: return
        
        // Copiar dados para buffer
        val inputBuffer = codec.getInputBuffer(inputIndex)
        if (inputBuffer != null) {
            inputBuffer.clear()
            if (inputBuffer.remaining() < frame.data.size) {
                framesDropped++
                Logger.w(
                    Logger.TAG_VIDEO,
                    "Codec input buffer too small (${inputBuffer.remaining()}B) for frame ${frame.data.size}B"
                )
                return
            }
            inputBuffer.put(frame.data)
            
            // Converter timestamp RTP (90kHz) para microsegundos
            val timestampUs = (frame.timestamp * 1000000L) / 90000L
            
            // Enfileirar para decodificação
            codec.queueInputBuffer(
                inputIndex,
                0,
                frame.data.size,
                timestampUs,
                if (frame.isKeyFrame) MediaCodec.BUFFER_FLAG_KEY_FRAME else 0
            )
            submittedInputFrames++
            bytesSubmittedSinceLastBitrateSample += frame.data.size.toLong()
            updateBitrateIfNeeded()
            if (submittedInputFrames <= 3L) {
                Logger.i(
                    Logger.TAG_VIDEO,
                    "Queued codec input #$submittedInputFrames (${frame.data.size} bytes, keyFrame=${frame.isKeyFrame})"
                )
            }
        } else {
            framesDropped++
        }
    }

    private fun pollFrameForCodec(): H264Frame? {
        var frame = inputQueue.poll() ?: return null

        while (submittedInputFrames == 0L && !frame.isKeyFrame) {
            framesDropped++
            if (framesDropped % 10 == 0L) {
                Logger.w(Logger.TAG_VIDEO, "Dropping pre-start non-keyframe while waiting for an IDR (total dropped=$framesDropped)")
            }
            frame = inputQueue.poll() ?: return null
        }

        return frame
    }

    private fun dropOldestQueuedNonKeyFrame(): Boolean {
        for (queuedFrame in inputQueue) {
            if (!queuedFrame.isKeyFrame) {
                return inputQueue.remove(queuedFrame)
            }
        }
        return false
    }
    
    /**
     * Processa saída (renderiza frames decodificados)
     */
    private fun processOutput(timeoutUs: Long) {
        val codec = this.codec ?: return
        
        val bufferInfo = MediaCodec.BufferInfo()
        val outputIndex = codec.dequeueOutputBuffer(bufferInfo, timeoutUs)
        
        when {
            outputIndex >= 0 -> {
                // Frame decodificado disponível
                
                // Renderizar no Surface (true = render)
                codec.releaseOutputBuffer(outputIndex, true)
                
                framesDecoded++
                updateFps()
                
                // Calcular latência corretamente:
                // presentationTimeUs é relativo ao início da sessão
                // Precisamos comparar com o tempo atual também relativo ao início da sessão
                val currentTimeUs = System.currentTimeMillis() * 1000L
                val elapsedSinceSessionStartUs = currentTimeUs - sessionStartTimeUs
                val latencyUs = elapsedSinceSessionStartUs - bufferInfo.presentationTimeUs
                val latencyMs = latencyUs / 1000
                lastRenderedPresentationTimeUs = bufferInfo.presentationTimeUs
                
                // Atualizar telemetria
                telemetryCollector.updateVideoMetrics(
                    fps = currentFps,
                    latencyMs = latencyMs.toInt(),
                    droppedFrames = framesDropped.toInt(),
                    totalFrames = (framesDecoded + framesDropped).toInt()
                )
            }
            
            outputIndex == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                val format = codec.outputFormat
                extractDisplaySize(format)?.let { (width, height) ->
                    onVideoSizeChanged(width, height)
                }
                Logger.i(Logger.TAG_VIDEO, "Output format changed: $format")
            }
            
            outputIndex == MediaCodec.INFO_TRY_AGAIN_LATER -> {
                // Nenhum frame disponível ainda
            }
        }
    }
    
    /**
     * Atualiza contador de FPS
     */
    private fun updateFps() {
        fpsCounter++
        
        val now = System.currentTimeMillis()
        val elapsed = now - lastFpsTime
        
        if (elapsed >= 1000) { // Atualizar a cada segundo
            currentFps = (fpsCounter * 1000 / elapsed).toInt()
            fpsCounter = 0
            lastFpsTime = now
            
            // Verificar performance periodicamente
            checkPerformance()
        }
    }

    private fun updateBitrateIfNeeded() {
        val now = System.currentTimeMillis()
        val elapsedMs = now - lastBitrateSampleTimeMs
        if (elapsedMs < 1000) {
            return
        }

        val bitrateMbps = if (elapsedMs > 0) {
            (bytesSubmittedSinceLastBitrateSample * 8f) / (elapsedMs / 1000f) / 1_000_000f
        } else {
            0f
        }

        telemetryCollector.updateBitrate(bitrateMbps)
        bytesSubmittedSinceLastBitrateSample = 0L
        lastBitrateSampleTimeMs = now
    }

    fun getLastRenderedPresentationTimeUs(): Long = lastRenderedPresentationTimeUs
    
    /**
     * Verifica performance e ajusta buffer dinamicamente
     */
    private fun checkPerformance() {
        val now = System.currentTimeMillis()
        if (now - lastPerformanceCheck < performanceCheckInterval) {
            return
        }
        lastPerformanceCheck = now
        
        // Calcular taxa de drop
        val totalFrames = framesDecoded + framesDropped
        val dropRate = if (totalFrames > 0) {
            (framesDropped.toFloat() / totalFrames.toFloat()) * 100f
        } else {
            0f
        }
        
        // Detectar FPS baixo (< 20 por 3 segundos consecutivos)
        if (currentFps < 20 && currentFps > 0) {
            lowFpsCounter++
            if (lowFpsCounter >= 3) {
                Logger.w(Logger.TAG_VIDEO, "Low FPS detected: $currentFps (threshold: 20)")
                // TODO: Implementar fallback de resolução (1080p → 720p)
                // Requer renegociação RTSP, será implementado na segunda metade da Fase 6
                lowFpsCounter = 0
            }
        } else {
            lowFpsCounter = 0
        }
        
        // Detectar alta taxa de drop (> 5% por 3 segundos consecutivos)
        if (dropRate > 5f) {
            highDropRateCounter++
            if (highDropRateCounter >= 3) {
                Logger.w(Logger.TAG_VIDEO, "High drop rate detected: ${dropRate.toInt()}% (threshold: 5%)")
                adjustBufferSize(increase = true)
                highDropRateCounter = 0
            }
        } else {
            highDropRateCounter = 0
        }
        
        // Se latência muito alta e drop rate baixo, reduzir buffer
        val latency = telemetryCollector.telemetry.value.latencyMs
        if (latency > 1000 && dropRate < 1f) {
            Logger.i(Logger.TAG_VIDEO, "High latency detected: ${latency}ms, reducing buffer")
            adjustBufferSize(increase = false)
        }
    }
    
    /**
     * Ajusta tamanho do buffer dinamicamente
     */
    private fun adjustBufferSize(increase: Boolean) {
        val oldSize = bufferSize
        
        if (increase) {
            // Aumentar buffer (máximo: 14 frames)
            bufferSize = minOf(bufferSize + 2, 14)
        } else {
            // Reduzir buffer (mínimo: 6 frames)
            bufferSize = maxOf(bufferSize - 2, 6)
        }
        
        if (bufferSize != oldSize) {
            Logger.i(Logger.TAG_VIDEO, "Buffer size adjusted: $oldSize → $bufferSize frames")
            
            // Recriar fila com novo tamanho (preservar frames existentes)
            val oldQueue = inputQueue
            inputQueue = LinkedBlockingQueue(bufferSize)
            oldQueue.drainTo(inputQueue)
        }
    }
    
    /**
     * Obtém FPS atual
     */
    fun getCurrentFps(): Int = currentFps
    
    /**
     * Obtém número de frames decodificados
     */
    fun getFramesDecoded(): Long = framesDecoded
    
    /**
     * Obtém número de frames dropados
     */
    fun getFramesDropped(): Long = framesDropped
    
    /**
     * Reseta métricas
     */
    fun resetMetrics() {
        framesDecoded = 0
        framesDropped = 0
        fpsCounter = 0
        currentFps = 0
        submittedInputFrames = 0
        bytesSubmittedSinceLastBitrateSample = 0L
        lastFpsTime = System.currentTimeMillis()
        lastBitrateSampleTimeMs = lastFpsTime
    }

    private fun extractDisplaySize(format: MediaFormat): Pair<Int, Int>? {
        return try {
            val width = if (
                format.containsKey("crop-left") &&
                format.containsKey("crop-right")
            ) {
                format.getInteger("crop-right") - format.getInteger("crop-left") + 1
            } else {
                format.getInteger(MediaFormat.KEY_WIDTH)
            }

            val height = if (
                format.containsKey("crop-top") &&
                format.containsKey("crop-bottom")
            ) {
                format.getInteger("crop-bottom") - format.getInteger("crop-top") + 1
            } else {
                format.getInteger(MediaFormat.KEY_HEIGHT)
            }

            if (width > 0 && height > 0) {
                Pair(width, height)
            } else {
                null
            }
        } catch (_: Exception) {
            null
        }
    }
}
