@file:OptIn(ExperimentalTime::class)

package id.walt.oid4vc.providers

import id.walt.oid4vc.requests.AuthorizationRequest
import kotlin.time.Clock
import kotlin.time.ExperimentalTime
import kotlin.time.Instant

abstract class AuthorizationSession {
    abstract val id: String
    abstract val authorizationRequest: AuthorizationRequest?
    abstract val expirationTimestamp: Instant
    abstract val authServerState: String? //the state used for additional authentication with pwd, id_token or vp_token.
    val isExpired get() = expirationTimestamp < Clock.System.now()
}
