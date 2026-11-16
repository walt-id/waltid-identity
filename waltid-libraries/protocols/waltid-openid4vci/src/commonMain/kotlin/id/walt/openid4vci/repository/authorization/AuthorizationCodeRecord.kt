package id.walt.openid4vci.repository.authorization

import id.walt.openid4vci.Session
import id.walt.openid4vci.pkce.PkceBinding
import kotlinx.serialization.Serializable
import kotlin.time.Instant

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
    /** Null identifies an older code whose original PKCE state is unknown and must be rejected. */
    val pkceBinding: PkceBinding?
}

@Serializable
data class DefaultAuthorizationCodeRecord(
    override val code: String,
    override val clientId: String,
    override val redirectUri: String?,
    override val grantedScopes: Set<String>,
    override val grantedAudience: Set<String>,
    override val session: Session,
    override val expiresAt: Instant,
    override val pkceBinding: PkceBinding? = null,
) : AuthorizationCodeRecord
