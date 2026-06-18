package id.walt.openid4vci.repository.authorization

import kotlinx.atomicfu.locks.SynchronizedObject
import kotlinx.atomicfu.locks.synchronized
import kotlin.time.Clock

/**
 * In-memory authorization code repository.
 *
 * Suitable for single-instance deployments, development, and testing.
 */
class InMemoryAuthorizationCodeRepository : AuthorizationCodeRepository {
    private val records = mutableMapOf<String, AuthorizationCodeRecord>()
    private val lock = SynchronizedObject()

    override suspend fun save(record: AuthorizationCodeRecord) {
        synchronized(lock) {
            if (records.containsKey(record.code)) {
                throw DuplicateCodeException()
            }
            records[record.code] = record
        }
    }

    override suspend fun consume(code: String): AuthorizationCodeRecord? =
        synchronized(lock) {
            val record = records.remove(code) ?: return@synchronized null
            record.takeIf { Clock.System.now() < it.expiresAt }
        }
}
