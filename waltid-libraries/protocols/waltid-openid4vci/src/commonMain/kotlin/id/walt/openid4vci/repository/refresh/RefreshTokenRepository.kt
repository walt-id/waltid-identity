package id.walt.openid4vci.repository.refresh

import id.walt.openid4vci.Session
import id.walt.openid4vci.repository.authorization.DuplicateCodeException
import id.walt.openid4vci.requests.token.AccessTokenRequest
import kotlin.time.Instant

interface RefreshTokenRepository {
    @Throws(DuplicateCodeException::class)
    suspend fun save(record: RefreshTokenRecord)
    suspend fun get(tokenSignature: String): RefreshTokenRecord?

    /**
     * Keeps the old record as inactive and stores [replacement].
     *
     * Returns the old active record on success. Returns null when the token is
     * unknown, already inactive, or expired.
     */
    @Throws(DuplicateCodeException::class)
    suspend fun rotate(tokenSignature: String, replacement: RefreshTokenRecord): RefreshTokenRecord?
}

interface RefreshTokenRecord {
    val tokenSignature: String
    val active: Boolean
    val accessTokenRequest: AccessTokenRequest
    val accessTokenSignature: String
    val clientId: String?
    val grantedScopes: Set<String>
    val grantedAudience: Set<String>
    val session: Session
    val expiresAt: Instant
}

data class DefaultRefreshTokenRecord(
    override val tokenSignature: String,
    override val active: Boolean = true,
    override val accessTokenRequest: AccessTokenRequest,
    override val accessTokenSignature: String,
    override val clientId: String?,
    override val grantedScopes: Set<String>,
    override val grantedAudience: Set<String>,
    override val session: Session,
    override val expiresAt: Instant,
) : RefreshTokenRecord
