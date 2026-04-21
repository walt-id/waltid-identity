package id.walt.issuer2.oauth

import id.walt.openid4vci.Session
import id.walt.openid4vci.offers.TxCode
import id.walt.openid4vci.repository.authorization.DuplicateCodeException
import id.walt.openid4vci.repository.preauthorized.PreAuthorizedCodeRecord
import id.walt.openid4vci.repository.preauthorized.PreAuthorizedCodeRepository
import java.util.concurrent.ConcurrentHashMap
import kotlin.time.Clock
import kotlin.time.Instant

data class InMemoryPreAuthorizedCodeRecord(
    override val code: String,
    override val clientId: String?,
    override val txCode: TxCode?,
    override val txCodeValue: String?,
    override val grantedScopes: Set<String>,
    override val grantedAudience: Set<String>,
    override val session: Session,
    override val expiresAt: Instant,
    override val credentialNonce: String?,
    override val credentialNonceExpiresAt: Instant?,
    val sessionId: String,
) : PreAuthorizedCodeRecord

class InMemoryPreAuthorizedCodeRepository : PreAuthorizedCodeRepository {
    private val records = ConcurrentHashMap<String, InMemoryPreAuthorizedCodeRecord>()

    override suspend fun save(record: PreAuthorizedCodeRecord) {
        if (records.containsKey(record.code)) {
            throw DuplicateCodeException()
        }
        records[record.code] = when (record) {
            is InMemoryPreAuthorizedCodeRecord -> record
            else -> InMemoryPreAuthorizedCodeRecord(
                code = record.code,
                clientId = record.clientId,
                txCode = record.txCode,
                txCodeValue = record.txCodeValue,
                grantedScopes = record.grantedScopes,
                grantedAudience = record.grantedAudience,
                session = record.session,
                expiresAt = record.expiresAt,
                credentialNonce = record.credentialNonce,
                credentialNonceExpiresAt = record.credentialNonceExpiresAt,
                sessionId = "",
            )
        }
    }

    override suspend fun get(code: String): PreAuthorizedCodeRecord? = records[code]

    override suspend fun consume(code: String): PreAuthorizedCodeRecord? {
        val record = records.remove(code) ?: return null
        if (Clock.System.now() > record.expiresAt) {
            return null
        }
        return record
    }

    fun getRecord(code: String): InMemoryPreAuthorizedCodeRecord? = records[code]
}
