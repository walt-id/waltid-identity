@file:OptIn(kotlin.time.ExperimentalTime::class)

package id.walt.openid4vci.repository.preauthorized

import id.walt.openid4vci.Session
import kotlin.time.Instant

/**
 * Storage abstraction for OpenID4VCI pre-authorized code sessions.
 */
interface PreAuthorizedCodeRepository {
    suspend fun save(record: PreAuthorizedCodeRecord)
    suspend fun get(code: String): PreAuthorizedCodeRecord?
    suspend fun consume(code: String): PreAuthorizedCodeRecord?
}

data class PreAuthorizedCodeRecord constructor(
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
