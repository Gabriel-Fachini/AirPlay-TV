package com.airplay.tv.protocol

internal class AudioAccessUnitExtractor {

    fun extract(config: AudioStreamConfig, payload: ByteArray): List<ByteArray> {
        if (payload.isEmpty()) {
            return emptyList()
        }

        val unitsFromHeaders = extractFromAuHeaders(payload)
        if (unitsFromHeaders != null) {
            return unitsFromHeaders.filter { it.isNotEmpty() && isValidAudioFrame(config, it) }
        }

        return if (isValidAudioFrame(config, payload)) {
            listOf(payload)
        } else {
            emptyList()
        }
    }

    private fun extractFromAuHeaders(payload: ByteArray): List<ByteArray>? {
        if (payload.size < MIN_AU_HEADER_PAYLOAD_SIZE) {
            return null
        }

        val headersLengthBits = readUInt16(payload, 0)
        if (headersLengthBits <= 0 || headersLengthBits % AU_HEADER_BITS != 0) {
            return null
        }

        val headerSectionBytes = AU_HEADERS_LENGTH_FIELD_BYTES + ((headersLengthBits + 7) / 8)
        if (headerSectionBytes >= payload.size) {
            return null
        }

        val accessUnitCount = headersLengthBits / AU_HEADER_BITS
        val sizes = ArrayList<Int>(accessUnitCount)
        for (index in 0 until accessUnitCount) {
            val bitOffset = AU_HEADERS_LENGTH_FIELD_BITS + (index * AU_HEADER_BITS)
            val size = readBits(payload, bitOffset, AU_SIZE_BITS)
            if (size <= 0) {
                return null
            }
            sizes += size
        }

        var dataOffset = headerSectionBytes
        val units = ArrayList<ByteArray>(sizes.size)
        for (size in sizes) {
            val endOffset = dataOffset + size
            if (endOffset > payload.size) {
                return null
            }
            units += payload.copyOfRange(dataOffset, endOffset)
            dataOffset = endOffset
        }

        return units
    }

    private fun readUInt16(payload: ByteArray, offset: Int): Int {
        return ((payload[offset].toInt() and 0xFF) shl 8) or
            (payload[offset + 1].toInt() and 0xFF)
    }

    private fun readBits(payload: ByteArray, bitOffset: Int, bitLength: Int): Int {
        var value = 0
        for (index in 0 until bitLength) {
            val absoluteBit = bitOffset + index
            val byteIndex = absoluteBit / 8
            val bitIndex = 7 - (absoluteBit % 8)
            val bit = (payload[byteIndex].toInt() shr bitIndex) and 0x01
            value = (value shl 1) or bit
        }
        return value
    }

    private fun isValidAudioFrame(config: AudioStreamConfig, frame: ByteArray): Boolean {
        val firstByte = frame.firstOrNull()?.toInt()?.and(0xFF) ?: return false
        return when (config.compressionType) {
            8 -> firstByte == 0x8c || firstByte == 0x8d || firstByte == 0x8e ||
                firstByte == 0x80 || firstByte == 0x81 || firstByte == 0x82
            4 -> firstByte == 0xFF
            else -> true
        }
    }

    private companion object {
        const val AU_HEADERS_LENGTH_FIELD_BITS = 16
        const val AU_HEADERS_LENGTH_FIELD_BYTES = 2
        const val AU_HEADER_BITS = 16
        const val AU_SIZE_BITS = 13
        const val MIN_AU_HEADER_PAYLOAD_SIZE = 4
    }
}
