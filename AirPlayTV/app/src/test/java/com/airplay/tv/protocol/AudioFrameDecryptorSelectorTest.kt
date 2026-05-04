package com.airplay.tv.protocol

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AudioFrameDecryptorSelectorTest {

    @Test
    fun `locks first candidate after three valid frames`() {
        val selector = AudioFrameDecryptorSelector(
            candidates = listOf(
                AudioFrameDecryptorSelector.Candidate(label = "hashed", decrypt = { byteArrayOf(0x8c.toByte(), 0x01) }),
                AudioFrameDecryptorSelector.Candidate(label = "plain", decrypt = { byteArrayOf(0x00, 0x01) }),
            ),
            framesToLock = 3,
        )

        repeat(2) {
            val result = selector.decrypt(byteArrayOf(0x01)) { it.first() == 0x8c.toByte() }
            assertNull(result.frame)
            assertFalse(result.lockAcquired)
        }

        val locked = selector.decrypt(byteArrayOf(0x01)) { it.first() == 0x8c.toByte() }

        assertTrue(locked.lockAcquired)
        assertEquals("hashed", locked.lockedLabel)
        assertArrayEquals(byteArrayOf(0x8c.toByte(), 0x01), locked.frame)
    }

    @Test
    fun `falls back to plain when hashed never validates`() {
        val selector = AudioFrameDecryptorSelector(
            candidates = listOf(
                AudioFrameDecryptorSelector.Candidate(label = "hashed", decrypt = { byteArrayOf(0x00, 0x01) }),
                AudioFrameDecryptorSelector.Candidate(label = "plain", decrypt = { byteArrayOf(0x8d.toByte(), 0x02) }),
            ),
            framesToLock = 3,
        )

        repeat(2) {
            val result = selector.decrypt(byteArrayOf(0x01)) { it.first() == 0x8d.toByte() }
            assertNull(result.frame)
            assertFalse(result.lockAcquired)
        }

        val locked = selector.decrypt(byteArrayOf(0x01)) { it.first() == 0x8d.toByte() }

        assertTrue(locked.lockAcquired)
        assertEquals("plain", locked.lockedLabel)
        assertArrayEquals(byteArrayOf(0x8d.toByte(), 0x02), locked.frame)
    }

    @Test
    fun `counts invalid packets before key lock`() {
        val selector = AudioFrameDecryptorSelector(
            candidates = listOf(
                AudioFrameDecryptorSelector.Candidate(label = "hashed", decrypt = { byteArrayOf(0x00, 0x01) }),
                AudioFrameDecryptorSelector.Candidate(label = "plain", decrypt = { byteArrayOf(0x01, 0x02) }),
            ),
            framesToLock = 3,
        )

        val first = selector.decrypt(byteArrayOf(0x01)) { it.first() == 0x8c.toByte() }
        val second = selector.decrypt(byteArrayOf(0x01)) { it.first() == 0x8c.toByte() }

        assertEquals("bad_key", first.failReason)
        assertEquals(1L, first.invalidCount)
        assertEquals(2L, second.invalidCount)
    }
}
