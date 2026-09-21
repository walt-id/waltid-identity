package id.walt.verifier2.handlers.sessioncreation

import id.walt.verifier.openid.models.authorization.AuthorizationRequest

/**
 * OpenID4VP pre-registered client identifiers have no prefix, so they contain no `:`.
 * Those clients authenticate from out-of-band registration; in-band `client_metadata` is rejected.
 */
internal fun isPreRegisteredVerifierClientId(clientId: String?): Boolean =
    !clientId.isNullOrBlank() && ':' !in clientId

/** Wallet-facing copy of a generated request: registered clients must not carry in-band metadata. */
internal fun AuthorizationRequest.withoutPreRegisteredInBandMetadata(): AuthorizationRequest =
    if (clientMetadata != null && isPreRegisteredVerifierClientId(clientId)) {
        copy(clientMetadata = null)
    } else {
        this
    }
