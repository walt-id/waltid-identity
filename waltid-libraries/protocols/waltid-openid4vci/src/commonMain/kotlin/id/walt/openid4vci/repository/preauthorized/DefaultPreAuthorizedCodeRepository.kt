package id.walt.openid4vci.repository.preauthorized

import id.walt.openid4vci.repository.authorization.DuplicateCodeException
import kotlinx.atomicfu.locks.SynchronizedObject
import kotlinx.atomicfu.locks.synchronized

internal fun defaultPreAuthorizedCodeRepository(): PreAuthorizedCodeRepository =
    object : PreAuthorizedCodeRepository {
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

        override suspend fun consume(code: String): PreAuthorizedCodeRecord? {
            return synchronized(lock) {
                val record = records.remove(code) ?: return null
                if (kotlin.time.Clock.System.now() > record.expiresAt) {
                    return null
                }
                record
            }
        }
    }
