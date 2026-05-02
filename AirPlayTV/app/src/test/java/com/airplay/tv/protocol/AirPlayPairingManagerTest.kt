package com.airplay.tv.protocol

import org.bouncycastle.crypto.agreement.X25519Agreement
import org.bouncycastle.crypto.generators.Ed25519KeyPairGenerator
import org.bouncycastle.crypto.generators.X25519KeyPairGenerator
import org.bouncycastle.crypto.params.Ed25519KeyGenerationParameters
import org.bouncycastle.crypto.params.Ed25519PrivateKeyParameters
import org.bouncycastle.crypto.params.Ed25519PublicKeyParameters
import org.bouncycastle.crypto.params.X25519KeyGenerationParameters
import org.bouncycastle.crypto.params.X25519PrivateKeyParameters
import org.bouncycastle.crypto.params.X25519PublicKeyParameters
import org.bouncycastle.crypto.signers.Ed25519Signer
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.security.MessageDigest
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec

class AirPlayPairingManagerTest {

    @Test
    fun `pairing flow completes end to end`() {
        val manager = AirPlayPairingManager(SecureRandom())

        val receiverPublicSigningKey = manager.handlePairSetup(ByteArray(32))
        assertNotNull(receiverPublicSigningKey)
        assertEquals(32, receiverPublicSigningKey!!.size)

        val clientSigningKeyPair = generateEd25519KeyPair()
        val clientSigningPrivateKey =
            clientSigningKeyPair.getPrivate() as Ed25519PrivateKeyParameters
        val clientSigningPublicKeyBytes = (clientSigningKeyPair.getPublic() as Ed25519PublicKeyParameters).encoded

        val clientCurveKeyPair = generateX25519KeyPair()
        val clientCurvePrivateKey =
            clientCurveKeyPair.getPrivate() as X25519PrivateKeyParameters
        val clientCurvePublicKeyBytes = ByteArray(32)
        (clientCurveKeyPair.getPublic() as X25519PublicKeyParameters)
            .encode(clientCurvePublicKeyBytes, 0)

        val pairVerifyStep1Request = byteArrayOf(1, 0, 0, 0) +
            clientCurvePublicKeyBytes +
            clientSigningPublicKeyBytes

        val pairVerifyStep1Response = manager.handlePairVerify(pairVerifyStep1Request)
        assertNotNull(pairVerifyStep1Response)
        assertEquals(96, pairVerifyStep1Response!!.size)

        val serverCurvePublicKeyBytes = pairVerifyStep1Response.copyOfRange(0, 32)
        val encryptedServerSignature = pairVerifyStep1Response.copyOfRange(32, 96)

        val sharedSecret = ByteArray(32)
        val agreement = X25519Agreement()
        agreement.init(clientCurvePrivateKey)
        agreement.calculateAgreement(X25519PublicKeyParameters(serverCurvePublicKeyBytes, 0), sharedSecret, 0)

        val serverSignature = applyPairVerifyCtr(
            sharedSecret = sharedSecret,
            input = encryptedServerSignature,
            discardFirstBlock = false
        )

        val receiverPublicKey = Ed25519PublicKeyParameters(receiverPublicSigningKey, 0)
        assertTrue(
            verifySignature(
                publicKey = receiverPublicKey,
                payload = serverCurvePublicKeyBytes + clientCurvePublicKeyBytes,
                signature = serverSignature
            )
        )

        val clientSignature = sign(
            privateKey = clientSigningPrivateKey,
            payload = clientCurvePublicKeyBytes + serverCurvePublicKeyBytes
        )

        val encryptedClientSignature = applyPairVerifyCtr(
            sharedSecret = sharedSecret,
            input = clientSignature,
            discardFirstBlock = true
        )

        val pairVerifyStep2Request = byteArrayOf(0, 0, 0, 0) + encryptedClientSignature
        val pairVerifyStep2Response = manager.handlePairVerify(pairVerifyStep2Request)
        assertNotNull(pairVerifyStep2Response)
        assertArrayEquals(ByteArray(0), pairVerifyStep2Response)
    }

    @Test
    fun `pair setup rejects unexpected body sizes`() {
        val manager = AirPlayPairingManager(SecureRandom())
        val response = manager.handlePairSetup(ByteArray(31))
        assertEquals(null, response)
    }

    private fun generateEd25519KeyPair() =
        Ed25519KeyPairGenerator().apply {
            init(Ed25519KeyGenerationParameters(SecureRandom()))
        }.generateKeyPair()

    private fun generateX25519KeyPair() =
        X25519KeyPairGenerator().apply {
            init(X25519KeyGenerationParameters(SecureRandom()))
        }.generateKeyPair()

    private fun sign(privateKey: Ed25519PrivateKeyParameters, payload: ByteArray): ByteArray {
        val signer = Ed25519Signer()
        signer.init(true, privateKey)
        signer.update(payload, 0, payload.size)
        return signer.generateSignature()
    }

    private fun verifySignature(
        publicKey: Ed25519PublicKeyParameters,
        payload: ByteArray,
        signature: ByteArray
    ): Boolean {
        val signer = Ed25519Signer()
        signer.init(false, publicKey)
        signer.update(payload, 0, payload.size)
        return signer.verifySignature(signature)
    }

    private fun applyPairVerifyCtr(
        sharedSecret: ByteArray,
        input: ByteArray,
        discardFirstBlock: Boolean
    ): ByteArray {
        val key = deriveCtrMaterial("Pair-Verify-AES-Key".toByteArray(), sharedSecret)
        val iv = deriveCtrMaterial("Pair-Verify-AES-IV".toByteArray(), sharedSecret)
        val cipher = Cipher.getInstance("AES/CTR/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, SecretKeySpec(key, "AES"), IvParameterSpec(iv))

        if (discardFirstBlock) {
            cipher.update(ByteArray(64))
        }

        return cipher.doFinal(input)
    }

    private fun deriveCtrMaterial(salt: ByteArray, sharedSecret: ByteArray): ByteArray {
        val digest = MessageDigest.getInstance("SHA-512")
        digest.update(salt)
        digest.update(sharedSecret)
        return digest.digest().copyOf(16)
    }
}
