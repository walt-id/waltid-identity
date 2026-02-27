package id.walt.openid4vci.tokens

import id.walt.crypto.keys.KeyType
import id.walt.crypto.keys.jwk.JWKKey
import id.walt.openid4vci.tokens.jwt.JwtAccessTokenIssuer
import id.walt.openid4vci.tokens.jwt.JwtAccessTokenVerifier
import id.walt.openid4vci.tokens.jwt.defaultAccessTokenClaims
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.time.Clock

class JwtAccessTokenVerifierTest {

    @Test
    fun `verifies JWT access token with required claims`(): Unit = runBlocking {
        val key = JWKKey.generate(KeyType.Ed25519)
        val signer = JwtAccessTokenIssuer(resolver = { key })
        val verifier = JwtAccessTokenVerifier { _ -> key.getPublicKey() }

        val claims = defaultAccessTokenClaims(
            subject = "alice",
            issuer = "https://issuer.example",
            audience = "https://audience.example",
            scopes = setOf("openid"),
        )

        val token = signer.createAccessToken(claims)
        val payload = verifier.verify(
            token = token,
            expectedIssuer = "https://issuer.example",
            expectedAudience = "https://audience.example",
        )

        assertEquals("alice", payload["sub"]?.jsonPrimitive?.content)
        assertEquals("https://issuer.example", payload["iss"]?.jsonPrimitive?.content)
        assertNotNull(payload["exp"])
        assertNotNull(payload["iat"])
    }

    @Test
    fun `rejects token missing issuer claim`() = runBlocking {
        val key = JWKKey.generate(KeyType.Ed25519)
        val signer = JwtAccessTokenIssuer(resolver = { key })
        val verifier = JwtAccessTokenVerifier { _ -> key.getPublicKey() }

        val claims = defaultAccessTokenClaims(
            subject = "alice",
            issuer = "https://issuer.example",
            audience = "https://audience.example",
        ).toMutableMap().apply {
            remove("iss")
        }

        val token = signer.createAccessToken(claims)
        val ex = assertFailsWith<IllegalArgumentException> {
            verifier.verify(token, expectedIssuer = "https://issuer.example")
        }
        assertEquals("Access token is missing issuer claim", ex.message)
    }

    @Test
    fun `rejects token missing expiration claim`() = runBlocking {
        val key = JWKKey.generate(KeyType.Ed25519)
        val signer = JwtAccessTokenIssuer(resolver = { key })
        val verifier = JwtAccessTokenVerifier { _ -> key.getPublicKey() }

        val claims = defaultAccessTokenClaims(
            subject = "alice",
            issuer = "https://issuer.example",
            audience = "https://audience.example",
        ).toMutableMap().apply {
            remove("exp")
        }

        val token = signer.createAccessToken(claims)
        val ex = assertFailsWith<IllegalArgumentException> {
            verifier.verify(token, expectedIssuer = "https://issuer.example")
        }
        assertEquals("Access token is missing expiration claim", ex.message)
    }

    @Test
    fun `rejects expired token`() = runBlocking {
        val key = JWKKey.generate(KeyType.Ed25519)
        val signer = JwtAccessTokenIssuer(resolver = { key })
        val verifier = JwtAccessTokenVerifier { _ -> key.getPublicKey() }

        val expiredClaims = defaultAccessTokenClaims(
            subject = "alice",
            issuer = "https://issuer.example",
            audience = "https://audience.example",
        ).toMutableMap().apply {
            put("exp", Clock.System.now().epochSeconds - 5)
        }

        val token = signer.createAccessToken(expiredClaims)
        val ex = assertFailsWith<IllegalArgumentException> {
            verifier.verify(token, expectedIssuer = "https://issuer.example")
        }
        assertEquals("Access token expired", ex.message)
    }
}
