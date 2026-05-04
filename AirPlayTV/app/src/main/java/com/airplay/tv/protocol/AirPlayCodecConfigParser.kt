package com.airplay.tv.protocol

import com.airplay.tv.media.ANNEX_B_START_CODE
import com.airplay.tv.util.Logger
import java.nio.ByteBuffer

internal data class AirPlayCodecConfigPayload(
    val sps: ByteBuffer,
    val pps: ByteBuffer,
    val width: Int = 1920,
    val height: Int = 1080,
) {
    fun buildAnnexBPrefix(): ByteArray {
        val spsBytes = sps.toByteArray()
        val ppsBytes = pps.toByteArray()
        val output = ByteArray(ANNEX_B_START_CODE.size * 2 + spsBytes.size + ppsBytes.size)
        var offset = 0
        ANNEX_B_START_CODE.copyInto(output, offset)
        offset += ANNEX_B_START_CODE.size
        spsBytes.copyInto(output, offset)
        offset += spsBytes.size
        ANNEX_B_START_CODE.copyInto(output, offset)
        offset += ANNEX_B_START_CODE.size
        ppsBytes.copyInto(output, offset)
        return output
    }
}

internal fun parseAirPlayCodecConfig(payload: ByteArray): AirPlayCodecConfigPayload? {
    if (payload.size < 11) {
        Logger.w(Logger.TAG_PROTOCOL, "Codec config payload too small: ${payload.size} bytes")
        return null
    }

    val spsSize = readUInt16(payload, 6)
    val spsStart = 8
    val spsEnd = spsStart + spsSize
    if (spsEnd >= payload.size) {
        Logger.w(Logger.TAG_PROTOCOL, "Invalid SPS size in codec config: $spsSize")
        return null
    }

    val ppsCountIndex = spsEnd
    if (ppsCountIndex + 2 >= payload.size) {
        Logger.w(Logger.TAG_PROTOCOL, "Codec config missing PPS section")
        return null
    }

    val ppsSize = readUInt16(payload, ppsCountIndex + 1)
    val ppsStart = ppsCountIndex + 3
    val ppsEnd = ppsStart + ppsSize
    if (ppsEnd > payload.size) {
        Logger.w(Logger.TAG_PROTOCOL, "Invalid PPS size in codec config: $ppsSize")
        return null
    }

    return AirPlayCodecConfigPayload(
        sps = ByteBuffer.allocateDirect(spsSize).apply {
            put(payload, spsStart, spsSize)
            flip()
        },
        pps = ByteBuffer.allocateDirect(ppsSize).apply {
            put(payload, ppsStart, ppsSize)
            flip()
        },
    )
}

private fun ByteBuffer.toByteArray(): ByteArray {
    val duplicate = duplicate()
    val bytes = ByteArray(duplicate.remaining())
    duplicate.get(bytes)
    return bytes
}

private fun readUInt16(buffer: ByteArray, offset: Int): Int {
    return ((buffer[offset].toInt() and 0xFF) shl 8) or (buffer[offset + 1].toInt() and 0xFF)
}
