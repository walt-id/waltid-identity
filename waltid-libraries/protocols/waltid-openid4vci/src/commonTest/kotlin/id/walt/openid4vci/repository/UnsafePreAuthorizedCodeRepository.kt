package id.walt.openid4vci.repository

import id.walt.openid4vci.repository.preauthorized.PreAuthorizedCodeRecord
import id.walt.openid4vci.repository.preauthorized.PreAuthorizedCodeRepository

class UnsafePreAuthorizedCodeRepository : PreAuthorizedCodeRepository {
    private val records = mutableMapOf<String, PreAuthorizedCodeRecord>()

    override suspend fun save(record: PreAuthorizedCodeRecord) {
        records[record.code] = record
    }

    override suspend fun get(code: String): PreAuthorizedCodeRecord? =
        records[code]

    override suspend fun consume(code: String): PreAuthorizedCodeRecord? {
        val record = records.remove(code) ?: return null
        if (kotlin.time.Clock.System.now() > record.expiresAt) {
            return null
        }
        return record
    }
}
