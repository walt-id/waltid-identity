package id.walt.openid4vci.validation

import id.walt.openid4vci.Session
import id.walt.openid4vci.requests.authorization.AuthorizationRequestResult
import id.walt.openid4vci.requests.credential.CredentialRequestResult
import id.walt.openid4vci.requests.token.AccessTokenRequestResult

/**
 * Parses authorization parameters and rejects requests disallowed by current policy.
 *
 * Also invoked when a PAR reference is redeemed, using the stored parameters without
 * endpoint-only client credentials. A failure rejects redemption; success permits the
 * stored typed request to continue, preserving its identity and enriched fields.
 */
fun interface AuthorizationRequestValidator {
    fun validate(parameters: Map<String, List<String>>): AuthorizationRequestResult
}

fun interface AccessTokenRequestValidator {
    fun validate(parameters: Map<String, List<String>>, session: Session): AccessTokenRequestResult
}

interface CredentialRequestValidator {
    fun validate(parameters: Map<String, List<String>>, session: Session?): CredentialRequestResult
}
