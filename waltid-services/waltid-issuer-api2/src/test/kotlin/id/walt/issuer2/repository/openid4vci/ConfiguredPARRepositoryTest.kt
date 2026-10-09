package id.walt.issuer2.repository.openid4vci

import id.walt.commons.persistence.ConfiguredPersistence
import kotlinx.serialization.json.*
import id.walt.openid4vci.DefaultClient
import id.walt.openid4vci.repository.par.DefaultPARRecord
import id.walt.openid4vci.repository.par.DuplicatePARRecordException
import id.walt.openid4vci.requests.authorization.DefaultAuthorizationRequest
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.time.Clock
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test

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

    @Test
    fun legacyParametersAreConsumedAsInvalidReferences() = runTest {
        val raw = ConfiguredPersistence<String>("issuer2_pushed_authorization_requests", 90.seconds, { it }, { it })
        val record = testRecord()
        val current = Json.encodeToJsonElement(record).jsonObject
        val legacy = JsonObject((current - "authorizationRequest") + mapOf(
            "requestParameters" to Json.encodeToJsonElement(record.authorizationRequest.requestForm),
            "clientId" to JsonPrimitive(record.clientId),
        )).toString()
        raw.set(record.requestId, legacy, 90.seconds)
        val repository = ConfiguredPARRepository(raw)
        assertFailsWith<DuplicatePARRecordException> { repository.save(record) }
        assertEquals(legacy, raw[record.requestId])
        assertNull(repository.consume(record.requestId, Clock.System.now()))
        assertNull(raw[record.requestId])
        assertNull(repository.consume(record.requestId, Clock.System.now()))
    }

    private fun testRecord(): DefaultPARRecord {
        val now = Clock.System.now()
        val requestId = "par-${now.toEpochMilliseconds()}"
        return DefaultPARRecord(
            requestId = requestId,
            authorizationRequest = DefaultAuthorizationRequest(
                client = DefaultClient("wallet-client", listOf("https://wallet.example/callback"), setOf("authorization_code"), setOf("code")),
                responseTypes = setOf("code"), redirectUri = "https://wallet.example/callback", state = null,
                requestedScopes = setOf("openid"),
                requestForm = mapOf(
                    "client_id" to listOf("wallet-client"),
                    "response_type" to listOf("code"),
                    "redirect_uri" to listOf("https://wallet.example/callback"),
                    "scope" to listOf("openid"),
                ),
            ),
            createdAt = now,
            expiresAt = now.plus(90.seconds),
        )
    }
}
