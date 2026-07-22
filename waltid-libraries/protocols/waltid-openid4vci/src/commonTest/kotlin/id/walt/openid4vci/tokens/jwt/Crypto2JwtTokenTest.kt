package id.walt.openid4vci.tokens.jwt

import id.walt.crypto2.CryptoRuntime
import id.walt.crypto2.jose.CompactJws
import id.walt.crypto2.jose.JwsAlgorithm
import id.walt.crypto2.keys.EcCurve
import id.walt.crypto2.keys.Key
import id.walt.crypto2.keys.KeyId
import id.walt.crypto2.keys.KeySpec
import id.walt.crypto2.keys.KeyUsage
import id.walt.crypto2.providers.GenerateSoftwareKeyRequest
import id.walt.crypto2.providers.cryptography.CryptographySoftwareKeyProvider
import id.walt.openid4vci.tokens.jwt.access.JwtAccessTokenIssuer
import id.walt.openid4vci.tokens.jwt.access.JwtAccessTokenVerifier
import id.walt.openid4vci.tokens.jwt.refresh.JwtRefreshTokenIssuer
import id.walt.openid4vci.tokens.jwt.refresh.JwtRefreshTokenVerifier
import id.walt.openid4vci.tokens.refresh.RefreshTokenGenerationRequest
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.time.Clock
import kotlin.time.Duration.Companion.minutes

class Crypto2JwtTokenTest {
    private val runtime = CryptoRuntime(listOf(CryptographySoftwareKeyProvider()))

    @Test
    fun `access token signs and verifies with explicit crypto2 algorithm and kid`() = runTest {
        val key = generate(KeySpec.Ec(EcCurve.P256))
        val issuer = JwtAccessTokenIssuer(key, JwsAlgorithm.ES256, "access-token-key")
        val verifier = JwtAccessTokenVerifier(key, setOf(JwsAlgorithm.ES256))
        val token = issuer.issue(
            mapOf(
                JwtPayloadClaims.ISSUER to "https://issuer.example",
                JwtPayloadClaims.SUBJECT to "subject",
                JwtPayloadClaims.AUDIENCE to "client",
                JwtPayloadClaims.EXPIRATION to Clock.System.now().epochSeconds + 60,
            )
        )

        val decoded = CompactJws.decodeUnverified(token)
        assertEquals(JwsAlgorithm.ES256, decoded.algorithm)
        assertEquals("access-token-key", decoded.protectedHeader[JwtHeaderParams.KEY_ID]?.toString()?.trim('"'))
        val payload = verifier.verify(token, "https://issuer.example", "client")
        assertEquals("subject", payload[JwtPayloadClaims.SUBJECT]?.toString()?.trim('"'))
    }

    @Test
    fun `algorithm allowlist tampering and kid conflicts are rejected`() = runTest {
        val key = generate(KeySpec.Ec(EcCurve.P256))
        val signer = JwtTokenSigner(
            Crypto2JwtSigningKeyResolver { Crypto2JwtSigningKey(key, JwsAlgorithm.ES256, "expected-kid") }
        )
        assertFailsWith<IllegalArgumentException> {
            signer.sign(mapOf("sub" to "subject"), mapOf(JwtHeaderParams.KEY_ID to "other-kid"))
        }

        val token = signer.sign(mapOf("sub" to "subject"))
        val verifier = JwtTokenVerifier(
            Crypto2JwtVerificationKeyResolver {
                Crypto2JwtVerificationKey(key, setOf(JwsAlgorithm.ES384))
            }
        )
        assertFailsWith<IllegalArgumentException> { verifier.verify(token, "Access token") }

        val parts = token.split('.').toMutableList()
        parts[2] = (if (parts[2].first() == 'A') "B" else "A") + parts[2].drop(1)
        val validAlgorithmVerifier = JwtTokenVerifier(
            Crypto2JwtVerificationKeyResolver {
                Crypto2JwtVerificationKey(key, setOf(JwsAlgorithm.ES256))
            }
        )
        assertFailsWith<IllegalArgumentException> {
            validAlgorithmVerifier.verify(parts.joinToString("."), "Access token")
        }
    }

    @Test
    fun `refresh token uses direct crypto2 key contracts`() = runTest {
        val key = generate(KeySpec.Ec(EcCurve.P256))
        val now = Clock.System.now()
        val issuer = JwtRefreshTokenIssuer(key, JwsAlgorithm.ES256, "refresh-token-key")
        val verifier = JwtRefreshTokenVerifier(key, setOf(JwsAlgorithm.ES256))
        val token = issuer.issue(
            RefreshTokenGenerationRequest(
                issuer = "https://issuer.example",
                subject = "subject",
                clientId = "client",
                scopes = setOf("credential"),
                expiresAt = now + 5.minutes,
                sessionId = "session",
                issuedAt = now,
            )
        )

        assertEquals("refresh-token-key", CompactJws.decodeUnverified(token).protectedHeader[JwtHeaderParams.KEY_ID]
            ?.toString()?.trim('"'))
        assertEquals("subject", verifier.verify(token, "https://issuer.example", "client").subject)
    }

    private suspend fun generate(spec: KeySpec): Key = runtime.generateSoftwareKey(
        GenerateSoftwareKeyRequest(
            id = KeyId("token-key-${spec.hashCode()}"),
            spec = spec,
            usages = setOf(KeyUsage.SIGN, KeyUsage.VERIFY),
        )
    )
}
