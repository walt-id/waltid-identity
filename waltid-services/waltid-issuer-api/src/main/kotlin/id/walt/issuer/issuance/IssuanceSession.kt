@file:OptIn(ExperimentalTime::class)

package id.walt.issuer.issuance

import id.walt.oid4vc.data.CredentialOffer
import id.walt.oid4vc.data.TxCode
import id.walt.oid4vc.providers.AuthorizationSession
import id.walt.oid4vc.requests.AuthorizationRequest
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement
import kotlin.time.ExperimentalTime
import kotlin.time.Instant

@Serializable
enum class IssuanceSessionStatus {
    ACTIVE, SUCCESSFUL, UNSUCCESSFUL, REJECTED_BY_USER, EXPIRED
}

@Serializable
data class IssuanceSession(
    override val id: String,
    override val authorizationRequest: AuthorizationRequest?,
    override val expirationTimestamp: Instant,
    val issuanceRequests: List<IssuanceRequest>,
    val txCode: TxCode? = null,
    val txCodeValue: String? = null,
    override val authServerState: String? = null, //the state used for additional authentication with pwd, id_token or vp_token.
    val credentialOffer: CredentialOffer? = null,
    val cNonce: String? = null,
    val callbackUrl: String? = null,
    val customParameters: Map<String, JsonElement>? = null,
    val status: IssuanceSessionStatus = IssuanceSessionStatus.ACTIVE,
    val statusReason: String? = null,
    val isClosed: Boolean = false,
) : AuthorizationSession()
