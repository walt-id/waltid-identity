package id.walt.crypto2.providers.cryptography

import id.walt.crypto2.CryptoRuntime
import id.walt.crypto2.algorithms.DigestAlgorithm
import id.walt.crypto2.algorithms.EcdsaSignatureCodec
import id.walt.crypto2.algorithms.EcdsaSignatureEncoding
import id.walt.crypto2.algorithms.SignatureAlgorithm
import id.walt.crypto2.keys.EcCurve
import id.walt.crypto2.keys.EncodedKey
import id.walt.crypto2.keys.JWK_ALGORITHM_METADATA_KEY
import id.walt.crypto2.keys.KeyEncodingFormat
import id.walt.crypto2.keys.KeyId
import id.walt.crypto2.keys.KeySpec
import id.walt.crypto2.keys.KeyUsage
import id.walt.crypto2.keys.SoftwareKey
import id.walt.crypto2.keys.toStoredSoftwareKey
import id.walt.crypto2.providers.GenerateSoftwareKeyRequest
import id.walt.crypto2.serialization.BinaryData
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonPrimitive
import kotlin.io.encoding.Base64
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFails
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

class Openssl3Secp256k1SoftwareKeyProviderTest {
    private val provider = Openssl3Secp256k1SoftwareKeyProvider()
    private val runtime = CryptoRuntime(listOf(provider))
    private val p1363 = SignatureAlgorithm.Ecdsa(DigestAlgorithm.SHA_256)
    private val der = SignatureAlgorithm.Ecdsa(DigestAlgorithm.SHA_256, EcdsaSignatureEncoding.DER)

    @Test
    fun `OpenSSL secp256k1 round trips private and public keys through JWK and DER`() = runTest {
        val key = generate()
        val message = "openssl-secp256k1".encodeToByteArray()
        val publicJwk = assertIs<EncodedKey.Jwk>(key.capabilities.publicKeyExporter!!.exportPublicKey())
        val privateJwk = assertIs<EncodedKey.Jwk>(key.capabilities.privateKeyExporter!!.exportPrivateKey())
        val spki = assertIs<EncodedKey.SpkiDer>(
            key.capabilities.publicKeyExporter!!.exportPublicKey(KeyEncodingFormat.SPKI_DER),
        )
        val pkcs8 = assertIs<EncodedKey.Pkcs8Der>(
            key.capabilities.privateKeyExporter!!.exportPrivateKey(KeyEncodingFormat.PKCS8_DER),
        )

        val privateDerKey = assertIs<SoftwareKey>(runtime.restore(key.storedKey.copy(material = pkcs8)))
        val publicDerKey = assertIs<SoftwareKey>(
            runtime.restore(key.storedKey.copy(usages = setOf(KeyUsage.VERIFY), material = spki)),
        )
        val privateJwkKey = assertIs<SoftwareKey>(
            runtime.restore(
                privateJwk.toStoredSoftwareKey(
                    id = KeyId("openssl-private-jwk"),
                    usages = setOf(KeyUsage.SIGN, KeyUsage.VERIFY),
                ),
            ),
        )

        assertEquals(setOf(p1363, der), key.capabilities.signatureAlgorithms)
        listOf(key, privateDerKey, privateJwkKey).forEach { signingKey ->
            val p1363Signature = signingKey.capabilities.signer!!.sign(message, p1363)
            assertEquals(64, p1363Signature.size)
            assertTrue(publicDerKey.capabilities.verifier!!.verify(message, p1363Signature, p1363))
            assertTrue(
                publicDerKey.capabilities.verifier!!.verify(
                    message,
                    EcdsaSignatureCodec.p1363ToDer(p1363Signature, 32),
                    der,
                ),
            )

            val derSignature = signingKey.capabilities.signer!!.sign(message, der)
            assertEquals(0x30, derSignature.first().toInt())
            assertTrue(publicDerKey.capabilities.verifier!!.verify(message, derSignature, der))
            assertTrue(
                publicDerKey.capabilities.verifier!!.verify(
                    message,
                    EcdsaSignatureCodec.derToP1363(derSignature, 32),
                    p1363,
                ),
            )
        }

        val importedPublicJwk = assertIs<EncodedKey.Jwk>(privateDerKey.capabilities.publicKeyExporter!!.exportPublicKey())
        assertEquals(publicJwk.member("x"), importedPublicJwk.member("x"))
        assertEquals(publicJwk.member("y"), importedPublicJwk.member("y"))
        assertContentEquals(
            spki.data.toByteArray(),
            privateJwkKey.capabilities.publicKeyExporter!!.exportPublicKey(KeyEncodingFormat.SPKI_DER).data.toByteArray(),
        )
    }

    @Test
    fun `OpenSSL secp256k1 verifies a known DER and P1363 vector`() = runTest {
        val key = assertIs<SoftwareKey>(
            runtime.restore(
                publicJwk(VECTOR_X, VECTOR_Y)
                    .toStoredSoftwareKey(KeyId("openssl-wycheproof-2"), setOf(KeyUsage.VERIFY)),
            ),
        )
        val message = "4d7367".hexToByteArray()
        val derSignature = (
            "30450220109cd8ae0374358984a8249c0a843628f2835ffad1df1a9a69aa2fe72355545c" +
                "022100ac6f00daf53bd8b1e34da329359b6e08019c5b037fed79ee383ae39f85a159c6"
            ).hexToByteArray()
        val p1363Signature = EcdsaSignatureCodec.derToP1363(derSignature, 32)

        assertTrue(key.capabilities.verifier!!.verify(message, derSignature, der))
        assertTrue(key.capabilities.verifier!!.verify(message, p1363Signature, p1363))
        p1363Signature[0] = (p1363Signature[0].toInt() xor 1).toByte()
        assertFalse(key.capabilities.verifier!!.verify(message, p1363Signature, p1363))
    }

    @Test
    fun `OpenSSL secp256k1 rejects malformed signatures and private material`() = runTest {
        val key = generate()
        val privateJwk = assertIs<EncodedKey.Jwk>(key.capabilities.privateKeyExporter!!.exportPrivateKey())
        val message = byteArrayOf(1, 2, 3)
        val malformedDer = byteArrayOf(0x30, 0x01, 0x00)

        assertFails { key.capabilities.verifier!!.verify(message, malformedDer, der) }
        assertFailsWith<IllegalArgumentException> { EcdsaSignatureCodec.derToP1363(malformedDer, 32) }
        assertFails {
            runtime.restore(
                key.storedKey.copy(
                    material = EncodedKey.Pkcs8Der(BinaryData(byteArrayOf(0x30, 0x00))),
                ),
            )
        }
        val spki = key.capabilities.publicKeyExporter!!.exportPublicKey(KeyEncodingFormat.SPKI_DER)
        assertFails {
            runtime.restore(
                key.storedKey.copy(material = EncodedKey.Pkcs8Der(spki.data)),
            )
        }
        assertFails { runtime.restore(key.storedKey.copy(material = privateJwk.mutate("d"))) }
        assertFails { runtime.restore(key.storedKey.copy(material = privateJwk.mutate("x"))) }
    }

    private suspend fun generate(): SoftwareKey = runtime.generateSoftwareKey(
        GenerateSoftwareKeyRequest(
            id = KeyId("openssl-secp256k1"),
            spec = KeySpec.Ec(EcCurve.SECP256K1),
            usages = setOf(KeyUsage.SIGN, KeyUsage.VERIFY),
            metadata = mapOf(JWK_ALGORITHM_METADATA_KEY to "ES256K"),
        ),
    )

    private fun publicJwk(x: String, y: String): EncodedKey.Jwk {
        val base64Url = Base64.UrlSafe.withPadding(Base64.PaddingOption.ABSENT)
        return EncodedKey.Jwk(
            data = BinaryData(
                """{"kty":"EC","crv":"secp256k1","x":"${base64Url.encode(x.hexToByteArray())}","y":"${base64Url.encode(y.hexToByteArray())}","alg":"ES256K"}"""
                    .encodeToByteArray(),
            ),
            privateMaterial = false,
        )
    }

    private fun EncodedKey.Jwk.member(name: String): String =
        (Json.parseToJsonElement(data.toByteArray().decodeToString()) as JsonObject)
            .getValue(name).jsonPrimitive.content

    private fun EncodedKey.Jwk.mutate(name: String): EncodedKey.Jwk {
        val json = Json.parseToJsonElement(data.toByteArray().decodeToString()) as JsonObject
        val base64Url = Base64.UrlSafe.withPadding(Base64.PaddingOption.ABSENT)
        val value = base64Url.decode(json.getValue(name).jsonPrimitive.content)
        value[value.lastIndex] = (value.last().toInt() xor 1).toByte()
        return copy(
            data = BinaryData(
                Json.encodeToString(JsonObject(json + (name to JsonPrimitive(base64Url.encode(value)))))
                    .encodeToByteArray(),
            ),
        )
    }

    private companion object {
        const val VECTOR_X = "782c8ed17e3b2a783b5464f33b09652a71c678e05ec51e84e2bcfc663a3de963"
        const val VECTOR_Y = "af9acb4280b8c7f7c42f4ef9aba6245ec1ec1712fd38a0fa96418d8cd6aa6152"
    }
}
