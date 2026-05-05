package com.airplay.tv.media

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import com.airplay.tv.util.Logger

class AudioTrackManager {
    private var audioTrack: AudioTrack? = null
    private var firstWriteLogged = false

    fun configure(sampleRate: Int, channels: Int): Boolean {
        try {
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
            firstWriteLogged = false
            return true
        } catch (e: Exception) {
            Logger.e(Logger.TAG_AUDIO, "Failed to configure AudioTrack", e)
            return false
        }
    }

    fun play() {
        audioTrack?.play()
    }

    fun stopAndRelease() {
        try {
            try {
                audioTrack?.stop()
            } catch (e: IllegalStateException) {
                Logger.w(Logger.TAG_AUDIO, "AudioTrack already stopped or in invalid state", e)
            }
            audioTrack?.release()
            audioTrack = null
        } catch (e: Exception) {
            Logger.e(Logger.TAG_AUDIO, "Error stopping AudioTrack", e)
        }
    }

    fun write(audioData: ByteArray, offsetInBytes: Int, sizeInBytes: Int): Int {
        val track = audioTrack ?: return -1
        var totalWritten = 0
        while (totalWritten < sizeInBytes) {
            val written = track.write(audioData, offsetInBytes + totalWritten, sizeInBytes - totalWritten)
            if (written <= 0) {
                return if (totalWritten > 0) totalWritten else written
            }
            totalWritten += written
        }
        if (!firstWriteLogged) {
            Logger.i(Logger.TAG_AUDIO, "AudioTrack first write bytes=$totalWritten")
            firstWriteLogged = true
        }
        return totalWritten
    }

    fun setPlaybackRate(rate: Float) {
        try {
            val track = audioTrack ?: return
            val params = track.playbackParams
            track.playbackParams = params.setSpeed(rate)
            Logger.d(Logger.TAG_AUDIO, "Playback rate adjusted to $rate")
        } catch (e: Exception) {
            Logger.e(Logger.TAG_AUDIO, "Failed to set playback rate", e)
        }
    }
}
