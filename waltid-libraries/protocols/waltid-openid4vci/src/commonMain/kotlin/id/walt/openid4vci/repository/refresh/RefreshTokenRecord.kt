package id.walt.openid4vci.repository.refresh

import id.walt.openid4vci.Session
import id.walt.openid4vci.requests.token.AccessTokenRequest
import kotlin.time.Instant

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
