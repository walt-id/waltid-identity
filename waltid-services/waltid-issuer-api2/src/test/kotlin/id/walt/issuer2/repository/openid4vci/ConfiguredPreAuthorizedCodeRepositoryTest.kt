package id.walt.issuer2.repository.openid4vci

import id.walt.openid4vci.DefaultSession
import id.walt.openid4vci.offers.TxCode
import id.walt.openid4vci.repository.authorization.DuplicateCodeException
import id.walt.openid4vci.repository.preauthorized.DefaultPreAuthorizedCodeRecord
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.time.Clock
import kotlin.time.Duration.Companion.minutes

class ConfiguredPreAuthorizedCodeRepositoryTest {

    @Test
    fun saveGetRejectDuplicateAndConsumeIsOneTime() = runTest {
        val repository = ConfiguredPreAuthorizedCodeRepository()
        val record = testRecord()

        repository.save(record)

        assertEquals(record, repository.get(record.code))
        assertFailsWith<DuplicateCodeException> {
            repository.save(record)
        }

        assertEquals(record, repository.consume(record.code))
        assertNull(repository.get(record.code))
        assertNull(repository.consume(record.code))
    }

    @Test
    fun concurrentConsumeReturnsRecordOnce() = runTest {
        val repository = ConfiguredPreAuthorizedCodeRepository()
        val record = testRecord()

        repository.save(record)

        val results = (1..20)
            .map { async(Dispatchers.Default) { repository.consume(record.code) } }
            .awaitAll()

        assertEquals(1, results.count { it != null })
        assertEquals(record, results.filterNotNull().single())
    }

    private fun testRecord() = DefaultPreAuthorizedCodeRecord(
        code = "pre-auth-code-${Clock.System.now().toEpochMilliseconds()}",
        clientId = null,
        txCode = TxCode(inputMode = "numeric", length = 6, description = "PIN"),
        txCodeValue = "hashed-pin",
        grantedScopes = setOf("openid"),
        grantedAudience = setOf("issuer2"),
        session = DefaultSession(subject = "subject"),
        expiresAt = Clock.System.now().plus(5.minutes),
        issuanceSessionId = "issuance-session-id",
    )
}
