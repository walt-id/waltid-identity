package id.walt.openid4vci.repository.preauthorized

import id.walt.openid4vci.repository.authorization.DuplicateCodeException
import kotlinx.atomicfu.locks.SynchronizedObject
import kotlinx.atomicfu.locks.synchronized
import kotlin.time.Clock

/**
 * In-memory pre-authorized code repository.
 *
 * Suitable for single-instance deployments, development, and testing.
 */
class InMemoryPreAuthorizedCodeRepository : PreAuthorizedCodeRepository {
    private val records = mutableMapOf<String, PreAuthorizedCodeRecord>()
    private val lock = SynchronizedObject()

    override suspend fun save(record: PreAuthorizedCodeRecord) {
        synchronized(lock) {
            if (records.containsKey(record.code)) {
                throw DuplicateCodeException()
            }
            records[record.code] = record
        }
    }

    override suspend fun get(code: String): PreAuthorizedCodeRecord? =
        synchronized(lock) {
            records[code]
        }

    override suspend fun consume(code: String): PreAuthorizedCodeRecord? =
        synchronized(lock) {
            val record = records.remove(code) ?: return@synchronized null
            record.takeIf { Clock.System.now() <= it.expiresAt }
        }
}
