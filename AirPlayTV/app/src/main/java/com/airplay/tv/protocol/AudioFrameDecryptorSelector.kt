package com.airplay.tv.protocol

import javax.crypto.Cipher
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec

internal class AudioFrameDecryptorSelector internal constructor(
    private val candidates: List<Candidate>,
    private val framesToLock: Int = REQUIRED_VALID_STREAK,
) {
    private var lockedCandidate: Candidate? = null
    private var invalidPackets = 0L

    constructor(config: AudioCryptoConfig) : this(
        candidates = buildCandidates(config),
        framesToLock = REQUIRED_VALID_STREAK,
    )

    fun decrypt(payload: ByteArray, isValidFrame: (ByteArray) -> Boolean): Result {
        val locked = lockedCandidate
        if (locked != null) {
            val frame = locked.decrypt(payload)
            if (isValidFrame(frame)) {
                locked.validStreak = framesToLock
                return Result(frame = frame, lockedLabel = locked.label, lockAcquired = false)
            }

            locked.validStreak = 0
            val relockResult = tryCandidates(
                payload = payload,
                isValidFrame = isValidFrame,
                orderedCandidates = candidates.filter { it !== locked } + locked,
            )
            if (relockResult != null) {
                return relockResult
            }

            invalidPackets++
            return Result(
                frame = null,
                lockedLabel = locked.label,
                lockAcquired = false,
                failReason = "bad_key",
                invalidCount = invalidPackets,
            )
        }

        val lockResult = tryCandidates(payload, isValidFrame, candidates)
        if (lockResult != null) {
            return lockResult
        }

        invalidPackets++
        return Result(
            frame = null,
            lockedLabel = null,
            lockAcquired = false,
            failReason = "bad_key",
            invalidCount = invalidPackets,
        )
    }

    internal data class Result(
        val frame: ByteArray?,
        val lockedLabel: String?,
        val lockAcquired: Boolean,
        val failReason: String? = null,
        val invalidCount: Long = 0L,
    )

    internal data class Candidate(
        val label: String,
        val decrypt: (ByteArray) -> ByteArray,
        var validStreak: Int = 0,
    )

    private fun tryCandidates(
        payload: ByteArray,
        isValidFrame: (ByteArray) -> Boolean,
        orderedCandidates: List<Candidate>,
    ): Result? {
        for (candidate in orderedCandidates) {
            val frame = candidate.decrypt(payload)
            if (!isValidFrame(frame)) {
                candidate.validStreak = 0
                continue
            }

            candidate.validStreak++
            if (candidate.validStreak >= framesToLock) {
                lockedCandidate = candidate
                invalidPackets = 0
                return Result(
                    frame = frame,
                    lockedLabel = candidate.label,
                    lockAcquired = true,
                )
            }
        }

        return null
    }

    companion object {
        private const val REQUIRED_VALID_STREAK = 6

        private fun buildCandidates(config: AudioCryptoConfig): List<Candidate> {
            val ordered = mutableListOf<Candidate>()

            fun addCandidate(label: String, key: ByteArray) {
                ordered.add(
                    Candidate(label = label, decrypt = { payload ->
                        AudioPayloadDecryptor(aesKey = key, aesIv = config.aesIv).decrypt(payload)
                    })
                )
            }

            if (config.preferHashed && config.hashedAesKey != null) {
                addCandidate("hashed", config.hashedAesKey)
            }
            addCandidate("plain", config.plainAesKey)
            if (!config.preferHashed && config.hashedAesKey != null) {
                addCandidate("hashed", config.hashedAesKey)
            }

            return ordered
        }
    }
}

internal class AudioPayloadDecryptor(
    aesKey: ByteArray,
    aesIv: ByteArray,
) {
    private val cipher = Cipher.getInstance("AES/CBC/NoPadding")
    private val keySpec = SecretKeySpec(aesKey, "AES")
    private val ivSpec = IvParameterSpec(aesIv)

    fun decrypt(payload: ByteArray): ByteArray {
        val encryptedLength = (payload.size / BLOCK_SIZE_BYTES) * BLOCK_SIZE_BYTES
        if (encryptedLength == 0) {
            return payload.copyOf()
        }

        cipher.init(Cipher.DECRYPT_MODE, keySpec, ivSpec)
        val output = ByteArray(payload.size)
        val decrypted = cipher.doFinal(payload, 0, encryptedLength)
        decrypted.copyInto(output, 0, 0, encryptedLength)
        if (encryptedLength < payload.size) {
            payload.copyInto(output, encryptedLength, encryptedLength, payload.size)
        }
        return output
    }

    private companion object {
        const val BLOCK_SIZE_BYTES = 16
    }
}
