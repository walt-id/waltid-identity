package id.walt.oid4vc.providers

import id.walt.oid4vc.data.CredentialOffer
import id.walt.oid4vc.data.TxCode
import id.walt.oid4vc.requests.AuthorizationRequest
import kotlinx.datetime.Instant
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement

@Serializable
data class IssuanceSession(
    override val id: String,
    override val authorizationRequest: AuthorizationRequest?,
    override val expirationTimestamp: Instant,
    val txCode: TxCode? = null,
    val txCodeValue: String? = null,
    override val authServerState: String?  = null, //the state used for additional authentication with pwd, id_token or vp_token.
    val credentialOffer: CredentialOffer? = null,
    val cNonce: String? = null,
    val customParameters: Map<String, JsonElement>? = null,
) : AuthorizationSession()
