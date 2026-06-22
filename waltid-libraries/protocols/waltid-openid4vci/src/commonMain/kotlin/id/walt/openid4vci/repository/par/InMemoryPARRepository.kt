package id.walt.openid4vci.repository.par

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.time.Instant

/**
 * In-memory pushed authorization request repository.
 *
 * Suitable for single-instance deployments, development, and testing.
 */
class InMemoryPARRepository : PARRepository {
    private val records = mutableMapOf<String, PARRecord>()
    private val mutex = Mutex()

    override suspend fun save(record: PARRecord): Unit = mutex.withLock {
        if (records.containsKey(record.requestId)) {
            throw DuplicatePARRecordException()
        }
        records[record.requestId] = record
    }

    override suspend fun consume(requestId: String, now: Instant): PARRecord? = mutex.withLock {
        val record = records.remove(requestId) ?: return@withLock null
        record.takeIf { it.isValid(now) }
    }
}
