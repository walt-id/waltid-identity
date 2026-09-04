@file:OptIn(
    dev.whyoleg.cryptography.CryptographyProviderApi::class,
    dev.whyoleg.cryptography.DelicateCryptographyApi::class,
)

package id.walt.mdoc.proximity

import dev.whyoleg.cryptography.CryptographyProvider
import dev.whyoleg.cryptography.algorithms.AES
import id.walt.cose.CoseKey
import id.walt.cose.toEncodedJwk
import id.walt.crypto2.keys.Key
import id.walt.mdoc.crypto.MdocKdf
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.CancellationException
import org.kotlincrypto.hash.sha2.SHA256

/** ISO/IEC 18013-5 cipher-suite 1 session keys, directional IVs, and replay-safe counters. */
class MdocSessionCipher private constructor(
    val role: MdocSessionRole,
    private val encryptionKey: ByteArray,
    private val decryptionKey: ByteArray,
    private val encryptionIdentifier: UInt,
    private val decryptionIdentifier: UInt,
    private val provider: CryptographyProvider,
    initialEncryptionCounter: ULong = 1uL,
    initialDecryptionCounter: ULong = 1uL,
) {
    private val mutex = Mutex()
    private var encryptionCounter = initialEncryptionCounter
    private var decryptionCounter = initialDecryptionCounter
    private var closed = false

    val encryptedMessages: ULong get() = encryptionCounter - 1uL
    val decryptedMessages: ULong get() = decryptionCounter - 1uL

    suspend fun encrypt(plaintext: ByteArray): ByteArray = mutex.withLock {
        ensureOpen()
        val counter = nextCounter(encryptionCounter, "outbound")
        val encrypted = try {
            val key = provider.get(AES.GCM).keyDecoder().decodeFromByteArray(AES.Key.Format.RAW, encryptionKey)
            key.cipher().encryptWithIv(iv(encryptionIdentifier, counter), plaintext, byteArrayOf())
        } catch (cancelled: CancellationException) {
            closeLocked()
            throw cancelled
        } catch (failure: Throwable) {
            closeLocked()
            throw MdocSessionCryptoException("Unable to encrypt the session message", failure)
        }
        encryptionCounter++
        encrypted
    }

    suspend fun decrypt(ciphertextAndTag: ByteArray): ByteArray = mutex.withLock {
        ensureOpen()
        if (ciphertextAndTag.size < AUTH_TAG_BYTES) {
            closeLocked()
            throw MdocSessionCryptoException("Session ciphertext is shorter than its authentication tag")
        }
        val counter = nextCounter(decryptionCounter, "inbound")
        val plaintext = try {
            val key = provider.get(AES.GCM).keyDecoder().decodeFromByteArray(AES.Key.Format.RAW, decryptionKey)
            key.cipher().decryptWithIv(iv(decryptionIdentifier, counter), ciphertextAndTag, byteArrayOf())
        } catch (cancelled: CancellationException) {
            closeLocked()
            throw cancelled
        } catch (failure: Throwable) {
            closeLocked()
            throw MdocSessionCryptoException("Session message authentication failed", failure)
        }
        decryptionCounter++
        plaintext
    }

    suspend fun close() = mutex.withLock { closeLocked() }

    private fun closeLocked() {
        if (closed) return
        closed = true
        encryptionKey.fill(0)
        decryptionKey.fill(0)
    }

    private fun ensureOpen() = check(!closed) { "Mdoc session cipher is closed" }

    private fun nextCounter(value: ULong, direction: String): UInt {
        if (value > UInt.MAX_VALUE.toULong()) {
            closeLocked()
            throw MdocSessionCryptoException("$direction session counter is exhausted")
        }
        return value.toUInt()
    }

    companion object {
        private const val AUTH_TAG_BYTES = 16
        private const val KEY_BYTES = 32

        suspend fun establishForHolder(
            eDeviceKey: Key,
            eReaderKey: CoseKey,
            sessionTranscriptBytes: ByteArray,
        ): MdocSessionCipher = establish(
            role = MdocSessionRole.HOLDER,
            localKey = eDeviceKey,
            peerKey = eReaderKey,
            sessionTranscriptBytes = sessionTranscriptBytes,
            provider = CryptographyProvider.Default,
        )

        suspend fun establishForReader(
            eReaderKey: Key,
            eDeviceKey: CoseKey,
            sessionTranscriptBytes: ByteArray,
        ): MdocSessionCipher = establish(
            role = MdocSessionRole.READER,
            localKey = eReaderKey,
            peerKey = eDeviceKey,
            sessionTranscriptBytes = sessionTranscriptBytes,
            provider = CryptographyProvider.Default,
        )

        private suspend fun establish(
            role: MdocSessionRole,
            localKey: Key,
            peerKey: CoseKey,
            sessionTranscriptBytes: ByteArray,
            provider: CryptographyProvider,
        ): MdocSessionCipher {
            require(sessionTranscriptBytes.size >= 4 && sessionTranscriptBytes[0] == 0xd8.toByte() && sessionTranscriptBytes[1] == 0x18.toByte()) {
                "SessionTranscriptBytes must retain CBOR tag 24"
            }
            val keyAgreementAlgorithm = MdocSessionKeyValidator.agreementAlgorithm(localKey)
            val agreement = requireNotNull(localKey.capabilities.keyAgreement) {
                "Local ephemeral session key cannot perform key agreement"
            }
            val encodedPeer = MdocSessionKeyValidator.requireCompatiblePeerKey(localKey, peerKey.toEncodedJwk(), provider)
            val sharedSecret = agreement.generateSharedSecret(encodedPeer, keyAgreementAlgorithm).toByteArray()
            val salt = SHA256().digest(sessionTranscriptBytes)
            val (skDevice, skReader) = try {
                MdocKdf.deriveSha256(sharedSecret, salt, "SKDevice".encodeToByteArray(), KEY_BYTES) to
                    MdocKdf.deriveSha256(sharedSecret, salt, "SKReader".encodeToByteArray(), KEY_BYTES)
            } finally {
                sharedSecret.fill(0)
                salt.fill(0)
            }
            return when (role) {
                MdocSessionRole.HOLDER -> MdocSessionCipher(role, skDevice, skReader, 1u, 0u, provider)
                MdocSessionRole.READER -> MdocSessionCipher(role, skReader, skDevice, 0u, 1u, provider)
            }
        }

        internal fun iv(identifier: UInt, counter: UInt): ByteArray = ByteArray(12).also { iv ->
            iv[7] = identifier.toByte()
            iv[8] = (counter shr 24).toByte()
            iv[9] = (counter shr 16).toByte()
            iv[10] = (counter shr 8).toByte()
            iv[11] = counter.toByte()
        }

        internal fun fromDerivedKeys(
            role: MdocSessionRole,
            skDevice: ByteArray,
            skReader: ByteArray,
            initialEncryptionCounter: ULong = 1uL,
            initialDecryptionCounter: ULong = 1uL,
            provider: CryptographyProvider = CryptographyProvider.Default,
        ): MdocSessionCipher = when (role) {
            MdocSessionRole.HOLDER -> MdocSessionCipher(
                role, skDevice.copyOf(), skReader.copyOf(), 1u, 0u, provider,
                initialEncryptionCounter, initialDecryptionCounter,
            )
            MdocSessionRole.READER -> MdocSessionCipher(
                role, skReader.copyOf(), skDevice.copyOf(), 0u, 1u, provider,
                initialEncryptionCounter, initialDecryptionCounter,
            )
        }
    }
}

enum class MdocSessionRole { HOLDER, READER }

class MdocSessionCryptoException(message: String, cause: Throwable? = null) : Exception(message, cause)
