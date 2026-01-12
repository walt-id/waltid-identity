package id.walt.openid4vci.repository.authorization

import id.walt.openid4vci.Session
import kotlin.time.Instant

/**
 * Storage abstraction for authorization code sessions.
 */
interface AuthorizationCodeRepository {
    @Throws(DuplicateCodeException::class)
    suspend fun save(record: AuthorizationCodeRecord)
    suspend fun consume(code: String): AuthorizationCodeRecord?
}

class DuplicateCodeException : IllegalStateException("Code collision detected")

/**
 * Authorization code payload. Applications can supply custom implementations if they need extra fields.
 */
interface AuthorizationCodeRecord {
    val code: String
    val clientId: String
    val redirectUri: String?
    val grantedScopes: Set<String>
    val grantedAudience: Set<String>
    val session: Session
    val expiresAt: Instant
}

data class DefaultAuthorizationCodeRecord(
    override val code: String,
    override val clientId: String,
    override val redirectUri: String?,
    override val grantedScopes: Set<String>,
    override val grantedAudience: Set<String>,
    override val session: Session,
    override val expiresAt: Instant,
) : AuthorizationCodeRecord
