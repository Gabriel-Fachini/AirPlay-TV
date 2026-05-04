package com.airplay.tv.service

import com.airplay.tv.media.AudioDecoder
import com.airplay.tv.protocol.ProtocolHandler
import com.airplay.tv.util.Logger

internal class AudioPipelineConfigurator(
    private val audioDecoder: AudioDecoder
) {
    fun startAudioPipeline(sessionInfo: ProtocolHandler.SessionInfo): Boolean {
        val audioStreamConfig = sessionInfo.audioStreamConfig
        if (!audioStreamConfig.isSupportedAac) {
            Logger.w(
                Logger.TAG_SERVICE,
                "Audio codec unsupported for current session: ${audioStreamConfig.codecLabel}. Video will continue without audio."
            )
            return false
        }

        val aacConfig = audioStreamConfig.buildCodecSpecificData()
        if (aacConfig == null) {
            Logger.e(
                Logger.TAG_SERVICE,
                "Negotiated AAC codec ${audioStreamConfig.codecLabel} but codec specific data was unavailable"
            )
            return false
        }

        val audioSuccess = audioDecoder.configure(
            sampleRate = sessionInfo.audioSampleRate,
            channels = sessionInfo.audioChannels,
            aacConfig = aacConfig,
        )
        if (!audioSuccess) {
            Logger.e(Logger.TAG_SERVICE, "Failed to configure audio decoder")
            return false
        }

        return try {
            audioDecoder.start()
            true
        } catch (e: Exception) {
            Logger.e(Logger.TAG_SERVICE, "Failed to start audio decoder", e)
            try {
                audioDecoder.stop()
            } catch (cleanupError: Exception) {
                Logger.e(Logger.TAG_SERVICE, "Error stopping audio decoder during cleanup", cleanupError)
            }
            false
        }
    }
}
