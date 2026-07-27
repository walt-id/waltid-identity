package id.walt.openid4vci.repository.preauthorized

import id.walt.openid4vci.Session
import id.walt.openid4vci.offers.TxCode
import kotlinx.serialization.Serializable
import kotlin.time.Instant

/**
 * Pre-authorized code payload. Applications can supply custom implementations if they need extra fields.
 */
interface PreAuthorizedCodeRecord {
    val code: String
    val clientId: String?
    val txCode: TxCode?
    val txCodeValue: String?
    val grantedScopes: Set<String>
    val grantedAudience: Set<String>
    val session: Session
    val expiresAt: Instant

    /** Optional issuance session ID for O(1) lookup on credential endpoint */
    val issuanceSessionId: String?
        get() = null
}

@Serializable
data class DefaultPreAuthorizedCodeRecord(
    override val code: String,
    override val clientId: String?,
    override val txCode: TxCode?,
    override val txCodeValue: String?,
    override val grantedScopes: Set<String>,
    override val grantedAudience: Set<String>,
    override val session: Session,
    override val expiresAt: Instant,
    override val issuanceSessionId: String? = null,
) : PreAuthorizedCodeRecord
