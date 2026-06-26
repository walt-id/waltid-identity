package id.walt.openid4vci.repository.refresh

import id.walt.openid4vci.DefaultClient
import id.walt.openid4vci.DefaultSession
import id.walt.openid4vci.TokenType
import id.walt.openid4vci.repository.authorization.DuplicateCodeException
import id.walt.openid4vci.requests.token.DefaultAccessTokenRequest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.time.Clock
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Instant

class InMemoryRefreshTokenRepositoryTest {

    @Test
    fun `should save and get refresh token by signature`() = runTest {
        val repo = InMemoryRefreshTokenRepository()
        val record = testRecord("initial")

        repo.save(record)

        val stored = repo.get(record.tokenSignature)
        assertNotNull(stored)
        assertEquals(record, stored)
    }

    @Test
    fun `should reject duplicate refresh token signatures`() = runTest {
        val repo = InMemoryRefreshTokenRepository()
        val record = testRecord("duplicate")

        repo.save(record)

        assertFailsWith<DuplicateCodeException> {
            repo.save(record)
        }
    }

    @Test
    fun `should rotate refresh token and keep old token inactive`() = runTest {
        val repo = InMemoryRefreshTokenRepository()
        val old = testRecord("old")
        val replacement = testRecord("replacement")

        repo.save(old)

        assertEquals(old, repo.rotate(old.tokenSignature, replacement))

        val oldStored = repo.get(old.tokenSignature)
        assertNotNull(oldStored)
        assertFalse(oldStored.active)
        assertEquals(replacement, repo.get(replacement.tokenSignature))
    }

    @Test
    fun `should reject reuse of rotated refresh token`() = runTest {
        val repo = InMemoryRefreshTokenRepository()
        val old = testRecord("old")

        repo.save(old)
        assertNotNull(repo.rotate(old.tokenSignature, testRecord("replacement")))

        assertNull(repo.rotate(old.tokenSignature, testRecord("second-replacement")))
    }

    @Test
    fun `should mark expired token inactive and not store replacement`() = runTest {
        val repo = InMemoryRefreshTokenRepository()
        val expired = testRecord("expired", expiresAt = timestampOffset((-1).minutes))
        val replacement = testRecord("replacement")

        repo.save(expired)

        assertNull(repo.rotate(expired.tokenSignature, replacement))

        val expiredStored = repo.get(expired.tokenSignature)
        assertNotNull(expiredStored)
        assertFalse(expiredStored.active)
        assertNull(repo.get(replacement.tokenSignature))
    }

    @Test
    fun `should return null when rotating unknown token`() = runTest {
        val repo = InMemoryRefreshTokenRepository()

        assertNull(repo.rotate("unknown-signature", testRecord("replacement")))
    }

    @Test
    fun `concurrent rotate should succeed once`() = runTest {
        val repo = InMemoryRefreshTokenRepository()
        val old = testRecord("old")
        val replacements = (1..20).map { testRecord("replacement-$it") }

        repo.save(old)

        val results = replacements
            .map { replacement ->
                async(Dispatchers.Default) {
                    repo.rotate(old.tokenSignature, replacement)
                }
            }
            .awaitAll()

        assertEquals(1, results.count { it != null })
        assertEquals(old, results.filterNotNull().single())
        assertFalse(repo.get(old.tokenSignature)!!.active)
        assertEquals(1, replacements.count { repo.get(it.tokenSignature) != null })
    }

    private fun testRecord(
        suffix: String,
        expiresAt: Instant = timestampOffset(5.minutes),
    ): DefaultRefreshTokenRecord {
        val session = DefaultSession(
            subject = "subject-$suffix",
            expiresAt = mapOf(
                TokenType.ACCESS_TOKEN to timestampOffset(5.minutes),
                TokenType.REFRESH_TOKEN to expiresAt,
            ),
            customAttributes = mapOf("issuance_session_id" to "issuance-session-$suffix"),
        )
        val client = DefaultClient(
            id = "client-$suffix",
            redirectUris = listOf("https://wallet.example/callback"),
            grantTypes = setOf("authorization_code", "refresh_token"),
            responseTypes = setOf("code"),
            scopes = setOf("openid"),
            audience = setOf("issuer"),
        )

        return DefaultRefreshTokenRecord(
            tokenSignature = "refresh-token-signature-$suffix",
            active = true,
            accessTokenRequest = DefaultAccessTokenRequest(
                client = client,
                grantTypes = setOf("authorization_code"),
                handledGrantTypes = setOf("authorization_code"),
                requestedScopes = setOf("openid"),
                grantedScopes = setOf("openid"),
                requestedAudience = setOf("issuer"),
                grantedAudience = setOf("issuer"),
                requestForm = mapOf(
                    "grant_type" to listOf("authorization_code"),
                    "client_id" to listOf(client.id),
                ),
                session = session,
                issClaim = "https://issuer.example",
            ),
            accessTokenSignature = "access-token-signature-$suffix",
            clientId = client.id,
            grantedScopes = setOf("openid"),
            grantedAudience = setOf("issuer"),
            session = session,
            expiresAt = expiresAt,
        )
    }

    private fun timestampOffset(duration: Duration): Instant =
        Clock.System.now() + duration
}
