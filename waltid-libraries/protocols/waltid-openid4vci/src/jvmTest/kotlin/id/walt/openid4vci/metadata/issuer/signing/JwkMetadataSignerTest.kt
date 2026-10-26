package id.walt.openid4vci.metadata.issuer.signing

import id.walt.crypto.keys.KeyType
import id.walt.crypto.keys.jwk.JWKKey
import id.walt.crypto.utils.JwsUtils.decodeJws
import id.walt.crypto2.jose.Jwk
import id.walt.crypto2.CryptoRuntime
import id.walt.crypto2.jose.CompactJws
import id.walt.crypto2.jose.JwsAlgorithm
import id.walt.crypto2.keys.KeyId
import id.walt.crypto2.keys.KeyUsage
import id.walt.crypto2.keys.toStoredSoftwareKey
import id.walt.crypto2.providers.cryptography.defaultSoftwareKeyProviders
import id.walt.crypto2.keys.EncodedKey
import id.walt.crypto2.serialization.BinaryData
import id.walt.openid4vci.metadata.issuer.signing.MetadataSigningMethod
import id.walt.openid4vci.metadata.issuer.CredentialIssuerMetadata
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class JwkMetadataSignerTest {
    private val metadata = CredentialIssuerMetadata(
        credentialIssuer = "https://issuer.example/openid4vci",
        credentialEndpoint = "https://issuer.example/openid4vci/credential",
        credentialConfigurationsSupported = emptyMap(),
    )

    @Test
    fun `supports asymmetric key families and publishes only the public jwk`() = runTest {
        for ((type, algorithm) in listOf(KeyType.secp256r1 to "ES256", KeyType.RSA to "RS256", KeyType.Ed25519 to "EdDSA")) {
            val key = JWKKey.generate(type)
            val jwk = JsonObject(key.exportJWKObject() + mapOf(
                "kid" to JsonPrimitive("metadata-key"),
                "alg" to JsonPrimitive(algorithm),
                "use" to JsonPrimitive("sig"),
                "key_ops" to JsonArray(listOf(JsonPrimitive("sign"))),
            ))
            val config = MetadataSigningMethod.StaticJwk(jwk)
            val signer = JwkMetadataSigner.load(config)
            val jwt = signer.sign(metadata)
            assertTrue(key.getPublicKey().verifyJws(jwt).isSuccess)
            val header = jwt.decodeJws().header
            assertEquals(algorithm, header["alg"]?.jsonPrimitive?.content)
            assertEquals("metadata-key", header["kid"]?.jsonPrimitive?.content)
            assertNull(header["x5c"])
            assertFalse(Jwk.containsPrivateMaterial(header["jwk"]!!.jsonObject))
            assertEquals("metadata-key", header["jwk"]!!.jsonObject["kid"]?.jsonPrimitive?.content)
            val publicJwk = EncodedKey.Jwk(BinaryData(header["jwk"].toString().encodeToByteArray()), false)
            val verificationKey = CryptoRuntime(defaultSoftwareKeyProviders()).restore(
                publicJwk.toStoredSoftwareKey(KeyId("verifier"), setOf(KeyUsage.VERIFY)),
            )
            CompactJws.verify(jwt, verificationKey, JwsAlgorithm.parse(algorithm))
        }
    }

    @Test
    fun `algorithm is optional and honors declared alg before the key default`() = runTest {
        for ((type, expected) in listOf(KeyType.secp256r1 to "ES256", KeyType.Ed25519 to "Ed25519", KeyType.RSA to "RS256")) {
            val jwk = JsonObject(JWKKey.generate(type).exportJWKObject() - "alg")
            val jwt = JwkMetadataSigner.load(MetadataSigningMethod.StaticJwk(jwk = jwk)).sign(metadata)
            assertEquals(expected, jwt.decodeJws().header["alg"]?.jsonPrimitive?.content)
        }
        val jwk = JsonObject(JWKKey.generate(KeyType.RSA).exportJWKObject() + ("alg" to JsonPrimitive("PS256")))
        val declared = JwkMetadataSigner.load(MetadataSigningMethod.StaticJwk(jwk = jwk)).sign(metadata)
        assertEquals("PS256", declared.decodeJws().header["alg"]?.jsonPrimitive?.content)
    }

    @Test
    fun `inline jwk signs without file access and redacts string output`() = runTest {
        val key = JWKKey.generate(KeyType.secp256r1)
        val jwk = key.exportJWKObject()
        val config = MetadataSigningMethod.StaticJwk(jwk = jwk)
        val jwt = JwkMetadataSigner.load(config).sign(metadata)
        assertTrue(key.getPublicKey().verifyJws(jwt).isSuccess)
        assertFalse(config.toString().contains(jwk.getValue("d").jsonPrimitive.content))
        val malformed = MetadataSigningMethod.StaticJwk(jwk = JsonObject(jwk + ("d" to JsonPrimitive("secret-inline-key"))))
        val error = assertFailsWith<IllegalArgumentException> { JwkMetadataSigner.load(malformed) }
        assertFalse(error.stackTraceToString().contains("secret-inline-key"))
    }

    @Test
    fun `missing kid uses the stable public jwk thumbprint`() = runTest {
        val jwk = JsonObject(JWKKey.generate(KeyType.secp256r1).exportJWKObject() - "kid")
        val jwt = JwkMetadataSigner.load(MetadataSigningMethod.StaticJwk(jwk)).sign(metadata)
        val expected = Jwk.sha256Thumbprint(EncodedKey.Jwk(BinaryData(jwk.toString().encodeToByteArray()), true))
        assertEquals(expected, jwt.decodeJws().header["kid"]?.jsonPrimitive?.content)
    }

    @Test
    fun `invalid jwks algorithms and restrictions fail without exposing material`() = runTest {
        val key = JWKKey.generate(KeyType.secp256r1)
        val privateJwk = JsonObject(key.exportJWKObject() - "alg")
        val other = JWKKey.generate(KeyType.secp256r1).exportJWKObject()
        val invalid = listOf(
            JsonObject(privateJwk - "d"),
            JsonObject(privateJwk + ("use" to JsonPrimitive("enc"))),
            JsonObject(privateJwk + ("alg" to JsonPrimitive("ES384"))),
            JsonObject(privateJwk + ("kid" to JsonPrimitive(""))),
            JsonObject(privateJwk + ("key_ops" to JsonArray(listOf(JsonPrimitive("verify"))))),
            JsonObject(privateJwk + mapOf("x" to other.getValue("x"), "y" to other.getValue("y"))),
            JsonObject(mapOf("kty" to JsonPrimitive("oct"), "k" to JsonPrimitive("c2VjcmV0"))),
        )
        for (jwk in invalid) {
            val error = assertFailsWith<IllegalArgumentException> { JwkMetadataSigner.load(MetadataSigningMethod.StaticJwk(jwk)) }
            assertTrue(error.message!!.startsWith("Invalid signedMetadata configuration:"))
            assertFalse(error.message!!.contains(privateJwk.getValue("d").jsonPrimitive.content))
            assertNull(error.cause)
        }
        for (algorithm in listOf("none", "HS256", "ES384", "unknown")) {
            assertFailsWith<IllegalArgumentException> { JwkMetadataSigner.load(MetadataSigningMethod.StaticJwk(JsonObject(privateJwk + ("alg" to JsonPrimitive(algorithm))))) }
        }
    }

    @Test
    fun `JWKS wrappers are not accepted as a single private JWK`() = runTest {
        val key = JWKKey.generate(KeyType.secp256r1).exportJWKObject()
        val wrapped = JsonObject(mapOf("keys" to JsonArray(listOf(key))))
        val error = assertFailsWith<IllegalArgumentException> { JwkMetadataSigner.load(MetadataSigningMethod.StaticJwk(wrapped)) }
        assertFalse(error.stackTraceToString().contains(key.getValue("d").jsonPrimitive.content))
    }
}
