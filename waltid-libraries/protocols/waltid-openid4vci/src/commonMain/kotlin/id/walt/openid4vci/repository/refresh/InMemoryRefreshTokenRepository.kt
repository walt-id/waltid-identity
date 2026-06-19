package id.walt.openid4vci.repository.refresh

import id.walt.openid4vci.repository.authorization.DuplicateCodeException
import kotlinx.atomicfu.locks.SynchronizedObject
import kotlinx.atomicfu.locks.synchronized
import kotlin.time.Clock

class InMemoryRefreshTokenRepository : RefreshTokenRepository {
    private val records = mutableMapOf<String, RefreshTokenRecord>()
    private val inactiveSignatures = mutableSetOf<String>()
    private val lock = SynchronizedObject()

    override suspend fun save(record: RefreshTokenRecord) {
        synchronized(lock) {
            if (records.containsKey(record.tokenSignature)) {
                throw DuplicateCodeException()
            }
            records[record.tokenSignature] = record
            if (!record.active) {
                inactiveSignatures += record.tokenSignature
            }
        }
    }

    override suspend fun get(tokenSignature: String): RefreshTokenRecord? =
        synchronized(lock) {
            records[tokenSignature]?.withActiveOverride(tokenSignature)
        }

    override suspend fun rotate(
        tokenSignature: String,
        replacement: RefreshTokenRecord,
    ): RefreshTokenRecord? =
        synchronized(lock) {
            val current = records[tokenSignature] ?: return null
            if (!current.isActive(tokenSignature) || Clock.System.now() >= current.expiresAt) {
                inactiveSignatures += tokenSignature
                return null
            }
            if (records.containsKey(replacement.tokenSignature)) {
                throw DuplicateCodeException()
            }
            inactiveSignatures += tokenSignature
            records[replacement.tokenSignature] = replacement
            if (!replacement.active) {
                inactiveSignatures += replacement.tokenSignature
            }
            current
        }

    private fun RefreshTokenRecord.isActive(tokenSignature: String): Boolean =
        active && tokenSignature !in inactiveSignatures

    private fun RefreshTokenRecord.withActiveOverride(tokenSignature: String): RefreshTokenRecord =
        takeIf { isActive(tokenSignature) } ?: StoredRefreshTokenRecord(this, activeOverride = false)
}

private data class StoredRefreshTokenRecord(
    private val delegate: RefreshTokenRecord,
    private val activeOverride: Boolean,
) : RefreshTokenRecord by delegate {
    override val active: Boolean
        get() = activeOverride
}
