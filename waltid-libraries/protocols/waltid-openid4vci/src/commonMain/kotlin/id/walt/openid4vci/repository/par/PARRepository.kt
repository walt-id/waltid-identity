package id.walt.openid4vci.repository.par

import kotlin.time.Instant

/**
 * Repository abstraction for PAR storage.
 *
 * Implementations:
 * - In-memory (OSS): simple concurrent map
 * - Persistent (Enterprise): database-backed with TTL indexes
 */
interface PARRepository {
    /**
     * Store a new PAR entry
     * @return the stored entry
     */
    suspend fun store(entry: PAREntry): PAREntry

    /**
     * Retrieve a PAR entry by request ID
     * @return the entry if found and valid, null otherwise
     */
    suspend fun findByRequestId(requestId: String, now: Instant): PAREntry?

    /**
     * Mark a PAR entry as consumed (single-use enforcement)
     * @return the updated entry, or null if not found
     */
    suspend fun markConsumed(requestId: String): PAREntry?

    /**
     * Delete expired PAR entries (cleanup)
     * @return number of entries deleted
     */
    suspend fun deleteExpired(now: Instant): Int

    /**
     * Delete a specific PAR entry
     */
    suspend fun delete(requestId: String): Boolean
}
