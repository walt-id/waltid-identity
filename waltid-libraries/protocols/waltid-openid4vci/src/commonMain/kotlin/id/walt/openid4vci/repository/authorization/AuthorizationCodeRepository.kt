@file:OptIn(kotlin.time.ExperimentalTime::class)

package id.walt.openid4vci.repository.authorization

import id.walt.openid4vci.Session
import kotlin.time.Instant

/**
 * Storage abstraction for authorization code sessions.
 */
interface AuthorizationCodeRepository {
    suspend fun save(record: AuthorizationCodeRecord)
    suspend fun consume(code: String): AuthorizationCodeRecord?
}

data class AuthorizationCodeRecord constructor(
    val code: String,
    val clientId: String,
    val redirectUri: String?,
    val grantedScopes: Set<String>,
    val grantedAudience: Set<String>,
    val session: Session,
    val expiresAt: Instant,
)
