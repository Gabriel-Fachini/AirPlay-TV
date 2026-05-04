package com.airplay.tv.media

import java.nio.ByteBuffer

internal val ANNEX_B_START_CODE = byteArrayOf(0x00, 0x00, 0x00, 0x01)

internal fun buildCodecSpecificDataBuffer(nalUnit: ByteBuffer): ByteBuffer {
    val source = nalUnit.duplicate()
    val bytes = ByteArray(source.remaining())
    source.get(bytes)

    val outputBytes = if (hasAnnexBStartCode(bytes, 0)) {
        bytes
    } else {
        ANNEX_B_START_CODE + bytes
    }

    return ByteBuffer.allocateDirect(outputBytes.size).apply {
        put(outputBytes)
        flip()
    }
}

internal fun isValidAnnexBFrame(data: ByteArray): Boolean {
    if (data.size < 5) {
        return false
    }

    var index = 0
    while (index + ANNEX_B_START_CODE.size < data.size) {
        if (hasAnnexBStartCode(data, index)) {
            val nalHeaderIndex = index + ANNEX_B_START_CODE.size
            if (nalHeaderIndex < data.size) {
                val nalType = data[nalHeaderIndex].toInt() and 0x1F
                if (nalType in 1..12) {
                    return true
                }
            }
        }
        index++
    }

    return false
}

internal fun containsIdrNalUnit(data: ByteArray): Boolean {
    var index = 0
    while (index + ANNEX_B_START_CODE.size < data.size) {
        if (hasAnnexBStartCode(data, index)) {
            val nalHeaderIndex = index + ANNEX_B_START_CODE.size
            if (nalHeaderIndex < data.size) {
                val nalType = data[nalHeaderIndex].toInt() and 0x1F
                if (nalType == 5) {
                    return true
                }
            }
            index = nalHeaderIndex
        } else {
            index++
        }
    }

    return false
}

private fun hasAnnexBStartCode(data: ByteArray, offset: Int): Boolean {
    return offset + ANNEX_B_START_CODE.size <= data.size &&
        data[offset] == 0.toByte() &&
        data[offset + 1] == 0.toByte() &&
        data[offset + 2] == 0.toByte() &&
        data[offset + 3] == 1.toByte()
}
