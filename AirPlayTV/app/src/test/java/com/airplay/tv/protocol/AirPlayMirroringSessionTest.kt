package com.airplay.tv.protocol

import com.dd.plist.BinaryPropertyListParser
import com.dd.plist.BinaryPropertyListWriter
import com.dd.plist.NSArray
import com.dd.plist.NSDictionary
import com.dd.plist.NSNumber
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test
import java.nio.ByteBuffer

class AirPlayMirroringSessionTest {

    @Test
    fun `formats stream connection id as unsigned decimal`() {
        assertEquals(
            "15876509839857110628",
            formatAirPlayStreamConnectionId(-2570234233852440988L)
        )
    }

    @Test
    fun `captures audio setup metadata for aac eld stream`() {
        var capturedAudioConfig: AudioStreamConfig? = null
        val session = AirPlayMirroringSession(
            videoDecoder = null,
            pairingManager = AirPlayPairingManager(),
            decryptFairPlayAesKey = { null },
            startMirrorVideoServer = { 0 },
            getEventPort = { 7000 },
            onCodecConfigReceived = { _: ByteBuffer, _: ByteBuffer, _: Int, _: Int -> },
            onAudioCryptoConfigured = { },
            prepareAudioStream = {
                capturedAudioConfig = it.copy(
                    remoteTimingPort = 7777,
                    localDataPort = 7111,
                    localControlPort = 6111,
                    localTimingPort = 7005,
                )
                capturedAudioConfig!!
            },
        )

        val request = NSDictionary().apply {
            put(
                "streams",
                NSArray(
                    NSDictionary().apply {
                        put("type", NSNumber(96))
                        put("ct", NSNumber(8))
                        put("spf", NSNumber(480))
                        put("audioFormat", NSNumber(0x40000000000L))
                        put("controlPort", NSNumber(5555))
                        put("isMedia", NSNumber(true))
                        put("usingScreen", NSNumber(true))
                    }
                )
            )
        }

        val responseBytes = requireNotNull(
            session.buildSetupResponse(BinaryPropertyListWriter.writeToArray(request))
        )
        val parsedResponse = BinaryPropertyListParser.parse(responseBytes) as NSDictionary
        val streams = parsedResponse.objectForKey("streams") as NSArray
        val firstStream = streams.objectAtIndex(0) as NSDictionary

        assertEquals(7111L, (firstStream.objectForKey("dataPort") as NSNumber).longValue())
        assertEquals(6111L, (firstStream.objectForKey("controlPort") as NSNumber).longValue())
        assertEquals(96L, (firstStream.objectForKey("type") as NSNumber).longValue())

        val config = capturedAudioConfig
        requireNotNull(config)
        assertEquals(8, config.compressionType)
        assertEquals(480, config.samplesPerFrame)
        assertEquals(0x40000000000L, config.audioFormat)
        assertEquals(5555, config.remoteControlPort)
        assertEquals(7777, config.remoteTimingPort)
        assertEquals(7111, config.localDataPort)
        assertEquals(6111, config.localControlPort)
        assertEquals(7005, config.localTimingPort)
        assertEquals(true, config.isMedia)
        assertEquals(true, config.usingScreen)
    }
}
