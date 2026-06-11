package id.walt.openid4vci.repository.par

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.time.Instant

/**
 * In-memory PAR repository implementation.
 *
 * Thread-safe concurrent storage using Mutex.
 * Suitable for:
 * - Single-instance deployments
 * - Development and testing
 * - OSS Issuer2 default implementation
 *
 * For clustered/distributed Enterprise deployments, use a persistent implementation.
 */
class InMemoryPARRepository : PARRepository {
    private val storage = mutableMapOf<String, PAREntry>()
    private val mutex = Mutex()

    override suspend fun store(entry: PAREntry): PAREntry = mutex.withLock {
        storage[entry.requestId] = entry
        entry
    }

    override suspend fun findByRequestId(requestId: String, now: Instant): PAREntry? = mutex.withLock {
        storage[requestId]?.takeIf { it.isValid(now) }
    }

    override suspend fun markConsumed(requestId: String): PAREntry? = mutex.withLock {
        storage[requestId]?.let { entry ->
            val consumed = entry.markConsumed()
            storage[requestId] = consumed
            consumed
        }
    }

    override suspend fun deleteExpired(now: Instant): Int = mutex.withLock {
        val expired = storage.values.filter { !it.isValid(now) }
        expired.forEach { storage.remove(it.requestId) }
        expired.size
    }

    override suspend fun delete(requestId: String): Boolean = mutex.withLock {
        storage.remove(requestId) != null
    }

    /**
     * Clear all entries (for testing)
     */
    suspend fun clear() = mutex.withLock {
        storage.clear()
    }

    /**
     * Get current size (for testing)
     */
    suspend fun size(): Int = mutex.withLock {
        storage.size
    }
}
