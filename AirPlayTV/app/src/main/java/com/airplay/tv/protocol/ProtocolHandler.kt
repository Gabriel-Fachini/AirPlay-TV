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
    private val audioPipeline = ProtocolAudioPipeline(audioDecoder)
    private val playbackStateStore = ProtocolPlaybackStateStore()
    private val mirroringSession = AirPlayMirroringSession(
        videoDecoder = videoDecoder,
        pairingManager = pairingManager,
        decryptFairPlayAesKey = { encryptedKey -> decryptFairPlayAesKeyNative(encryptedKey) },
        startMirrorVideoServer = { startMirrorVideoServerNative() },
        onCodecConfigReceived = { sps: ByteBuffer, pps: ByteBuffer, width: Int, height: Int ->
            // Emit codec config event
            _codecConfigReceived.tryEmit(CodecConfig(sps, pps, width, height))
        },
        onAudioCryptoConfigured = { config ->
            audioPipeline.configureCrypto(config)
        },
        onAudioStreamConfigured = { config ->
            audioPipeline.configureStream(config)
            updateAudioSessionConfigNative(
                config.compressionType,
                config.samplesPerFrame,
                config.audioFormat,
                config.sampleRate,
                config.channels,
                config.remoteControlPort,
                config.localDataPort,
                config.localControlPort,
                config.localTimingPort,
                config.isMedia,
                config.usingScreen,
            )
        },
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
        val audioChannels: Int,
        val audioStreamConfig: AudioStreamConfig,
    )

    private var currentSession: SessionInfo? = null
    
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
    private external fun updateAudioSessionConfigNative(
        compressionType: Int,
        samplesPerFrame: Int,
        audioFormat: Long,
        sampleRate: Int,
        channels: Int,
        remoteControlPort: Int,
        localDataPort: Int,
        localControlPort: Int,
        localTimingPort: Int,
        isMedia: Boolean,
        usingScreen: Boolean,
    )
    private external fun resetAudioSessionConfigNative()
    
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
            audioPipeline.reset()
            playbackStateStore.reset()
            resetAudioSessionConfigNative()
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
        audioPipeline.reset()
        _mediaPlaybackState.value = MediaPlaybackState.Idle
        playbackStateStore.reset()
        _connectionState.value = ConnectionState.Idle
        resetAudioSessionConfigNative()
        
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
        val streamConfig = audioPipeline.currentStreamConfig.let { configured ->
            if (configured.compressionType != 0 || configured.samplesPerFrame != 0) {
                configured
            } else {
                configured.copy(
                    sampleRate = audioConfig.getOrElse(0) { configured.sampleRate },
                    channels = audioConfig.getOrElse(1) { configured.channels },
                )
            }
        }
        
        return SessionInfo(
            clientIp = clientIp,
            videoWidth = videoRes[0],
            videoHeight = videoRes[1],
            audioSampleRate = streamConfig.sampleRate,
            audioChannels = streamConfig.channels,
            audioStreamConfig = streamConfig,
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
                "${sessionInfo.audioSampleRate}Hz ${sessionInfo.audioChannels}ch " +
                "codec=${sessionInfo.audioStreamConfig.codecLabel}")
        }
    }
    
    @Suppress("unused")
    private fun onClientDisconnected() {
        Logger.i(Logger.TAG_PROTOCOL, "Client disconnected")
        currentSession = null
        audioPipeline.reset()
        _mediaPlaybackState.value = MediaPlaybackState.Idle
        playbackStateStore.reset()
        pairingManager.resetSession()
        mirroringSession.reset()
        _connectionState.value = ConnectionState.Idle
        resetAudioSessionConfigNative()
        
        // Logar estatísticas da sessão
        rtpParser.logStats()
    }
    
    @Suppress("unused")
    private fun onError(error: String) {
        Logger.e(Logger.TAG_PROTOCOL, "Protocol error: $error")
        audioPipeline.reset()
        _mediaPlaybackState.value = MediaPlaybackState.Idle
        playbackStateStore.reset()
        pairingManager.resetSession()
        mirroringSession.reset()
        _connectionState.value = ConnectionState.Error(error)
        resetAudioSessionConfigNative()
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

    @Suppress("unused")
    private fun onAudioSync(rtpSync: Long, remoteNtpUs: Long, localNtpUs: Long, initial: Boolean) {
        audioPipeline.handleSync(rtpSync, remoteNtpUs, localNtpUs, initial)
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

        _sessionActivity.tryEmit(if (isSlideshow) "SLIDESHOW_PHOTO" else "PHOTO")
        _mediaPlaybackState.value = playbackStateStore.onPhotoPlayback(
            clientIp = clientIp,
            sessionId = sessionId,
            assetKey = assetKey,
            transition = transition,
            imageData = imageData,
            isSlideshow = isSlideshow,
        )
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
                "hasImage=${playbackStateStore.hasCachedImage()}"
        )
        _sessionActivity.tryEmit("SLIDESHOW_STATE")
        _mediaPlaybackState.value = playbackStateStore.onSlideshowState(
            clientIp = clientIp,
            sessionId = sessionId,
            theme = theme,
            slideDurationSeconds = slideDurationSeconds,
            state = state,
        )
    }

    @Suppress("unused")
    private fun onMediaPlaybackStopped(sessionId: String) {
        Logger.i(
            Logger.TAG_PROTOCOL,
            "Media callback stop: sessionId=${sessionId.ifEmpty { "none" }} " +
                "lastAsset=${playbackStateStore.currentAssetKey() ?: "none"} hadImage=${playbackStateStore.hasCachedImage()}"
        )
        playbackStateStore.reset()
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
    private fun onVideoData(data: ByteArray, @Suppress("UNUSED_PARAMETER") timestamp: Long) {
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
        audioPipeline.handlePayload(data, timestamp)
    }

}
