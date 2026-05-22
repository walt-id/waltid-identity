package id.walt.issuer2.repository.openid4vci

import id.walt.openid4vci.DefaultSession
import id.walt.openid4vci.repository.authorization.DefaultAuthorizationCodeRecord
import id.walt.openid4vci.repository.authorization.DuplicateCodeException
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.time.Clock
import kotlin.time.Duration.Companion.minutes

class ConfiguredAuthorizationCodeRepositoryTest {

    @Test
    fun saveRejectsDuplicateAndConsumeIsOneTime() = runTest {
        val repository = ConfiguredAuthorizationCodeRepository()
        val record = testRecord()

        repository.save(record)

        assertFailsWith<DuplicateCodeException> {
            repository.save(record)
        }

        assertEquals(record, repository.consume(record.code))
        assertNull(repository.consume(record.code))
    }

    private fun testRecord() = DefaultAuthorizationCodeRecord(
        code = "auth-code-${Clock.System.now().toEpochMilliseconds()}",
        clientId = "wallet-client",
        redirectUri = "https://wallet.example/callback",
        grantedScopes = setOf("openid"),
        grantedAudience = setOf("issuer2"),
        session = DefaultSession(subject = "subject"),
        expiresAt = Clock.System.now().plus(5.minutes),
    )
}
