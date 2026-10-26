package id.walt.openid4vci.metadata.issuer

import id.walt.crypto.keys.KeyType
import id.walt.crypto.keys.jwk.JWKKey
import id.walt.crypto.utils.JwsUtils.decodeJws
import id.walt.crypto2.CryptoRuntime
import id.walt.crypto2.jose.CompactJws
import id.walt.crypto2.jose.JwsAlgorithm
import id.walt.crypto2.keys.EcCurve
import id.walt.crypto2.keys.KeyId
import id.walt.crypto2.keys.KeySpec
import id.walt.crypto2.keys.KeyUsage
import id.walt.crypto2.providers.GenerateSoftwareKeyRequest
import id.walt.crypto2.providers.cryptography.defaultSoftwareKeyProviders
import id.walt.openid4vci.CredentialFormat
import id.walt.openid4vci.tokens.jwt.JwtHeaderParams
import id.walt.openid4vci.tokens.jwt.JwtPayloadClaims
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.jsonArray
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.test.assertNull
import kotlin.time.Instant

class CredentialIssuerMetadataJwtTest {
    @Test
    fun `crypto2 preserves jwk behavior and supports certificate header without changing payload`() = runTest {
        val key = crypto2Key()
        val issuedAt = Instant.fromEpochSeconds(1_716_000_000)
        val metadata = metadata(mapOf("example_extension" to JsonPrimitive("preserved")))
        val original = metadata.toSignedJwt(key, JwsAlgorithm.ES256, issuedAt, "published-kid")
        val originalHeader = original.decodeJws().header
        assertNull(originalHeader["x5c"])
        assertFalse("d" in originalHeader["jwk"]!!.jsonObject)
        // Certificate parsing and key matching are service responsibilities; this test checks header transport.
        val chain = listOf("+/8=", "AQID")
        val signed = metadata.toSignedJwt(key, JwsAlgorithm.ES256, chain, issuedAt, "published-kid")
        val header = signed.decodeJws().header
        assertEquals(chain, header["x5c"]!!.jsonArray.map { it.jsonPrimitive.content })
        assertNull(header["jwk"])
        assertEquals("published-kid", header["kid"]?.jsonPrimitive?.content)
        assertEquals(CredentialIssuerMetadataJwt.TYPE, header["typ"]?.jsonPrimitive?.content)
        assertEquals(original.decodeJws().payload, signed.decodeJws().payload)
        CompactJws.verify(original, key, JwsAlgorithm.ES256)
        CompactJws.verify(signed, key, JwsAlgorithm.ES256)
        assertEquals(original.decodeJws().header,
            metadata.toSignedJwt(key, JwsAlgorithm.ES256, null, issuedAt, "published-kid").decodeJws().header)
    }

    @Test
    fun `crypto2 certificate overload retains input restrictions`() = runTest {
        val key = crypto2Key()
        assertFailsWith<IllegalArgumentException> {
            metadata().toSignedJwt(key, JwsAlgorithm.ES256, emptyList())
        }
        assertFailsWith<IllegalArgumentException> {
            metadata().toSignedJwt(key, JwsAlgorithm.ES384, listOf("AQID"))
        }
        // Crypto2's algorithm type excludes symmetric and unsigned JWS entirely.
        assertFailsWith<IllegalArgumentException> { JwsAlgorithm.parse("HS256") }
        assertFailsWith<IllegalArgumentException> { JwsAlgorithm.parse("none") }
        assertFailsWith<IllegalArgumentException> {
            metadata().toSignedJwt(key, JwsAlgorithm.ES256, listOf("AQID"), keyId = "")
        }
        CredentialIssuerMetadataJwt.reservedPayloadClaims.forEach { claim ->
            assertFailsWith<IllegalArgumentException> {
                metadata(mapOf(claim to JsonPrimitive("collision"))).toSignedJwt(key, JwsAlgorithm.ES256, listOf("AQID"))
            }
        }
    }

    private suspend fun crypto2Key() = CryptoRuntime(defaultSoftwareKeyProviders()).generateSoftwareKey(
        GenerateSoftwareKeyRequest(
            id = KeyId("metadata-test"),
            spec = KeySpec.Ec(EcCurve.P256),
            usages = setOf(KeyUsage.SIGN, KeyUsage.VERIFY),
        ),
    )

    @Test
    fun `signs complete issuer metadata with required headers and claims`() = runTest {
        val signingKey = JWKKey.generate(KeyType.secp256r1)
        val issuedAt = Instant.fromEpochSeconds(1_716_000_000)
        val metadata = metadata(
            customParameters = mapOf("example_extension" to JsonPrimitive("example-value")),
        )

        val signedMetadata = metadata.toSignedJwt(signingKey, issuedAt)
        val decoded = signedMetadata.decodeJws()

        assertEquals(signingKey.keyType.jwsAlg, decoded.header[JwtHeaderParams.ALGORITHM]?.jsonPrimitive?.content)
        assertEquals(CredentialIssuerMetadataJwt.TYPE, decoded.header[JwtHeaderParams.TYPE]?.jsonPrimitive?.content)
        assertEquals(signingKey.getKeyId(), decoded.header[JwtHeaderParams.KEY_ID]?.jsonPrimitive?.content)
        val publicJwk = requireNotNull(decoded.header[JwtHeaderParams.JSON_WEB_KEY]?.jsonObject)
        assertEquals(signingKey.getKeyId(), publicJwk["kid"]?.jsonPrimitive?.content)
        assertEquals(signingKey.keyType.jwsAlg, publicJwk["alg"]?.jsonPrimitive?.content)
        assertEquals("sig", publicJwk["use"]?.jsonPrimitive?.content)
        signingKey.getPublicKey().exportJWKObject()
            .filterKeys { it !in setOf("kid", "alg", "use") }
            .forEach { (name, value) -> assertEquals(value, publicJwk[name]) }
        assertFalse("d" in publicJwk)

        val payload = decoded.payload
        assertEquals(metadata.credentialIssuer, payload[JwtPayloadClaims.ISSUER]?.jsonPrimitive?.content)
        assertEquals(metadata.credentialIssuer, payload[JwtPayloadClaims.SUBJECT]?.jsonPrimitive?.content)
        assertEquals(issuedAt.epochSeconds, payload[JwtPayloadClaims.ISSUED_AT]?.jsonPrimitive?.content?.toLong())
        assertFalse(JwtPayloadClaims.EXPIRATION in payload)
        assertEquals("example-value", payload["example_extension"]?.jsonPrimitive?.content)

        val expectedMetadataClaims = Json
            .encodeToJsonElement(CredentialIssuerMetadata.serializer(), metadata)
            .jsonObject
        expectedMetadataClaims.forEach { (name, value) ->
            assertEquals(value, payload[name], "Signed payload must contain metadata parameter $name unchanged")
        }

        val verifiedPayload = signingKey.getPublicKey().verifyJws(signedMetadata).getOrThrow().jsonObject
        assertEquals(payload, verifiedPayload)
        assertEquals(3, signedMetadata.split('.').size)
    }

    @Test
    fun `supports representative asymmetric signing key families`() = runTest {
        listOf(KeyType.Ed25519, KeyType.secp256r1, KeyType.RSA).forEach { keyType ->
            val key = JWKKey.generate(keyType)
            val signedMetadata = metadata().toSignedJwt(key, Instant.fromEpochSeconds(1))

            assertTrue(key.getPublicKey().verifyJws(signedMetadata).isSuccess, "Expected $keyType signature to verify")
            assertEquals(
                keyType.jwsAlg,
                signedMetadata.decodeJws().header[JwtHeaderParams.ALGORITHM]?.jsonPrimitive?.content,
            )
        }
    }

    @Test
    fun `delegates signing capability validation to the key`() = runTest {
        val publicKey = JWKKey.generate(KeyType.secp256r1).getPublicKey()

        val error = assertFailsWith<IllegalStateException> {
            metadata().toSignedJwt(publicKey)
        }

        assertTrue(error.message.orEmpty().contains("No private key", ignoreCase = true))
    }

    @Test
    fun `rejects metadata extensions that collide with signed metadata claims`() = runTest {
        val signingKey = JWKKey.generate(KeyType.secp256r1)

        CredentialIssuerMetadataJwt.reservedPayloadClaims.forEach { reservedClaim ->
            val metadata = metadata(customParameters = mapOf(reservedClaim to JsonPrimitive("collision")))

            val error = assertFailsWith<IllegalArgumentException> {
                metadata.toSignedJwt(signingKey)
            }
            assertTrue(reservedClaim in error.message.orEmpty())
        }
    }

    private fun metadata(
        customParameters: Map<String, kotlinx.serialization.json.JsonElement>? = null,
    ) = CredentialIssuerMetadata(
        credentialIssuer = "https://issuer.example.com/tenant",
        authorizationServers = listOf("https://authorization.example.com"),
        credentialEndpoint = "https://issuer.example.com/tenant/credential",
        nonceEndpoint = "https://issuer.example.com/tenant/nonce",
        credentialConfigurationsSupported = mapOf(
            "identity" to CredentialConfiguration(format = CredentialFormat.SD_JWT_VC),
        ),
        customParameters = customParameters,
    )
}
