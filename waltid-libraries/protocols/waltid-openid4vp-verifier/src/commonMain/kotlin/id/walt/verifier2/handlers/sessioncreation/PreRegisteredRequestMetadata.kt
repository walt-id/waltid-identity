package id.walt.verifier2.handlers.sessioncreation

import id.walt.openid4vp.clientidprefix.ClientIdPrefix
import id.walt.verifier.openid.models.authorization.AuthorizationRequest

/**
 * A client id is pre-registered when it has no recognized OpenID4VP prefix.
 *
 * Plain names have no colon. URL-shaped ids such as `https://verifier.example` contain a colon,
 * but `https` is not a [ClientIdPrefix], so the wallet authenticates them through registration
 * fallback and rejects in-band `client_metadata`. Recognized prefixes (`redirect_uri`, `x509_*`,
 * DID, attestation, federation) keep in-band metadata.
 */
internal fun isPreRegisteredVerifierClientId(clientId: String?): Boolean {
    if (clientId.isNullOrBlank()) return false
    val separator = clientId.indexOf(':')
    if (separator < 0) return true
    val prefix = ClientIdPrefix.fromValue(clientId.substring(0, separator))
    return prefix == null || prefix == ClientIdPrefix.PRE_REGISTERED
}

/** Wallet-facing copy of a generated request: registered clients must not carry in-band metadata. */
internal fun AuthorizationRequest.withoutPreRegisteredInBandMetadata(): AuthorizationRequest =
    if (clientMetadata != null && isPreRegisteredVerifierClientId(clientId)) {
        copy(clientMetadata = null)
    } else {
        this
    }
