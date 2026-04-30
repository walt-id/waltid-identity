package id.walt.issuer2.oauth

import id.walt.openid4vci.Session
import id.walt.openid4vci.repository.authorization.AuthorizationCodeRecord
import id.walt.openid4vci.repository.authorization.AuthorizationCodeRepository
import id.walt.openid4vci.repository.authorization.DuplicateCodeException
import java.util.concurrent.ConcurrentHashMap
import kotlin.time.Clock
import kotlin.time.Instant

data class InMemoryAuthorizationCodeRecord(
    override val code: String,
    override val clientId: String,
    override val redirectUri: String?,
    override val grantedScopes: Set<String>,
    override val grantedAudience: Set<String>,
    override val session: Session,
    override val expiresAt: Instant,
    val sessionId: String,
    val codeChallenge: String? = null,
    val codeChallengeMethod: String? = null,
) : AuthorizationCodeRecord

class InMemoryAuthorizationCodeRepository : AuthorizationCodeRepository {
    private val records = ConcurrentHashMap<String, InMemoryAuthorizationCodeRecord>()

    override suspend fun save(record: AuthorizationCodeRecord) {
        val newRecord = when (record) {
            is InMemoryAuthorizationCodeRecord -> record
            else -> InMemoryAuthorizationCodeRecord(
                code = record.code,
                clientId = record.clientId,
                redirectUri = record.redirectUri,
                grantedScopes = record.grantedScopes,
                grantedAudience = record.grantedAudience,
                session = record.session,
                expiresAt = record.expiresAt,
                sessionId = "",
            )
        }
        val existing = records.putIfAbsent(newRecord.code, newRecord)
        if (existing != null) {
            throw DuplicateCodeException()
        }
    }

    override suspend fun consume(code: String): AuthorizationCodeRecord? {
        val record = records.remove(code) ?: return null
        if (Clock.System.now() > record.expiresAt) {
            return null
        }
        return record
    }

    fun getRecord(code: String): InMemoryAuthorizationCodeRecord? = records[code]
}
