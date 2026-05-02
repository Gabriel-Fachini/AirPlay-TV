package com.airplay.tv.media

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Test
import java.nio.ByteBuffer

class VideoDecoderTest {

    @Test
    fun `codec specific data buffer prepends start code when missing`() {
        val nalUnit = byteArrayOf(0x67, 0x64, 0x00, 0x28.toByte())

        val buffer = buildCodecSpecificDataBuffer(ByteBuffer.wrap(nalUnit))

        assertEquals(8, buffer.remaining())
        val output = ByteArray(buffer.remaining())
        buffer.get(output)
        assertArrayEquals(
            byteArrayOf(0x00, 0x00, 0x00, 0x01, 0x67, 0x64, 0x00, 0x28.toByte()),
            output
        )
    }

    @Test
    fun `codec specific data buffer preserves existing start code`() {
        val nalUnit = byteArrayOf(0x00, 0x00, 0x00, 0x01, 0x68, 0xEE.toByte(), 0x3C, 0xB0.toByte())

        val buffer = buildCodecSpecificDataBuffer(ByteBuffer.wrap(nalUnit))

        assertEquals(nalUnit.size, buffer.remaining())
        val output = ByteArray(buffer.remaining())
        buffer.get(output)
        assertArrayEquals(nalUnit, output)
    }
}
