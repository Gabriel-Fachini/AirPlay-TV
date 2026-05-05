package com.airplay.tv.media


import android.media.MediaCodec
import android.media.MediaFormat
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
 * Decodificador de áudio AAC usando MediaCodec
 * Reproduz áudio via AudioTrack
 */
class AudioDecoder(
    private val performanceTracker: AudioPerformanceTracker = AudioPerformanceTracker(),
    private val inputQueue: AudioInputQueue = AudioInputQueue(),
    private val trackManager: AudioTrackManager = AudioTrackManager()
) {
    private val _state = MutableStateFlow<DecoderState>(DecoderState.Idle)
    val state: StateFlow<DecoderState> = _state.asStateFlow()
    
    private var codec: MediaCodec? = null
    private var decoderJob: Job? = null
    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())
    private var firstQueuedFrameLogged = false
    private var firstDecodedPcmLogged = false
    
    data class AACFrame(
        val data: ByteArray,
        val rtpTimestamp: Long,
        val presentationTimeUs: Long,
        val clockLocked: Boolean,
    ) {
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (javaClass != other?.javaClass) return false
            
            other as AACFrame
            
            if (!data.contentEquals(other.data)) return false
            if (rtpTimestamp != other.rtpTimestamp) return false
            if (presentationTimeUs != other.presentationTimeUs) return false
            if (clockLocked != other.clockLocked) return false
            
            return true
        }
        
        override fun hashCode(): Int {
            var result = data.contentHashCode()
            result = 31 * result + rtpTimestamp.hashCode()
            result = 31 * result + presentationTimeUs.hashCode()
            result = 31 * result + clockLocked.hashCode()
            return result
        }
    }
    
    private var sampleRate = 44100
    private var channels = 2
    
    /**
     * Configura decoder com parâmetros de áudio
     * 
     * @param sampleRate Taxa de amostragem (Hz)
     * @param channels Número de canais (1=mono, 2=stereo)
     * @param aacConfig AudioSpecificConfig (AAC)
     */
    fun configure(
        sampleRate: Int,
        channels: Int,
        aacConfig: ByteBuffer
    ): Boolean {
        try {
            Logger.i(
                Logger.TAG_AUDIO,
                "Cfg mime=${Constants.AUDIO_CODEC_MIME} rate=${sampleRate} ch=$channels csd=${aacConfig.toCompactHex()}"
            )
            
            this.sampleRate = sampleRate
            this.channels = channels
            performanceTracker.resetMetrics()
            firstQueuedFrameLogged = false
            firstDecodedPcmLogged = false
            
            // Criar decoder AAC
            codec = MediaCodec.createDecoderByType(Constants.AUDIO_CODEC_MIME)
            
            // Configurar formato
            val format = MediaFormat.createAudioFormat(
                Constants.AUDIO_CODEC_MIME,
                sampleRate,
                channels
            ).apply {
                setByteBuffer("csd-0", aacConfig) // AudioSpecificConfig
                setInteger(MediaFormat.KEY_MAX_INPUT_SIZE, Constants.MAX_INPUT_BUFFER_SIZE)
            }
            
            codec?.configure(format, null, null, 0)
            
            // Criar AudioTrack
            if (!trackManager.configure(sampleRate, channels)) {
                _state.value = DecoderState.Error("AudioTrack configuration failed")
                return false
            }
            
            _state.value = DecoderState.Configured
            Logger.i(Logger.TAG_AUDIO, "Audio decoder configured successfully")
            
            return true
            
        } catch (e: Exception) {
            Logger.e(Logger.TAG_AUDIO, "Failed to configure audio decoder", e)
            _state.value = DecoderState.Error(e.message ?: "Configuration failed")
            return false
        }
    }
    
    /**
     * Inicia decodificação e reprodução
     */
    fun start() {
        if (_state.value != DecoderState.Configured) {
            Logger.w(Logger.TAG_AUDIO, "Cannot start: decoder not configured")
            return
        }
        
        try {
            codec?.start()
            trackManager.play()
            
            _state.value = DecoderState.Running
            
            // Iniciar thread de decodificação
            decoderJob = scope.launch {
                decoderLoop()
            }
            
            Logger.i(Logger.TAG_AUDIO, "Start qCap=${inputQueue.getRemainingCapacity() + inputQueue.getSize()}")
            
        } catch (e: Exception) {
            Logger.e(Logger.TAG_AUDIO, "Failed to start audio decoder", e)
            _state.value = DecoderState.Error(e.message ?: "Start failed")
        }
    }
    
    /**
     * Para decodificação e reprodução
     */
    fun stop() {
        Logger.i(Logger.TAG_AUDIO, "Stopping audio decoder")
        _state.value = DecoderState.Idle
        
        decoderJob?.cancel()
        decoderJob = null
        
        try {
            trackManager.stopAndRelease()
            
            try {
                codec?.stop()
            } catch (e: IllegalStateException) {
                Logger.w(Logger.TAG_AUDIO, "Codec already stopped or in invalid state", e)
            }
            codec?.release()
            codec = null
        } catch (e: Exception) {
            Logger.e(Logger.TAG_AUDIO, "Error stopping decoder", e)
        }

        inputQueue.clear()
        performanceTracker.resetMetrics()

        Logger.i(Logger.TAG_AUDIO, "Audio decoder stopped (decoded=${performanceTracker.samplesDecoded}, dropped=${performanceTracker.samplesDropped})")
    }
    
    /**
     * Enfileira frame AAC para decodificação
     * 
     * @param data Payload AAC
     * @param rtpTimestamp Timestamp RTP
     */
    fun queueFrame(data: ByteArray, rtpTimestamp: Long, presentationTimeUs: Long, clockLocked: Boolean) {
        if (_state.value != DecoderState.Running) {
            return
        }
        if (!firstQueuedFrameLogged) {
            Logger.i(
                Logger.TAG_AUDIO,
                "Queue first AAC frame size=${data.size} rtpTs=$rtpTimestamp pts=$presentationTimeUs locked=$clockLocked"
            )
            firstQueuedFrameLogged = true
        }
        performanceTracker.onFrameQueued(data.size, rtpTimestamp, presentationTimeUs, clockLocked)
        inputQueue.queueFrame(data, rtpTimestamp, presentationTimeUs, clockLocked) {
            performanceTracker.onFrameDropped()
        }
    }

    fun flush() {
        inputQueue.clear()
        val codec = this.codec ?: return
        try {
            codec.flush()
            Logger.i(Logger.TAG_AUDIO, "Audio decoder flushed")
        } catch (e: IllegalStateException) {
            Logger.w(Logger.TAG_AUDIO, "Audio decoder flush ignored due to codec state", e)
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
                
                // Processar saída (reproduzir áudio)
                processOutput(timeoutUs)
                
                // Yield para não bloquear thread
                yield()
                
            } catch (e: CancellationException) {
                break
            } catch (e: IllegalStateException) {
                if (_state.value == DecoderState.Idle || !currentCoroutineContext().isActive) {
                    break
                }
                Logger.e(Logger.TAG_AUDIO, "Error in decoder loop", e)
                _state.value = DecoderState.Error(e.message ?: "Decoder error")
                break
            } catch (e: Exception) {
                Logger.e(Logger.TAG_AUDIO, "Error in decoder loop", e)
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

        if (inputQueue.peek() == null) {
            return
        }

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
            
            // Enfileirar para decodificação
            codec.queueInputBuffer(
                inputIndex,
                0,
                frame.data.size,
                frame.presentationTimeUs,
                0
            )
            performanceTracker.onFrameSubmittedToCodec(frame.presentationTimeUs, frame.clockLocked)
        } else {
            performanceTracker.onFrameDropped()
        }
    }
    
    /**
     * Processa saída (reproduz áudio decodificado)
     */
    private fun processOutput(timeoutUs: Long) {
        val codec = this.codec ?: return
        
        val bufferInfo = MediaCodec.BufferInfo()
        val outputIndex = codec.dequeueOutputBuffer(bufferInfo, timeoutUs)
        
        when {
            outputIndex >= 0 -> {
                // Samples decodificados disponíveis
                
                val outputBuffer = codec.getOutputBuffer(outputIndex)
                if (outputBuffer != null && bufferInfo.size > 0) {
                    // Criar array para AudioTrack
                    val audioData = ByteArray(bufferInfo.size)
                    outputBuffer.get(audioData)
                    outputBuffer.clear()
                    
                    // Reproduzir via AudioTrack
                    val written = trackManager.write(audioData, 0, audioData.size)
                    
                    if (written < 0) {
                        Logger.e(Logger.TAG_AUDIO, "AudioTrack write error: $written")
                    } else {
                        if (!firstDecodedPcmLogged) {
                            Logger.i(
                                Logger.TAG_AUDIO,
                                "First PCM decoded bytes=${audioData.size} pts=${bufferInfo.presentationTimeUs} written=$written"
                            )
                            firstDecodedPcmLogged = true
                        }
                        performanceTracker.onSamplesDecoded(written, bufferInfo.presentationTimeUs)
                    }
                }
                
                codec.releaseOutputBuffer(outputIndex, false)
            }
            
            outputIndex == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                val format = codec.outputFormat
                Logger.i(Logger.TAG_AUDIO, "Output format changed: $format")
            }
            
            outputIndex == MediaCodec.INFO_TRY_AGAIN_LATER -> {
                // Nenhum sample disponível ainda
            }
        }
    }
    
    /**
     * Ajusta playback rate para sincronização
     * 
     * @param rate Taxa de playback (1.0 = normal, >1.0 = mais rápido, <1.0 = mais lento)
     */
    fun setPlaybackRate(rate: Float) {
        trackManager.setPlaybackRate(rate)
    }
    
    /**
     * Obtém timestamp de reprodução atual (microsegundos)
     */
    fun getCurrentTimestampUs(): Long {
        return performanceTracker.lastRenderedPresentationTimeUs
    }

    fun getLastRenderedPresentationTimeUs(): Long = performanceTracker.lastRenderedPresentationTimeUs

    fun hasSynchronizedClock(): Boolean = performanceTracker.hasSynchronizedClock
    
    /**
     * Obtém número de samples decodificados
     */
    fun getSamplesDecoded(): Long = performanceTracker.samplesDecoded
    
    /**
     * Obtém número de samples dropados
     */
    fun getSamplesDropped(): Long = performanceTracker.samplesDropped
    
    /**
     * Reseta métricas
     */
    fun resetMetrics() {
        performanceTracker.resetMetrics()
    }

    private fun ByteBuffer.toCompactHex(): String {
        val duplicate = duplicate()
        val bytes = ByteArray(duplicate.remaining())
        duplicate.get(bytes)
        return bytes.joinToString(separator = "") { byte -> "%02x".format(byte) }
    }
}
