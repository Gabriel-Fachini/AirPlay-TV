package com.airplay.tv.media

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
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
class AudioDecoder {
    
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
    private var audioTrack: AudioTrack? = null
    private var decoderJob: Job? = null
    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())
    
    // Buffer de entrada (fila de payloads AAC)
    private val inputQueue = LinkedBlockingQueue<AACFrame>(Constants.JITTER_BUFFER_FRAMES * 2)
    
    data class AACFrame(
        val data: ByteArray,
        val timestamp: Long
    ) {
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (javaClass != other?.javaClass) return false
            
            other as AACFrame
            
            if (!data.contentEquals(other.data)) return false
            if (timestamp != other.timestamp) return false
            
            return true
        }
        
        override fun hashCode(): Int {
            var result = data.contentHashCode()
            result = 31 * result + timestamp.hashCode()
            return result
        }
    }
    
    // Métricas
    private var samplesDecoded = 0L
    private var samplesDropped = 0L
    
    // Configuração de áudio
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
            Logger.i(Logger.TAG_AUDIO, "Configuring audio decoder: ${sampleRate}Hz ${channels}ch")
            
            this.sampleRate = sampleRate
            this.channels = channels
            
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
            val channelConfig = if (channels == 1) {
                AudioFormat.CHANNEL_OUT_MONO
            } else {
                AudioFormat.CHANNEL_OUT_STEREO
            }
            
            val bufferSize = AudioTrack.getMinBufferSize(
                sampleRate,
                channelConfig,
                AudioFormat.ENCODING_PCM_16BIT
            )
            
            audioTrack = AudioTrack.Builder()
                .setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_MEDIA)
                        .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                        .build()
                )
                .setAudioFormat(
                    AudioFormat.Builder()
                        .setSampleRate(sampleRate)
                        .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                        .setChannelMask(channelConfig)
                        .build()
                )
                .setBufferSizeInBytes(bufferSize * 2) // Buffer maior para estabilidade
                .setTransferMode(AudioTrack.MODE_STREAM)
                .build()
            
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
            audioTrack?.play()
            
            _state.value = DecoderState.Running
            
            // Iniciar thread de decodificação
            decoderJob = scope.launch {
                decoderLoop()
            }
            
            Logger.i(Logger.TAG_AUDIO, "Audio decoder started")
            
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
            try {
                audioTrack?.stop()
            } catch (e: IllegalStateException) {
                Logger.w(Logger.TAG_AUDIO, "AudioTrack already stopped or in invalid state", e)
            }
            audioTrack?.release()
            audioTrack = null
            
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
        
        Logger.i(Logger.TAG_AUDIO, "Audio decoder stopped (decoded=$samplesDecoded, dropped=$samplesDropped)")
    }
    
    /**
     * Enfileira frame AAC para decodificação
     * 
     * @param data Payload AAC
     * @param timestamp Timestamp RTP
     */
    fun queueFrame(data: ByteArray, timestamp: Long) {
        if (_state.value != DecoderState.Running) {
            return
        }
        
        val frame = AACFrame(data, timestamp)
        
        if (!inputQueue.offer(frame)) {
            samplesDropped++
            Logger.w(Logger.TAG_AUDIO, "Input queue full, dropping frame (dropped=$samplesDropped)")
        }
    }
    
    /**
     * Loop principal de decodificação
     */
    private suspend fun decoderLoop() {
        Logger.i(Logger.TAG_AUDIO, "Decoder loop started")
        
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
        
        Logger.i(Logger.TAG_AUDIO, "Decoder loop ended")
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
            
            // Converter timestamp RTP para microsegundos
            // AAC usa mesma base de tempo que vídeo (90kHz)
            val timestampUs = (frame.timestamp * 1000000L) / 90000L
            
            // Enfileirar para decodificação
            codec.queueInputBuffer(
                inputIndex,
                0,
                frame.data.size,
                timestampUs,
                0
            )
        } else {
            samplesDropped++
        }
    }
    
    /**
     * Processa saída (reproduz áudio decodificado)
     */
    private fun processOutput(timeoutUs: Long) {
        val codec = this.codec ?: return
        val audioTrack = this.audioTrack ?: return
        
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
                    val written = audioTrack.write(audioData, 0, audioData.size)
                    
                    if (written < 0) {
                        Logger.e(Logger.TAG_AUDIO, "AudioTrack write error: $written")
                    } else {
                        samplesDecoded += (written / 2).toLong() // 16-bit samples
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
        try {
            val track = audioTrack ?: return
            val params = track.playbackParams
            if (params != null) {
                track.playbackParams = params.setSpeed(rate)
                Logger.d(Logger.TAG_AUDIO, "Playback rate adjusted to $rate")
            }
        } catch (e: Exception) {
            Logger.e(Logger.TAG_AUDIO, "Failed to set playback rate", e)
        }
    }
    
    /**
     * Obtém timestamp de reprodução atual (microsegundos)
     */
    fun getCurrentTimestampUs(): Long {
        val audioTrack = this.audioTrack ?: return 0L
        
        return try {
            val timestamp = android.media.AudioTimestamp()
            if (audioTrack.getTimestamp(timestamp)) {
                timestamp.nanoTime / 1000
            } else {
                0L
            }
        } catch (e: Exception) {
            0L
        }
    }
    
    /**
     * Obtém número de samples decodificados
     */
    fun getSamplesDecoded(): Long = samplesDecoded
    
    /**
     * Obtém número de samples dropados
     */
    fun getSamplesDropped(): Long = samplesDropped
    
    /**
     * Reseta métricas
     */
    fun resetMetrics() {
        samplesDecoded = 0
        samplesDropped = 0
    }
}
