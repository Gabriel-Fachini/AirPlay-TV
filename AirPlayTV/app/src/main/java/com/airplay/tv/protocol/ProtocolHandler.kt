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
    private val audioAccessUnitExtractor = AudioAccessUnitExtractor()

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
        },
        onAudioCryptoConfigured = { config ->
            audioFrameDecryptorSelector = config?.let { AudioFrameDecryptorSelector(it) }
        },
        onAudioStreamConfigured = { config ->
            currentAudioStreamConfig = config
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

    private data class AudioSyncState(
        val rtpSync: Long,
        val remoteNtpUs: Long,
        val localNtpUs: Long,
        val initial: Boolean,
    )
    
    private var currentSession: SessionInfo? = null
    private var currentAudioStreamConfig = AudioStreamConfig()
    private var currentAudioSyncState: AudioSyncState? = null
    private var audioFrameDecryptorSelector: AudioFrameDecryptorSelector? = null
    private var lastMediaImageData: ByteArray? = null
    private var lastMediaAssetKey: String? = null
    private var lastMediaTransition: String? = null
    private var audioPayloadsReceived = 0L
    private var audioAccessUnitsQueued = 0L
    private var audioNoDataPacketsDropped = 0L
    private var audioSyncEventsReceived = 0L
    private var audioPayloadsBeforeSync = 0L
    private var audioInvalidFramesDropped = 0L
    
    companion object {
        const val AUDIO_RTP_SAMPLE_RATE = 44_100L
        val AAC_ELD_NO_DATA_MARKER = byteArrayOf(0x00, 0x68, 0x34, 0x00)

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
            currentAudioStreamConfig = AudioStreamConfig()
            currentAudioSyncState = null
            audioFrameDecryptorSelector = null
            resetAudioSessionConfigNative()
            audioPayloadsReceived = 0L
            audioAccessUnitsQueued = 0L
            audioNoDataPacketsDropped = 0L
            audioSyncEventsReceived = 0L
            audioPayloadsBeforeSync = 0L
            audioInvalidFramesDropped = 0L
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
        currentAudioStreamConfig = AudioStreamConfig()
        currentAudioSyncState = null
        audioFrameDecryptorSelector = null
        _mediaPlaybackState.value = MediaPlaybackState.Idle
        lastMediaImageData = null
        lastMediaAssetKey = null
        lastMediaTransition = null
        audioPayloadsReceived = 0L
        audioAccessUnitsQueued = 0L
        audioNoDataPacketsDropped = 0L
        audioSyncEventsReceived = 0L
        audioPayloadsBeforeSync = 0L
        audioInvalidFramesDropped = 0L
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
        val streamConfig = currentAudioStreamConfig.let { configured ->
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
        currentAudioStreamConfig = AudioStreamConfig()
        currentAudioSyncState = null
        audioFrameDecryptorSelector = null
        audioPayloadsReceived = 0L
        audioAccessUnitsQueued = 0L
        audioInvalidFramesDropped = 0L
        _mediaPlaybackState.value = MediaPlaybackState.Idle
        lastMediaImageData = null
        lastMediaAssetKey = null
        lastMediaTransition = null
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
        audioPayloadsReceived = 0L
        audioAccessUnitsQueued = 0L
        currentAudioStreamConfig = AudioStreamConfig()
        currentAudioSyncState = null
        audioFrameDecryptorSelector = null
        _mediaPlaybackState.value = MediaPlaybackState.Idle
        lastMediaImageData = null
        lastMediaAssetKey = null
        lastMediaTransition = null
        pairingManager.resetSession()
        mirroringSession.reset()
        _connectionState.value = ConnectionState.Error(error)
        resetAudioSessionConfigNative()
        audioInvalidFramesDropped = 0L
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
        audioSyncEventsReceived++
        currentAudioSyncState = AudioSyncState(
            rtpSync = rtpSync,
            remoteNtpUs = remoteNtpUs,
            localNtpUs = localNtpUs,
            initial = initial,
        )
        if (initial || audioSyncEventsReceived <= 3L || audioSyncEventsReceived % 25L == 0L) {
            Logger.i(
                Logger.TAG_PROTOCOL,
                "Audio sync#$audioSyncEventsReceived initial=$initial rtpSync=$rtpSync " +
                    "remoteNtpUs=$remoteNtpUs localNtpUs=$localNtpUs queuedAUs=$audioAccessUnitsQueued"
            )
        }
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

        val audioConfig = currentAudioStreamConfig
        if (!audioConfig.isSupportedAac) {
            if (audioPayloadsReceived == 0L) {
                Logger.w(
                    Logger.TAG_PROTOCOL,
                    "Dropping audio payloads: negotiated codec unsupported " +
                        "codec=${audioConfig.codecLabel} ct=${audioConfig.compressionType} " +
                        "fmt=0x${audioConfig.audioFormat.toString(16)}"
                )
            }
            audioPayloadsReceived++
            return
        }

        if (isAacEldNoDataPacket(audioConfig, data)) {
            audioNoDataPacketsDropped++
            if (audioNoDataPacketsDropped <= 3L) {
                Logger.d(
                    Logger.TAG_PROTOCOL,
                    "Skip ELD no-data #$audioNoDataPacketsDropped"
                )
            }
            return
        }

        val decryptorSelector = audioFrameDecryptorSelector
        if (decryptorSelector == null) {
            if (audioPayloadsReceived == 0L) {
                Logger.w(Logger.TAG_PROTOCOL, "Audio key=none")
            }
            audioPayloadsReceived++
            return
        }

        audioPayloadsReceived++
        if (currentAudioSyncState == null) {
            audioPayloadsBeforeSync++
            if (audioPayloadsBeforeSync <= 3L) {
                Logger.w(
                    Logger.TAG_PROTOCOL,
                    "Audio pre-sync pkt#=$audioPayloadsReceived size=${data.size}"
                )
            }
        }
        val decryptResult = decryptorSelector.decrypt(data) { frame ->
            audioAccessUnitExtractor.extract(audioConfig, frame).isNotEmpty()
        }
        val accessUnits = decryptResult.frame?.let { frame ->
            audioAccessUnitExtractor.extract(audioConfig, frame)
        }.orEmpty()
        if (decryptResult.lockAcquired && decryptResult.lockedLabel != null) {
            Logger.i(Logger.TAG_PROTOCOL, "Audio key=${decryptResult.lockedLabel}")
        }
        if (accessUnits.isEmpty()) {
            audioInvalidFramesDropped++
            if (
                decryptResult.failReason != null &&
                (audioInvalidFramesDropped <= 3L || audioInvalidFramesDropped % 25L == 0L)
            ) {
                Logger.w(
                    Logger.TAG_PROTOCOL,
                    "Audio fail reason=${decryptResult.failReason} count=$audioInvalidFramesDropped"
                )
            }
            return
        }

        if (audioPayloadsReceived <= 3L) {
            Logger.i(
                Logger.TAG_PROTOCOL,
                "Audio pkt#=$audioPayloadsReceived size=${data.size} au=${accessUnits.size} " +
                    "ts=$timestamp codec=${audioConfig.codecLabel}"
            )
        }

        accessUnits.forEachIndexed { index, accessUnit ->
            audioAccessUnitsQueued++
            val accessUnitTimestamp = timestamp + (index * audioConfig.samplesPerFrame.toLong())
            val presentationTimeUs = (accessUnitTimestamp * 1_000_000L) / AUDIO_RTP_SAMPLE_RATE
            if (audioAccessUnitsQueued <= 3L) {
                Logger.i(
                    Logger.TAG_PROTOCOL,
                    "Queue AU#=$audioAccessUnitsQueued size=${accessUnit.size} " +
                        "pts=$presentationTimeUs lock=${currentAudioSyncState != null}"
                )
            }
            audioDecoder?.queueFrame(
                data = accessUnit,
                rtpTimestamp = accessUnitTimestamp,
                presentationTimeUs = presentationTimeUs,
                clockLocked = currentAudioSyncState != null,
            )
        }
    }

    private fun isAacEldNoDataPacket(config: AudioStreamConfig, payload: ByteArray): Boolean {
        return config.compressionType == 8 &&
            payload.size == AAC_ELD_NO_DATA_MARKER.size &&
            payload.contentEquals(AAC_ELD_NO_DATA_MARKER)
    }

}
