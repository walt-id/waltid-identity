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
import id.walt.crypto2.providers.CryptoOperation
import id.walt.crypto2.providers.CryptoRequirement
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
import kotlin.test.assertEquals
import kotlin.test.assertFails
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

class BouncyCastleSecp256k1SoftwareKeyProviderTest {
    private val provider = BouncyCastleSecp256k1SoftwareKeyProvider()
    private val runtime = CryptoRuntime(listOf(provider))
    private val p1363 = SignatureAlgorithm.Ecdsa(DigestAlgorithm.SHA_256)
    private val der = SignatureAlgorithm.Ecdsa(DigestAlgorithm.SHA_256, EcdsaSignatureEncoding.DER)

    @Test
    fun `generate export import sign and verify ES256K across P1363 and DER boundaries`() = runTest {
        val key = generate()
        val message = "crypto2-secp256k1".encodeToByteArray()
        val publicJwk = assertIs<EncodedKey.Jwk>(key.capabilities.publicKeyExporter!!.exportPublicKey())
        val privateJwk = assertIs<EncodedKey.Jwk>(key.capabilities.privateKeyExporter!!.exportPrivateKey())

        assertEquals(setOf(p1363, der), key.capabilities.signatureAlgorithms)
        assertSecp256k1Jwk(publicJwk, private = false)
        assertSecp256k1Jwk(privateJwk, private = true)

        val p1363Signature = key.capabilities.signer!!.sign(message, p1363)
        assertEquals(64, p1363Signature.size)
        assertTrue(key.capabilities.verifier!!.verify(message, p1363Signature, p1363))
        assertTrue(
            key.capabilities.verifier!!.verify(
                message,
                EcdsaSignatureCodec.p1363ToDer(p1363Signature, 32),
                der,
            ),
        )

        val derSignature = key.capabilities.signer!!.sign(message, der)
        assertEquals(0x30, derSignature.first().toInt())
        assertTrue(key.capabilities.verifier!!.verify(message, derSignature, der))
        assertTrue(
            key.capabilities.verifier!!.verify(
                message,
                EcdsaSignatureCodec.derToP1363(derSignature, 32),
                p1363,
            ),
        )

        val imported = assertIs<SoftwareKey>(
            runtime.restore(
                privateJwk.toStoredSoftwareKey(
                    id = KeyId("imported"),
                    usages = setOf(KeyUsage.SIGN, KeyUsage.VERIFY),
                ),
            ),
        )
        val importedSignature = imported.capabilities.signer!!.sign(message, p1363)
        assertTrue(imported.capabilities.verifier!!.verify(message, importedSignature, p1363))

        val pkcs8 = key.capabilities.privateKeyExporter!!.exportPrivateKey(KeyEncodingFormat.PKCS8_DER)
        val spki = key.capabilities.publicKeyExporter!!.exportPublicKey(KeyEncodingFormat.SPKI_DER)
        val privateDerKey = assertIs<SoftwareKey>(runtime.restore(key.storedKey.copy(material = pkcs8)))
        val publicDerKey = assertIs<SoftwareKey>(
            runtime.restore(key.storedKey.copy(usages = setOf(KeyUsage.VERIFY), material = spki)),
        )
        val keyDerSignature = privateDerKey.capabilities.signer!!.sign(message, p1363)
        assertTrue(publicDerKey.capabilities.verifier!!.verify(message, keyDerSignature, p1363))
    }

    @Test
    fun `Wycheproof secp256k1 SHA-256 vector verifies as DER and P1363`() = runTest {
        // C2SP Wycheproof ecdsa_secp256k1_sha256_test.json, tcId 2.
        val publicJwk = publicJwk(
            curve = "secp256k1",
            algorithm = "ES256K",
            x = "782c8ed17e3b2a783b5464f33b09652a71c678e05ec51e84e2bcfc663a3de963",
            y = "af9acb4280b8c7f7c42f4ef9aba6245ec1ec1712fd38a0fa96418d8cd6aa6152",
        )
        val key = assertIs<SoftwareKey>(
            runtime.restore(publicJwk.toStoredSoftwareKey(KeyId("wycheproof-2"), setOf(KeyUsage.VERIFY))),
        )
        val message = "4d7367".hexToByteArray()
        val derSignature = "30450220109cd8ae0374358984a8249c0a843628f2835ffad1df1a9a69aa2fe72355545c" +
            "022100ac6f00daf53bd8b1e34da329359b6e08019c5b037fed79ee383ae39f85a159c6"
        val derBytes = derSignature.hexToByteArray()
        val p1363Bytes = EcdsaSignatureCodec.derToP1363(derBytes, 32)

        assertTrue(key.capabilities.verifier!!.verify(message, derBytes, der))
        assertTrue(key.capabilities.verifier!!.verify(message, p1363Bytes, p1363))
        p1363Bytes[0] = (p1363Bytes[0].toInt() xor 1).toByte()
        assertFalse(key.capabilities.verifier!!.verify(message, p1363Bytes, p1363))
    }

    @Test
    fun `provider and JWK validation reject wrong curves and algorithms`() = runTest {
        assertFalse(
            provider.supports(
                CryptoRequirement(
                    operation = CryptoOperation.SIGN,
                    spec = KeySpec.Ec(EcCurve.P256),
                    usages = setOf(KeyUsage.SIGN),
                    signatureAlgorithm = p1363,
                ),
            ),
        )
        assertFalse(
            provider.supports(
                CryptoRequirement(
                    operation = CryptoOperation.SIGN,
                    spec = KeySpec.Ec(EcCurve.SECP256K1),
                    usages = setOf(KeyUsage.SIGN),
                    signatureAlgorithm = SignatureAlgorithm.Ecdsa(DigestAlgorithm.SHA_384),
                ),
            ),
        )
        assertFailsWith<IllegalArgumentException> {
            generate(metadataAlgorithm = "ES256")
        }
        assertFailsWith<IllegalArgumentException> {
            publicJwk("secp256k1", "ES256", VECTOR_X, VECTOR_Y)
                .toStoredSoftwareKey(KeyId("wrong-alg"), setOf(KeyUsage.VERIFY))
        }
        assertFailsWith<IllegalArgumentException> {
            publicJwk("P-256", "ES256K", VECTOR_X, VECTOR_Y)
                .toStoredSoftwareKey(KeyId("wrong-curve"), setOf(KeyUsage.VERIFY))
        }

        val key = generate()
        assertFailsWith<IllegalArgumentException> {
            key.capabilities.signer!!.sign(
                byteArrayOf(1),
                SignatureAlgorithm.Ecdsa(DigestAlgorithm.SHA_384),
            )
        }
    }

    @Test
    fun `private secp256k1 JWK rejects mismatched private and public members`() = runTest {
        val key = generate()
        val privateJwk = assertIs<EncodedKey.Jwk>(key.capabilities.privateKeyExporter!!.exportPrivateKey())

        assertFails { runtime.restore(key.storedKey.copy(material = privateJwk.mutate("d"))) }
        assertFails { runtime.restore(key.storedKey.copy(material = privateJwk.mutate("x"))) }
    }

    private suspend fun generate(metadataAlgorithm: String = "ES256K"): SoftwareKey =
        runtime.generateSoftwareKey(
            GenerateSoftwareKeyRequest(
                id = KeyId("secp256k1"),
                spec = KeySpec.Ec(EcCurve.SECP256K1),
                usages = setOf(KeyUsage.SIGN, KeyUsage.VERIFY),
                metadata = mapOf(JWK_ALGORITHM_METADATA_KEY to metadataAlgorithm),
            ),
        )

    private fun assertSecp256k1Jwk(jwk: EncodedKey.Jwk, private: Boolean) {
        val json = Json.parseToJsonElement(jwk.data.toByteArray().decodeToString()) as JsonObject
        assertEquals("EC", json.getValue("kty").jsonPrimitive.content)
        assertEquals("secp256k1", json.getValue("crv").jsonPrimitive.content)
        assertEquals("ES256K", json.getValue("alg").jsonPrimitive.content)
        assertEquals(private, "d" in json)
    }

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

    private fun publicJwk(curve: String, algorithm: String, x: String, y: String): EncodedKey.Jwk {
        val base64Url = Base64.UrlSafe.withPadding(Base64.PaddingOption.ABSENT)
        return EncodedKey.Jwk(
            data = BinaryData(
                """{"kty":"EC","crv":"$curve","x":"${base64Url.encode(x.hexToByteArray())}","y":"${base64Url.encode(y.hexToByteArray())}","alg":"$algorithm"}"""
                    .encodeToByteArray(),
            ),
            privateMaterial = false,
        )
    }

    private companion object {
        const val VECTOR_X = "782c8ed17e3b2a783b5464f33b09652a71c678e05ec51e84e2bcfc663a3de963"
        const val VECTOR_Y = "af9acb4280b8c7f7c42f4ef9aba6245ec1ec1712fd38a0fa96418d8cd6aa6152"
    }
}
