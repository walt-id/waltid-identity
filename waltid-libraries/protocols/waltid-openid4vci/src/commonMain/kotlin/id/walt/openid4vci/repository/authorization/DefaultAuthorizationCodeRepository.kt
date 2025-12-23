package id.walt.openid4vci.repository.authorization

import kotlinx.atomicfu.locks.SynchronizedObject
import kotlinx.atomicfu.locks.synchronized
import kotlin.time.ExperimentalTime

@OptIn(ExperimentalTime::class)
internal fun defaultAuthorizationCodeRepository(): AuthorizationCodeRepository =
    object : AuthorizationCodeRepository {
        private val records = mutableMapOf<String, AuthorizationCodeRecord>()
        private val lock = SynchronizedObject()

        override suspend fun save(record: AuthorizationCodeRecord) {
            synchronized(lock) {
                records[record.code] = record
            }
        }

        override suspend fun consume(code: String): AuthorizationCodeRecord? {
            return synchronized(lock) {
                val record = records.remove(code) ?: return null
                if (kotlin.time.Clock.System.now() > record.expiresAt) {
                    return null
                }
                record
            }
        }
    }
