package id.walt.openid4vci.repository.preauthorized

import id.walt.openid4vci.Session
import kotlin.time.Instant
import kotlin.time.ExperimentalTime

/**
 * Storage abstraction for OpenID4VCI pre-authorized code sessions.
 */
interface PreAuthorizedCodeRepository {
    fun save(record: PreAuthorizedCodeRecord, issuerId: String)
    fun get(code: String, issuerId: String): PreAuthorizedCodeRecord?
    fun consume(code: String, issuerId: String): PreAuthorizedCodeRecord?
}

data class PreAuthorizedCodeRecord @OptIn(ExperimentalTime::class) constructor(
    val code: String,
    val clientId: String?,
    val userPinRequired: Boolean,
    val userPin: String?,
    val grantedScopes: Set<String>,
    val grantedAudience: Set<String>,
    val session: Session,
    val expiresAt: Instant,
    val credentialNonce: String?,
    val credentialNonceExpiresAt: Instant?,
)
