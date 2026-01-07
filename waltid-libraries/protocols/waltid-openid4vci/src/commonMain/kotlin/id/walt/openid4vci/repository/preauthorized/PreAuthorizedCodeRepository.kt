@file:OptIn(kotlin.time.ExperimentalTime::class)

package id.walt.openid4vci.repository.preauthorized

import id.walt.openid4vci.Session
import kotlin.time.Instant

interface PreAuthorizedCodeRepository {
    suspend fun save(record: PreAuthorizedCodeRecord)
    suspend fun get(code: String): PreAuthorizedCodeRecord?
    suspend fun consume(code: String): PreAuthorizedCodeRecord?
}

/**
 * Pre-authorized code payload. Applications can supply custom implementations if they need extra fields.
 */
interface PreAuthorizedCodeRecord {
    val code: String
    val clientId: String?
    val userPinRequired: Boolean
    val userPin: String?
    val grantedScopes: Set<String>
    val grantedAudience: Set<String>
    val session: Session
    val expiresAt: Instant
    val credentialNonce: String?
    val credentialNonceExpiresAt: Instant?
}

data class DefaultPreAuthorizedCodeRecord(
    override val code: String,
    override val clientId: String?,
    override val userPinRequired: Boolean,
    override val userPin: String?,
    override val grantedScopes: Set<String>,
    override val grantedAudience: Set<String>,
    override val session: Session,
    override val expiresAt: Instant,
    override val credentialNonce: String?,
    override val credentialNonceExpiresAt: Instant?,
) : PreAuthorizedCodeRecord
