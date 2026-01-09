package id.walt.openid4vci.core

import id.walt.openid4vci.DefaultSession
import id.walt.openid4vci.Session
import id.walt.openid4vci.TokenEndpointResult
import id.walt.openid4vci.platform.urlEncode
import id.walt.openid4vci.request.AccessTokenRequest
import id.walt.openid4vci.request.AuthorizationRequest

/**
 * Default implementation of [OAuth2Provider] that handles validators and handler registries.
 *
 * Keeping this class clean ensures that composition/config tests can stub collaborators without
 * pulling HTTP frameworks into the mix.
 *
 * Thread-safety: the provider stores the immutable [OAuth2ProviderConfig] and does not mutate handler registries
 * after `buildProvider` completes. Supplied validators/handlers/repositories must therefore be
 * concurrency-safe; the bundled in-memory repositories use synchronization so a single provider
 * instance can serve concurrent requests safely.
 *
 * Constructors are public so advanced consumers can bypass [buildOAuth2Provider] and wire handlers manually,
 * but typical callers should prefer [buildOAuth2Provider] which registers the default handlers.
 */
class DefaultOAuth2Provider(
    val config: OAuth2ProviderConfig,
) : OAuth2Provider {

    override fun createAuthorizeRequest(parameters: Map<String, String>): AuthorizeRequestResult =
        config.authorizeRequestValidator.validate(parameters)

    override suspend fun createAuthorizeResponse(
        request: AuthorizationRequest,
        session: Session
    ): AuthorizeResponseResult {
        request.setSession(session)
        val responses = mutableListOf<AuthorizeResponseResult>()
        for (handler in config.authorizeEndpointHandlers) {
            responses += handler.handleAuthorizeEndpointRequest(request, session)
        }

        val success = responses.filterIsInstance<AuthorizeResponseResult.Success>().firstOrNull()
        if (success != null) {
            return success
        }

        val failure = responses.filterIsInstance<AuthorizeResponseResult.Failure>().firstOrNull()
        return failure ?: AuthorizeResponseResult.Failure(
            OAuthError(
                error = "unsupported_response_type",
                description = request.getResponseTypes().joinToString(" ")
                    .ifBlank { "No authorize handler could handle the requested response type." },
            ),
        )
    }

    override fun writeAuthorizeError(request: AuthorizationRequest, error: OAuthError): AuthorizeHttpResponse {
        val baseRedirect = request.redirectUri
            ?: request.getClient().redirectUris.firstOrNull()

        return if (baseRedirect != null) {
            val parameters = buildMap {
                put("error", error.error)
                error.description?.let { put("error_description", it) }
                request.state?.let { put("state", it) }
            }
            val location = appendParams(baseRedirect, parameters)
            val headers = mutableMapOf("Location" to location)
            AuthorizeHttpResponse(
                status = 302,
                redirectUri = location,
                parameters = parameters,
                headers = headers,
            )
        } else {
            AuthorizeHttpResponse(
                status = 400,
                redirectUri = null,
                body = error.description ?: error.error,
            )
        }
    }

    override fun writeAuthorizeResponse(
        request: AuthorizationRequest,
        response: AuthorizeResponse
    ): AuthorizeHttpResponse {
        val location = appendParams(response.redirectUri, response.parameters)
        val headers = response.headers.toMutableMap()
        headers["Location"] = location
        return AuthorizeHttpResponse(
            status = 302,
            redirectUri = location,
            parameters = response.parameters,
            headers = headers,
        )
    }

    override fun createAccessRequest(parameters: Map<String, String>, session: Session?): AccessRequestResult {
        return config.accessRequestValidator.validate(
            parameters = parameters,
            session = session ?: DefaultSession()
        )
    }

    override suspend fun createAccessResponse(request: AccessTokenRequest): AccessResponseResult {
        for (handler in config.tokenEndpointHandlers.toList()) {
            if (!handler.canHandleTokenEndpointRequest(request)) {
                continue
            }

            return when (val result = handler.handleTokenEndpointRequest(request)) {
                is TokenEndpointResult.Success -> AccessResponseResult.Success(
                    AccessTokenResponse(
                        tokenType = result.tokenType,
                        accessToken = result.accessToken,
                        extra = result.extra,
                    ),
                )

                is TokenEndpointResult.Failure -> AccessResponseResult.Failure(
                    OAuthError(result.error, result.description),
                )
            }
        }

        val description = request.getGrantTypes().joinToString(" ").takeIf { it.isNotBlank() }
        return AccessResponseResult.Failure(
            OAuthError("unsupported_grant_type", description),
        )
    }

    override fun writeAccessError(request: AccessTokenRequest, error: OAuthError): AccessHttpResponse =
        AccessHttpResponse(
            status = 400,
            payload = buildMap {
                put("error", error.error)
                error.description?.let { put("error_description", it) }
            },
        )

    override fun writeAccessResponse(request: AccessTokenRequest, response: AccessTokenResponse): AccessHttpResponse =
        AccessHttpResponse(
            status = 200,
            payload = buildMap {
                put("token_type", response.tokenType)
                put("access_token", response.accessToken)
                response.expiresIn?.let { put("expires_in", it) }
                putAll(response.extra)
            },
        )

    private fun appendParams(base: String, parameters: Map<String, String>): String {
        if (parameters.isEmpty()) return base
        val separator = if (base.contains("?")) "&" else "?"
        val query = parameters.entries.joinToString("&") { (key, value) ->
            "${urlEncode(key)}=${urlEncode(value)}"
        }
        return base + separator + query
    }
}
