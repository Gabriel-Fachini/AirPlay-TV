package com.airplay.tv.protocol

import com.airplay.tv.media.containsIdrNalUnit
import com.airplay.tv.media.VideoDecoder
import com.airplay.tv.util.Constants
import com.airplay.tv.util.Logger
import java.nio.ByteBuffer

internal fun formatAirPlayStreamConnectionId(value: Long): String = java.lang.Long.toUnsignedString(value)

/**
 * Trata o fluxo legado de AirPlay mirroring:
 * 1. SETUP inicial com chaves/timing
 * 2. SETUP do stream type=110 para vídeo
 * 3. Pacotes TCP com SPS/PPS + NAL units criptografadas em AES-CTR
 */
class AirPlayMirroringSession(
    private val videoDecoder: VideoDecoder?,
    private val pairingManager: AirPlayPairingManager,
    private val decryptFairPlayAesKey: (ByteArray) -> ByteArray?,
    private val startMirrorVideoServer: () -> Int,
    private val onCodecConfigReceived: (sps: ByteBuffer, pps: ByteBuffer, width: Int, height: Int) -> Unit,
    private val onAudioCryptoConfigured: (AudioCryptoConfig?) -> Unit,
    private val onAudioStreamConfigured: (AudioStreamConfig) -> Unit,
) {
    private val packetHandler = MirroringPacketHandler(
        videoDecoder = videoDecoder,
        onCodecConfigReceived = onCodecConfigReceived
    )

    private val setupBuilder = MirroringSetupBuilder(
        pairingManager = pairingManager,
        decryptFairPlayAesKey = decryptFairPlayAesKey,
        startMirrorVideoServer = startMirrorVideoServer,
        onAudioCryptoConfigured = onAudioCryptoConfigured,
        onAudioStreamConfigured = onAudioStreamConfigured,
        onVideoStreamConfigured = { decryptor ->
            packetHandler.decryptor = decryptor
        }
    )

    fun reset() {
        setupBuilder.reset()
        packetHandler.reset()
        onAudioCryptoConfigured(null)
    }

    fun buildSetupResponse(requestBody: ByteArray): ByteArray? {
        return setupBuilder.buildSetupResponse(requestBody)
    }

    fun handleVideoPacket(payloadType: Int, payload: ByteArray) {
        packetHandler.handleVideoPacket(payloadType, payload)
    }
}

data class AudioCryptoConfig(
    val plainAesKey: ByteArray,
    val hashedAesKey: ByteArray?,
    val aesIv: ByteArray,
    val preferHashed: Boolean,
)
