package id.walt.openid4vp.clientidprefix

import id.walt.openid4vp.clientidprefix.prefixes.*

object ClientIdPrefixAuthenticator {

    /**
     * Authenticates a client by validating its credentials against the request context.
     */
    suspend fun authenticate(
        clientId: ClientId,
        context: RequestContext,
        preRegisteredMetadataProvider: suspend (String) -> String? = { null }
    ): ClientValidationResult {
        return when (clientId) {
            is RedirectUri -> clientId.authenticateRedirectUri(context)
            is X509SanDns -> clientId.authenticateX509SanDns(clientId, context)
            is X509Hash -> clientId.authenticateX509Hash(clientId, context)
            is DecentralizedIdentifier -> clientId.authenticateDecentralizedIdentifier(clientId, context)
            // is VerifierAttestation -> clientId.authenticateVerifierAttestation(clientId, context) // To be implemented later
            // is OpenIdFederation -> clientId.authenticateOpenIdFederation(clientId, context)     // To be implemented later
            is PreRegistered -> clientId.authenticatePreRegistered(clientId, preRegisteredMetadataProvider)
            // Origin is not allowed to be accepted by the wallet
            is Unsupported -> ClientValidationResult.Failure(ClientIdError.UnsupportedPrefix(clientId.prefix))
            else -> TODO("Handler not implemented for ${clientId::class.simpleName}")
        }
    }
}
