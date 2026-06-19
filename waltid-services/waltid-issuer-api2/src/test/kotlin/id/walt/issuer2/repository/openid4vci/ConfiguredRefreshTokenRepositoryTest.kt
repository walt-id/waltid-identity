package id.walt.issuer2.repository.openid4vci

import id.walt.openid4vci.DefaultClient
import id.walt.openid4vci.DefaultSession
import id.walt.openid4vci.TokenType
import id.walt.openid4vci.repository.authorization.DuplicateCodeException
import id.walt.openid4vci.repository.refresh.DefaultRefreshTokenRecord
import id.walt.openid4vci.requests.token.DefaultAccessTokenRequest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import kotlin.random.Random
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.time.Clock
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Instant

class ConfiguredRefreshTokenRepositoryTest {

    @Test
    fun saveGetRejectDuplicateAndRotateDeactivatesOldToken() = runTest {
        val repository = ConfiguredRefreshTokenRepository()
        val record = testRecord("initial")
        val replacement = testRecord("replacement")

        repository.save(record)

        assertEquals(record, repository.get(record.tokenSignature))
        assertFailsWith<DuplicateCodeException> {
            repository.save(record)
        }

        assertEquals(record, repository.rotate(record.tokenSignature, replacement))
        assertFalse(repository.get(record.tokenSignature)!!.active)
        assertEquals(replacement, repository.get(replacement.tokenSignature))
        assertNull(repository.rotate(record.tokenSignature, testRecord("second-replacement")))
    }

    @Test
    fun rotateMarksExpiredTokenInactiveAndDoesNotStoreReplacement() = runTest {
        val repository = ConfiguredRefreshTokenRepository()
        val expired = testRecord("expired", expiresAt = timestampOffset(-1.minutes))
        val replacement = testRecord("replacement")

        repository.save(expired)

        assertNull(repository.rotate(expired.tokenSignature, replacement))
        assertFalse(repository.get(expired.tokenSignature)!!.active)
        assertNull(repository.get(replacement.tokenSignature))
    }

    @Test
    fun concurrentRotateReturnsRecordOnce() = runTest {
        val repository = ConfiguredRefreshTokenRepository()
        val record = testRecord("concurrent")
        val replacements = (1..20).map { testRecord("replacement-$it") }

        repository.save(record)

        val results = replacements
            .map { replacement ->
                async(Dispatchers.Default) {
                    repository.rotate(record.tokenSignature, replacement)
                }
            }
            .awaitAll()

        assertEquals(1, results.count { it != null })
        assertEquals(record, results.filterNotNull().single())
        assertFalse(repository.get(record.tokenSignature)!!.active)
        assertEquals(1, replacements.count { repository.get(it.tokenSignature) != null })
    }

    private fun testRecord(
        label: String,
        expiresAt: Instant = timestampOffset(5.minutes),
    ): DefaultRefreshTokenRecord {
        val suffix = "${Clock.System.now().toEpochMilliseconds()}-${Random.nextLong()}-$label"
        val accessTokenExpiresAt = timestampOffset(5.minutes)
        val session = DefaultSession(
            subject = "subject-$suffix",
            expiresAt = mapOf(
                TokenType.ACCESS_TOKEN to accessTokenExpiresAt,
                TokenType.REFRESH_TOKEN to expiresAt,
            ),
            customAttributes = mapOf("issuance_session_id" to "issuance-session-$suffix"),
        )
        val client = DefaultClient(
            id = "wallet-client-$suffix",
            redirectUris = listOf("https://wallet.example/callback"),
            grantTypes = setOf("authorization_code", "refresh_token"),
            responseTypes = setOf("code"),
            scopes = setOf("openid"),
            audience = setOf("issuer2"),
        )
        val accessTokenRequest = DefaultAccessTokenRequest(
            id = "request-$suffix",
            requestedAt = timestampOffset(0.minutes),
            client = client,
            grantTypes = setOf("authorization_code"),
            handledGrantTypes = setOf("authorization_code"),
            requestedScopes = setOf("openid"),
            grantedScopes = setOf("openid"),
            requestedAudience = setOf("issuer2"),
            grantedAudience = setOf("issuer2"),
            requestForm = mapOf(
                "grant_type" to listOf("authorization_code"),
                "client_id" to listOf(client.id),
            ),
            session = session,
            issClaim = "http://localhost",
        )

        return DefaultRefreshTokenRecord(
            tokenSignature = "refresh-token-signature-$suffix",
            active = true,
            accessTokenRequest = accessTokenRequest,
            accessTokenSignature = "access-token-signature-$suffix",
            clientId = client.id,
            grantedScopes = setOf("openid"),
            grantedAudience = setOf("issuer2"),
            session = session,
            expiresAt = expiresAt,
        )
    }

    private fun timestampOffset(duration: kotlin.time.Duration): Instant =
        Instant.fromEpochMilliseconds(Clock.System.now().plus(duration).toEpochMilliseconds())
}
