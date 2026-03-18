package id.walt.openid4vci.validation

import id.walt.openid4vci.errors.OAuthError
import id.walt.openid4vci.requests.authorization.AuthorizationRequest

/**
 * Optional hook for issuer-defined issuer_state validation.
 *
 * The library treats issuer_state as opaque and only invokes this hook when provided.
 * Implementations should return null when the value is acceptable, or an OAuthError
 * to reject the request.
 */
fun interface IssuerStateValidator {
    fun validate(issuerState: String, request: AuthorizationRequest): OAuthError?
}
