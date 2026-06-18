package id.walt.openid4vci.repository.refresh

import id.walt.openid4vci.repository.authorization.DuplicateCodeException
import kotlinx.atomicfu.locks.SynchronizedObject
import kotlinx.atomicfu.locks.synchronized
import kotlin.time.Clock

fun defaultRefreshTokenRepository(): RefreshTokenRepository =
    object : RefreshTokenRepository {
        private val records = mutableMapOf<String, StoredRefreshTokenRecord>()
        private val lock = SynchronizedObject()

        override suspend fun save(record: RefreshTokenRecord) {
            synchronized(lock) {
                if (records.containsKey(record.tokenSignature)) {
                    throw DuplicateCodeException()
                }
                records[record.tokenSignature] = StoredRefreshTokenRecord(record, record.active)
            }
        }

        override suspend fun get(tokenSignature: String): RefreshTokenRecord? =
            synchronized(lock) {
                records[tokenSignature]
            }

        override suspend fun rotate(
            tokenSignature: String,
            replacement: RefreshTokenRecord,
        ): RefreshTokenRecord? =
            synchronized(lock) {
                val current = records[tokenSignature] ?: return null
                if (!current.active || Clock.System.now() >= current.expiresAt) {
                    records[tokenSignature] = current.copy(activeOverride = false)
                    return null
                }
                if (records.containsKey(replacement.tokenSignature)) {
                    throw DuplicateCodeException()
                }
                records[tokenSignature] = current.copy(activeOverride = false)
                records[replacement.tokenSignature] = StoredRefreshTokenRecord(replacement, replacement.active)
                current
            }
    }

private data class StoredRefreshTokenRecord(
    private val delegate: RefreshTokenRecord,
    private val activeOverride: Boolean,
) : RefreshTokenRecord by delegate {
    override val active: Boolean
        get() = activeOverride
}
