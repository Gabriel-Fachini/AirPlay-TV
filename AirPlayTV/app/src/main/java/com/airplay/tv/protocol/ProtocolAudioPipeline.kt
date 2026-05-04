package com.airplay.tv.protocol

import com.airplay.tv.media.AudioDecoder
import com.airplay.tv.util.Logger

internal class ProtocolAudioPipeline(
    private val audioDecoder: AudioDecoder?,
    private val audioAccessUnitExtractor: AudioAccessUnitExtractor = AudioAccessUnitExtractor(),
) {
    data class AudioSyncState(
        val rtpSync: Long,
        val remoteNtpUs: Long,
        val localNtpUs: Long,
        val initial: Boolean,
    )

    var currentStreamConfig = AudioStreamConfig()
        private set

    var currentSyncState: AudioSyncState? = null
        private set

    private var frameDecryptorSelector: AudioFrameDecryptorSelector? = null
    private var audioPayloadsReceived = 0L
    private var audioAccessUnitsQueued = 0L
    private var audioNoDataPacketsDropped = 0L
    private var audioSyncEventsReceived = 0L
    private var audioPayloadsBeforeSync = 0L
    private var audioInvalidFramesDropped = 0L

    fun reset() {
        currentStreamConfig = AudioStreamConfig()
        currentSyncState = null
        frameDecryptorSelector = null
        audioPayloadsReceived = 0L
        audioAccessUnitsQueued = 0L
        audioNoDataPacketsDropped = 0L
        audioSyncEventsReceived = 0L
        audioPayloadsBeforeSync = 0L
        audioInvalidFramesDropped = 0L
    }

    fun configureCrypto(config: AudioCryptoConfig?) {
        frameDecryptorSelector = config?.let { AudioFrameDecryptorSelector(it) }
    }

    fun configureStream(config: AudioStreamConfig) {
        currentStreamConfig = config
    }

    fun handleSync(rtpSync: Long, remoteNtpUs: Long, localNtpUs: Long, initial: Boolean) {
        audioSyncEventsReceived++
        currentSyncState = AudioSyncState(rtpSync, remoteNtpUs, localNtpUs, initial)
        if (initial || audioSyncEventsReceived <= 3L || audioSyncEventsReceived % 25L == 0L) {
            Logger.i(
                Logger.TAG_PROTOCOL,
                "Audio sync#$audioSyncEventsReceived initial=$initial rtpSync=$rtpSync " +
                    "remoteNtpUs=$remoteNtpUs localNtpUs=$localNtpUs queuedAUs=$audioAccessUnitsQueued"
            )
        }
    }

    fun handlePayload(data: ByteArray, timestamp: Long) {
        val audioConfig = currentStreamConfig
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
                Logger.d(Logger.TAG_PROTOCOL, "Skip ELD no-data #$audioNoDataPacketsDropped")
            }
            return
        }

        val decryptorSelector = frameDecryptorSelector
        if (decryptorSelector == null) {
            if (audioPayloadsReceived == 0L) {
                Logger.w(Logger.TAG_PROTOCOL, "Audio key=none")
            }
            audioPayloadsReceived++
            return
        }

        audioPayloadsReceived++
        if (currentSyncState == null) {
            audioPayloadsBeforeSync++
            if (audioPayloadsBeforeSync <= 3L) {
                Logger.w(Logger.TAG_PROTOCOL, "Audio pre-sync pkt#=$audioPayloadsReceived size=${data.size}")
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
                        "pts=$presentationTimeUs lock=${currentSyncState != null}"
                )
            }
            audioDecoder?.queueFrame(
                data = accessUnit,
                rtpTimestamp = accessUnitTimestamp,
                presentationTimeUs = presentationTimeUs,
                clockLocked = currentSyncState != null,
            )
        }
    }

    private fun isAacEldNoDataPacket(config: AudioStreamConfig, payload: ByteArray): Boolean {
        return config.compressionType == 8 &&
            payload.size == AAC_ELD_NO_DATA_MARKER.size &&
            payload.contentEquals(AAC_ELD_NO_DATA_MARKER)
    }

    private companion object {
        const val AUDIO_RTP_SAMPLE_RATE = 44_100L
        val AAC_ELD_NO_DATA_MARKER = byteArrayOf(0x00, 0x68, 0x34, 0x00)
    }
}
