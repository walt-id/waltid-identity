package id.walt.issuer2.repository.openid4vci

import id.walt.openid4vci.repository.par.DefaultPARRecord
import id.walt.openid4vci.repository.par.DuplicatePARRecordException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.time.Clock
import kotlin.time.Duration.Companion.seconds

class ConfiguredPARRepositoryTest {

    @Test
    fun saveRejectsDuplicateAndConsumeIsOneTime() = runTest {
        val repository = ConfiguredPARRepository()
        val record = testRecord()
        val now = Clock.System.now()

        repository.save(record)

        assertFailsWith<DuplicatePARRecordException> {
            repository.save(record)
        }

        assertEquals(record, repository.consume(record.requestId, now))
        assertNull(repository.consume(record.requestId, now))
    }

    @Test
    fun concurrentConsumeReturnsRecordOnce() = runTest {
        val repository = ConfiguredPARRepository()
        val record = testRecord()
        val now = Clock.System.now()

        repository.save(record)

        val results = (1..20)
            .map { async(Dispatchers.Default) { repository.consume(record.requestId, now) } }
            .awaitAll()

        assertEquals(1, results.count { it != null })
        assertEquals(record, results.filterNotNull().single())
    }

    private fun testRecord(): DefaultPARRecord {
        val now = Clock.System.now()
        val requestId = "par-${now.toEpochMilliseconds()}"
        return DefaultPARRecord(
            requestId = requestId,
            requestParameters = mapOf(
                "client_id" to listOf("wallet-client"),
                "response_type" to listOf("code"),
                "redirect_uri" to listOf("https://wallet.example/callback"),
                "scope" to listOf("openid"),
            ),
            createdAt = now,
            expiresAt = now.plus(90.seconds),
        )
    }
}
