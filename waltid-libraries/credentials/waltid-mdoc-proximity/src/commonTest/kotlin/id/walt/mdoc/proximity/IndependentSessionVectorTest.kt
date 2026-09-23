@file:OptIn(kotlinx.serialization.ExperimentalSerializationApi::class,
    dev.whyoleg.cryptography.CryptographyProviderApi::class,
    dev.whyoleg.cryptography.DelicateCryptographyApi::class)

package id.walt.mdoc.proximity

import id.walt.cose.CoseKey
import id.walt.cose.coseCompliantCbor
import dev.whyoleg.cryptography.CryptographyProvider
import dev.whyoleg.cryptography.algorithms.EC
import dev.whyoleg.cryptography.algorithms.ECDH
import id.walt.crypto2.algorithms.KeyAgreementAlgorithm
import id.walt.crypto2.keys.KeyAgreement
import id.walt.crypto2.keys.KeyCapabilities
import id.walt.crypto2.keys.EcCurve
import id.walt.crypto2.keys.EncodedKey
import id.walt.crypto2.keys.Key
import id.walt.crypto2.keys.KeyId
import id.walt.crypto2.keys.KeySpec
import id.walt.crypto2.keys.KeyUsage
import id.walt.crypto2.serialization.BinaryData
import id.walt.mdoc.objects.session.SessionData
import id.walt.mdoc.objects.session.SessionEstablishment
import id.walt.mdoc.proximity.vectors.MultipazSessionVectors as V
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.decodeFromByteArray
import kotlinx.serialization.encodeToByteArray
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

/** Fixed external values cover the ECDH -> tagged transcript -> HKDF -> directional GCM boundary. */
class IndependentSessionVectorTest {
    private val establishment get() = coseCompliantCbor.decodeFromByteArray<SessionEstablishment>(V.SESSION_ESTABLISHMENT.hexToByteArray())
    private val response get() = coseCompliantCbor.decodeFromByteArray<SessionData>(V.SESSION_DATA.hexToByteArray()).data!!

    @Test fun publicPeerVectorMatchesKeyAgreementTranscriptAndBothDirections() = runTest {
        val holder = holder()
        val reader = MdocSessionCipher.establishForReader(key(device = false), publicDeviceKey(), V.SESSION_TRANSCRIPT_BYTES.hexToByteArray())
        try {
            assertContentEquals(V.DEVICE_REQUEST.hexToByteArray(), holder.decrypt(establishment.data))
            assertContentEquals(establishment.data, reader.encrypt(V.DEVICE_REQUEST.hexToByteArray()))
            val ciphertext = holder.encrypt(V.DEVICE_RESPONSE.hexToByteArray())
            assertContentEquals(response, ciphertext)
            assertContentEquals(V.SESSION_DATA.hexToByteArray(), coseCompliantCbor.encodeToByteArray(SessionData(data = ciphertext)))
            assertContentEquals(V.DEVICE_RESPONSE.hexToByteArray(), reader.decrypt(response))
        } finally { holder.close(); reader.close() }
    }

    @Test fun publicPeerCiphertextMutationsFailClosedAndFreshControlRecovers() = runTest {
        val canonical = establishment.data
        val cases = listOf(
            Mutation("ciphertext-bit", canonical.copyOf().apply { this[0] = (this[0].toInt() xor 1).toByte() }),
            Mutation("tag-bit", canonical.copyOf().apply { this[lastIndex] = (last().toInt() xor 1).toByte() }),
            Mutation("truncated-tag", canonical.copyOf(canonical.size - 1)),
            Mutation("shorter-than-tag", canonical.copyOf(15)),
            Mutation("reflected-holder-response", response),
        )
        for (case in cases) {
            val damaged = holder()
            assertFailsWith<MdocSessionCryptoException>(case.id) { damaged.decrypt(case.ciphertext) }
            assertFailsWith<IllegalStateException>(case.id) { damaged.encrypt(byteArrayOf(1)) }
            assertEquals(0uL, damaged.decryptedMessages, case.id)
            val control = holder()
            assertContentEquals(V.DEVICE_REQUEST.hexToByteArray(), control.decrypt(canonical), case.id)
            control.close()
        }
    }

    @Test fun publicPeerTranscriptMutationCannotAuthenticateTheCanonicalRequest() = runTest {
        val changed = V.SESSION_TRANSCRIPT_BYTES.hexToByteArray().apply { this[lastIndex] = (last().toInt() xor 1).toByte() }
        val cipher = holder(changed)
        assertFailsWith<MdocSessionCryptoException> { cipher.decrypt(establishment.data) }
        assertFailsWith<IllegalStateException> { cipher.encrypt(byteArrayOf(1)) }
        val control = holder()
        assertContentEquals(V.DEVICE_REQUEST.hexToByteArray(), control.decrypt(establishment.data))
        control.close()
    }

    @Test fun publicPeerCounterOneCannotBeReplayedAsCounterTwo() = runTest {
        val cipher = holder()
        assertContentEquals(V.DEVICE_REQUEST.hexToByteArray(), cipher.decrypt(establishment.data))
        assertFailsWith<MdocSessionCryptoException> { cipher.decrypt(establishment.data) }
        assertEquals(1uL, cipher.decryptedMessages)
        assertFailsWith<IllegalStateException> { cipher.encrypt(byteArrayOf(1)) }
    }

    private data class Mutation(val id: String, val ciphertext: ByteArray)
    private suspend fun holder(transcript: ByteArray = V.SESSION_TRANSCRIPT_BYTES.hexToByteArray()) =
        MdocSessionCipher.establishForHolder(key(device = true), establishment.eReaderKey.value, transcript)

    private fun publicDeviceKey() = CoseKey(kty = 2, crv = 1,
        x = V.EPHEMERAL_DEVICE_KEY_X.hexToByteArray(), y = V.EPHEMERAL_DEVICE_KEY_Y.hexToByteArray())

    // Public fixture material is loaded directly into the platform ECDH backend. Android's
    // application CryptoRuntime intentionally disallows private EC import; this is not an
    // import-policy test. All key agreement, KDF and cipher operations remain real.
    private suspend fun key(device: Boolean): Key {
        val ecdh = CryptographyProvider.Default.get(ECDH)
        val privateKey = ecdh.privateKeyDecoder(EC.Curve.P256).decodeFromByteArray(
            EC.PrivateKey.Format.DER, (if (device) V.EPHEMERAL_DEVICE_PKCS8 else V.EPHEMERAL_READER_PKCS8).hexToByteArray())
        return object : Key {
            override val id = KeyId(if (device) "public-vector-device" else "public-vector-reader")
            override val spec = KeySpec.Ec(EcCurve.P256)
            override val usages = setOf(KeyUsage.KEY_AGREEMENT)
            override val capabilities = KeyCapabilities(
                keyAgreementAlgorithms = setOf(KeyAgreementAlgorithm.Ecdh),
                keyAgreement = KeyAgreement { peer, algorithm ->
                    require(algorithm == KeyAgreementAlgorithm.Ecdh)
                    require(peer is EncodedKey.Jwk && !peer.privateMaterial)
                    val publicKey = ecdh.publicKeyDecoder(EC.Curve.P256)
                        .decodeFromByteArray(EC.PublicKey.Format.JWK, peer.data.toByteArray())
                    BinaryData(privateKey.sharedSecretGenerator().generateSharedSecretToByteArray(publicKey))
                })
        }
    }
}
