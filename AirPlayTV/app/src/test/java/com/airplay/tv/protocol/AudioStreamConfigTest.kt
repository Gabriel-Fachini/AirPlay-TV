package com.airplay.tv.protocol

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AudioStreamConfigTest {

    @Test
    fun `builds codec specific data for aac eld`() {
        val config = AudioStreamConfig(compressionType = 8)

        val csd = config.buildCodecSpecificData()

        assertNotNull(csd)
        val bytes = ByteArray(csd!!.remaining())
        csd.get(bytes)
        assertArrayEquals(byteArrayOf(0xF8.toByte(), 0xE8.toByte(), 0x50.toByte(), 0x00.toByte()), bytes)
        assertTrue(config.isSupportedAac)
        assertEquals("AAC-ELD", config.codecLabel)
    }

    @Test
    fun `builds codec specific data for aac lc`() {
        val config = AudioStreamConfig(compressionType = 4)

        val csd = config.buildCodecSpecificData()

        assertNotNull(csd)
        val bytes = ByteArray(csd!!.remaining())
        csd.get(bytes)
        assertArrayEquals(byteArrayOf(0x12.toByte(), 0x10.toByte()), bytes)
        assertTrue(config.isSupportedAac)
        assertEquals("AAC-LC", config.codecLabel)
    }

    @Test
    fun `rejects unsupported codec`() {
        val config = AudioStreamConfig(compressionType = 2)

        assertFalse(config.isSupportedAac)
        assertEquals("ALAC", config.codecLabel)
        assertEquals(null, config.buildCodecSpecificData())
    }
}
