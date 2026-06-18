package id.walt.openid4vci.repository.par

import kotlin.time.Instant

/**
 * Storage abstraction for pushed authorization request records.
 *
 * Implementations must enforce single-use semantics in [consume].
 */
interface PARRepository {
    @Throws(DuplicatePARRecordException::class)
    suspend fun save(record: PARRecord)
    suspend fun consume(requestId: String, now: Instant): PARRecord?
}

class DuplicatePARRecordException :
    IllegalStateException("Pushed authorization request collision detected")
