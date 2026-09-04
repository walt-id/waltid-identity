@file:OptIn(kotlinx.serialization.ExperimentalSerializationApi::class)

package id.walt.mdoc.proximity

import id.walt.cose.coseCompliantCbor
import id.walt.cose.toCoseKey
import id.walt.crypto2.CryptoRuntime
import id.walt.crypto2.keys.EcCurve
import id.walt.crypto2.keys.EncodedKey
import id.walt.crypto2.keys.KeyId
import id.walt.crypto2.keys.KeySpec
import id.walt.crypto2.keys.KeyUsage
import id.walt.crypto2.keys.MontgomeryCurve
import id.walt.crypto2.providers.GenerateSoftwareKeyRequest
import id.walt.crypto2.providers.CryptoOperation
import id.walt.crypto2.providers.CryptoRequirement
import id.walt.crypto2.providers.cryptography.defaultSoftwareKeyProviders
import id.walt.mdoc.crypto.MdocKdf
import id.walt.mdoc.crypto.MdocCryptoHelper
import id.walt.mdoc.objects.SessionTranscript
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class MdocSessionCipherTest {
    private val runtime = CryptoRuntime(defaultSoftwareKeyProviders())

    @Test
    fun `holder and reader use independent directional keys and counters`() = runTest {
        val deviceKey = ByteArray(32) { it.toByte() }
        val readerKey = ByteArray(32) { (it + 32).toByte() }
        val holder = MdocSessionCipher.fromDerivedKeys(MdocSessionRole.HOLDER, deviceKey, readerKey)
        val reader = MdocSessionCipher.fromDerivedKeys(MdocSessionRole.READER, deviceKey, readerKey)

        val firstRequest = reader.encrypt("request-1".encodeToByteArray())
        assertContentEquals(
            "798e19ca1ca93b8b71ea12d1d7e06ef002a5833db636d5d84c".hexToByteArray(),
            firstRequest,
        )
        assertContentEquals("request-1".encodeToByteArray(), holder.decrypt(firstRequest))
        assertContentEquals("response-1".encodeToByteArray(), reader.decrypt(holder.encrypt("response-1".encodeToByteArray())))
        assertContentEquals("request-2".encodeToByteArray(), holder.decrypt(reader.encrypt("request-2".encodeToByteArray())))
        assertTrue(holder.encryptedMessages == 1uL && holder.decryptedMessages == 2uL)
    }

    @Test
    fun `tampering closes the cipher and replay cannot advance`() = runTest {
        val keys = ByteArray(32) { (it + 64).toByte() }
        val holder = MdocSessionCipher.fromDerivedKeys(MdocSessionRole.HOLDER, keys, keys)
        val reader = MdocSessionCipher.fromDerivedKeys(MdocSessionRole.READER, keys, keys)
        val encrypted = reader.encrypt("request".encodeToByteArray())
        encrypted[0] = (encrypted[0].toInt() xor 1).toByte()

        assertFailsWith<MdocSessionCryptoException> { holder.decrypt(encrypted) }
        assertFailsWith<IllegalStateException> { holder.decrypt(reader.encrypt("late".encodeToByteArray())) }
    }

    @Test
    fun `ciphertext mutation matrix rejects tampering across the authenticated message`() = runTest {
        val keys = ByteArray(32) { (it + 48).toByte() }
        val canonicalReader = MdocSessionCipher.fromDerivedKeys(MdocSessionRole.READER, keys, keys)
        val encrypted = canonicalReader.encrypt("authenticated-session-data".encodeToByteArray())

        MdocWireMutationMatrix.bitFlips(encrypted).forEach { mutation ->
            val holder = MdocSessionCipher.fromDerivedKeys(MdocSessionRole.HOLDER, keys, keys)
            assertFailsWith<MdocSessionCryptoException>("SM_SESSION_DATA:${mutation.id}") {
                holder.decrypt(mutation.bytes)
            }
            assertFailsWith<IllegalStateException>("SM_SESSION_DATA:${mutation.id}:closed") {
                holder.decrypt(encrypted)
            }
        }
    }

    @Test
    fun `a successfully decrypted ciphertext cannot be replayed at the next counter`() = runTest {
        val keys = ByteArray(32) { (it + 96).toByte() }
        val holder = MdocSessionCipher.fromDerivedKeys(MdocSessionRole.HOLDER, keys, keys)
        val reader = MdocSessionCipher.fromDerivedKeys(MdocSessionRole.READER, keys, keys)
        val encrypted = reader.encrypt("request".encodeToByteArray())

        assertContentEquals("request".encodeToByteArray(), holder.decrypt(encrypted))
        assertFailsWith<MdocSessionCryptoException> { holder.decrypt(encrypted) }
    }

    @Test
    fun `counter exhaustion fails closed before IV reuse`() = runTest {
        val key = ByteArray(32)
        val cipher = MdocSessionCipher.fromDerivedKeys(
            MdocSessionRole.HOLDER,
            key,
            key,
            initialEncryptionCounter = UInt.MAX_VALUE.toULong() + 1uL,
        )

        assertFailsWith<MdocSessionCryptoException> { cipher.encrypt(byteArrayOf(1)) }
        assertFailsWith<IllegalStateException> { cipher.encrypt(byteArrayOf(2)) }
    }

    @Test
    fun `IV layout follows ISO identifier and 32 bit counter`() {
        assertContentEquals(
            byteArrayOf(0, 0, 0, 0, 0, 0, 0, 1, 0x01, 0x02, 0x03, 0x04),
            MdocSessionCipher.iv(1u, 0x01020304u),
        )
    }

    @Test
    fun `HKDF SHA-256 matches RFC 5869 test vector one`() {
        assertContentEquals(
            "3cb25f25faacd57a90434f64d0362f2a2d2d0a90cf1a5a4c5db02d56ecc4c5bf34007208d5b887185865".hexToByteArray(),
            MdocKdf.deriveSha256(
                inputKeyMaterial = ByteArray(22) { 0x0b },
                salt = "000102030405060708090a0b0c".hexToByteArray(),
                info = "f0f1f2f3f4f5f6f7f8f9".hexToByteArray(),
                length = 42,
            ),
        )
    }

    @Test
    fun `every implemented cipher-suite one curve establishes matching directional keys`() = runTest {
        val specs = listOf(
            KeySpec.Ec(EcCurve.P256),
            KeySpec.Ec(EcCurve.P384),
            KeySpec.Ec(EcCurve.P521),
            KeySpec.Montgomery(MontgomeryCurve.X25519),
            KeySpec.Montgomery(MontgomeryCurve.X448),
        )
        for ((index, spec) in specs.filter(::supportsAgreementKeyGeneration).withIndex()) {
            val device = agreementKey("device-$index", spec)
            val reader = agreementKey("reader-$index", spec)
            val deviceCose = (device.capabilities.publicKeyExporter!!.exportPublicKey() as EncodedKey.Jwk).toCoseKey()
            val readerCose = (reader.capabilities.publicKeyExporter!!.exportPublicKey() as EncodedKey.Jwk).toCoseKey()
            val transcript = MdocCryptoHelper.buildSessionTranscriptBytes(
                SessionTranscript.forQr(
                    coseCompliantCbor.encodeToByteArray(id.walt.cose.CoseKey.serializer(), deviceCose),
                    coseCompliantCbor.encodeToByteArray(id.walt.cose.CoseKey.serializer(), readerCose),
                )
            )
            val holderCipher = MdocSessionCipher.establishForHolder(device, readerCose, transcript)
            val readerCipher = MdocSessionCipher.establishForReader(reader, deviceCose, transcript)

            assertContentEquals(
                spec.toString().encodeToByteArray(),
                holderCipher.decrypt(readerCipher.encrypt(spec.toString().encodeToByteArray())),
            )
            holderCipher.close()
            readerCipher.close()
        }
    }

    private suspend fun agreementKey(id: String, spec: KeySpec) = runtime.generateSoftwareKey(
        GenerateSoftwareKeyRequest(
            KeyId(id),
            spec,
            setOf(KeyUsage.KEY_AGREEMENT),
        )
    )

    private fun supportsAgreementKeyGeneration(spec: KeySpec): Boolean = runCatching {
        runtime.resolveSoftwareProvider(
            CryptoRequirement(
                operation = CryptoOperation.GENERATE_KEY,
                spec = spec,
                usages = setOf(KeyUsage.KEY_AGREEMENT),
            )
        )
    }.isSuccess
}
