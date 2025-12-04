package id.walt.openid4vci.repository.authorization

import kotlinx.atomicfu.locks.SynchronizedObject
import kotlinx.atomicfu.locks.synchronized
import kotlin.time.ExperimentalTime

@OptIn(ExperimentalTime::class)
internal fun defaultAuthorizationCodeRepository(): AuthorizationCodeRepository =
    object : AuthorizationCodeRepository {
        private val records = mutableMapOf<Pair<String, String>, AuthorizationCodeRecord>()
        private val lock = SynchronizedObject()

        override fun save(record: AuthorizationCodeRecord, issuerId: String) {
            synchronized(lock) {
                records[issuerKey(issuerId, record.code)] = record
            }
        }

        override fun consume(code: String, issuerId: String): AuthorizationCodeRecord? {
            return synchronized(lock) {
                val record = records.remove(issuerKey(issuerId, code)) ?: return null
                if (kotlin.time.Clock.System.now() > record.expiresAt) {
                    return null
                }
                record
            }
        }

        private fun issuerKey(issuerId: String, code: String) = issuerId to code
    }
