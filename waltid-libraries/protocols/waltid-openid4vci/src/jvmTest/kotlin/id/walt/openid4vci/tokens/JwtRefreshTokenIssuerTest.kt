package id.walt.openid4vci.tokens

import id.walt.crypto.keys.KeyType
import id.walt.crypto.keys.jwk.JWKKey
import id.walt.crypto.utils.JwsUtils.decodeJws
import id.walt.openid4vci.tokens.jwt.refresh.KEYCLOAK_REFRESH_TOKEN_TYPE
import id.walt.openid4vci.tokens.jwt.refresh.JwtRefreshTokenIssuer
import id.walt.openid4vci.tokens.jwt.refresh.JwtRefreshTokenVerifier
import id.walt.openid4vci.tokens.refresh.RefreshTokenGenerationRequest
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Clock
import kotlin.time.Duration.Companion.days

class JwtRefreshTokenIssuerTest {

    @Test
    fun `generates keycloak style jwt refresh token claims and signature lookup value`() = runTest {
        val key = JWKKey.generate(KeyType.Ed25519)
        val issuer = JwtRefreshTokenIssuer(
            signingKeyResolver = { key },
        )
        val verifier = JwtRefreshTokenVerifier { _ -> key.getPublicKey() }

        val token = issuer.issue(
            RefreshTokenGenerationRequest(
                issuer = "https://issuer.example",
                subject = "subject-1",
                clientId = "wallet-client",
                scopes = setOf("openid", "email"),
                expiresAt = Clock.System.now() + 30.days,
                sessionId = "issuance-session-1",
            )
        )

        val parts = token.split('.')
        assertEquals(3, parts.size)
        assertEquals(parts[2], issuer.signature(token))

        val decoded = token.decodeJws().payload
        assertEquals(KEYCLOAK_REFRESH_TOKEN_TYPE, decoded["typ"]?.jsonPrimitive?.content)
        assertEquals("wallet-client", decoded["azp"]?.jsonPrimitive?.content)
        assertEquals("https://issuer.example", decoded["aud"]?.jsonPrimitive?.content)
        assertEquals("issuance-session-1", decoded["sid"]?.jsonPrimitive?.content)

        val verified = verifier.verify(
            token = token,
            expectedIssuer = "https://issuer.example",
            expectedClientId = "wallet-client",
        )
        assertEquals("subject-1", verified.subject)
        assertEquals(setOf("openid", "email"), verified.scopes)
        assertTrue(verified.id.isNotBlank())
    }

    @Test
    fun `generates unbound jwt refresh token without authorized party`() = runTest {
        val key = JWKKey.generate(KeyType.Ed25519)
        val issuer = JwtRefreshTokenIssuer(
            signingKeyResolver = { key },
        )
        val verifier = JwtRefreshTokenVerifier { _ -> key.getPublicKey() }

        val token = issuer.issue(
            RefreshTokenGenerationRequest(
                issuer = "https://issuer.example",
                subject = "subject-1",
                clientId = null,
                scopes = setOf("openid"),
                expiresAt = Clock.System.now() + 30.days,
                sessionId = "issuance-session-1",
            )
        )

        val decoded = token.decodeJws().payload
        assertEquals(KEYCLOAK_REFRESH_TOKEN_TYPE, decoded["typ"]?.jsonPrimitive?.content)
        assertNull(decoded["azp"])

        val verified = verifier.verify(
            token = token,
            expectedIssuer = "https://issuer.example",
            expectedClientId = null,
        )
        assertNull(verified.issuedFor)
        assertEquals(setOf("openid"), verified.scopes)
    }

    @Test
    fun `rejects jwt refresh token for wrong client`() = runTest {
        val key = JWKKey.generate(KeyType.Ed25519)
        val issuer = JwtRefreshTokenIssuer(
            signingKeyResolver = { key },
        )
        val verifier = JwtRefreshTokenVerifier { _ -> key.getPublicKey() }
        val token = issuer.issue(
            RefreshTokenGenerationRequest(
                issuer = "https://issuer.example",
                subject = "subject-1",
                clientId = "wallet-client",
                scopes = emptySet(),
                expiresAt = Clock.System.now() + 30.days,
                sessionId = null,
            )
        )

        assertFailsWith<IllegalArgumentException> {
            verifier.verify(
                token = token,
                expectedIssuer = "https://issuer.example",
                expectedClientId = "other-client",
            )
        }
    }
}
