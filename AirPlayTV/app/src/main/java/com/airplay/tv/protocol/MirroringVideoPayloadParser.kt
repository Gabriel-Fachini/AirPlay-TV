package com.airplay.tv.protocol

import com.airplay.tv.media.ANNEX_B_START_CODE
import com.airplay.tv.util.Logger
import java.security.MessageDigest
import javax.crypto.Cipher
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec

internal fun parseMirroringVideoPayload(payload: ByteArray): ByteArray? {
    var inputOffset = 0
    var outputOffset = 0
    val output = ByteArray(payload.size * 2)
    var nalCount = 0

    while (inputOffset + 4 <= payload.size) {
        val nalSize = readInt32(payload, inputOffset)
        if (nalSize <= 0 || inputOffset + 4 + nalSize > payload.size) {
            if (nalCount == 0) {
                Logger.w(Logger.TAG_PROTOCOL, "Invalid NAL size in mirrored payload: $nalSize")
            }
            break
        }

        ANNEX_B_START_CODE.copyInto(output, outputOffset)
        outputOffset += ANNEX_B_START_CODE.size
        payload.copyInto(output, outputOffset, inputOffset + 4, inputOffset + 4 + nalSize)
        outputOffset += nalSize
        inputOffset += 4 + nalSize
        nalCount++
    }

    return if (nalCount > 0) output.copyOf(outputOffset) else null
}

internal class VideoStreamDecryptor(
    streamConnectionId: Long,
    masterKey: ByteArray,
    sharedSecret: ByteArray,
) {
    private val cipher: Cipher
    private var pendingKeystream: ByteArray? = null
    private var pendingOffset = 0

    init {
        val eaesKey = ByteArray(64)
        val tempKey = ByteArray(16)
        masterKey.copyInto(tempKey, 0, 0, minOf(16, masterKey.size))
        val eaesKeyHash = sha512(tempKey + sharedSecret)
        eaesKeyHash.copyInto(eaesKey, 0, 0, minOf(64, eaesKeyHash.size))

        val streamConnectionIdText = formatAirPlayStreamConnectionId(streamConnectionId)
        val videoKey = sha512("AirPlayStreamKey$streamConnectionIdText".toByteArray() + eaesKey.copyOf(16))
            .copyOf(AES_BLOCK_SIZE)
        val videoIv = sha512("AirPlayStreamIV$streamConnectionIdText".toByteArray() + eaesKey.copyOf(16))
            .copyOf(AES_BLOCK_SIZE)

        cipher = Cipher.getInstance("AES/CTR/NoPadding").apply {
            init(Cipher.ENCRYPT_MODE, SecretKeySpec(videoKey, "AES"), IvParameterSpec(videoIv))
        }
    }

    fun decodeVideoPayload(payload: ByteArray): ByteArray {
        return parseMirroringVideoPayload(decrypt(payload)) ?: ByteArray(0)
    }

    private fun decrypt(input: ByteArray): ByteArray {
        val output = ByteArray(input.size)
        var inputOffset = 0
        var outputOffset = 0

        val pending = pendingKeystream
        if (pending != null && pendingOffset > 0) {
            val pendingStart = AES_BLOCK_SIZE - pendingOffset
            while (pendingOffset < AES_BLOCK_SIZE && inputOffset < input.size) {
                output[outputOffset++] =
                    (input[inputOffset++].toInt() xor pending[pendingStart + (pendingOffset - pendingStart)].toInt()).toByte()
                pendingOffset++
            }
            if (pendingOffset >= AES_BLOCK_SIZE) {
                pendingKeystream = null
                pendingOffset = 0
            }
        }

        val remaining = input.size - inputOffset
        val alignedLength = (remaining / AES_BLOCK_SIZE) * AES_BLOCK_SIZE
        if (alignedLength > 0) {
            val decryptedChunk = cipher.update(input, inputOffset, alignedLength)
            if (decryptedChunk != null) {
                decryptedChunk.copyInto(output, outputOffset)
                outputOffset += decryptedChunk.size
            }
            inputOffset += alignedLength
        }

        val tailLength = input.size - inputOffset
        if (tailLength > 0) {
            val paddedTail = ByteArray(AES_BLOCK_SIZE)
            input.copyInto(paddedTail, 0, inputOffset, input.size)
            val decryptedTail = cipher.update(paddedTail) ?: ByteArray(AES_BLOCK_SIZE)
            decryptedTail.copyInto(output, outputOffset, 0, tailLength)
            pendingKeystream = decryptedTail
            pendingOffset = tailLength
        }

        return output
    }

    private fun sha512(data: ByteArray): ByteArray {
        return MessageDigest.getInstance("SHA-512").digest(data)
    }

    private companion object {
        const val AES_BLOCK_SIZE = 16
    }
}

private fun readInt32(buffer: ByteArray, offset: Int): Int {
    return ((buffer[offset].toInt() and 0xFF) shl 24) or
        ((buffer[offset + 1].toInt() and 0xFF) shl 16) or
        ((buffer[offset + 2].toInt() and 0xFF) shl 8) or
        (buffer[offset + 3].toInt() and 0xFF)
}
