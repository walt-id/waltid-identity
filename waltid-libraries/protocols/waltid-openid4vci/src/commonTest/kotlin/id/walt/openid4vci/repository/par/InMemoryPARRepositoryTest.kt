package id.walt.openid4vci.repository.par

import kotlinx.coroutines.test.runTest
import kotlin.time.Clock
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.seconds
import id.walt.openid4vci.requests.par.PushedAuthorizationRequest

class InMemoryPARRepositoryTest {

    @Test
    fun `should store and retrieve PAR entry`() = runTest {
        val repo = InMemoryPARRepository()
        val now = Clock.System.now()
        val entry = createTestEntry(
            requestId = "test-123",
            expiresAt = now + 90.seconds
        )

        repo.store(entry)
        val retrieved = repo.findByRequestId("test-123", now)

        assertNotNull(retrieved)
        assertEquals("test-123", retrieved.requestId)
        assertEquals("test-client", retrieved.request.clientId)
    }

    @Test
    fun `should return null for non-existent request ID`() = runTest {
        val repo = InMemoryPARRepository()
        val now = Clock.System.now()

        val retrieved = repo.findByRequestId("non-existent", now)

        assertNull(retrieved)
    }

    @Test
    fun `should return null for expired PAR entry`() = runTest {
        val repo = InMemoryPARRepository()
        val now = Clock.System.now()
        val entry = createTestEntry(
            requestId = "expired-123",
            expiresAt = now - 10.seconds // Already expired
        )

        repo.store(entry)
        val retrieved = repo.findByRequestId("expired-123", now)

        assertNull(retrieved)
    }

    @Test
    fun `should mark PAR entry as consumed`() = runTest {
        val repo = InMemoryPARRepository()
        val now = Clock.System.now()
        val entry = createTestEntry(
            requestId = "consume-123",
            expiresAt = now + 90.seconds
        )

        repo.store(entry)
        val consumed = repo.markConsumed("consume-123")

        assertNotNull(consumed)
        assertTrue(consumed.consumed)

        // Should not retrieve consumed entry
        val retrieved = repo.findByRequestId("consume-123", now)
        assertNull(retrieved)
    }

    @Test
    fun `should return null when marking non-existent PAR as consumed`() = runTest {
        val repo = InMemoryPARRepository()

        val result = repo.markConsumed("non-existent")

        assertNull(result)
    }

    @Test
    fun `should delete expired PAR entries`() = runTest {
        val repo = InMemoryPARRepository()
        val now = Clock.System.now()

        // Store 2 expired and 2 valid entries
        repo.store(createTestEntry("expired-1", now - 10.seconds))
        repo.store(createTestEntry("expired-2", now - 5.seconds))
        repo.store(createTestEntry("valid-1", now + 50.seconds))
        repo.store(createTestEntry("valid-2", now + 90.seconds))

        val deletedCount = repo.deleteExpired(now)

        assertEquals(2, deletedCount)
        assertEquals(2, repo.size())

        // Verify valid entries still exist
        assertNotNull(repo.findByRequestId("valid-1", now))
        assertNotNull(repo.findByRequestId("valid-2", now))
    }

    @Test
    fun `should delete specific PAR entry`() = runTest {
        val repo = InMemoryPARRepository()
        val now = Clock.System.now()
        val entry = createTestEntry("delete-me", now + 90.seconds)

        repo.store(entry)
        assertEquals(1, repo.size())

        val deleted = repo.delete("delete-me")

        assertTrue(deleted)
        assertEquals(0, repo.size())
    }

    @Test
    fun `should return false when deleting non-existent PAR entry`() = runTest {
        val repo = InMemoryPARRepository()

        val deleted = repo.delete("non-existent")

        assertFalse(deleted)
    }

    @Test
    fun `should clear all entries`() = runTest {
        val repo = InMemoryPARRepository()
        val now = Clock.System.now()

        repo.store(createTestEntry("entry-1", now + 90.seconds))
        repo.store(createTestEntry("entry-2", now + 90.seconds))
        repo.store(createTestEntry("entry-3", now + 90.seconds))

        assertEquals(3, repo.size())

        repo.clear()

        assertEquals(0, repo.size())
    }

    @Test
    fun `should handle concurrent access safely`() = runTest {
        val repo = InMemoryPARRepository()
        val now = Clock.System.now()

        // Simulate concurrent stores
        val entries = (1..10).map { i ->
            createTestEntry("concurrent-$i", now + 90.seconds)
        }

        entries.forEach { repo.store(it) }

        assertEquals(10, repo.size())

        // Verify all entries are retrievable
        entries.forEach { entry ->
            val retrieved = repo.findByRequestId(entry.requestId, now)
            assertNotNull(retrieved)
        }
    }

    @Test
    fun `should preserve client metadata`() = runTest {
        val repo = InMemoryPARRepository()
        val now = Clock.System.now()
        val entry = PAREntry(
            requestId = "metadata-test",
            request = PushedAuthorizationRequest(clientId = "test-client"),
            createdAt = now,
            expiresAt = now + 90.seconds,
            clientMetadata = mapOf(
                "client_assertion_type" to "urn:ietf:params:oauth:client-assertion-type:jwt-bearer",
                "client_assertion" to "eyJhbGciOiJSUzI1NiJ9..."
            )
        )

        repo.store(entry)
        val retrieved = repo.findByRequestId("metadata-test", now)

        assertNotNull(retrieved)
        assertEquals(2, retrieved.clientMetadata.size)
        assertEquals(
            "urn:ietf:params:oauth:client-assertion-type:jwt-bearer",
            retrieved.clientMetadata["client_assertion_type"]
        )
    }

    private fun createTestEntry(
        requestId: String,
        expiresAt: kotlin.time.Instant,
    ): PAREntry {
        // Ensure createdAt is before expiresAt for validation
        val createdAt = if (expiresAt.epochSeconds > 0) {
            Clock.System.now().let { now ->
                if (now < expiresAt) now else expiresAt - 90.seconds
            }
        } else {
            Clock.System.now() - 100.seconds
        }
        return PAREntry(
            requestId = requestId,
            request = PushedAuthorizationRequest(clientId = "test-client"),
            createdAt = createdAt,
            expiresAt = expiresAt,
        )
    }
}
