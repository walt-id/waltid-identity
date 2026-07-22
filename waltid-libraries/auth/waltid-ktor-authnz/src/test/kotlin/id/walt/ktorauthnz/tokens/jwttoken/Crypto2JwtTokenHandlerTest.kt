package id.walt.ktorauthnz.tokens.jwttoken

import id.walt.commons.web.ExpiredTokenException
import id.walt.crypto2.CryptoRuntime
import id.walt.crypto2.jose.CompactJws
import id.walt.crypto2.jose.JwsAlgorithm
import id.walt.crypto2.keys.EcCurve
import id.walt.crypto2.keys.KeyId
import id.walt.crypto2.keys.KeySpec
import id.walt.crypto2.keys.KeyUsage
import id.walt.crypto2.providers.GenerateSoftwareKeyRequest
import id.walt.crypto2.providers.cryptography.CryptographySoftwareKeyProvider
import id.walt.ktorauthnz.sessions.AuthSession
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.time.Clock
import kotlin.time.Duration.Companion.hours
import kotlin.time.Instant

class Crypto2JwtTokenHandlerTest {
    private val runtime = CryptoRuntime(listOf(CryptographySoftwareKeyProvider()))
    private val now = Instant.parse("2026-07-18T12:00:00Z")
    private val clock = object : Clock {
        override fun now(): Instant = this@Crypto2JwtTokenHandlerTest.now
    }

    @Test
    fun `crypto2 token signs verifies and exposes session claims`() = runTest {
        val key = key("auth-key")
        val handler = JwtTokenHandler(key, algorithm = JwsAlgorithm.ES256).apply { clock = this@Crypto2JwtTokenHandlerTest.clock }
        val token = handler.generateToken(
            AuthSession(
                id = "session-id",
                accountId = "account-id",
                expiration = now + 1.hours,
            )
        )

        assertEquals(JwsAlgorithm.ES256, CompactJws.decodeUnverified(token).algorithm)
        assertTrue(handler.validateToken(token))
        assertEquals("session-id", handler.getTokenSessionId(token))
        assertEquals("account-id", handler.getTokenAccountId(token))

        val parts = token.split('.').toMutableList().apply {
            this[2] = (if (this[2].first() == 'A') "B" else "A") + this[2].drop(1)
        }
        assertFalse(handler.validateToken(parts.joinToString(".")))
    }

    @Test
    fun `expired token is rejected after signature verification`() = runTest {
        val key = key("expired-key")
        val handler = JwtTokenHandler(key, algorithm = JwsAlgorithm.ES256).apply { clock = this@Crypto2JwtTokenHandlerTest.clock }
        val token = handler.generateToken(
            AuthSession(
                id = "expired-session",
                accountId = "account",
                expiration = Instant.fromEpochSeconds(now.epochSeconds - 1),
            )
        )

        assertFailsWith<ExpiredTokenException> { handler.validateToken(token) }
    }

    private suspend fun key(id: String) = runtime.generateSoftwareKey(
        GenerateSoftwareKeyRequest(
            id = KeyId(id),
            spec = KeySpec.Ec(EcCurve.P256),
            usages = setOf(KeyUsage.SIGN, KeyUsage.VERIFY),
        )
    )
}
