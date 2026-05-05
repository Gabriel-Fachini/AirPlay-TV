package com.airplay.tv.media

import android.media.MediaCodec
import android.media.MediaFormat
import android.view.Surface
import com.airplay.tv.util.Constants
import com.airplay.tv.util.Logger
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.nio.ByteBuffer
import java.util.ArrayDeque
import kotlin.coroutines.coroutineContext

/**
 * Decodificador de vídeo H.264 usando MediaCodec
 * Renderiza frames em SurfaceView
 */
class VideoDecoder(
    private val performanceTracker: VideoPerformanceTracker,
    private val inputQueue: VideoInputQueue,
    private val onVideoSizeChanged: (width: Int, height: Int) -> Unit = { _, _ -> }
) {
    init {
        performanceTracker.onAdjustBufferSize = { increase ->
            inputQueue.adjustBufferSize(increase)
        }
    }
    
    private val _state = MutableStateFlow<DecoderState>(DecoderState.Idle)
    val state: StateFlow<DecoderState> = _state.asStateFlow()
    
    private var codec: MediaCodec? = null
    private var decoderJob: Job? = null
    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())
    private val queuedFrameTimesByPtsUs = mutableMapOf<Long, ArrayDeque<Long>>()

    data class H264Frame(
        val data: ByteArray,
        val timestamp: Long,
        val isKeyFrame: Boolean = false,
        val receivedAtNs: Long,
    ) {
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (javaClass != other?.javaClass) return false
            
            other as H264Frame
            
            if (!data.contentEquals(other.data)) return false
            if (timestamp != other.timestamp) return false
            if (isKeyFrame != other.isKeyFrame) return false
            if (receivedAtNs != other.receivedAtNs) return false
            
            return true
        }
        
        override fun hashCode(): Int {
            var result = data.contentHashCode()
            result = 31 * result + timestamp.hashCode()
            result = 31 * result + isKeyFrame.hashCode()
            result = 31 * result + receivedAtNs.hashCode()
            return result
        }
    }
    

    
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
            performanceTracker.onSessionStarted()
            
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
        queuedFrameTimesByPtsUs.clear()
        performanceTracker.onSessionStopped()
        _state.value = DecoderState.Idle
        
        Logger.i(Logger.TAG_VIDEO, "Video decoder stopped (decoded=${performanceTracker.framesDecoded}, dropped=${performanceTracker.droppedLocalFrames}, fps=${performanceTracker.currentFps})")
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
        if (!isValidAnnexBFrame(data)) {
            performanceTracker.onFramesDropped(VideoPerformanceTracker.LocalDropReason.INVALID_FRAME)
            val framesDropped = performanceTracker.droppedLocalFrames
            if (framesDropped % 10 == 0L) {
                Logger.w(Logger.TAG_VIDEO, "Dropping invalid H.264 frame (total dropped=$framesDropped)")
            }
            return
        }

        val frame = H264Frame(
            data = data,
            timestamp = timestamp,
            isKeyFrame = isKeyFrame,
            receivedAtNs = performanceTracker.captureFrameReceivedAtNs(),
        )
        inputQueue.queueFrame(frame, performanceTracker.submittedInputFrames) { reason, count ->
            performanceTracker.onFramesDropped(reason, count)
        }
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
        val frame = inputQueue.pollFrameForCodec(performanceTracker.submittedInputFrames) { count ->
            performanceTracker.onFramesDropped(VideoPerformanceTracker.LocalDropReason.PRE_START_NON_KEYFRAME, count)
        } ?: return
        
        // Copiar dados para buffer
        val inputBuffer = codec.getInputBuffer(inputIndex)
        if (inputBuffer != null) {
            inputBuffer.clear()
            if (inputBuffer.remaining() < frame.data.size) {
                performanceTracker.onFramesDropped(VideoPerformanceTracker.LocalDropReason.CODEC_INPUT_REJECTED)
                Logger.w(
                    Logger.TAG_VIDEO,
                    "Codec input buffer too small (${inputBuffer.remaining()}B) for frame ${frame.data.size}B"
                )
                return
            }
            inputBuffer.put(frame.data)
            
            // Converter timestamp RTP (90kHz) para microsegundos
            val timestampUs = (frame.timestamp * 1000000L) / 90000L
            queuedFrameTimesByPtsUs.getOrPut(timestampUs) { ArrayDeque() }.addLast(frame.receivedAtNs)
            
            // Enfileirar para decodificação
            codec.queueInputBuffer(
                inputIndex,
                0,
                frame.data.size,
                timestampUs,
                if (frame.isKeyFrame) MediaCodec.BUFFER_FLAG_KEY_FRAME else 0
            )
            performanceTracker.onFrameSubmittedToCodec(frame.data.size, frame.isKeyFrame)
        } else {
            performanceTracker.onFramesDropped(VideoPerformanceTracker.LocalDropReason.CODEC_INPUT_REJECTED)
        }
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
                val frameReceivedAtNs = queuedFrameTimesByPtsUs.removeFirst(bufferInfo.presentationTimeUs)
                
                // Renderizar no Surface (true = render)
                codec.releaseOutputBuffer(outputIndex, true)
                performanceTracker.onFrameDecoded(bufferInfo.presentationTimeUs, frameReceivedAtNs)
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
    
    fun getCurrentFps(): Int = performanceTracker.currentFps
    
    /**
     * Obtém número de frames decodificados
     */
    fun getFramesDecoded(): Long = performanceTracker.framesDecoded
    
    /**
     * Obtém número de frames dropados
     */
    fun getFramesDropped(): Long = performanceTracker.droppedLocalFrames
    
    /**
     * Reseta métricas
     */
    fun resetMetrics() {
        performanceTracker.resetMetrics()
    }

    fun getLastRenderedPresentationTimeUs(): Long = performanceTracker.lastRenderedPresentationTimeUs

    private fun MutableMap<Long, ArrayDeque<Long>>.removeFirst(presentationTimeUs: Long): Long? {
        val samples = get(presentationTimeUs) ?: return null
        val receivedAtNs = if (samples.isEmpty()) null else samples.removeFirst()
        if (samples.isEmpty()) {
            remove(presentationTimeUs)
        }
        return receivedAtNs
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
