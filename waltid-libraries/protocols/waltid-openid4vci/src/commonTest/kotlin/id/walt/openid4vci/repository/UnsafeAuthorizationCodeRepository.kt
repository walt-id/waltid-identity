package id.walt.openid4vci.repository

import id.walt.openid4vci.repository.authorization.AuthorizationCodeRecord
import id.walt.openid4vci.repository.authorization.AuthorizationCodeRepository
import kotlin.time.ExperimentalTime

class UnsafeAuthorizationCodeRepository : AuthorizationCodeRepository {
    private val records = mutableMapOf<Pair<String, String>, AuthorizationCodeRecord>()

    override fun save(record: AuthorizationCodeRecord, issuerId: String) {
        records[issuerId to record.code] = record
    }

    @OptIn(ExperimentalTime::class)
    override fun consume(code: String, issuerId: String): AuthorizationCodeRecord? {
        val record = records.remove(issuerId to code) ?: return null
        if (kotlin.time.Clock.System.now() > record.expiresAt) {
            return null
        }
        return record
    }
}
