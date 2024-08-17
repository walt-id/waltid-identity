package id.walt.oid4vc.providers

import id.walt.oid4vc.requests.AuthorizationRequest
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant

abstract class AuthorizationSession {
    abstract val id: String
    abstract val authorizationRequest: AuthorizationRequest?
    abstract val expirationTimestamp: Instant
    abstract val authServerState: String? //the state used for additional authentication with pwd, id_token or vp_token.
    val isExpired get() = expirationTimestamp < Clock.System.now()
}
