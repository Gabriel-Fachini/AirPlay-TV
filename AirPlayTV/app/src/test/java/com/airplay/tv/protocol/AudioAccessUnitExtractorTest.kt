package com.airplay.tv.protocol

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AudioAccessUnitExtractorTest {

    private val extractor = AudioAccessUnitExtractor()

    @Test
    fun `returns raw aac eld frame when payload has no au headers`() {
        val payload = byteArrayOf(0x8c.toByte(), 0x01, 0x02, 0x03)

        val units = extractor.extract(AudioStreamConfig(compressionType = 8), payload)

        assertEquals(1, units.size)
        assertArrayEquals(payload, units.single())
    }

    @Test
    fun `extracts multiple access units from rfc3640 payload`() {
        val firstUnit = byteArrayOf(0x8c.toByte(), 0x11, 0x12)
        val secondUnit = byteArrayOf(0x8d.toByte(), 0x21)
        val payload = byteArrayOf(
            0x00, 0x20,
            0x00, 0x18,
            0x00, 0x10,
            *firstUnit,
            *secondUnit,
        )

        val units = extractor.extract(AudioStreamConfig(compressionType = 8), payload)

        assertEquals(2, units.size)
        assertArrayEquals(firstUnit, units[0])
        assertArrayEquals(secondUnit, units[1])
    }

    @Test
    fun `rejects malformed au payloads that overrun available data`() {
        val payload = byteArrayOf(
            0x00, 0x10,
            0x00, 0x28,
            0x8c.toByte(), 0x01,
        )

        val units = extractor.extract(AudioStreamConfig(compressionType = 8), payload)

        assertTrue(units.isEmpty())
    }
}
