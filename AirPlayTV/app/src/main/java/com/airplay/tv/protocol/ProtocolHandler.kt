package com.airplay.tv.protocol

import com.airplay.tv.media.AudioDecoder
import com.airplay.tv.media.VideoDecoder
import com.airplay.tv.util.Constants
import com.airplay.tv.util.Logger
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import java.nio.ByteBuffer

/**
 * Handler do protocolo AirPlay (RTSP/RTP)
 * Gerencia servidor RTSP e handshake com clientes
 * Integra com RTPParser e decoders de mídia
 */
class ProtocolHandler(
    private val videoDecoder: VideoDecoder? = null,
    private val audioDecoder: AudioDecoder? = null
) {
    enum class SessionKind {
        MIRRORING,
        PHOTO,
        SLIDESHOW
    }

    private val pairingManager = AirPlayPairingManager()
    private val mirroringSession = AirPlayMirroringSession(
        videoDecoder = videoDecoder,
        pairingManager = pairingManager,
        decryptFairPlayAesKey = { encryptedKey -> decryptFairPlayAesKeyNative(encryptedKey) },
        startMirrorVideoServer = { startMirrorVideoServerNative() },
        onCodecConfigReceived = { sps: ByteBuffer, pps: ByteBuffer, width: Int, height: Int ->
            // Emit codec config event
            _codecConfigReceived.tryEmit(CodecConfig(sps, pps, width, height))
        }
    )
    
    // Estado da conexão
    sealed class ConnectionState {
        object Idle : ConnectionState()
        data class Connected(val clientIp: String) : ConnectionState()
        data class Error(val message: String) : ConnectionState()
    }

    sealed class MediaPlaybackState {
        data object Idle : MediaPlaybackState()

        data class PhotoDisplayed(
            val clientIp: String,
            val sessionId: String,
            val assetKey: String,
            val transition: String?,
            val imageData: ByteArray
        ) : MediaPlaybackState()

        data class SlideshowPlaying(
            val clientIp: String,
            val sessionId: String,
            val theme: String?,
            val slideDurationSeconds: Int,
            val state: String,
            val imageData: ByteArray?,
            val assetKey: String?,
            val transition: String?
        ) : MediaPlaybackState()
    }
    
    private val _connectionState = MutableStateFlow<ConnectionState>(ConnectionState.Idle)
    val connectionState: StateFlow<ConnectionState> = _connectionState.asStateFlow()
    private val _mediaPlaybackState = MutableStateFlow<MediaPlaybackState>(MediaPlaybackState.Idle)
    val mediaPlaybackState: StateFlow<MediaPlaybackState> = _mediaPlaybackState.asStateFlow()
    private val _sessionActivity = MutableSharedFlow<String>(
        replay = 0,
        extraBufferCapacity = 32
    )
    val sessionActivity: SharedFlow<String> = _sessionActivity.asSharedFlow()
    
    // Controle de throttling para sessionActivity
    private var lastActivityTime = 0L
    private val activityThrottleMs = 1000L // Emitir no máximo 1x por segundo
    
    // Codec config received event
    data class CodecConfig(
        val sps: java.nio.ByteBuffer,
        val pps: java.nio.ByteBuffer,
        val width: Int,
        val height: Int
    )
    private val _codecConfigReceived = MutableSharedFlow<CodecConfig>(
        replay = 0,
        extraBufferCapacity = 1
    )
    val codecConfigReceived: SharedFlow<CodecConfig> = _codecConfigReceived.asSharedFlow()
    
    // Parser RTP
    private val rtpParser = RTPParser()
    
    // Informações da sessão
    data class SessionInfo(
        val clientIp: String,
        val videoWidth: Int,
        val videoHeight: Int,
        val audioSampleRate: Int,
        val audioChannels: Int
    )
    
    private var currentSession: SessionInfo? = null
    private var lastMediaImageData: ByteArray? = null
    private var lastMediaAssetKey: String? = null
    private var lastMediaTransition: String? = null
    private var audioPayloadsReceived = 0L
    private var audioAccessUnitsQueued = 0L
    
    companion object {
        init {
            try {
                System.loadLibrary("airplay-native")
                Logger.i(Logger.TAG_PROTOCOL, "Native library loaded successfully")
            } catch (e: UnsatisfiedLinkError) {
                Logger.e(Logger.TAG_PROTOCOL, "Failed to load native library", e)
            }
        }
    }
    
    // JNI functions
    external fun getVersionFromJNI(): String
    private external fun startRTSPServerNative(port: Int): Boolean
    private external fun stopRTSPServerNative()
    private external fun isServerRunningNative(): Boolean
    private external fun decryptFairPlayAesKeyNative(encryptedKey: ByteArray): ByteArray?
    private external fun startMirrorVideoServerNative(): Int
    private external fun getClientIpNative(): String
    private external fun getVideoResolutionNative(): IntArray
    private external fun getAudioConfigNative(): IntArray
    
    /**
     * Inicia servidor RTSP
     */
    fun startRTSPServer(port: Int = Constants.RTSP_PORT): Boolean {
        Logger.i(Logger.TAG_PROTOCOL, "Starting RTSP server on port $port")
        
        if (isServerRunning()) {
            Logger.w(Logger.TAG_PROTOCOL, "Server already running")
            return false
        }
        
        val success = startRTSPServerNative(port)
        
        if (success) {
            Logger.i(Logger.TAG_PROTOCOL, "RTSP server started successfully")
            _connectionState.value = ConnectionState.Idle
            rtpParser.resetStats()
        } else {
            Logger.e(Logger.TAG_PROTOCOL, "Failed to start RTSP server")
            _connectionState.value = ConnectionState.Error("Failed to start server")
        }
        
        return success
    }
    
    /**
     * Para servidor RTSP
     */
    fun stopRTSPServer() {
        Logger.i(Logger.TAG_PROTOCOL, "Stopping RTSP server")
        
        if (!isServerRunning()) {
            Logger.w(Logger.TAG_PROTOCOL, "Server not running")
            return
        }
        
        stopRTSPServerNative()
        currentSession = null
        _mediaPlaybackState.value = MediaPlaybackState.Idle
        lastMediaImageData = null
        lastMediaAssetKey = null
        lastMediaTransition = null
        _connectionState.value = ConnectionState.Idle
        
        // Logar estatísticas finais
        rtpParser.logStats()
        
        Logger.i(Logger.TAG_PROTOCOL, "RTSP server stopped")
    }
    
    /**
     * Verifica se servidor está rodando
     */
    fun isServerRunning(): Boolean {
        return isServerRunningNative()
    }
    
    /**
     * Obtém informações da sessão atual
     */
    fun getSessionInfo(): SessionInfo? {
        if (!isServerRunning()) {
            return null
        }
        
        val clientIp = getClientIpNative()
        if (clientIp.isEmpty()) {
            return null
        }
        
        val videoRes = getVideoResolutionNative()
        val audioConfig = getAudioConfigNative()
        
        return SessionInfo(
            clientIp = clientIp,
            videoWidth = videoRes[0],
            videoHeight = videoRes[1],
            audioSampleRate = audioConfig[0],
            audioChannels = audioConfig[1]
        )
    }
    
    /**
     * Obtém estatísticas RTP
     */
    fun getRTPStats() = Pair(rtpParser.getVideoStats(), rtpParser.getAudioStats())
    
    // Callbacks chamados do código nativo (JNI)
    @Suppress("unused")
    private fun onClientConnected(clientIp: String) {
        Logger.i(Logger.TAG_PROTOCOL, "Client connected: $clientIp")
        
        // Atualizar informações da sessão
        val sessionInfo = getSessionInfo()
        if (sessionInfo != null) {
            currentSession = sessionInfo
            _connectionState.value = ConnectionState.Connected(clientIp)
            
            Logger.i(Logger.TAG_PROTOCOL, 
                "Session established: ${sessionInfo.videoWidth}x${sessionInfo.videoHeight}, " +
                "${sessionInfo.audioSampleRate}Hz ${sessionInfo.audioChannels}ch")
        }
    }
    
    @Suppress("unused")
    private fun onClientDisconnected() {
        Logger.i(Logger.TAG_PROTOCOL, "Client disconnected")
        currentSession = null
        audioPayloadsReceived = 0L
        audioAccessUnitsQueued = 0L
        _mediaPlaybackState.value = MediaPlaybackState.Idle
        lastMediaImageData = null
        lastMediaAssetKey = null
        lastMediaTransition = null
        pairingManager.resetSession()
        mirroringSession.reset()
        _connectionState.value = ConnectionState.Idle
        
        // Logar estatísticas da sessão
        rtpParser.logStats()
    }
    
    @Suppress("unused")
    private fun onError(error: String) {
        Logger.e(Logger.TAG_PROTOCOL, "Protocol error: $error")
        audioPayloadsReceived = 0L
        audioAccessUnitsQueued = 0L
        _mediaPlaybackState.value = MediaPlaybackState.Idle
        lastMediaImageData = null
        lastMediaAssetKey = null
        lastMediaTransition = null
        pairingManager.resetSession()
        mirroringSession.reset()
        _connectionState.value = ConnectionState.Error(error)
    }

    @Suppress("unused")
    private fun onControlRequestHandled(method: String) {
        if (
            method == "GET /server-info" ||
            method == "GET /slideshow-features" ||
            method == "PUT /photo" ||
            method == "PUT /slideshows/1" ||
            method == "POST /stop" ||
            method == "/play" ||
            method == "/scrub" ||
            method == "/rate" ||
            method == "/playback-info" ||
            method == "/event" ||
            method == "/setProperty"
        ) {
            Logger.i(Logger.TAG_PROTOCOL, "Native media control handled: $method")
        }
        _sessionActivity.tryEmit(method)
    }

    @Suppress("unused")
    private fun onPairSetup(data: ByteArray): ByteArray? {
        return pairingManager.handlePairSetup(data)
    }

    @Suppress("unused")
    private fun onPairVerify(data: ByteArray): ByteArray? {
        return pairingManager.handlePairVerify(data)
    }

    @Suppress("unused")
    private fun onSetupRequest(data: ByteArray): ByteArray? {
        return mirroringSession.buildSetupResponse(data)
    }

    /**
     * Callback de pacote de vídeo de mirroring do código nativo
     * Emite sessionActivity com throttling para evitar overhead
     */
    @Suppress("unused")
    private fun onMirroringVideoPacket(payloadType: Int, data: ByteArray) {
        // Throttle sessionActivity para evitar emitir a cada frame (30-60 fps)
        val now = System.currentTimeMillis()
        if (now - lastActivityTime >= activityThrottleMs) {
            _sessionActivity.tryEmit("MIRROR_VIDEO")
            lastActivityTime = now
        }
        
        mirroringSession.handleVideoPacket(payloadType, data)
    }

    @Suppress("unused")
    private fun onPhotoPlaybackSession(
        clientIp: String,
        sessionId: String,
        assetKey: String,
        transition: String?,
        imageData: ByteArray,
        isSlideshow: Boolean
    ) {
        Logger.i(
            Logger.TAG_PROTOCOL,
            "Media callback photo: client=$clientIp sessionId=${sessionId.ifEmpty { "none" }} " +
                "bytes=${imageData.size} assetKey=${assetKey.ifEmpty { "none" }} " +
                "transition=${transition ?: "none"} slideshow=$isSlideshow"
        )
        lastMediaImageData = imageData.copyOf()
        lastMediaAssetKey = assetKey
        lastMediaTransition = transition

        _sessionActivity.tryEmit(if (isSlideshow) "SLIDESHOW_PHOTO" else "PHOTO")
        _mediaPlaybackState.value = if (isSlideshow) {
            MediaPlaybackState.SlideshowPlaying(
                clientIp = clientIp,
                sessionId = sessionId,
                theme = null,
                slideDurationSeconds = 0,
                state = "playing",
                imageData = imageData.copyOf(),
                assetKey = assetKey,
                transition = transition
            )
        } else {
            MediaPlaybackState.PhotoDisplayed(
                clientIp = clientIp,
                sessionId = sessionId,
                assetKey = assetKey,
                transition = transition,
                imageData = imageData.copyOf()
            )
        }
    }

    @Suppress("unused")
    private fun onSlideshowPlaybackState(
        clientIp: String,
        sessionId: String,
        theme: String?,
        slideDurationSeconds: Int,
        state: String
    ) {
        Logger.i(
            Logger.TAG_PROTOCOL,
            "Media callback slideshow: client=$clientIp sessionId=${sessionId.ifEmpty { "none" }} " +
                "state=$state theme=${theme ?: "none"} duration=${slideDurationSeconds}s " +
                "hasImage=${lastMediaImageData != null}"
        )
        _sessionActivity.tryEmit("SLIDESHOW_STATE")
        _mediaPlaybackState.value = if (state.equals("stopped", ignoreCase = true)) {
            MediaPlaybackState.Idle
        } else {
            MediaPlaybackState.SlideshowPlaying(
                clientIp = clientIp,
                sessionId = sessionId,
                theme = theme,
                slideDurationSeconds = slideDurationSeconds,
                state = state,
                imageData = lastMediaImageData?.copyOf(),
                assetKey = lastMediaAssetKey,
                transition = lastMediaTransition
            )
        }
    }

    @Suppress("unused")
    private fun onMediaPlaybackStopped(sessionId: String) {
        Logger.i(
            Logger.TAG_PROTOCOL,
            "Media callback stop: sessionId=${sessionId.ifEmpty { "none" }} " +
                "lastAsset=${lastMediaAssetKey ?: "none"} hadImage=${lastMediaImageData != null}"
        )
        lastMediaImageData = null
        lastMediaAssetKey = null
        lastMediaTransition = null
        _sessionActivity.tryEmit("MEDIA_STOP")
        _mediaPlaybackState.value = MediaPlaybackState.Idle
    }
    
    /**
     * Callback de dados de vídeo (H.264) do código nativo
     * 
     * @param data Pacote RTP completo
     * @param timestamp Timestamp RTP (90kHz)
     */
    @Suppress("unused")
    private fun onVideoData(data: ByteArray, timestamp: Long) {
        _sessionActivity.tryEmit("VIDEO")
        // Parsear pacote RTP
        val packet = rtpParser.parsePacket(data, data.size)
        
        if (packet != null && packet.header.payloadType == RTPParser.PAYLOAD_TYPE_H264) {
            // Enfileirar payload H.264 para decodificação
            videoDecoder?.queueFrame(
                data = packet.payload,
                timestamp = packet.header.timestamp,
                isKeyFrame = packet.header.marker // Marker bit indica fim de frame
            )
        }
    }
    
    /**
     * Callback de dados de áudio AAC do código nativo.
     * O RTPReceiver nativo já removeu o header RTP; aqui recebemos apenas o payload
     * MPEG4-generic e precisamos extrair os Access Units AAC antes de enviar ao decoder.
     */
    @Suppress("unused")
    private fun onAudioData(data: ByteArray, timestamp: Long) {
        _sessionActivity.tryEmit("AUDIO")

        audioPayloadsReceived++
        val accessUnits = extractAacAccessUnits(data)
        if (audioPayloadsReceived <= 3L || accessUnits.isEmpty()) {
            Logger.i(
                Logger.TAG_PROTOCOL,
                "Audio payload received: payload#=$audioPayloadsReceived bytes=${data.size} accessUnits=${accessUnits.size}"
            )
        }

        if (accessUnits.isEmpty()) {
            return
        }

        accessUnits.forEach { accessUnit ->
            audioAccessUnitsQueued++
            if (audioAccessUnitsQueued <= 3L) {
                Logger.i(
                    Logger.TAG_PROTOCOL,
                    "Queueing AAC access unit #$audioAccessUnitsQueued (${accessUnit.size} bytes)"
                )
            }
            audioDecoder?.queueFrame(
                data = accessUnit,
                timestamp = timestamp
            )
        }
    }

    private fun extractAacAccessUnits(payload: ByteArray): List<ByteArray> {
        if (payload.size < 4) {
            Logger.w(Logger.TAG_PROTOCOL, "AAC payload too small: ${payload.size} bytes")
            return emptyList()
        }

        val auHeadersLengthBits = ((payload[0].toInt() and 0xFF) shl 8) or
            (payload[1].toInt() and 0xFF)
        if (auHeadersLengthBits <= 0) {
            Logger.w(Logger.TAG_PROTOCOL, "AAC payload missing AU headers: ${payload.size} bytes")
            return emptyList()
        }

        val auHeadersLengthBytes = (auHeadersLengthBits + 7) / 8
        val headersStart = 2
        val dataStart = headersStart + auHeadersLengthBytes
        if (payload.size < dataStart) {
            Logger.w(
                Logger.TAG_PROTOCOL,
                "AAC payload truncated before AU data: payload=${payload.size} headersBytes=$auHeadersLengthBytes"
            )
            return emptyList()
        }

        val accessUnits = mutableListOf<ByteArray>()
        var headerOffset = headersStart
        var dataOffset = dataStart
        val headerCount = auHeadersLengthBits / 16

        repeat(headerCount) {
            if (headerOffset + 1 >= dataStart) {
                Logger.w(Logger.TAG_PROTOCOL, "AAC AU header truncated at index=$it")
                return accessUnits
            }

            val auHeader = ((payload[headerOffset].toInt() and 0xFF) shl 8) or
                (payload[headerOffset + 1].toInt() and 0xFF)
            val accessUnitSize = (auHeader shr 3) and 0x1FFF
            headerOffset += 2

            if (accessUnitSize <= 0) {
                Logger.w(Logger.TAG_PROTOCOL, "AAC AU header with invalid size=$accessUnitSize")
                return@repeat
            }
            if (dataOffset + accessUnitSize > payload.size) {
                Logger.w(
                    Logger.TAG_PROTOCOL,
                    "AAC access unit exceeds payload: size=$accessUnitSize remaining=${payload.size - dataOffset}"
                )
                return accessUnits
            }

            accessUnits += payload.copyOfRange(dataOffset, dataOffset + accessUnitSize)
            dataOffset += accessUnitSize
        }

        return accessUnits
    }
}
