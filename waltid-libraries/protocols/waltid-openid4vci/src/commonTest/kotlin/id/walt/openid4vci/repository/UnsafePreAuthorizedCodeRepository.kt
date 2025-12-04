package id.walt.openid4vci.repository

import id.walt.openid4vci.repository.preauthorized.PreAuthorizedCodeRecord
import id.walt.openid4vci.repository.preauthorized.PreAuthorizedCodeRepository
import kotlin.time.ExperimentalTime

class UnsafePreAuthorizedCodeRepository : PreAuthorizedCodeRepository {
    private val records = mutableMapOf<Pair<String, String>, PreAuthorizedCodeRecord>()

    override fun save(record: PreAuthorizedCodeRecord, issuerId: String) {
        records[issuerId to record.code] = record
    }

    override fun get(code: String, issuerId: String): PreAuthorizedCodeRecord? =
        records[issuerId to code]

    @OptIn(ExperimentalTime::class)
    override fun consume(code: String, issuerId: String): PreAuthorizedCodeRecord? {
        val record = records.remove(issuerId to code) ?: return null
        if (kotlin.time.Clock.System.now() > record.expiresAt) {
            return null
        }
        return record
    }
}
