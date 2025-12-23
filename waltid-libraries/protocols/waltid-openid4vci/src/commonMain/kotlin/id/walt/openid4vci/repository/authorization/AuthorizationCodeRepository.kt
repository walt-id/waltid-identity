package id.walt.openid4vci.repository.authorization

import id.walt.openid4vci.Session
import kotlin.time.Instant
import kotlin.time.ExperimentalTime

/**
 * Storage abstraction for authorization code sessions.
 */
interface AuthorizationCodeRepository {
    suspend fun save(record: AuthorizationCodeRecord)
    suspend fun consume(code: String): AuthorizationCodeRecord?
}

data class AuthorizationCodeRecord @OptIn(ExperimentalTime::class) constructor(
    val code: String,
    val clientId: String,
    val redirectUri: String?,
    val grantedScopes: Set<String>,
    val grantedAudience: Set<String>,
    val session: Session,
    val expiresAt: Instant,
)
