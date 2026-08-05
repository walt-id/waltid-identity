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
    ): ClientValidationResult = authenticate(
        clientId,
        context,
        preRegisteredMetadataProvider,
        ClientIdTrustConfiguration(),
    )

    suspend fun authenticate(
        clientId: ClientId,
        context: RequestContext,
        preRegisteredMetadataProvider: suspend (String) -> String?,
        trustConfiguration: ClientIdTrustConfiguration,
    ): ClientValidationResult {
        return when (clientId) {
            is RedirectUri -> clientId.authenticateRedirectUri(context)
            is X509SanDns -> clientId.authenticateX509SanDns(clientId, context, trustConfiguration)
            is X509Hash -> clientId.authenticateX509Hash(clientId, context, trustConfiguration)
            is DecentralizedIdentifier -> clientId.authenticateDecentralizedIdentifier(clientId, context)
            is VerifierAttestation -> clientId.authenticateVerifierAttestation(clientId, context, trustConfiguration)
            is PreRegistered -> clientId.authenticatePreRegistered(clientId, context, preRegisteredMetadataProvider)

            // OpenIdFederation requires OpenID Federation trust chain resolution (not yet implemented)
            is OpenIdFederation -> ClientValidationResult.Failure(ClientIdError.FederationError("OpenID Federation trust chain resolution is not yet implemented"))
            // Origin is not allowed to be accepted by the wallet
            is Unsupported -> ClientValidationResult.Failure(ClientIdError.UnsupportedPrefix(clientId.prefix))
        }
    }
}
