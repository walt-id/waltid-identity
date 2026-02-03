package id.walt.eudi

import id.walt.crypto.keys.jwk.JWKKey
import id.walt.issuer.issuance.DPoPHandler
import id.walt.oid4vc.util.JwtUtils
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.*
import kotlin.test.*
import kotlin.time.Clock

/**
 * Comprehensive tests for DPoP (Demonstrating Proof of Possession) Handler.
 * Tests RFC 9449 compliance for OAuth 2.0 DPoP.
 */
class DPoPHandlerTest {

    // ==================== JWK Thumbprint Tests ====================

    @Test
    fun testEcKeyThumbprintCalculation() {
        // EC P-256 key thumbprint calculation per RFC 7638
        val ecJwk = buildJsonObject {
            put("kty", JsonPrimitive("EC"))
            put("crv", JsonPrimitive("P-256"))
            put("x", JsonPrimitive("WbbKn2rON2PEXQPxe-FqKDDJMVxEXwvmsO-UFFZH1FE"))
            put("y", JsonPrimitive("BhWbKn2rON2PEXQPxeFqKDDJMVxEXwvmsOUFFZH1FE8"))
        }

        val thumbprint = DPoPHandler.calculateJwkThumbprint(ecJwk)

        assertNotNull(thumbprint)
        assertTrue(thumbprint.length > 20, "Thumbprint should be a reasonable length")
    }

    @Test
    fun testRsaKeyThumbprintCalculation() {
        // RSA key thumbprint calculation
        val rsaJwk = buildJsonObject {
            put("kty", JsonPrimitive("RSA"))
            put("n", JsonPrimitive("0vx7agoebGcQSuuPiLJXZptN9nndrQmbXEps2aiAFbWhM78LhWx4cbbfAAtVT86zwu1RK7aPFFxuhDR1L6tSoc_BJECPebWKRXjBZCiFV4n3oknjhMstn64tZ_2W-5JsGY4Hc5n9yBXArwl93lqt7_RN5w6Cf0h4QyQ5v-65YGjQR0_FDW2QvzqY368QQMicAtaSqzs8KJZgnYb9c7d0zgdAZHzu6qMQvRL5hajrn1n91CbOpbISD08qNLyrdkt-bFTWhAI4vMQFh6WeZu0fM4lFd2NcRwr3XPksINHaQ-G_xBniIqbw0Ls1jF44-csFCur-kEgU8awapJzKnqDKgw"))
            put("e", JsonPrimitive("AQAB"))
        }

        val thumbprint = DPoPHandler.calculateJwkThumbprint(rsaJwk)

        assertNotNull(thumbprint)
        assertTrue(thumbprint.length > 20)
    }

    @Test
    fun testThumbprintConsistency() {
        // Same key should always produce the same thumbprint
        val jwk = buildJsonObject {
            put("kty", JsonPrimitive("EC"))
            put("crv", JsonPrimitive("P-256"))
            put("x", JsonPrimitive("consistentX"))
            put("y", JsonPrimitive("consistentY"))
        }

        val results = (1..10).map { DPoPHandler.calculateJwkThumbprint(jwk) }

        assertTrue(results.all { it == results[0] }, "All thumbprints should be identical")
    }

    @Test
    fun testThumbprintOnlyUsesRequiredFields() {
        // RFC 7638: Only use required members for thumbprint calculation
        // For EC: crv, kty, x, y (alphabetical order)
        val jwkWithExtras = buildJsonObject {
            put("kty", JsonPrimitive("EC"))
            put("crv", JsonPrimitive("P-256"))
            put("x", JsonPrimitive("testX"))
            put("y", JsonPrimitive("testY"))
            put("kid", JsonPrimitive("extra-kid"))
            put("use", JsonPrimitive("sig"))
            put("alg", JsonPrimitive("ES256"))
        }

        val jwkMinimal = buildJsonObject {
            put("kty", JsonPrimitive("EC"))
            put("crv", JsonPrimitive("P-256"))
            put("x", JsonPrimitive("testX"))
            put("y", JsonPrimitive("testY"))
        }

        val thumbprint1 = DPoPHandler.calculateJwkThumbprint(jwkWithExtras)
        val thumbprint2 = DPoPHandler.calculateJwkThumbprint(jwkMinimal)

        assertEquals(thumbprint1, thumbprint2, "Extra fields should not affect thumbprint")
    }

    // ==================== Access Token Hash Tests ====================

    @Test
    fun testAccessTokenHashCalculation() {
        val accessToken = "Kz~8mXK1EalYznwH-LC-1fBAo.4Ljp~zsPE_NeO.gxU"
        val hash = DPoPHandler.calculateAccessTokenHash(accessToken)

        assertNotNull(hash)
        assertTrue(hash.isNotEmpty())
        // SHA-256 produces 32 bytes, base64url encoded = ~43 chars
        assertTrue(hash.length >= 40)
    }

    @Test
    fun testAccessTokenHashDeterministic() {
        val token = "test-token-abc123"
        val hash1 = DPoPHandler.calculateAccessTokenHash(token)
        val hash2 = DPoPHandler.calculateAccessTokenHash(token)

        assertEquals(hash1, hash2, "Same token should produce same hash")
    }

    @Test
    fun testAccessTokenHashDifferentTokens() {
        val hash1 = DPoPHandler.calculateAccessTokenHash("token1")
        val hash2 = DPoPHandler.calculateAccessTokenHash("token2")

        assertNotEquals(hash1, hash2, "Different tokens should produce different hashes")
    }

    @Test
    fun testAccessTokenHashIsBase64UrlEncoded() {
        val hash = DPoPHandler.calculateAccessTokenHash("any-token")

        // Base64url should not contain these characters
        assertFalse(hash.contains("+"), "Should not contain +")
        assertFalse(hash.contains("/"), "Should not contain /")
        assertFalse(hash.contains("="), "Should not contain padding")
    }

    // ==================== DPoP Proof Validation Tests ====================

    @Test
    fun testValidDPoPProofStructure() = runTest {
        // Create a valid DPoP proof JWT for testing
        val key = JWKKey.generate(id.walt.crypto.keys.KeyType.secp256r1)
        val publicJwk = Json.parseToJsonElement(key.getPublicKey().exportJWK()).jsonObject

        val header = buildJsonObject {
            put("typ", JsonPrimitive("dpop+jwt"))
            put("alg", JsonPrimitive("ES256"))
            put("jwk", publicJwk)
        }

        val payload = buildJsonObject {
            put("jti", JsonPrimitive("unique-id-${System.currentTimeMillis()}"))
            put("htm", JsonPrimitive("POST"))
            put("htu", JsonPrimitive("https://issuer.example.com/token"))
            put("iat", JsonPrimitive(Clock.System.now().epochSeconds))
        }

        val dpopProof = key.signJws(
            plaintext = payload.toString().toByteArray(),
            headers = mapOf(
                "typ" to JsonPrimitive("dpop+jwt"),
                "alg" to JsonPrimitive("ES256"),
                "jwk" to publicJwk
            )
        )

        val result = DPoPHandler.validateDPoPProof(
            dpopProof = dpopProof,
            httpMethod = "POST",
            httpUri = "https://issuer.example.com/token"
        )

        assertTrue(result is DPoPHandler.DPoPValidationResult.Success, "Valid proof should pass validation")
    }

    @Test
    fun testInvalidTypHeader() = runTest {
        // DPoP proof must have typ: dpop+jwt
        val result = DPoPHandler.validateDPoPProof(
            dpopProof = createMockJwt(typ = "jwt"), // Wrong typ
            httpMethod = "POST",
            httpUri = "https://issuer.example.com/token"
        )

        assertTrue(result is DPoPHandler.DPoPValidationResult.Error)
        assertTrue((result as DPoPHandler.DPoPValidationResult.Error).message.contains("typ"))
    }

    @Test
    fun testMissingJwkHeader() = runTest {
        // DPoP proof must contain jwk header
        val result = DPoPHandler.validateDPoPProof(
            dpopProof = createMockJwtWithoutJwk(),
            httpMethod = "POST",
            httpUri = "https://issuer.example.com/token"
        )

        assertTrue(result is DPoPHandler.DPoPValidationResult.Error)
        assertTrue((result as DPoPHandler.DPoPValidationResult.Error).message.contains("jwk"))
    }

    @Test
    fun testHttpMethodMismatch() = runTest {
        val key = JWKKey.generate(id.walt.crypto.keys.KeyType.secp256r1)
        val publicJwk = Json.parseToJsonElement(key.getPublicKey().exportJWK()).jsonObject

        val dpopProof = createValidDPoPProof(
            key = key,
            jwk = publicJwk,
            htm = "POST",  // Proof says POST
            htu = "https://issuer.example.com/token"
        )

        val result = DPoPHandler.validateDPoPProof(
            dpopProof = dpopProof,
            httpMethod = "GET",  // But actual request is GET
            httpUri = "https://issuer.example.com/token"
        )

        assertTrue(result is DPoPHandler.DPoPValidationResult.Error)
        assertTrue((result as DPoPHandler.DPoPValidationResult.Error).message.contains("htm"))
    }

    @Test
    fun testHttpUriMismatch() = runTest {
        val key = JWKKey.generate(id.walt.crypto.keys.KeyType.secp256r1)
        val publicJwk = Json.parseToJsonElement(key.getPublicKey().exportJWK()).jsonObject

        val dpopProof = createValidDPoPProof(
            key = key,
            jwk = publicJwk,
            htm = "POST",
            htu = "https://issuer.example.com/token"  // Proof says /token
        )

        val result = DPoPHandler.validateDPoPProof(
            dpopProof = dpopProof,
            httpMethod = "POST",
            httpUri = "https://issuer.example.com/credential"  // But actual is /credential
        )

        assertTrue(result is DPoPHandler.DPoPValidationResult.Error)
        assertTrue((result as DPoPHandler.DPoPValidationResult.Error).message.contains("htu"))
    }

    @Test
    fun testAccessTokenHashValidation() = runTest {
        val key = JWKKey.generate(id.walt.crypto.keys.KeyType.secp256r1)
        val publicJwk = Json.parseToJsonElement(key.getPublicKey().exportJWK()).jsonObject

        val accessToken = "test-access-token"
        val correctAth = DPoPHandler.calculateAccessTokenHash(accessToken)

        val dpopProof = createValidDPoPProof(
            key = key,
            jwk = publicJwk,
            htm = "POST",
            htu = "https://issuer.example.com/credential",
            ath = correctAth
        )

        val result = DPoPHandler.validateDPoPProof(
            dpopProof = dpopProof,
            httpMethod = "POST",
            httpUri = "https://issuer.example.com/credential",
            accessTokenHash = correctAth
        )

        assertTrue(result is DPoPHandler.DPoPValidationResult.Success)
    }

    @Test
    fun testAccessTokenHashMismatch() = runTest {
        val key = JWKKey.generate(id.walt.crypto.keys.KeyType.secp256r1)
        val publicJwk = Json.parseToJsonElement(key.getPublicKey().exportJWK()).jsonObject

        val dpopProof = createValidDPoPProof(
            key = key,
            jwk = publicJwk,
            htm = "POST",
            htu = "https://issuer.example.com/credential",
            ath = "wrong-hash"
        )

        val result = DPoPHandler.validateDPoPProof(
            dpopProof = dpopProof,
            httpMethod = "POST",
            httpUri = "https://issuer.example.com/credential",
            accessTokenHash = DPoPHandler.calculateAccessTokenHash("actual-token")
        )

        assertTrue(result is DPoPHandler.DPoPValidationResult.Error)
        assertTrue((result as DPoPHandler.DPoPValidationResult.Error).message.contains("ath"))
    }

    // ==================== Supported Algorithms Tests ====================

    @Test
    fun testSupportedAlgorithmsIncludeEcAlgorithms() {
        assertTrue(DPoPHandler.SUPPORTED_ALGORITHMS.contains("ES256"))
        assertTrue(DPoPHandler.SUPPORTED_ALGORITHMS.contains("ES384"))
        assertTrue(DPoPHandler.SUPPORTED_ALGORITHMS.contains("ES512"))
    }

    @Test
    fun testSupportedAlgorithmsIncludeRsaAlgorithms() {
        assertTrue(DPoPHandler.SUPPORTED_ALGORITHMS.contains("RS256"))
        assertTrue(DPoPHandler.SUPPORTED_ALGORITHMS.contains("RS384"))
        assertTrue(DPoPHandler.SUPPORTED_ALGORITHMS.contains("RS512"))
    }

    @Test
    fun testSymmetricAlgorithmsNotSupported() {
        // Symmetric algorithms should not be supported for DPoP
        assertFalse(DPoPHandler.SUPPORTED_ALGORITHMS.contains("HS256"))
        assertFalse(DPoPHandler.SUPPORTED_ALGORITHMS.contains("HS384"))
        assertFalse(DPoPHandler.SUPPORTED_ALGORITHMS.contains("HS512"))
    }

    // ==================== Helper Functions ====================

    private fun createMockJwt(typ: String): String {
        // Create a minimal JWT with wrong typ for testing
        val header = buildJsonObject {
            put("typ", JsonPrimitive(typ))
            put("alg", JsonPrimitive("ES256"))
        }
        val payload = buildJsonObject {
            put("jti", JsonPrimitive("test"))
            put("htm", JsonPrimitive("POST"))
            put("htu", JsonPrimitive("https://example.com"))
            put("iat", JsonPrimitive(Clock.System.now().epochSeconds))
        }

        val headerB64 = java.util.Base64.getUrlEncoder().withoutPadding()
            .encodeToString(header.toString().toByteArray())
        val payloadB64 = java.util.Base64.getUrlEncoder().withoutPadding()
            .encodeToString(payload.toString().toByteArray())

        return "$headerB64.$payloadB64.mock-signature"
    }

    private fun createMockJwtWithoutJwk(): String {
        val header = buildJsonObject {
            put("typ", JsonPrimitive("dpop+jwt"))
            put("alg", JsonPrimitive("ES256"))
            // Missing jwk
        }
        val payload = buildJsonObject {
            put("jti", JsonPrimitive("test"))
            put("htm", JsonPrimitive("POST"))
            put("htu", JsonPrimitive("https://example.com"))
            put("iat", JsonPrimitive(Clock.System.now().epochSeconds))
        }

        val headerB64 = java.util.Base64.getUrlEncoder().withoutPadding()
            .encodeToString(header.toString().toByteArray())
        val payloadB64 = java.util.Base64.getUrlEncoder().withoutPadding()
            .encodeToString(payload.toString().toByteArray())

        return "$headerB64.$payloadB64.mock-signature"
    }

    private suspend fun createValidDPoPProof(
        key: JWKKey,
        jwk: JsonObject,
        htm: String,
        htu: String,
        ath: String? = null
    ): String {
        val header = buildJsonObject {
            put("typ", JsonPrimitive("dpop+jwt"))
            put("alg", JsonPrimitive("ES256"))
            put("jwk", jwk)
        }

        val payload = buildJsonObject {
            put("jti", JsonPrimitive("unique-${System.nanoTime()}"))
            put("htm", JsonPrimitive(htm))
            put("htu", JsonPrimitive(htu))
            put("iat", JsonPrimitive(Clock.System.now().epochSeconds))
            ath?.let { put("ath", JsonPrimitive(it)) }
        }

        return key.signJws(
            plaintext = payload.toString().toByteArray(),
            headers = mapOf(
                "typ" to JsonPrimitive("dpop+jwt"),
                "alg" to JsonPrimitive("ES256"),
                "jwk" to jwk
            )
        )
    }
}
