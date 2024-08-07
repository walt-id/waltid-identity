package id.walt.oid4vc.providers

import id.walt.oid4vc.requests.AuthorizationRequest
import kotlinx.datetime.Instant

open class SIOPSession(
    override val id: String,
    override val authorizationRequest: AuthorizationRequest?,
    override val expirationTimestamp: Instant,
    override val authServerState: String?  = null,  //the state used for additional authentication with pwd, id_token or vp_token.
) : AuthorizationSession() {
    val presentationDefinition get() = authorizationRequest?.presentationDefinition
    val nonce get() = authorizationRequest?.nonce
}
