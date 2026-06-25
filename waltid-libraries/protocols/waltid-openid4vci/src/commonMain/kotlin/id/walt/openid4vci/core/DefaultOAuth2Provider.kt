package id.walt.openid4vci.core

import id.walt.openid4vci.DefaultSession
import id.walt.openid4vci.ResponseMode
import id.walt.openid4vci.Session
import id.walt.openid4vci.errors.OAuthError
import id.walt.openid4vci.errors.OAuthErrorCodes
import id.walt.openid4vci.platform.urlEncode
import id.walt.openid4vci.requests.authorization.AuthorizationRequest
import id.walt.openid4vci.requests.authorization.AuthorizationRequestResult
import id.walt.openid4vci.requests.token.AccessTokenRequest
import id.walt.openid4vci.requests.token.AccessTokenRequestResult
import id.walt.openid4vci.requests.credential.CredentialRequest
import id.walt.openid4vci.responses.authorization.AuthorizationResponse
import id.walt.openid4vci.responses.authorization.AuthorizationResponseResult
import id.walt.openid4vci.responses.authorization.AuthorizationResponseHttp
import id.walt.openid4vci.responses.par.PushedAuthorizationResponse
import id.walt.openid4vci.responses.par.PushedAuthorizationResponseHttp
import id.walt.openid4vci.responses.par.PushedAuthorizationResponseResult
import id.walt.openid4vci.responses.token.AccessTokenResponse
import id.walt.openid4vci.responses.token.AccessTokenResponseHttp
import id.walt.openid4vci.responses.token.AccessTokenResponseResult
import id.walt.openid4vci.responses.token.TokenResponseOptions
import id.walt.openid4vci.responses.token.withOptions
import id.walt.openid4vci.responses.credential.CredentialResponse
import id.walt.openid4vci.responses.credential.CredentialResponseHttp
import id.walt.openid4vci.responses.credential.CredentialResponseResult
import id.walt.openid4vci.requests.credential.CredentialRequestResult
import id.walt.openid4vci.metadata.issuer.CredentialConfiguration
import id.walt.openid4vci.metadata.issuer.CredentialDisplay
import id.walt.mdoc.dataelement.json.JsonObjectToCborMappingConfig as LegacyMdocJsonObjectToCborMappingConfig
import id.walt.crypto.keys.Key
import id.walt.mdoc.objects.mso.Status
import id.walt.openid4vci.tokens.access.AccessTokenContext
import id.walt.sdjwt.SDMap
import id.walt.x509.CertificateDer
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlin.time.Clock
import kotlin.time.Instant


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

    override suspend fun createAuthorizationRequest(parameters: Map<String, List<String>>): AuthorizationRequestResult =
        when (val resolution = resolveAuthorizationParameters(parameters)) {
            is AuthorizationParameterResolution.Success ->
                config.authorizationRequestValidator.validate(resolution.parameters)

            is AuthorizationParameterResolution.Failure ->
                AuthorizationRequestResult.Failure(resolution.error)
        }

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
                error = OAuthErrorCodes.UNSUPPORTED_RESPONSE_TYPE,
                description = authorizationRequest.responseTypes.joinToString(" ")
                    .ifBlank { "No authorize handler could handle the requested response type." },
            ),
        )
    }

    override fun writeAuthorizationError(error: OAuthError): AuthorizationResponseHttp =
        AuthorizationResponseHttp(
            status = 400,
            redirectUri = null,
            body = error.description ?: error.error,
        )

    override fun writeAuthorizationError(
        authorizationRequest: AuthorizationRequest,
        error: OAuthError
    ): AuthorizationResponseHttp {
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
            ResponseMode.QUERY -> appendParams(response.redirectUri, params)
            ResponseMode.FRAGMENT -> appendFragment(response.redirectUri, params)
        }

        val headers = response.headers.toMutableMap().apply { this["Location"] = location }
        return AuthorizationResponseHttp(
            status = 302,
            redirectUri = location,
            parameters = params,
            headers = headers,
        )
    }

    override suspend fun createPushedAuthorizationRequest(parameters: Map<String, List<String>>): AuthorizationRequestResult {
        if (parameters["request_uri"].orEmpty().any { it.isNotBlank() }) {
            return AuthorizationRequestResult.Failure(
                OAuthError(
                    error = OAuthErrorCodes.INVALID_REQUEST,
                    description = "request_uri is not allowed at the pushed authorization request endpoint",
                )
            )
        }
        return config.authorizationRequestValidator.validate(parameters)
    }

    override suspend fun createPushedAuthorizationResponse(
        authorizationRequest: AuthorizationRequest,
        clientAuthentication: Map<String, String>,
    ): PushedAuthorizationResponseResult {
        if (config.pushedAuthorizationConfig == null && config.pushedAuthorizationEndpointHandlers.count() == 0) {
            return PushedAuthorizationResponseResult.Failure(
                OAuthError(
                    error = OAuthErrorCodes.SERVER_ERROR,
                    description = "Pushed authorization requests are not configured",
                )
            )
        }

        val failures = mutableListOf<PushedAuthorizationResponseResult.Failure>()

        for (handler in config.pushedAuthorizationEndpointHandlers) {
            when (
                val result = handler.handlePushedAuthorizationEndpointRequest(
                    authorizationRequest = authorizationRequest,
                    clientAuthentication = clientAuthentication,
                )
            ) {
                is PushedAuthorizationResponseResult.Success -> return result
                is PushedAuthorizationResponseResult.Failure -> failures += result
            }
        }

        return failures.firstOrNull() ?: PushedAuthorizationResponseResult.Failure(
            OAuthError(
                error = OAuthErrorCodes.SERVER_ERROR,
                description = "No pushed authorization endpoint handler is configured",
            )
        )
    }

    override fun writePushedAuthorizationError(error: OAuthError): PushedAuthorizationResponseHttp =
        PushedAuthorizationResponseHttp(
            status = oauthJsonErrorStatus(error),
            payload = oauthErrorPayload(error),
            headers = noStoreHeaders(),
        )

    override fun writePushedAuthorizationError(
        authorizationRequest: AuthorizationRequest,
        error: OAuthError,
    ): PushedAuthorizationResponseHttp =
        writePushedAuthorizationError(error)

    override fun writePushedAuthorizationResponse(
        authorizationRequest: AuthorizationRequest,
        response: PushedAuthorizationResponse,
    ): PushedAuthorizationResponseHttp =
        PushedAuthorizationResponseHttp(
            status = 201,
            payload = buildMap {
                put("request_uri", JsonPrimitive(response.requestUri))
                put("expires_in", JsonPrimitive(response.expiresIn))
            },
            headers = noStoreHeaders(),
        )

    override fun createAccessTokenRequest(
        parameters: Map<String, List<String>>,
        session: Session?
    ): AccessTokenRequestResult {
        return config.accessTokenRequestValidator.validate(
            parameters = parameters,
            session = session ?: DefaultSession()
        )
    }

    override suspend fun createAccessTokenResponse(
        request: AccessTokenRequest,
        options: TokenResponseOptions,
    ): AccessTokenResponseResult {
        for (handler in config.tokenEndpointHandlers.toList()) {
            if (!handler.canHandleTokenEndpointRequest(request)) {
                continue
            }

            return when (val result = handler.handleTokenEndpointRequest(request)) {
                is AccessTokenResponseResult.Success -> result.copy(
                    response = result.response.withOptions(options, result.request),
                )
                is AccessTokenResponseResult.Failure -> result
            }
        }

        val description = request.grantTypes.joinToString(" ").takeIf { it.isNotBlank() }
        return AccessTokenResponseResult.Failure(
            OAuthError("unsupported_grant_type", description),
        )
    }

    override fun writeAccessTokenError(error: OAuthError): AccessTokenResponseHttp =
        AccessTokenResponseHttp(
            status = 400,
            headers = TOKEN_RESPONSE_HEADERS,
            payload = buildMap {
                put("error", JsonPrimitive(error.error))
                error.description?.let { put("error_description", JsonPrimitive(it)) }
            },
        )

    override fun writeAccessTokenError(request: AccessTokenRequest, error: OAuthError): AccessTokenResponseHttp =
        writeAccessTokenError(error)

    override fun writeAccessTokenResponse(
        request: AccessTokenRequest,
        response: AccessTokenResponse
    ): AccessTokenResponseHttp =
        AccessTokenResponseHttp(
            status = 200,
            headers = TOKEN_RESPONSE_HEADERS,
            payload = buildMap {
                put("token_type", JsonPrimitive(response.tokenType))
                put("access_token", JsonPrimitive(response.accessToken))
                response.expiresIn?.let { put("expires_in", JsonPrimitive(it)) }
                response.refreshToken?.let { put("refresh_token", JsonPrimitive(it)) }
                response.scope?.let { put("scope", JsonPrimitive(it)) }
                response.extra.forEach { (key, value) ->
                    put(key, value.toJsonElement())
                }
            },
        )

    private fun Any?.toJsonElement(): JsonElement = when (this) {
        null -> JsonNull
        is JsonElement -> this
        is String -> JsonPrimitive(this)
        is Boolean -> JsonPrimitive(this)
        is Int -> JsonPrimitive(this)
        is Long -> JsonPrimitive(this)
        is Float -> JsonPrimitive(this)
        is Double -> JsonPrimitive(this)
        is Number -> JsonPrimitive(this.toDouble())
        is Iterable<*> -> buildJsonArray { this@toJsonElement.forEach { add(it.toJsonElement()) } }
        is Array<*> -> buildJsonArray { this@toJsonElement.forEach { add(it.toJsonElement()) } }
        is Map<*, *> -> JsonObject(this.entries.associate { (k, v) -> k.toString() to v.toJsonElement() })
        else -> JsonPrimitive(this.toString())
    }

    private companion object {
        val TOKEN_RESPONSE_HEADERS = mapOf(
            "Cache-Control" to "no-store",
            "Pragma" to "no-cache",
        )
    }

    override suspend fun createCredentialRequest(
        parameters: Map<String, List<String>>,
        session: Session?,
        accessTokenContext: AccessTokenContext?
    ): CredentialRequestResult {
        if (accessTokenContext != null) {
            val verifier = config.accessTokenVerifier
                ?: return CredentialRequestResult.Failure(
                    OAuthError("invalid_request", "access token verifier not configured")
                )
            try {
                verifier.verify(
                    token = accessTokenContext.token,
                    expectedIssuer = accessTokenContext.expectedIssuer,
                    expectedAudience = accessTokenContext.expectedAudience,
                )
            } catch (e: Exception) {
                return CredentialRequestResult.Failure(OAuthError("invalid_request", e.message))
            }
        }
        return config.credentialRequestValidator.validate(parameters, session ?: DefaultSession())
    }

    override suspend fun createCredentialResponse(
        request: CredentialRequest,
        configuration: CredentialConfiguration,
        issuerKey: Key,
        issuerId: String,
        credentialData: JsonObject,
        dataMapping: JsonObject?,
        selectiveDisclosure: SDMap?,
        x5Chain: List<CertificateDer>?,
        display: List<CredentialDisplay>?,
        w3cVersion: String?,
        mDocNameSpacesDataMappingConfig: Map<String, LegacyMdocJsonObjectToCborMappingConfig>?,
        credentialStatus: Status?,
        validFrom: Instant?,
        validUntil: Instant?,
    ): CredentialResponseResult {
        val handler = config.credentialEndpointHandlers.get(configuration.format)
            ?: return CredentialResponseResult.Failure(
                OAuthError(
                    error = "unsupported_credential_configuration",
                    description = "No handler for format ${configuration.format.value}"
                )
            )
        return handler.sign(
            request = request,
            configuration = configuration,
            issuerKey = issuerKey,
            issuerId = issuerId,
            credentialData = credentialData,
            dataMapping = dataMapping,
            selectiveDisclosure = selectiveDisclosure,
            x5Chain = x5Chain,
            display = display,
            w3cVersion = w3cVersion,
            mDocNameSpacesDataMappingConfig = mDocNameSpacesDataMappingConfig,
            credentialStatus = credentialStatus,
            validFrom = validFrom,
            validUntil = validUntil,
        )
    }

    override fun writeCredentialError(error: OAuthError): CredentialResponseHttp =
        CredentialResponseHttp(
            status = 400,
            payload = buildMap {
                put("error", JsonPrimitive(error.error))
                error.description?.let { put("error_description", JsonPrimitive(it)) }
            },
        )

    override fun writeCredentialError(request: CredentialRequest, error: OAuthError): CredentialResponseHttp =
        writeCredentialError(error)

    override fun writeCredentialResponse(
        request: CredentialRequest,
        response: CredentialResponse
    ): CredentialResponseHttp =
        CredentialResponseHttp(
            status = 200,
            payload = buildMap {
                response.credentials?.let { issued ->
                    put(
                        "credentials",
                        buildJsonArray {
                            issued.forEach { credentialEntry ->
                                add(
                                    JsonObject(
                                        mapOf("credential" to credentialEntry.credential)
                                    )
                                )
                            }
                        }
                    )
                }
                response.transactionId?.let { put("transaction_id", JsonPrimitive(it)) }
                response.interval?.let { put("interval", JsonPrimitive(it)) }
                response.notificationId?.let { put("notification_id", JsonPrimitive(it)) }
            }
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

    private suspend fun resolveAuthorizationParameters(
        parameters: Map<String, List<String>>,
    ): AuthorizationParameterResolution {
        val requestUriValues = parameters["request_uri"].orEmpty().filter { it.isNotBlank() }
        if (requestUriValues.size > 1) {
            return AuthorizationParameterResolution.Failure(
                OAuthError(OAuthErrorCodes.INVALID_REQUEST, "Multiple values for request_uri not allowed")
            )
        }

        val requestUri = requestUriValues.firstOrNull()
        val pushedAuthorizationConfig = config.pushedAuthorizationConfig
        if (requestUri == null) {
            return if (pushedAuthorizationConfig?.enforcePushedAuthorizationRequests == true) {
                AuthorizationParameterResolution.Failure(
                    OAuthError(
                        error = OAuthErrorCodes.INVALID_REQUEST,
                        description = "Pushed authorization requests are required",
                    )
                )
            } else {
                AuthorizationParameterResolution.Success(parameters)
            }
        }

        if (pushedAuthorizationConfig == null) {
            return AuthorizationParameterResolution.Failure(
                OAuthError(
                    error = OAuthErrorCodes.INVALID_REQUEST_URI,
                    description = "request_uri is not supported",
                )
            )
        }

        val clientIdValues = parameters["client_id"].orEmpty().filter { it.isNotBlank() }
        val clientId = when (clientIdValues.size) {
            0 -> return AuthorizationParameterResolution.Failure(
                OAuthError(
                    error = OAuthErrorCodes.INVALID_REQUEST,
                    description = "client_id is required when using request_uri",
                )
            )

            1 -> clientIdValues.first()
            else -> return AuthorizationParameterResolution.Failure(
                OAuthError(OAuthErrorCodes.INVALID_REQUEST, "Multiple values for client_id not allowed")
            )
        }

        val requestId = PushedAuthorizationResponse.extractRequestId(
            requestUri = requestUri,
            requestUriPrefix = pushedAuthorizationConfig.requestUriPrefix,
        ) ?: return AuthorizationParameterResolution.Failure(
            OAuthError(
                error = OAuthErrorCodes.INVALID_REQUEST_URI,
                description = "request_uri is invalid",
            )
        )

        val entry = pushedAuthorizationConfig.repository.consume(requestId, Clock.System.now())
            ?: return AuthorizationParameterResolution.Failure(
                OAuthError(
                    error = OAuthErrorCodes.INVALID_REQUEST_URI,
                    description = "request_uri is invalid, expired, or already consumed",
                )
            )

        if (entry.clientId != clientId) {
            return AuthorizationParameterResolution.Failure(
                OAuthError(
                    error = OAuthErrorCodes.INVALID_REQUEST,
                    description = "client_id mismatch between pushed and authorization requests",
                )
            )
        }

        return AuthorizationParameterResolution.Success(entry.requestParameters)
    }

    private fun oauthErrorPayload(error: OAuthError): Map<String, JsonElement> =
        buildMap {
            put("error", JsonPrimitive(error.error))
            error.description?.let { put("error_description", JsonPrimitive(it)) }
        }

    private fun oauthJsonErrorStatus(error: OAuthError): Int =
        when (error.error) {
            OAuthErrorCodes.INVALID_CLIENT -> 401
            OAuthErrorCodes.SERVER_ERROR,
            OAuthErrorCodes.TEMPORARILY_UNAVAILABLE -> 500

            else -> 400
        }

    private fun noStoreHeaders(): Map<String, String> =
        mapOf(
            "Cache-Control" to "no-store",
            "Pragma" to "no-cache",
        )

    private sealed class AuthorizationParameterResolution {
        data class Success(val parameters: Map<String, List<String>>) : AuthorizationParameterResolution()
        data class Failure(val error: OAuthError) : AuthorizationParameterResolution()
    }
}