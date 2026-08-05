package id.walt.crypto2.keys

import id.walt.crypto2.CryptoRuntime
import id.walt.crypto2.providers.CryptoOperation
import id.walt.crypto2.providers.CryptoRequirement
import id.walt.crypto2.providers.GenerateSoftwareKeyRequest
import id.walt.crypto2.providers.cryptography.CryptographySoftwareKeyProvider
import id.walt.crypto2.serialization.BinaryData
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlin.io.encoding.Base64
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertSame

class JwkStoredKeyTest {
    private val provider = CryptographySoftwareKeyProvider()
    private val runtime = CryptoRuntime(listOf(provider))

    @Test
    fun `public JWK becomes typed restorable verification key`() = runTest {
        val generated = runtime.generateSoftwareKey(
            GenerateSoftwareKeyRequest(
                id = KeyId("source"),
                spec = KeySpec.Ec(EcCurve.P256),
                usages = setOf(KeyUsage.SIGN, KeyUsage.VERIFY),
            )
        )
        val publicJwk = assertIs<EncodedKey.Jwk>(
            assertNotNull(generated.capabilities.publicKeyExporter).exportPublicKey()
        )

        val stored = publicJwk.toStoredSoftwareKey(KeyId("verification"), setOf(KeyUsage.VERIFY))

        assertEquals(KeySpec.Ec(EcCurve.P256), stored.spec)
        assertNotNull(runtime.restore(stored).capabilities.verifier)
    }

    @Test
    fun `RSA modulus determines exact key size`() {
        val modulus = ByteArray(384) { 0xff.toByte() }
        val jwk = EncodedKey.Jwk(
            BinaryData(
                """{"kty":"RSA","n":"${base64Url.encode(modulus)}","e":"AQAB"}""".encodeToByteArray()
            ),
            privateMaterial = false,
        )

        assertEquals(KeySpec.Rsa(3072), jwk.inferKeySpec())
    }

    @Test
    fun `private metadata and private usages fail closed`() {
        val publicJwk = EncodedKey.Jwk(
            BinaryData("""{"kty":"OKP","crv":"Ed25519","x":"AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA"}""".encodeToByteArray()),
            privateMaterial = true,
        )

        assertFailsWith<IllegalArgumentException> {
            publicJwk.toStoredSoftwareKey(KeyId("mislabeled"), setOf(KeyUsage.VERIFY))
        }
        assertFailsWith<IllegalArgumentException> {
            publicJwk.copy(privateMaterial = false)
                .toStoredSoftwareKey(KeyId("public-signing"), setOf(KeyUsage.SIGN))
        }
    }

    @Test
    fun `private JWK cannot be persisted for public-only usage`() = runTest {
        val generated = runtime.generateSoftwareKey(
            GenerateSoftwareKeyRequest(
                id = KeyId("private"),
                spec = KeySpec.Ec(EcCurve.P256),
                usages = setOf(KeyUsage.SIGN, KeyUsage.VERIFY),
            )
        )
        val privateJwk = assertIs<EncodedKey.Jwk>(generated.storedKey.material)

        assertFailsWith<IllegalArgumentException> {
            privateJwk.toStoredSoftwareKey(KeyId("verification"), setOf(KeyUsage.VERIFY))
        }
    }

    @Test
    fun `JWK restrictions and coordinates are validated before persistence`() = runTest {
        val generated = runtime.generateSoftwareKey(
            GenerateSoftwareKeyRequest(
                id = KeyId("restricted"),
                spec = KeySpec.Ec(EcCurve.P256),
                usages = setOf(KeyUsage.SIGN, KeyUsage.VERIFY),
            )
        )
        val privateJwk = assertIs<EncodedKey.Jwk>(generated.storedKey.material)
        val parsed = Json.parseToJsonElement(privateJwk.data.toByteArray().decodeToString()) as JsonObject
        val signOnly = privateJwk.copy(
            data = BinaryData(
                JsonObject(parsed + ("key_ops" to JsonArray(listOf(JsonPrimitive("sign")))))
                    .toString().encodeToByteArray()
            )
        )
        val publicJwk = assertIs<EncodedKey.Jwk>(
            assertNotNull(generated.capabilities.publicKeyExporter).exportPublicKey()
        )
        val publicParsed = Json.parseToJsonElement(publicJwk.data.toByteArray().decodeToString()) as JsonObject
        val invalidCoordinate = EncodedKey.Jwk(
            data = BinaryData(
                JsonObject(publicParsed + ("x" to JsonPrimitive("AA"))).toString().encodeToByteArray()
            ),
            privateMaterial = false,
        )

        assertFailsWith<IllegalArgumentException> {
            signOnly.toStoredSoftwareKey(KeyId("restricted"), setOf(KeyUsage.SIGN, KeyUsage.VERIFY))
        }
        assertFailsWith<IllegalArgumentException> {
            invalidCoordinate.toStoredSoftwareKey(KeyId("invalid-coordinate"), setOf(KeyUsage.VERIFY))
        }
    }

    @Test
    fun `descriptor preserves original JWK metadata`() = runTest {
        val generated = runtime.generateSoftwareKey(
            GenerateSoftwareKeyRequest(
                id = KeyId("metadata"),
                spec = KeySpec.Edwards(EdwardsCurve.ED25519),
                usages = setOf(KeyUsage.SIGN),
            )
        )
        val source = assertIs<EncodedKey.Jwk>(generated.storedKey.material)
        val parsed = Json.parseToJsonElement(source.data.toByteArray().decodeToString()) as JsonObject
        val withMetadata = source.copy(
            data = BinaryData(
                JsonObject(parsed + mapOf("kid" to JsonPrimitive("kid"), "alg" to JsonPrimitive("EdDSA")))
                    .toString().encodeToByteArray()
            )
        )

        val stored = withMetadata.toStoredSoftwareKey(KeyId("metadata"), setOf(KeyUsage.SIGN))

        assertSame(withMetadata, stored.material)
        assertEquals("EdDSA", stored.metadata[JWK_ALGORITHM_METADATA_KEY])
        if (
            provider.supports(
                CryptoRequirement(
                    operation = CryptoOperation.IMPORT_KEY,
                    spec = stored.spec,
                    usages = stored.usages,
                    keyEncoding = KeyEncodingFormat.JWK,
                ),
            )
        ) {
            assertNotNull(runtime.restore(stored).capabilities.signer)
        }
    }

    @Test
    fun `secp256k1 JWK requires ES256K when alg is present`() {
        val coordinate = base64Url.encode(ByteArray(32))

        fun jwk(curve: String, algorithm: String) = EncodedKey.Jwk(
            BinaryData(
                """{"kty":"EC","crv":"$curve","x":"$coordinate","y":"$coordinate","alg":"$algorithm"}"""
                    .encodeToByteArray(),
            ),
            privateMaterial = false,
        )

        assertEquals(
            KeySpec.Ec(EcCurve.SECP256K1),
            jwk("secp256k1", "ES256K").toStoredSoftwareKey(KeyId("valid"), setOf(KeyUsage.VERIFY)).spec,
        )
        assertFailsWith<IllegalArgumentException> {
            jwk("secp256k1", "ES256").toStoredSoftwareKey(KeyId("wrong-alg"), setOf(KeyUsage.VERIFY))
        }
        assertFailsWith<IllegalArgumentException> {
            jwk("P-256", "ES256K").toStoredSoftwareKey(KeyId("wrong-curve"), setOf(KeyUsage.VERIFY))
        }
    }

    private companion object {
        val base64Url = Base64.UrlSafe.withPadding(Base64.PaddingOption.ABSENT)
    }
}
