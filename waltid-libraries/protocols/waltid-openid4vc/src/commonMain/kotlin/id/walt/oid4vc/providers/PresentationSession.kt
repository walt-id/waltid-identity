package id.walt.oid4vc.providers

import id.walt.crypto.keys.Key
import id.walt.oid4vc.data.OpenId4VPProfile
import id.walt.oid4vc.data.dif.PresentationDefinition
import id.walt.oid4vc.requests.AuthorizationRequest
import id.walt.oid4vc.responses.TokenResponse
import kotlinx.datetime.Instant
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement

@Serializable
data class PresentationSession(
    override val id: String,
    override val authorizationRequest: AuthorizationRequest?,
    override val expirationTimestamp: Instant,
    override val authServerState: String?  = null,  //the state used for additional authentication with pwd, id_token or vp_token.
    val presentationDefinition: PresentationDefinition,
    val tokenResponse: TokenResponse? = null,
    val verificationResult: Boolean? = null,
    val walletInitiatedAuthState: String? = null,
    val customParameters: Map<String, JsonElement>? = null,
    val ephemeralEncKey: Key? = null,
    val openId4VPProfile: OpenId4VPProfile = OpenId4VPProfile.DEFAULT,
    val trustedRootCAs: List<String>? = null
) : AuthorizationSession()
