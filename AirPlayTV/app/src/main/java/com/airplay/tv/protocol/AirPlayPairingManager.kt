package com.airplay.tv.protocol

import com.airplay.tv.util.Logger
import org.bouncycastle.crypto.AsymmetricCipherKeyPair
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
import java.security.MessageDigest
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec

/**
 * Implementa o pairing moderno exigido pelo AirPlay mirroring.
 * Mantém uma chave Ed25519 estável do receptor e uma sessão efêmera por conexão.
 */
class AirPlayPairingManager(
    private val secureRandom: SecureRandom = SecureRandom()
) {

    private enum class SessionStatus {
        INITIAL,
        SETUP,
        HANDSHAKE,
        FINISHED,
    }

    private data class PairingSession(
        var status: SessionStatus = SessionStatus.INITIAL,
        var clientCurvePublicKey: X25519PublicKeyParameters? = null,
        var clientSigningPublicKey: Ed25519PublicKeyParameters? = null,
        var serverCurvePrivateKey: X25519PrivateKeyParameters? = null,
        var serverCurvePublicKey: ByteArray? = null,
        var sharedSecret: ByteArray? = null,
    )

    private val receiverSigningKeyPair: AsymmetricCipherKeyPair = generateEd25519KeyPair()
    private val receiverSigningPrivateKey =
        receiverSigningKeyPair.getPrivate() as Ed25519PrivateKeyParameters
    private val receiverSigningPublicKey =
        receiverSigningKeyPair.getPublic() as Ed25519PublicKeyParameters

    private var pairingSession = PairingSession()

    @Synchronized
    fun resetSession() {
        pairingSession = PairingSession()
    }

    @Synchronized
    fun getSharedSecret(): ByteArray? {
        return pairingSession.sharedSecret?.copyOf()
    }

    @Synchronized
    fun handlePairSetup(requestBody: ByteArray): ByteArray? {
        if (requestBody.size != X25519_KEY_SIZE) {
            Logger.e(Logger.TAG_PROTOCOL, "Invalid pair-setup body size: ${requestBody.size}")
            return null
        }

        pairingSession = PairingSession(status = SessionStatus.SETUP)
        return receiverSigningPublicKey.encoded
    }

    @Synchronized
    fun handlePairVerify(requestBody: ByteArray): ByteArray? {
        if (pairingSession.status == SessionStatus.INITIAL) {
            Logger.e(Logger.TAG_PROTOCOL, "Pair-verify received before pair-setup")
            return null
        }
        if (requestBody.size < 4) {
            Logger.e(Logger.TAG_PROTOCOL, "Invalid pair-verify body size: ${requestBody.size}")
            return null
        }

        return when (requestBody[0].toInt()) {
            1 -> handlePairVerifyStep1(requestBody)
            0 -> handlePairVerifyStep2(requestBody)
            else -> {
                Logger.e(
                    Logger.TAG_PROTOCOL,
                    "Unsupported pair-verify step: ${requestBody[0].toInt()}"
                )
                null
            }
        }
    }

    private fun handlePairVerifyStep1(requestBody: ByteArray): ByteArray? {
        if (requestBody.size != 4 + X25519_KEY_SIZE + ED25519_KEY_SIZE) {
            Logger.e(
                Logger.TAG_PROTOCOL,
                "Invalid pair-verify step 1 size: ${requestBody.size}"
            )
            return null
        }

        val clientCurvePublicKey = X25519PublicKeyParameters(requestBody, 4)
        val clientSigningPublicKey = Ed25519PublicKeyParameters(
            requestBody,
            4 + X25519_KEY_SIZE
        )

        val curveKeyPair = generateX25519KeyPair()
        val serverCurvePrivateKey = curveKeyPair.getPrivate() as X25519PrivateKeyParameters
        val serverCurvePublicKey = ByteArray(X25519_KEY_SIZE)
        (curveKeyPair.getPublic() as X25519PublicKeyParameters).encode(serverCurvePublicKey, 0)

        val sharedSecret = ByteArray(X25519_KEY_SIZE)
        val agreement = X25519Agreement()
        agreement.init(serverCurvePrivateKey)
        agreement.calculateAgreement(clientCurvePublicKey, sharedSecret, 0)

        pairingSession = pairingSession.copy(
            status = SessionStatus.HANDSHAKE,
            clientCurvePublicKey = clientCurvePublicKey,
            clientSigningPublicKey = clientSigningPublicKey,
            serverCurvePrivateKey = serverCurvePrivateKey,
            serverCurvePublicKey = serverCurvePublicKey,
            sharedSecret = sharedSecret
        )

        val signaturePayload = serverCurvePublicKey + requestBody.copyOfRange(4, 4 + X25519_KEY_SIZE)
        val signature = sign(signaturePayload)
        val encryptedSignature = applyPairVerifyCtr(sharedSecret, signature, discardFirstBlock = false)

        Logger.i(Logger.TAG_PROTOCOL, "Pair-verify step 1 completed")
        return serverCurvePublicKey + encryptedSignature
    }

    private fun handlePairVerifyStep2(requestBody: ByteArray): ByteArray? {
        if (pairingSession.status != SessionStatus.HANDSHAKE) {
            Logger.e(Logger.TAG_PROTOCOL, "Pair-verify step 2 received in wrong state")
            return null
        }
        if (requestBody.size != 4 + SIGNATURE_SIZE) {
            Logger.e(
                Logger.TAG_PROTOCOL,
                "Invalid pair-verify step 2 size: ${requestBody.size}"
            )
            return null
        }

        val sharedSecret = pairingSession.sharedSecret ?: return null.also {
            Logger.e(Logger.TAG_PROTOCOL, "Missing shared secret for pair-verify step 2")
        }
        val clientSigningPublicKey = pairingSession.clientSigningPublicKey ?: return null.also {
            Logger.e(Logger.TAG_PROTOCOL, "Missing client signing key for pair-verify step 2")
        }
        val clientCurvePublicKey = pairingSession.clientCurvePublicKey ?: return null.also {
            Logger.e(Logger.TAG_PROTOCOL, "Missing client curve key for pair-verify step 2")
        }
        val serverCurvePublicKey = pairingSession.serverCurvePublicKey ?: return null.also {
            Logger.e(Logger.TAG_PROTOCOL, "Missing server curve key for pair-verify step 2")
        }

        val encryptedSignature = requestBody.copyOfRange(4, requestBody.size)
        val decryptedSignature = applyPairVerifyCtr(
            sharedSecret,
            encryptedSignature,
            discardFirstBlock = true
        )

        val clientCurvePublicKeyBytes = ByteArray(X25519_KEY_SIZE)
        clientCurvePublicKey.encode(clientCurvePublicKeyBytes, 0)
        val signaturePayload = clientCurvePublicKeyBytes + serverCurvePublicKey

        if (!verify(clientSigningPublicKey, signaturePayload, decryptedSignature)) {
            Logger.e(Logger.TAG_PROTOCOL, "Invalid pair-verify step 2 signature")
            return null
        }

        pairingSession = pairingSession.copy(status = SessionStatus.FINISHED)
        Logger.i(Logger.TAG_PROTOCOL, "Pair-verify step 2 completed")
        return ByteArray(0)
    }

    private fun sign(payload: ByteArray): ByteArray {
        val signer = Ed25519Signer()
        signer.init(true, receiverSigningPrivateKey)
        signer.update(payload, 0, payload.size)
        return signer.generateSignature()
    }

    private fun verify(
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
        val key = deriveCtrMaterial(PAIR_VERIFY_AES_KEY_SALT, sharedSecret)
        val iv = deriveCtrMaterial(PAIR_VERIFY_AES_IV_SALT, sharedSecret)
        val cipher = Cipher.getInstance("AES/CTR/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, SecretKeySpec(key, "AES"), IvParameterSpec(iv))

        if (discardFirstBlock) {
            cipher.update(ByteArray(SIGNATURE_SIZE))
        }

        return cipher.doFinal(input)
    }

    private fun deriveCtrMaterial(salt: ByteArray, sharedSecret: ByteArray): ByteArray {
        val digest = MessageDigest.getInstance("SHA-512")
        digest.update(salt)
        digest.update(sharedSecret)
        return digest.digest().copyOf(AES_BLOCK_SIZE)
    }

    private fun generateEd25519KeyPair(): AsymmetricCipherKeyPair {
        val generator = Ed25519KeyPairGenerator()
        generator.init(Ed25519KeyGenerationParameters(secureRandom))
        return generator.generateKeyPair()
    }

    private fun generateX25519KeyPair(): AsymmetricCipherKeyPair {
        val generator = X25519KeyPairGenerator()
        generator.init(X25519KeyGenerationParameters(secureRandom))
        return generator.generateKeyPair()
    }

    private companion object {
        const val AES_BLOCK_SIZE = 16
        const val X25519_KEY_SIZE = 32
        const val ED25519_KEY_SIZE = 32
        const val SIGNATURE_SIZE = 64
        val PAIR_VERIFY_AES_KEY_SALT = "Pair-Verify-AES-Key".toByteArray()
        val PAIR_VERIFY_AES_IV_SALT = "Pair-Verify-AES-IV".toByteArray()
    }
}
