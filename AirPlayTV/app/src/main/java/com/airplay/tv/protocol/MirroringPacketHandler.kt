package com.airplay.tv.protocol

import com.airplay.tv.media.VideoDecoder
import com.airplay.tv.media.containsIdrNalUnit
import com.airplay.tv.util.Logger
import java.nio.ByteBuffer

internal class MirroringPacketHandler(
    private val videoDecoder: VideoDecoder?,
    private val onCodecConfigReceived: (sps: ByteBuffer, pps: ByteBuffer, width: Int, height: Int) -> Unit
) {
    var decryptor: VideoStreamDecryptor? = null
    
    private var cachedCodecPrefix: ByteArray? = null
    private var shouldPrependCodecPrefix = false
    private var mirroredVideoFramesQueued = 0

    fun reset() {
        decryptor = null
        cachedCodecPrefix = null
        shouldPrependCodecPrefix = false
        mirroredVideoFramesQueued = 0
    }

    fun handleVideoPacket(payloadType: Int, payload: ByteArray) {
        val timestamp90Khz = ((System.nanoTime() / 1_000L) * 90L) / 1_000_000L
        when (payloadType) {
            0 -> {
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
                val codecData = parseAirPlayCodecConfig(payload)
                if (codecData != null) {
                    val sps = codecData.sps
                    val pps = codecData.pps
                    val width = codecData.width
                    val height = codecData.height
                    cachedCodecPrefix = codecData.buildAnnexBPrefix()
                    shouldPrependCodecPrefix = cachedCodecPrefix != null
                    Logger.i(Logger.TAG_PROTOCOL, "Received codec config: ${width}x${height}, SPS=${sps.remaining()}B, PPS=${pps.remaining()}B")
                    
                    onCodecConfigReceived(sps, pps, width, height)
                } else {
                    Logger.w(Logger.TAG_PROTOCOL, "Failed to extract codec config from type 1 packet")
                }
            }
            
            2 -> {
                Logger.d(Logger.TAG_PROTOCOL, "Received heartbeat packet")
            }

            5 -> {
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
        }
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
}
