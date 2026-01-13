package id.walt.openid4vci.repository

import id.walt.openid4vci.repository.authorization.AuthorizationCodeRecord
import id.walt.openid4vci.repository.authorization.AuthorizationCodeRepository

class UnsafeAuthorizationCodeRepository : AuthorizationCodeRepository {
    private val records = mutableMapOf<String, AuthorizationCodeRecord>()

    override suspend fun save(record: AuthorizationCodeRecord) {
        records[record.code] = record
    }

    override suspend fun consume(code: String): AuthorizationCodeRecord? {
        val record = records.remove(code) ?: return null
        if (kotlin.time.Clock.System.now() >= record.expiresAt) {
            return null
        }
        return record
    }
}
