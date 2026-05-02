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

/**
 * Decodificador de vídeo H.264 usando MediaCodec
 * Renderiza frames em SurfaceView
 */
class VideoDecoder(
    private val telemetryCollector: TelemetryCollector
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
    
    // Buffer de entrada (fila de payloads H.264)
    private val inputQueue = LinkedBlockingQueue<H264Frame>(Constants.JITTER_BUFFER_FRAMES * 2)
    
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
                setByteBuffer("csd-0", sps) // SPS
                setByteBuffer("csd-1", pps) // PPS
                setInteger(MediaFormat.KEY_MAX_INPUT_SIZE, Constants.MAX_INPUT_BUFFER_SIZE)
                setInteger(MediaFormat.KEY_FRAME_RATE, Constants.TARGET_FPS)
            }
            
            codec?.configure(format, surface, null, 0)
            
            _state.value = DecoderState.Configured
            Logger.i(Logger.TAG_VIDEO, "Video decoder configured successfully")
            
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
            
            // Iniciar thread de decodificação
            decoderJob = scope.launch {
                decoderLoop()
            }
            
            Logger.i(Logger.TAG_VIDEO, "Video decoder started")
            
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
        
        decoderJob?.cancel()
        decoderJob = null
        
        try {
            codec?.stop()
            codec?.release()
            codec = null
        } catch (e: Exception) {
            Logger.e(Logger.TAG_VIDEO, "Error stopping decoder", e)
        }
        
        inputQueue.clear()
        _state.value = DecoderState.Idle
        
        Logger.i(Logger.TAG_VIDEO, "Video decoder stopped (decoded=$framesDecoded, dropped=$framesDropped)")
    }
    
    /**
     * Enfileira frame H.264 para decodificação
     * 
     * @param data Payload H.264 (NAL units)
     * @param timestamp Timestamp RTP (em unidades de 90kHz)
     * @param isKeyFrame Se é um keyframe (IDR)
     */
    fun queueFrame(data: ByteArray, timestamp: Long, isKeyFrame: Boolean = false) {
        if (_state.value != DecoderState.Running) {
            return
        }
        
        val frame = H264Frame(data, timestamp, isKeyFrame)
        
        if (!inputQueue.offer(frame)) {
            framesDropped++
            Logger.w(Logger.TAG_VIDEO, "Input queue full, dropping frame (dropped=$framesDropped)")
        }
    }
    
    /**
     * Loop principal de decodificação
     */
    private suspend fun decoderLoop() {
        Logger.i(Logger.TAG_VIDEO, "Decoder loop started")
        
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
            } catch (e: Exception) {
                Logger.e(Logger.TAG_VIDEO, "Error in decoder loop", e)
                _state.value = DecoderState.Error(e.message ?: "Decoder error")
                break
            }
        }
        
        Logger.i(Logger.TAG_VIDEO, "Decoder loop ended")
    }
    
    /**
     * Processa entrada (enfileira frames para decodificação)
     */
    private fun processInput(timeoutUs: Long) {
        val codec = this.codec ?: return
        
        // Obter buffer de entrada disponível
        val inputIndex = codec.dequeueInputBuffer(timeoutUs)
        if (inputIndex < 0) {
            return // Nenhum buffer disponível
        }
        
        // Obter próximo frame da fila
        val frame = inputQueue.poll() ?: return
        
        // Copiar dados para buffer
        val inputBuffer = codec.getInputBuffer(inputIndex)
        if (inputBuffer != null) {
            inputBuffer.clear()
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
                
                // Renderizar no Surface (true = render)
                codec.releaseOutputBuffer(outputIndex, true)
                
                framesDecoded++
                updateFps()
                
                // Calcular latência (timestamp do frame vs tempo atual)
                val currentTimeUs = System.nanoTime() / 1000
                val latencyUs = currentTimeUs - bufferInfo.presentationTimeUs
                val latencyMs = latencyUs / 1000
                
                // Atualizar telemetria
                telemetryCollector.updateVideoMetrics(
                    fps = currentFps,
                    latencyMs = latencyMs.toInt(),
                    droppedFrames = framesDropped.toInt()
                )
            }
            
            outputIndex == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                val format = codec.outputFormat
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
            
            Logger.d(Logger.TAG_VIDEO, "FPS: $currentFps (decoded=$framesDecoded, dropped=$framesDropped)")
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
        lastFpsTime = System.currentTimeMillis()
    }
}
