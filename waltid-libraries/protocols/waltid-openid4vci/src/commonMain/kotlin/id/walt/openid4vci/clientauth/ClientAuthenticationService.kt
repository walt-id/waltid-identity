package id.walt.openid4vci.clientauth

import id.walt.openid4vci.errors.OAuthError
import id.walt.openid4vci.errors.OAuthErrorCodes

class ClientAuthenticationService(
    private val config: ClientAuthenticationServiceConfig = ClientAuthenticationServiceConfig(),
) {
    private val methodsByName = config.methods.associateBy { it.name }

    suspend fun authenticate(
        endpoint: ClientAuthenticationEndpoint,
        parameters: Map<String, List<String>>,
        headers: Map<String, List<String>>,
        context: ClientAuthenticationContext = ClientAuthenticationContext(),
    ): ClientAuthenticationResult {
        val endpointMethods = config.methodsForEndpoint(endpoint)
        if (endpointMethods.isEmpty()) {
            return ClientAuthenticationResult.Unauthenticated
        }

        val requestedMethods = ClientAuthenticationMethodDetector.detectRequestedMethods(parameters, headers)

        if (requestedMethods.size > 1) {
            return ClientAuthenticationResult.Failure(
                OAuthError(OAuthErrorCodes.INVALID_CLIENT, "Multiple client authentication methods are not allowed"),
            )
        }

        val requestedMethod = requestedMethods.singleOrNull()
            ?: return ClientAuthenticationResult.Failure(
                OAuthError(OAuthErrorCodes.INVALID_CLIENT, "Client authentication is required for this endpoint"),
            )

        if (requestedMethod !in endpointMethods) {
            return ClientAuthenticationResult.Failure(
                OAuthError(
                    OAuthErrorCodes.INVALID_CLIENT,
                    "Client authentication method '$requestedMethod' is not allowed for this endpoint",
                ),
            )
        }

        val method = methodsByName[requestedMethod]
            ?: return ClientAuthenticationResult.Failure(
                OAuthError(OAuthErrorCodes.INVALID_CLIENT, "Unsupported client authentication method"),
            )

        return method.authenticate(endpoint, parameters, headers, context)
    }
}
