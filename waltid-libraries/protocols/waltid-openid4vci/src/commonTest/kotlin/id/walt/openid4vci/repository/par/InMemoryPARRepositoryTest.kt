package id.walt.openid4vci.repository.par

import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.time.Clock
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant

class InMemoryPARRepositoryTest {

    @Test
    fun `should save and consume pushed authorization request`() = runTest {
        val repo = InMemoryPARRepository()
        val now = Clock.System.now()
        val record = createTestRecord(
            requestId = "test-123",
            expiresAt = now + 90.seconds,
        )

        repo.save(record)
        val consumed = repo.consume("test-123", now)

        assertNotNull(consumed)
        assertEquals("test-123", consumed.requestId)
        assertEquals("test-client", consumed.clientId)
        assertEquals(listOf("test-client"), consumed.requestParameters["client_id"])
    }

    @Test
    fun `should consume pushed authorization request only once`() = runTest {
        val repo = InMemoryPARRepository()
        val now = Clock.System.now()

        repo.save(createTestRecord("single-use", now + 90.seconds))

        assertNotNull(repo.consume("single-use", now))
        assertNull(repo.consume("single-use", now))
    }

    @Test
    fun `should return null for non-existent request ID`() = runTest {
        val repo = InMemoryPARRepository()

        assertNull(repo.consume("non-existent", Clock.System.now()))
    }

    @Test
    fun `should return null for expired pushed authorization request`() = runTest {
        val repo = InMemoryPARRepository()
        val now = Clock.System.now()

        repo.save(createTestRecord("expired-123", now - 10.seconds))

        assertNull(repo.consume("expired-123", now))
        assertNull(repo.consume("expired-123", now))
    }

    @Test
    fun `should reject duplicate request IDs`() = runTest {
        val repo = InMemoryPARRepository()
        val now = Clock.System.now()

        repo.save(createTestRecord("duplicate", now + 90.seconds))

        assertFailsWith<DuplicatePARRecordException> {
            repo.save(createTestRecord("duplicate", now + 90.seconds))
        }
    }

    @Test
    fun `should preserve client metadata`() = runTest {
        val repo = InMemoryPARRepository()
        val now = Clock.System.now()
        val record = DefaultPARRecord(
            requestId = "metadata-test",
            requestParameters = testRequestParameters(),
            createdAt = now,
            expiresAt = now + 90.seconds,
            clientMetadata = mapOf(
                "client_assertion_type" to "urn:ietf:params:oauth:client-assertion-type:jwt-bearer",
                "client_assertion" to "eyJhbGciOiJSUzI1NiJ9...",
            ),
        )

        repo.save(record)
        val consumed = repo.consume("metadata-test", now)

        assertNotNull(consumed)
        assertEquals(2, consumed.clientMetadata.size)
        assertEquals(
            "urn:ietf:params:oauth:client-assertion-type:jwt-bearer",
            consumed.clientMetadata["client_assertion_type"],
        )
    }

    private fun createTestRecord(
        requestId: String,
        expiresAt: Instant,
    ): DefaultPARRecord {
        val createdAt = Clock.System.now().let { now ->
            if (now < expiresAt) now else expiresAt - 90.seconds
        }
        return DefaultPARRecord(
            requestId = requestId,
            requestParameters = testRequestParameters(),
            createdAt = createdAt,
            expiresAt = expiresAt,
        )
    }

    private fun testRequestParameters(): Map<String, List<String>> =
        mapOf(
            "client_id" to listOf("test-client"),
            "response_type" to listOf("code"),
        )
}
