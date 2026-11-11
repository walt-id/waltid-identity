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
            val now = Clock.System.now()
            records.values.removeAll { it.expiresAt <= now }
            if (records.containsKey(record.code)) {
                throw DuplicateCodeException()
            }
            records[record.code] = record
        }
    }

    override suspend fun get(code: String): PreAuthorizedCodeRecord? =
        synchronized(lock) {
            records[code]?.takeIf { it.isRedeemableAt(Clock.System.now()) }
        }

    override suspend fun compareAndSetRedemptionState(
        expected: PreAuthorizedCodeRecord,
        replacement: PreAuthorizedCodeRedemptionState,
    ): Boolean =
        synchronized(lock) {
            val record = records[expected.code]
            if (record !== expected || !expected.isRedeemableAt(Clock.System.now())) return@synchronized false
            val grant = (expected as? RecordWithRedemptionState)?.grant ?: expected
            records[expected.code] = RecordWithRedemptionState(grant, replacement)
            true
        }

    private class RecordWithRedemptionState(
        val grant: PreAuthorizedCodeRecord,
        override val redemptionState: PreAuthorizedCodeRedemptionState,
    ) : PreAuthorizedCodeRecord by grant
}
