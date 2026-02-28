package id.walt.openid4vci.core

import id.walt.openid4vci.DefaultSession
import id.walt.openid4vci.ResponseModeType
import id.walt.openid4vci.Session
import id.walt.openid4vci.errors.OAuthError
import id.walt.openid4vci.platform.urlEncode
import id.walt.openid4vci.requests.authorization.AuthorizationRequest
import id.walt.openid4vci.requests.authorization.AuthorizationRequestResult
import id.walt.openid4vci.requests.token.AccessTokenRequest
import id.walt.openid4vci.requests.token.AccessTokenRequestResult
import id.walt.openid4vci.responses.authorization.AuthorizationResponse
import id.walt.openid4vci.responses.authorization.AuthorizationResponseResult
import id.walt.openid4vci.responses.authorization.AuthorizationResponseHttp
import id.walt.openid4vci.responses.token.AccessTokenResponse
import id.walt.openid4vci.responses.token.AccessTokenResponseHttp
import id.walt.openid4vci.responses.token.AccessTokenResponseResult


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

    override fun createAuthorizationRequest(parameters: Map<String, List<String>>): AuthorizationRequestResult =
        config.authorizationRequestValidator.validate(parameters)

    override suspend fun createAuthorizationResponse(
        authorizationRequest: AuthorizationRequest,
        session: Session
    ): AuthorizationResponseResult {
        val responses = mutableListOf<AuthorizationResponseResult>()
        for (handler in config.authorizationEndpointHandlers) {
            responses += handler.handleAuthorizationEndpointRequest(authorizationRequest, session)
        }

        val success = responses.filterIsInstance<AuthorizationResponseResult.Success>().firstOrNull()
        if (success != null) {
            return success
        }

        val failure = responses.filterIsInstance<AuthorizationResponseResult.Failure>().firstOrNull()
        return failure ?: AuthorizationResponseResult.Failure(
            OAuthError(
                error = id.walt.openid4vci.errors.OAuthErrorCodes.UNSUPPORTED_RESPONSE_TYPE,
                description = authorizationRequest.responseTypes.joinToString(" ")
                    .ifBlank { "No authorize handler could handle the requested response type." },
            ),
        )
    }

    override fun writeAuthorizationError(authorizationRequest: AuthorizationRequest, error: OAuthError): AuthorizationResponseHttp {
        val baseRedirect = authorizationRequest.redirectUri
            ?: authorizationRequest.client.redirectUris.firstOrNull()

        return if (baseRedirect != null) {
            val parameters = buildMap {
                put("error", error.error)
                error.description?.let { put("error_description", it) }
                authorizationRequest.state?.let { put("state", it) }
            }
            val location = appendParams(baseRedirect, parameters)
            val headers = mutableMapOf("Location" to location)
            AuthorizationResponseHttp(
                status = 302,
                redirectUri = location,
                parameters = parameters,
                headers = headers,
            )
        } else {
            AuthorizationResponseHttp(
                status = 400,
                redirectUri = null,
                body = error.description ?: error.error,
            )
        }
    }

    override fun writeAuthorizationResponse(
        authorizationRequest: AuthorizationRequest,
        response: AuthorizationResponse
    ): AuthorizationResponseHttp {
        val params = buildMap {
            put("code", response.code)
            response.state?.let { put("state", it) }
            response.scope?.let { put("scope", it) }
            putAll(response.extraParameters)
        }

        val location = when (response.responseMode) {
            ResponseModeType.QUERY -> appendParams(response.redirectUri, params)
            ResponseModeType.FRAGMENT -> appendFragment(response.redirectUri, params)
        }

        val headers = response.headers.toMutableMap().apply { this["Location"] = location }
        return AuthorizationResponseHttp(
            status = 302,
            redirectUri = location,
            parameters = params,
            headers = headers,
        )
    }

    override fun createAccessTokenRequest(parameters: Map<String, List<String>>, session: Session?): AccessTokenRequestResult {
        return config.accessTokenRequestValidator.validate(
            parameters = parameters,
            session = session ?: DefaultSession()
        )
    }

    override suspend fun createAccessTokenResponse(request: AccessTokenRequest): AccessTokenResponseResult {
        for (handler in config.tokenEndpointHandlers.toList()) {
            if (!handler.canHandleTokenEndpointRequest(request)) {
                continue
            }

            return handler.handleTokenEndpointRequest(request)
        }

        val description = request.grantTypes.joinToString(" ").takeIf { it.isNotBlank() }
        return AccessTokenResponseResult.Failure(
            OAuthError("unsupported_grant_type", description),
        )
    }

    override fun writeAccessTokenError(request: AccessTokenRequest, error: OAuthError): AccessTokenResponseHttp =
        AccessTokenResponseHttp(
            status = 400,
            payload = buildMap {
                put("error", error.error)
                error.description?.let { put("error_description", it) }
            },
        )

    override fun writeAccessTokenResponse(request: AccessTokenRequest, response: AccessTokenResponse): AccessTokenResponseHttp =
        AccessTokenResponseHttp(
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

    private fun appendFragment(base: String, parameters: Map<String, String>): String {
        if (parameters.isEmpty()) return base
        val fragment = parameters.entries.joinToString("&") { (key, value) ->
            "${urlEncode(key)}=${urlEncode(value)}"
        }
        val hashIndex = base.indexOf("#")
        val baseWithoutFragment = if (hashIndex >= 0) base.substring(0, hashIndex) else base
        return "$baseWithoutFragment#$fragment"
    }
}
