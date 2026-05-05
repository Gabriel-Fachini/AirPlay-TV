package com.airplay.tv.protocol

import java.nio.ByteBuffer

data class AudioStreamConfig(
    val compressionType: Int = 0,
    val samplesPerFrame: Int = 0,
    val audioFormat: Long = 0L,
    val sampleRate: Int = 44_100,
    val channels: Int = 2,
    val remoteControlPort: Int = 0,
    val remoteTimingPort: Int = 0,
    val localDataPort: Int = 7100,
    val localControlPort: Int = 6001,
    val localTimingPort: Int = 7002,
    val isMedia: Boolean = false,
    val usingScreen: Boolean = false,
) {
    val codecLabel: String
        get() = when (compressionType) {
            8 -> "AAC-ELD"
            4 -> "AAC-LC"
            2 -> "ALAC"
            0 -> "unset"
            else -> "ct=$compressionType"
        }

    val isSupportedAac: Boolean
        get() = compressionType == 8 || compressionType == 4

    fun buildCodecSpecificData(): ByteBuffer? {
        val configBytes = when (compressionType) {
            8 -> byteArrayOf(0xF8.toByte(), 0xE8.toByte(), 0x50.toByte(), 0x00.toByte())
            4 -> byteArrayOf(0x12.toByte(), 0x10.toByte())
            else -> null
        } ?: return null

        return ByteBuffer.allocateDirect(configBytes.size).apply {
            put(configBytes)
            flip()
        }
    }
}
