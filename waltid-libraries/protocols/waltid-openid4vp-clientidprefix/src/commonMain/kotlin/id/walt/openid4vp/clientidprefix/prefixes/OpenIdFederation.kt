package id.walt.openid4vp.clientidprefix.prefixes

import kotlinx.serialization.Serializable

/**
 * Handles `openid_federation` prefix per OpenID4VP 1.0, Section 5.9.3.
 */
@Serializable
data class OpenIdFederation(val entityId: String, override val rawValue: String) : ClientId {
    /*
    private suspend fun authenticateOpenIdFederation(clientId: OpenIdFederation, context: RequestContext): ClientValidationResult {
        // This will be implemented at a later stage.
        return ClientValidationResult.Failure(ClientIdError.UnsupportedPrefix("openid_federation"))
    }
    */
}
