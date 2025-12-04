package id.walt.openid4vci.repository.preauthorized

import kotlinx.atomicfu.locks.SynchronizedObject
import kotlinx.atomicfu.locks.synchronized
import kotlin.time.ExperimentalTime

@OptIn(ExperimentalTime::class)
internal fun defaultPreAuthorizedCodeRepository(): PreAuthorizedCodeRepository =
    object : PreAuthorizedCodeRepository {
        private val records = mutableMapOf<Pair<String, String>, PreAuthorizedCodeRecord>()
        private val lock = SynchronizedObject()

        override fun save(record: PreAuthorizedCodeRecord, issuerId: String) {
            synchronized(lock) {
                records[issuerKey(issuerId, record.code)] = record
            }
        }

        override fun get(code: String, issuerId: String): PreAuthorizedCodeRecord? = synchronized(lock) {
            records[issuerKey(issuerId, code)]
        }

        override fun consume(code: String, issuerId: String): PreAuthorizedCodeRecord? {
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
