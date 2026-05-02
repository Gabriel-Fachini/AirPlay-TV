package com.airplay.tv.protocol

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
import javax.crypto.Cipher
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec

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
) {
    private var videoMasterKey: ByteArray? = null
    private var decryptor: VideoStreamDecryptor? = null
    private var cachedCodecPrefix: ByteArray? = null
    private var shouldPrependCodecPrefix = false
    private var mirroredVideoFramesQueued = 0

    fun reset() {
        videoMasterKey = null
        decryptor = null
        cachedCodecPrefix = null
        shouldPrependCodecPrefix = false
        mirroredVideoFramesQueued = 0
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
                // Try WITHOUT decryption first - payload might not be encrypted
                // Try to parse as NAL units directly (4 bytes size + NAL data)
                var inputOffset = 0
                var outputOffset = 0
                val output = ByteArray(payload.size * 2)
                var nalCount = 0
                
                while (inputOffset + 4 <= payload.size) {
                    val nalSize = ((payload[inputOffset].toInt() and 0xFF) shl 24) or
                                  ((payload[inputOffset + 1].toInt() and 0xFF) shl 16) or
                                  ((payload[inputOffset + 2].toInt() and 0xFF) shl 8) or
                                  (payload[inputOffset + 3].toInt() and 0xFF)
                    
                    if (nalSize <= 0 || inputOffset + 4 + nalSize > payload.size) {
                        if (nalCount == 0) {
                            // Fall back to decryption
                            val frame = decryptor?.decodeVideoPayload(payload)
                            if (frame != null && frame.isNotEmpty()) {
                                queueMirroringFrame(frame, timestamp90Khz)
                            }
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
                if (nalCount > 0) {
                    val frame = output.copyOf(outputOffset)
                    queueMirroringFrame(frame, timestamp90Khz)
                }
            }

            1 -> {
                // Codec config (SPS/PPS) - NOT encrypted
                // Extract SPS and PPS and configure decoder
                val codecData = extractCodecConfigData(payload)
                if (codecData != null) {
                    val (sps, pps, width, height) = codecData
                    cachedCodecPrefix = buildAnnexBCodecPrefix(payload)
                    shouldPrependCodecPrefix = cachedCodecPrefix != null
                    Logger.i(Logger.TAG_PROTOCOL, "Received codec config: ${width}x${height}, SPS=${sps.remaining()}B, PPS=${pps.remaining()}B")
                    
                    // Notify service to reconfigure decoder with SPS/PPS
                    onCodecConfigReceived(sps, pps, width, height)
                } else {
                    Logger.w(Logger.TAG_PROTOCOL, "Failed to extract codec config from type 1 packet")
                }
            }

            5 -> {
                // Try to extract codec config from type 5 if it looks like avcC format
                if (payload.size > 11 && payload[0].toInt() == 1) {
                    val codecConfig = extractCodecConfig(payload)
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
        if (encryptedKey == null || encryptedKey.size != ENCRYPTED_AES_KEY_SIZE) {
            Logger.e(Logger.TAG_PROTOCOL, "SETUP 1 missing valid ekey")
            return null
        }

        videoMasterKey = decryptFairPlayAesKey(encryptedKey)
        if (videoMasterKey == null) {
            Logger.e(Logger.TAG_PROTOCOL, "Failed to decrypt FairPlay AES key from SETUP 1")
            return null
        }

        val response = NSDictionary().apply {
            put("eventPort", NSNumber(Constants.RTSP_PORT.toLong()))
            put("timingPort", NSNumber(TIMING_PORT.toLong()))
        }

        Logger.i(Logger.TAG_PROTOCOL, "SETUP 1 accepted: mirror master key ready")
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
                    responseStreams.add(
                        NSDictionary().apply {
                            put("dataPort", NSNumber(LEGACY_AUDIO_DATA_PORT.toLong()))
                            put("controlPort", NSNumber(LEGACY_AUDIO_CONTROL_PORT.toLong()))
                            put("type", NSNumber(AUDIO_STREAM_TYPE.toLong()))
                        }
                    )
                    Logger.i(Logger.TAG_PROTOCOL, "SETUP audio stream acknowledged with legacy RTP ports")
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

    private fun extractCodecConfigData(payload: ByteArray): Tuple4<ByteBuffer, ByteBuffer, Int, Int>? {
        if (payload.size < 11) {
            Logger.w(Logger.TAG_PROTOCOL, "Codec config payload too small: ${payload.size} bytes")
            return null
        }

        // Extract dimensions from header (bytes 56-63 according to UxPlay)
        // But first try the avcC format parsing
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

        // Extract SPS and PPS as ByteBuffers
        val sps = ByteBuffer.allocateDirect(spsSize)
        sps.put(payload, spsStart, spsSize)
        sps.flip()
        
        val pps = ByteBuffer.allocateDirect(ppsSize)
        pps.put(payload, ppsStart, ppsSize)
        pps.flip()
        
        // Try to extract dimensions - default to 1920x1080 if not found
        var width = 1920
        var height = 1080
        
        // UxPlay shows dimensions are at bytes 56-63 in the 128-byte header
        // But we only have the payload here, not the full header
        // So we'll parse from SPS if possible, or use defaults
        
        return Tuple4(sps, pps, width, height)
    }
    
    private data class Tuple4<A, B, C, D>(val first: A, val second: B, val third: C, val fourth: D)

    private fun extractCodecConfig(payload: ByteArray): ByteArray? {
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

        val result = ByteArray(ANNEX_B_START_CODE.size * 2 + spsSize + ppsSize)
        var offset = 0
        ANNEX_B_START_CODE.copyInto(result, offset)
        offset += ANNEX_B_START_CODE.size
        payload.copyInto(result, offset, spsStart, spsEnd)
        offset += spsSize
        ANNEX_B_START_CODE.copyInto(result, offset)
        offset += ANNEX_B_START_CODE.size
        payload.copyInto(result, offset, ppsStart, ppsEnd)
        return result
    }

    private fun buildAnnexBCodecPrefix(payload: ByteArray): ByteArray? {
        val codecPrefix = extractCodecConfig(payload)
        if (codecPrefix == null) {
            Logger.w(Logger.TAG_PROTOCOL, "Failed to cache SPS/PPS prefix from codec config")
        }
        return codecPrefix
    }

    private fun queueMirroringFrame(frame: ByteArray, timestamp90Khz: Long) {
        val isKeyFrame = containsIdrNal(frame)
        val frameToQueue = maybePrependCodecPrefix(frame, isKeyFrame)
        mirroredVideoFramesQueued++
        if (mirroredVideoFramesQueued <= 3) {
            Logger.i(
                Logger.TAG_PROTOCOL,
                "Queueing mirrored frame #$mirroredVideoFramesQueued (${frameToQueue.size} bytes, keyFrame=${containsIdrNal(frameToQueue)})"
            )
        }
        videoDecoder?.queueFrame(
            data = frameToQueue,
            timestamp = timestamp90Khz,
            isKeyFrame = containsIdrNal(frameToQueue),
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

        if (isKeyFrame && videoDecoder?.state?.value == VideoDecoder.DecoderState.Running) {
            shouldPrependCodecPrefix = false
        }

        return prefixedFrame
    }

    private fun containsIdrNal(frame: ByteArray): Boolean {
        var index = 0
        while (index + ANNEX_B_START_CODE.size < frame.size) {
            if (frame[index] == 0.toByte() &&
                frame[index + 1] == 0.toByte() &&
                frame[index + 2] == 0.toByte() &&
                frame[index + 3] == 1.toByte()
            ) {
                val nalHeaderIndex = index + ANNEX_B_START_CODE.size
                if (nalHeaderIndex < frame.size) {
                    val nalType = frame[nalHeaderIndex].toInt() and 0x1F
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

    private fun readUInt16(buffer: ByteArray, offset: Int): Int {
        return ((buffer[offset].toInt() and 0xFF) shl 8) or (buffer[offset + 1].toInt() and 0xFF)
    }

    private class NSMutablePlistArray {
        private val items = mutableListOf<NSDictionary>()

        fun add(item: NSDictionary) {
            items += item
        }

        fun isEmpty(): Boolean = items.isEmpty()

        fun toNSArray(): NSArray = NSArray(*items.toTypedArray())
    }

    private class VideoStreamDecryptor(
        streamConnectionId: Long,
        masterKey: ByteArray,
        sharedSecret: ByteArray,
    ) {
        private val cipher: Cipher
        private var pendingKeystream: ByteArray? = null
        private var pendingOffset = 0

        init {
            // Match RPiPlay's key derivation exactly:
            // 1. eaeskey = SHA512(masterKey[16] + sharedSecret[32]) - use FULL 64-byte hash
            val eaeskey = ByteArray(64)
            val tempKey = ByteArray(16)
            masterKey.copyInto(tempKey, 0, 0, minOf(16, masterKey.size))
            val eaeskeyHash = sha512(tempKey + sharedSecret)
            eaeskeyHash.copyInto(eaeskey, 0, 0, minOf(64, eaeskeyHash.size))

            // The reference implementations format streamConnectionID as unsigned uint64 text.
            // Using Kotlin's signed Long string breaks AES-CTR key derivation when the high bit is set.
            val streamConnectionIdText = formatAirPlayStreamConnectionId(streamConnectionId)

            // 2. videoKey = SHA512("AirPlayStreamKey{id}" + eaeskey[16]) - first 16 bytes of eaeskey
            val keyString = "AirPlayStreamKey$streamConnectionIdText"
            val videoKey = sha512(keyString.toByteArray() + eaeskey.copyOf(16)).copyOf(AES_BLOCK_SIZE)

            // 3. videoIv = SHA512("AirPlayStreamIV{id}" + eaeskey[16]) - first 16 bytes of eaeskey
            val ivString = "AirPlayStreamIV$streamConnectionIdText"
            val videoIv = sha512(ivString.toByteArray() + eaeskey.copyOf(16)).copyOf(AES_BLOCK_SIZE)

            cipher = Cipher.getInstance("AES/CTR/NoPadding").apply {
                init(Cipher.ENCRYPT_MODE, SecretKeySpec(videoKey, "AES"), IvParameterSpec(videoIv))
            }
        }

        fun decodeVideoPayload(payload: ByteArray): ByteArray {
            val decrypted = decrypt(payload)

            var inputOffset = 0
            var outputOffset = 0
            val output = ByteArray(decrypted.size * 2) // Allocate more space for start codes

            var nalCount = 0
            while (inputOffset + 4 <= decrypted.size) {
                val nalSize = readInt32(decrypted, inputOffset)

                if (nalSize <= 0 || inputOffset + 4 + nalSize > decrypted.size) {
                    if (nalCount == 0) {
                        Logger.w(Logger.TAG_PROTOCOL, "Invalid first NAL size: $nalSize (payload size=${decrypted.size})")
                    }
                    break
                }

                ANNEX_B_START_CODE.copyInto(output, outputOffset)
                outputOffset += ANNEX_B_START_CODE.size
                decrypted.copyInto(output, outputOffset, inputOffset + 4, inputOffset + 4 + nalSize)
                outputOffset += nalSize
                inputOffset += 4 + nalSize
                nalCount++
            }

            return output.copyOf(outputOffset)
        }

        private fun decrypt(input: ByteArray): ByteArray {
            // RPiPlay-style decryption with proper handling of partial blocks
            val output = ByteArray(input.size)
            var inputOffset = 0
            var outputOffset = 0

            // Handle pending bytes from previous packet
            val pending = pendingKeystream
            if (pending != null && pendingOffset > 0) {
                val pendingStart = AES_BLOCK_SIZE - pendingOffset
                while (pendingOffset < AES_BLOCK_SIZE && inputOffset < input.size) {
                    output[outputOffset++] = (input[inputOffset++].toInt() xor pending[pendingStart + (pendingOffset - pendingStart)].toInt()).toByte()
                    pendingOffset++
                }
                if (pendingOffset >= AES_BLOCK_SIZE) {
                    pendingKeystream = null
                    pendingOffset = 0
                }
            }

            // Decrypt full blocks
            val remaining = input.size - inputOffset
            val alignedLength = (remaining / AES_BLOCK_SIZE) * AES_BLOCK_SIZE
            if (alignedLength > 0) {
                // Decrypt in place for efficiency
                val decryptedChunk = cipher.update(input, inputOffset, alignedLength)
                if (decryptedChunk != null) {
                    decryptedChunk.copyInto(output, outputOffset)
                    outputOffset += decryptedChunk.size
                }
                inputOffset += alignedLength
            }

            // Handle remaining bytes (partial block)
            val tailLength = input.size - inputOffset
            if (tailLength > 0) {
                // Pad to full block and decrypt
                val paddedTail = ByteArray(AES_BLOCK_SIZE)
                input.copyInto(paddedTail, 0, inputOffset, input.size)
                val decryptedTail = cipher.update(paddedTail) ?: ByteArray(AES_BLOCK_SIZE)
                
                // Copy only the actual data bytes
                decryptedTail.copyInto(output, outputOffset, 0, tailLength)
                outputOffset += tailLength
                
                // Save remaining keystream for next packet
                pendingKeystream = decryptedTail
                pendingOffset = tailLength
            }

            return output
        }

        private fun readInt32(buffer: ByteArray, offset: Int): Int {
            return ((buffer[offset].toInt() and 0xFF) shl 24) or
                ((buffer[offset + 1].toInt() and 0xFF) shl 16) or
                ((buffer[offset + 2].toInt() and 0xFF) shl 8) or
                (buffer[offset + 3].toInt() and 0xFF)
        }

        private fun sha512(data: ByteArray): ByteArray {
            return MessageDigest.getInstance("SHA-512").digest(data)
        }
    }

    private companion object {
        const val AES_BLOCK_SIZE = 16
        const val ENCRYPTED_AES_KEY_SIZE = 72
        const val MIRROR_STREAM_TYPE = 110
        const val AUDIO_STREAM_TYPE = 96
        const val TIMING_PORT = 7002
        const val LEGACY_AUDIO_DATA_PORT = 7100
        const val LEGACY_AUDIO_CONTROL_PORT = 6001
        val ANNEX_B_START_CODE = byteArrayOf(0x00, 0x00, 0x00, 0x01)
    }
}
