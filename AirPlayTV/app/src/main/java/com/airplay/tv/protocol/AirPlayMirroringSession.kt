package com.airplay.tv.protocol

import com.airplay.tv.media.containsIdrNalUnit
import com.airplay.tv.media.VideoDecoder
import com.airplay.tv.util.Constants
import com.airplay.tv.util.Logger
import com.dd.plist.BinaryPropertyListParser
import com.dd.plist.BinaryPropertyListWriter
import com.dd.plist.NSArray
import com.dd.plist.NSData
import com.dd.plist.NSDictionary
import com.dd.plist.NSNumber
import java.nio.ByteBuffer
import java.security.MessageDigest

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
    private var videoMasterKey: ByteArray? = null
    private var audioAesIv: ByteArray? = null
    private var decryptor: VideoStreamDecryptor? = null
    private var cachedCodecPrefix: ByteArray? = null
    private var shouldPrependCodecPrefix = false
    private var mirroredVideoFramesQueued = 0

    fun reset() {
        videoMasterKey = null
        audioAesIv = null
        decryptor = null
        cachedCodecPrefix = null
        shouldPrependCodecPrefix = false
        mirroredVideoFramesQueued = 0
        onAudioCryptoConfigured(null)
    }

    fun buildSetupResponse(requestBody: ByteArray): ByteArray? {
        return try {
            val root = BinaryPropertyListParser.parse(requestBody)
            if (root !is NSDictionary) {
                Logger.w(Logger.TAG_PROTOCOL, "Ignoring SETUP body with unexpected root type: ${root.javaClass.simpleName}")
                null
            } else {
                when {
                    root.objectForKey("ekey") is NSData -> buildInitialSetupResponse(root)
                    root.objectForKey("streams") is NSArray -> buildStreamSetupResponse(root)
                    else -> {
                        Logger.w(Logger.TAG_PROTOCOL, "SETUP body did not include FairPlay material or streams")
                        null
                    }
                }
            }
        } catch (e: Exception) {
            Logger.e(Logger.TAG_PROTOCOL, "Failed to parse SETUP plist", e)
            null
        }
    }

    fun handleVideoPacket(payloadType: Int, payload: ByteArray) {
        val timestamp90Khz = ((System.nanoTime() / 1_000L) * 90L) / 1_000_000L
        when (payloadType) {
            0 -> {
                // Type 0: Video bitstream (can be AES encrypted)
                // Try decryption FIRST (Mac usually sends encrypted data)
                val currentDecryptor = decryptor
                val frame = if (currentDecryptor != null) {
                    try {
                        currentDecryptor.decodeVideoPayload(payload)
                    } catch (e: Exception) {
                        Logger.w(Logger.TAG_PROTOCOL, "Decryption failed, trying unencrypted: ${e.message}")
                        parseMirroringVideoPayload(payload)
                    }
                } else {
                    parseMirroringVideoPayload(payload)
                }
                
                if (frame != null && frame.isNotEmpty()) {
                    queueMirroringFrame(frame, timestamp90Khz)
                }
            }

            1 -> {
                // Type 1: Codec config (SPS/PPS) - NOT encrypted
                // Extract SPS and PPS and configure decoder
                val codecData = parseAirPlayCodecConfig(payload)
                if (codecData != null) {
                    val sps = codecData.sps
                    val pps = codecData.pps
                    val width = codecData.width
                    val height = codecData.height
                    cachedCodecPrefix = codecData.buildAnnexBPrefix()
                    shouldPrependCodecPrefix = cachedCodecPrefix != null
                    Logger.i(Logger.TAG_PROTOCOL, "Received codec config: ${width}x${height}, SPS=${sps.remaining()}B, PPS=${pps.remaining()}B")
                    
                    // Notify service to reconfigure decoder with SPS/PPS
                    onCodecConfigReceived(sps, pps, width, height)
                } else {
                    Logger.w(Logger.TAG_PROTOCOL, "Failed to extract codec config from type 1 packet")
                }
            }
            
            2 -> {
                // Type 2: Heartbeat (sent every second, no payload)
                // Just acknowledge it to keep connection alive
                Logger.d(Logger.TAG_PROTOCOL, "Received heartbeat packet")
            }

            5 -> {
                // Try to extract codec config from type 5 if it looks like avcC format
                if (payload.size > 11 && payload[0].toInt() == 1) {
                    val codecConfig = parseAirPlayCodecConfig(payload)?.buildAnnexBPrefix()
                    if (codecConfig != null) {
                        videoDecoder?.queueFrame(
                            data = codecConfig,
                            timestamp = timestamp90Khz,
                            isKeyFrame = true,
                        )
                        Logger.i(Logger.TAG_PROTOCOL, "Extracted codec config from type 5 (${codecConfig.size} bytes)")
                    }
                }
            }

            else -> {
            }
        }
    }

    private fun buildInitialSetupResponse(root: NSDictionary): ByteArray? {
        val encryptedKey = (root.objectForKey("ekey") as? NSData)?.bytes()
        val encryptedIv = (root.objectForKey("eiv") as? NSData)?.bytes()
        if (encryptedKey == null || encryptedKey.size != ENCRYPTED_AES_KEY_SIZE) {
            Logger.e(Logger.TAG_PROTOCOL, "SETUP 1 missing valid ekey")
            return null
        }
        if (encryptedIv == null || encryptedIv.size != AES_BLOCK_SIZE) {
            Logger.e(Logger.TAG_PROTOCOL, "SETUP 1 missing valid eiv")
            return null
        }

        videoMasterKey = decryptFairPlayAesKey(encryptedKey)
        if (videoMasterKey == null) {
            Logger.e(Logger.TAG_PROTOCOL, "Failed to decrypt FairPlay AES key from SETUP 1")
            return null
        }
        audioAesIv = encryptedIv.copyOf()

        val sharedSecret = pairingManager.getSharedSecret()
        onAudioCryptoConfigured(
            AudioCryptoConfig(
                plainAesKey = videoMasterKey!!.copyOf(AES_BLOCK_SIZE),
                hashedAesKey = sharedSecret?.let {
                    MessageDigest.getInstance("SHA-256")
                        .digest(videoMasterKey!!.copyOf(AES_BLOCK_SIZE) + it)
                        .copyOf(AES_BLOCK_SIZE)
                },
                aesIv = encryptedIv.copyOf(),
                preferHashed = sharedSecret != null,
            )
        )

        val response = NSDictionary().apply {
            put("eventPort", NSNumber(Constants.RTSP_PORT.toLong()))
            put("timingPort", NSNumber(TIMING_PORT.toLong()))
        }

        Logger.i(Logger.TAG_PROTOCOL, "SETUP 1 ok")
        return BinaryPropertyListWriter.writeToArray(response)
    }

    private fun buildStreamSetupResponse(root: NSDictionary): ByteArray? {
        val streams = root.objectForKey("streams") as? NSArray ?: return null
        if (streams.count() == 0) {
            Logger.e(Logger.TAG_PROTOCOL, "SETUP stream body arrived with empty streams array")
            return null
        }

        val responseStreams = NSMutablePlistArray()
        for (index in 0 until streams.count()) {
            val stream = streams.objectAtIndex(index) as? NSDictionary ?: continue
            val type = (stream.objectForKey("type") as? NSNumber)?.intValue() ?: continue
            when (type) {
                MIRROR_STREAM_TYPE -> {
                    val streamConnectionId = (stream.objectForKey("streamConnectionID") as? NSNumber)?.longValue()
                    val masterKey = videoMasterKey
                    val sharedSecret = pairingManager.getSharedSecret()
                    if (streamConnectionId == null || masterKey == null || sharedSecret == null) {
                        Logger.e(Logger.TAG_PROTOCOL, "SETUP 2 missing streamConnectionID or crypto material")
                        return null
                    }

                    val port = startMirrorVideoServer()
                    if (port <= 0) {
                        Logger.e(Logger.TAG_PROTOCOL, "Failed to start mirror TCP listener")
                        return null
                    }

                    decryptor = VideoStreamDecryptor(
                        streamConnectionId = streamConnectionId,
                        masterKey = masterKey,
                        sharedSecret = sharedSecret,
                    )
                    val streamConnectionIdText = formatAirPlayStreamConnectionId(streamConnectionId)

                    responseStreams.add(
                        NSDictionary().apply {
                            put("dataPort", NSNumber(port.toLong()))
                            put("type", NSNumber(MIRROR_STREAM_TYPE.toLong()))
                        }
                    )

                    Logger.i(
                        Logger.TAG_PROTOCOL,
                        "SETUP 2 accepted: type=110 streamConnectionID=$streamConnectionIdText port=$port"
                    )
                }

                AUDIO_STREAM_TYPE -> {
                    val audioConfig = AudioStreamConfig(
                        compressionType = (stream.objectForKey("ct") as? NSNumber)?.intValue() ?: 0,
                        samplesPerFrame = (stream.objectForKey("spf") as? NSNumber)?.intValue() ?: 0,
                        audioFormat = (stream.objectForKey("audioFormat") as? NSNumber)?.longValue() ?: 0L,
                        sampleRate = AUDIO_SAMPLE_RATE,
                        channels = DEFAULT_AUDIO_CHANNELS,
                        remoteControlPort = (stream.objectForKey("controlPort") as? NSNumber)?.intValue() ?: 0,
                        localDataPort = LEGACY_AUDIO_DATA_PORT,
                        localControlPort = LEGACY_AUDIO_CONTROL_PORT,
                        localTimingPort = TIMING_PORT,
                        isMedia = (stream.objectForKey("isMedia") as? NSNumber)?.boolValue() ?: false,
                        usingScreen = (stream.objectForKey("usingScreen") as? NSNumber)?.boolValue() ?: false,
                    )
                    onAudioStreamConfigured(audioConfig)
                    responseStreams.add(
                        NSDictionary().apply {
                            put("dataPort", NSNumber(LEGACY_AUDIO_DATA_PORT.toLong()))
                            put("controlPort", NSNumber(LEGACY_AUDIO_CONTROL_PORT.toLong()))
                            put("type", NSNumber(AUDIO_STREAM_TYPE.toLong()))
                        }
                    )
                    Logger.i(
                        Logger.TAG_PROTOCOL,
                        "SETUP audio codec=${audioConfig.codecLabel} ct=${audioConfig.compressionType} " +
                            "spf=${audioConfig.samplesPerFrame} fmt=0x${audioConfig.audioFormat.toString(16)} " +
                            "rCtrl=${audioConfig.remoteControlPort} lData=${audioConfig.localDataPort} " +
                            "lCtrl=${audioConfig.localControlPort} ok=${audioConfig.isSupportedAac}"
                    )
                }

                else -> {
                    Logger.w(Logger.TAG_PROTOCOL, "Ignoring unsupported stream type=$type in SETUP")
                }
            }
        }

        if (responseStreams.isEmpty()) {
            return null
        }

        return BinaryPropertyListWriter.writeToArray(
            NSDictionary().apply {
                put("streams", responseStreams.toNSArray())
            }
        )
    }

    private fun queueMirroringFrame(frame: ByteArray, timestamp90Khz: Long) {
        val isKeyFrame = containsIdrNalUnit(frame)
        val frameToQueue = maybePrependCodecPrefix(frame, isKeyFrame)
        mirroredVideoFramesQueued++
        if (mirroredVideoFramesQueued <= 3) {
            Logger.i(
                Logger.TAG_PROTOCOL,
                "Queueing mirrored frame #$mirroredVideoFramesQueued (${frameToQueue.size} bytes, keyFrame=${containsIdrNalUnit(frameToQueue)})"
            )
        }
        videoDecoder?.queueFrame(
            data = frameToQueue,
            timestamp = timestamp90Khz,
            isKeyFrame = containsIdrNalUnit(frameToQueue),
        )
    }

    private fun maybePrependCodecPrefix(frame: ByteArray, isKeyFrame: Boolean): ByteArray {
        val codecPrefix = cachedCodecPrefix
        if (!shouldPrependCodecPrefix || codecPrefix == null) {
            return frame
        }

        val prefixedFrame = ByteArray(codecPrefix.size + frame.size)
        codecPrefix.copyInto(prefixedFrame, 0)
        frame.copyInto(prefixedFrame, codecPrefix.size)

        if (isKeyFrame && videoDecoder?.state?.value == com.airplay.tv.media.DecoderState.Running) {
            shouldPrependCodecPrefix = false
        }

        return prefixedFrame
    }

    private class NSMutablePlistArray {
        private val items = mutableListOf<NSDictionary>()

        fun add(item: NSDictionary) {
            items += item
        }

        fun isEmpty(): Boolean = items.isEmpty()

        fun toNSArray(): NSArray = NSArray(*items.toTypedArray())
    }

    private companion object {
        const val AES_BLOCK_SIZE = 16
        const val ENCRYPTED_AES_KEY_SIZE = 72
        const val MIRROR_STREAM_TYPE = 110
        const val AUDIO_STREAM_TYPE = 96
        const val AUDIO_SAMPLE_RATE = 44_100
        const val DEFAULT_AUDIO_CHANNELS = 2
        const val TIMING_PORT = 7002
        const val LEGACY_AUDIO_DATA_PORT = 7100
        const val LEGACY_AUDIO_CONTROL_PORT = 6001
    }
}

data class AudioCryptoConfig(
    val plainAesKey: ByteArray,
    val hashedAesKey: ByteArray?,
    val aesIv: ByteArray,
    val preferHashed: Boolean,
)
