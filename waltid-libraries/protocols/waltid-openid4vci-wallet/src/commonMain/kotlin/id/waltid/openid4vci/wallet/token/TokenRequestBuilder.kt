package id.waltid.openid4vci.wallet.token

import id.walt.openid4vci.GrantType
import id.walt.openid4vci.clientauth.attestation.ClientAttestationHeaders.CLIENT_ATTESTATION_CHALLENGE
import id.walt.openid4vci.errors.OAuthError
import id.walt.openid4vci.requests.authorization.AuthorizationDetail
import id.waltid.openid4vci.wallet.attestation.ClientAttestationHeaders
import id.waltid.openid4vci.wallet.clientauth.CLIENT_ASSERTION_TYPE_JWT_BEARER
import id.waltid.openid4vci.wallet.dpop.DPOP_HEADER
import id.waltid.openid4vci.wallet.dpop.DPOP_NONCE_ATTEMPTS
import id.waltid.openid4vci.wallet.dpop.DPOP_NONCE_HEADER
import id.waltid.openid4vci.wallet.dpop.USE_DPOP_NONCE
import id.waltid.openid4vci.wallet.oauth.ClientConfiguration
import io.github.oshai.kotlinlogging.KotlinLogging
import io.ktor.client.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

private val log = KotlinLogging.logger {}
private val tokenResponseJson = Json { ignoreUnknownKeys = true }

/** Creates a fresh RFC 9449 proof for the target endpoint and optional server nonce. */
typealias DPoPProofFactory = suspend (targetEndpoint: String, nonce: String?) -> String

/**
 * Produces an RFC 7523 `client_assertion` for `private_key_jwt` client authentication.
 *
 * Must return a **fresh** assertion on every invocation: RFC 7523 §3 requires a unique `jti`, and
 * authorization servers reject reuse. It is therefore called once per request attempt, including
 * each DPoP nonce retry, rather than once per logical token request.
 *
 * The audience is chosen by the caller that builds the factory, because the correct value depends
 * on the profile: FAPI 2.0 §5.3.3.1 requires the authorization server's issuer identifier, while
 * plain RFC 7523 also permits the token endpoint.
 */
typealias ClientAssertionFactory = suspend () -> String

/** Observes response headers without exposing token response bodies to the caller. */
typealias TokenResponseHeadersHandler = suspend (Headers) -> Unit

/** Builds fresh client-attestation headers for each token HTTP request. */
typealias ClientAttestationHeadersFactory = suspend () -> ClientAttestationHeaders

/**
 * Stands in for a body that carries no OAuth `error`, so the failure is attributable without
 * repeating an arbitrary response body.
 */
private const val NON_OAUTH_ERROR_BODY = "response body is not an OAuth error object"

/**
 * Sanitized token endpoint failure that never retains the response body.
 *
 * Only the `error` and `error_description` fields of an OAuth 2.0 error response (RFC 6749 §5.2)
 * are carried, because those are server-authored and meant to be shown. A response that is not an
 * OAuth error object is reported as such rather than echoed, so an opaque server-side failure
 * stays distinguishable from a genuinely rejected grant.
 */
class TokenRequestException(
    val statusCode: Int,
    val oauthError: String? = null,
    val oauthErrorDescription: String? = null,
    cause: Throwable? = null,
) : Exception(
    buildString {
        append("Token request failed with HTTP ")
        append(statusCode)
        listOfNotNull(oauthError, oauthErrorDescription)
            .takeIf { it.isNotEmpty() }
            ?.let { append(" (").append(it.joinToString(": ")).append(')') }
    },
    cause,
)

/**
 * Builds OAuth 2.0 token requests for OpenID4VCI.
 * Implements §6 of OpenID4VCI 1.0 specification (Token Endpoint).
 *
 * @property clientConfig The OAuth 2.0 client configuration
 * @property httpClient The HTTP client for token requests
 */
class TokenRequestBuilder(
    private val clientConfig: ClientConfiguration,
    private val httpClient: HttpClient,
) {

    /**
     * Token response from the authorization server
     */
    @Serializable
    data class TokenResponse(
        val access_token: String,
        val token_type: String,
        val expires_in: Long? = null,
        val refresh_token: String? = null,
        val scope: String? = null,
        val authorization_details: List<AuthorizationDetail>? = null
    )

    /**
     * Exchanges an authorization code for an access token
     *
     * @param tokenEndpoint The token endpoint URL from metadata
     * @param code The authorization code received from authorization endpoint
     * @param codeVerifier The PKCE code verifier (if PKCE was used)
     * @param additionalHeaders Extra HTTP headers for token endpoint client authentication
     * @param attestationHeaders Attestation-based client authentication headers
     * @return TokenResponse containing the OAuth access token response fields
     * @throws Exception if token request fails
     */
    suspend fun exchangeAuthorizationCode(
        tokenEndpoint: String,
        code: String,
        codeVerifier: String? = null,
        additionalHeaders: Map<String, String> = emptyMap(),
        attestationHeaders: ClientAttestationHeaders? = null,
    ): TokenResponse = exchangeAuthorizationCode(
        tokenEndpoint = tokenEndpoint,
        code = code,
        codeVerifier = codeVerifier,
        additionalHeaders = additionalHeaders,
        attestationHeaders = attestationHeaders,
        dpopProofFactory = null,
    )

    /** Exchanges an authorization code while creating fresh DPoP proofs when requested. */
    suspend fun exchangeAuthorizationCode(
        tokenEndpoint: String,
        code: String,
        codeVerifier: String? = null,
        additionalHeaders: Map<String, String> = emptyMap(),
        attestationHeaders: ClientAttestationHeaders? = null,
        dpopProofFactory: DPoPProofFactory?,
        /**
         * `private_key_jwt` client authentication (RFC 7523) for the token endpoint.
         *
         * The authorization-code exchange needs this exactly as much as the pre-authorized-code
         * exchange already does: omitting it left the request unauthenticated, which an authorization
         * server advertising `private_key_jwt` rejects with "Could not find client assertion in
         * request parameters".
         */
        clientAssertionFactory: ClientAssertionFactory? = null,
    ): TokenResponse = exchangeAuthorizationCode(
        tokenEndpoint = tokenEndpoint,
        code = code,
        codeVerifier = codeVerifier,
        additionalHeaders = additionalHeaders,
        attestationHeaders = attestationHeaders,
        dpopProofFactory = dpopProofFactory,
        clientAssertionFactory = clientAssertionFactory,
        onResponseHeaders = {},
        attestationHeadersFactory = null,
    )

    /** Exchanges an authorization code while creating fresh DPoP proofs when requested. */
    suspend fun exchangeAuthorizationCode(
        tokenEndpoint: String,
        code: String,
        codeVerifier: String? = null,
        additionalHeaders: Map<String, String> = emptyMap(),
        attestationHeaders: ClientAttestationHeaders? = null,
        dpopProofFactory: DPoPProofFactory?,
        clientAssertionFactory: ClientAssertionFactory? = null,
        onResponseHeaders: TokenResponseHeadersHandler,
        attestationHeadersFactory: ClientAttestationHeadersFactory?,
    ): TokenResponse {
        require(tokenEndpoint.isNotBlank()) { "Token endpoint cannot be blank" }
        require(code.isNotBlank()) { "Authorization code cannot be blank" }

        log.info { "Exchanging authorization code for access token" }
        log.trace { "Token endpoint: $tokenEndpoint" }
        log.trace { "Code verifier present: ${codeVerifier != null}" }
        log.trace { "Additional headers: ${additionalHeaders.keys}" }
        log.trace { "Client attestation: ${attestationHeaders != null || attestationHeadersFactory != null}" }

        val parameters = Parameters.build {
            append("grant_type", GrantType.AuthorizationCode.value)
            append("code", code)
            append("redirect_uri", clientConfig.primaryRedirectUri)
            append("client_id", clientConfig.clientId)
            codeVerifier?.let {
                append("code_verifier", it)
                log.trace { "Including PKCE code verifier in token request" }
            }
        }

        return executeTokenRequest(
            tokenEndpoint,
            parameters,
            additionalHeaders,
            attestationHeaders,
            dpopProofFactory,
            clientAssertionFactory,
            onResponseHeaders,
            attestationHeadersFactory,
        )
    }

    /**
     * Exchanges a pre-authorized code for an access token
     *
     * @param tokenEndpoint The token endpoint URL from metadata
     * @param preAuthorizedCode The pre-authorized code from credential offer
     * @param txCode Optional transaction code (PIN) if required by the issuer
     * @param additionalParameters Extra form parameters for the token request
     * @param additionalHeaders Extra HTTP headers for token endpoint client authentication
     * @param attestationHeaders Attestation-based client authentication headers
     * @param anonymous Whether to omit client_id for anonymous pre-authorized code token requests
     * @return TokenResponse containing the OAuth access token response fields
     * @throws Exception if token request fails
     */
    suspend fun exchangePreAuthorizedCode(
        tokenEndpoint: String,
        preAuthorizedCode: String,
        txCode: String? = null,
        additionalParameters: Map<String, String> = emptyMap(),
        additionalHeaders: Map<String, String> = emptyMap(),
        attestationHeaders: ClientAttestationHeaders? = null,
        anonymous: Boolean = false,
    ): TokenResponse = exchangePreAuthorizedCode(
        tokenEndpoint = tokenEndpoint,
        preAuthorizedCode = preAuthorizedCode,
        txCode = txCode,
        additionalParameters = additionalParameters,
        additionalHeaders = additionalHeaders,
        attestationHeaders = attestationHeaders,
        anonymous = anonymous,
        dpopProofFactory = null,
    )

    /** Exchanges a pre-authorized code while creating fresh DPoP proofs when requested. */
    suspend fun exchangePreAuthorizedCode(
        tokenEndpoint: String,
        preAuthorizedCode: String,
        txCode: String? = null,
        additionalParameters: Map<String, String> = emptyMap(),
        additionalHeaders: Map<String, String> = emptyMap(),
        attestationHeaders: ClientAttestationHeaders? = null,
        anonymous: Boolean = false,
        dpopProofFactory: DPoPProofFactory?,
        clientAssertionFactory: ClientAssertionFactory? = null,
    ): TokenResponse = exchangePreAuthorizedCode(
        tokenEndpoint = tokenEndpoint,
        preAuthorizedCode = preAuthorizedCode,
        txCode = txCode,
        additionalParameters = additionalParameters,
        additionalHeaders = additionalHeaders,
        attestationHeaders = attestationHeaders,
        anonymous = anonymous,
        dpopProofFactory = dpopProofFactory,
        clientAssertionFactory = clientAssertionFactory,
        onResponseHeaders = {},
        attestationHeadersFactory = null,
    )

    /** Exchanges a pre-authorized code while creating fresh DPoP proofs when requested. */
    suspend fun exchangePreAuthorizedCode(
        tokenEndpoint: String,
        preAuthorizedCode: String,
        txCode: String? = null,
        additionalParameters: Map<String, String> = emptyMap(),
        additionalHeaders: Map<String, String> = emptyMap(),
        attestationHeaders: ClientAttestationHeaders? = null,
        anonymous: Boolean = false,
        dpopProofFactory: DPoPProofFactory?,
        clientAssertionFactory: ClientAssertionFactory? = null,
        onResponseHeaders: TokenResponseHeadersHandler,
        attestationHeadersFactory: ClientAttestationHeadersFactory?,
    ): TokenResponse {
        require(tokenEndpoint.isNotBlank()) { "Token endpoint cannot be blank" }
        require(preAuthorizedCode.isNotBlank()) { "Pre-authorized code cannot be blank" }
        require(!anonymous || (additionalHeaders.isEmpty() && attestationHeaders == null && attestationHeadersFactory == null && clientAssertionFactory == null)) {
            "Anonymous pre-authorized code token requests cannot include client authentication headers"
        }

        log.info { "Exchanging pre-authorized code for access token" }
        log.trace { "Token endpoint: $tokenEndpoint" }
        log.trace { "Transaction code (PIN) present: ${txCode != null}" }
        log.trace { "Additional parameters: ${additionalParameters.keys}" }
        log.trace { "Additional headers: ${additionalHeaders.keys}" }
        log.trace { "Client attestation: ${attestationHeaders != null || attestationHeadersFactory != null}" }
        log.trace { "Client assertion: ${clientAssertionFactory != null}" }
        log.trace { "Anonymous pre-authorized request: $anonymous" }

        // Deliberately not added here: a client assertion must be regenerated per request attempt
        // so each carries a fresh jti, which executeTokenRequest handles.
        val parameters = Parameters.build {
            append("grant_type", "urn:ietf:params:oauth:grant-type:pre-authorized_code")
            append("pre-authorized_code", preAuthorizedCode)
            if (!anonymous) {
                append("client_id", clientConfig.clientId)
            }
            txCode?.let {
                append("tx_code", it)
                log.trace { "Including transaction code in token request" }
            }
            additionalParameters.forEach { (k, v) -> append(k, v) }
        }

        return executeTokenRequest(
            tokenEndpoint,
            parameters,
            additionalHeaders,
            attestationHeaders,
            dpopProofFactory,
            clientAssertionFactory,
            onResponseHeaders,
            attestationHeadersFactory,
        )
    }

    /**
     * Exchanges a refresh token for a new access token.
     *
     * @param tokenEndpoint The token endpoint URL from metadata
     * @param refreshToken The refresh token issued by the authorization server
     * @param additionalParameters Extra form parameters for the token request
     * @param additionalHeaders Extra HTTP headers for token endpoint client authentication
     * @param attestationHeaders Attestation-based client authentication headers
     * @param anonymous Whether to omit client_id for anonymous refresh token requests
     * @return TokenResponse containing a new access token and optional rotated refresh token
     * @throws Exception if token request fails
     */
    suspend fun refreshAccessToken(
        tokenEndpoint: String,
        refreshToken: String,
        additionalParameters: Map<String, String> = emptyMap(),
        additionalHeaders: Map<String, String> = emptyMap(),
        attestationHeaders: ClientAttestationHeaders? = null,
        anonymous: Boolean = false,
    ): TokenResponse = refreshAccessToken(
        tokenEndpoint = tokenEndpoint,
        refreshToken = refreshToken,
        additionalParameters = additionalParameters,
        additionalHeaders = additionalHeaders,
        attestationHeaders = attestationHeaders,
        anonymous = anonymous,
        dpopProofFactory = null,
    )

    /**
     * Advanced refresh entry point for callers that own refresh-token persistence and response challenge state.
     * [onResponseHeaders] should persist challenge updates; [attestationHeadersFactory] is evaluated immediately
     * before every actual token POST.
     */
    suspend fun refreshAccessToken(
        tokenEndpoint: String,
        refreshToken: String,
        additionalParameters: Map<String, String> = emptyMap(),
        additionalHeaders: Map<String, String> = emptyMap(),
        attestationHeaders: ClientAttestationHeaders? = null,
        anonymous: Boolean = false,
        dpopProofFactory: DPoPProofFactory?,
    ): TokenResponse = refreshAccessToken(
        tokenEndpoint = tokenEndpoint,
        refreshToken = refreshToken,
        additionalParameters = additionalParameters,
        additionalHeaders = additionalHeaders,
        attestationHeaders = attestationHeaders,
        anonymous = anonymous,
        dpopProofFactory = dpopProofFactory,
        onResponseHeaders = {},
        attestationHeadersFactory = null,
    )

    /** Refreshes an access token while creating fresh DPoP proofs when requested. */
    suspend fun refreshAccessToken(
        tokenEndpoint: String,
        refreshToken: String,
        additionalParameters: Map<String, String> = emptyMap(),
        additionalHeaders: Map<String, String> = emptyMap(),
        attestationHeaders: ClientAttestationHeaders? = null,
        anonymous: Boolean = false,
        dpopProofFactory: DPoPProofFactory?,
        onResponseHeaders: TokenResponseHeadersHandler,
        attestationHeadersFactory: ClientAttestationHeadersFactory?,
    ): TokenResponse {
        require(tokenEndpoint.isNotBlank()) { "Token endpoint cannot be blank" }
        require(refreshToken.isNotBlank()) { "Refresh token cannot be blank" }
        require(!anonymous || (additionalHeaders.isEmpty() && attestationHeaders == null && attestationHeadersFactory == null)) {
            "Anonymous refresh token requests cannot include client authentication headers"
        }

        log.info { "Refreshing access token" }
        log.trace { "Token endpoint: $tokenEndpoint" }
        log.trace { "Additional parameters: ${additionalParameters.keys}" }
        log.trace { "Additional headers: ${additionalHeaders.keys}" }
        log.trace { "Client attestation: ${attestationHeaders != null || attestationHeadersFactory != null}" }
        log.trace { "Anonymous refresh request: $anonymous" }

        val parameters = Parameters.build {
            append("grant_type", GrantType.RefreshToken.value)
            append("refresh_token", refreshToken)
            if (!anonymous) {
                append("client_id", clientConfig.clientId)
            }
            additionalParameters.forEach { (k, v) -> append(k, v) }
        }

        return executeTokenRequest(
            tokenEndpoint,
            parameters,
            additionalHeaders,
            attestationHeaders,
            dpopProofFactory,
            onResponseHeaders = onResponseHeaders,
            attestationHeadersFactory = attestationHeadersFactory,
        )
    }

    /**
     * Executes a token request and parses the response
     */
    private suspend fun executeTokenRequest(
        tokenEndpoint: String,
        parameters: Parameters,
        additionalHeaders: Map<String, String> = emptyMap(),
        attestationHeaders: ClientAttestationHeaders? = null,
        dpopProofFactory: DPoPProofFactory? = null,
        clientAssertionFactory: ClientAssertionFactory? = null,
        onResponseHeaders: TokenResponseHeadersHandler = {},
        attestationHeadersFactory: ClientAttestationHeadersFactory? = null,
    ): TokenResponse {
        require(dpopProofFactory == null || additionalHeaders.keys.none { it.equals(DPOP_HEADER, ignoreCase = true) }) {
            "DPoP must be configured with either dpopProofFactory or an additional header, not both"
        }
        require(attestationHeaders == null || attestationHeadersFactory == null) {
            "Client attestation must be configured with either attestationHeaders or attestationHeadersFactory, not both"
        }
        log.debug { "Sending token request to authorization server" }
        log.trace { "Request parameters count: ${parameters.names().size}" }

        var dpopNonce: String? = null
        // One bounded retry covers a single server challenge round. Response headers advance the attestation
        // challenge before a DPoP-nonce retry, so requirements from the same response are satisfied together.
        // Sequential challenge rounds intentionally do not receive independent retry budgets.
        // Client assertions are regenerated per attempt so every request carries a unique jti (RFC 7523 §3).
        repeat(DPOP_NONCE_ATTEMPTS) { attempt ->
            val attemptParameters = clientAssertionFactory?.let { factory ->
                val assertion = factory()
                Parameters.build {
                    appendAll(parameters)
                    append("client_assertion_type", CLIENT_ASSERTION_TYPE_JWT_BEARER)
                    append("client_assertion", assertion)
                }
            } ?: parameters
            val response = sendTokenRequestFollowingRedirects(
                tokenEndpoint = tokenEndpoint,
                parameters = attemptParameters,
                additionalHeaders = additionalHeaders,
                attestationHeaders = attestationHeaders,
                attestationHeadersFactory = attestationHeadersFactory,
                dpopProofFactory = dpopProofFactory,
                dpopNonce = dpopNonce,
                onResponseHeaders = onResponseHeaders,
            )

                if (!response.status.isSuccess()) {
                    val errorResponse = response.oauthError()
                    val oauthError = errorResponse?.error
                    val suppliedNonce = response.headers[DPOP_NONCE_HEADER]
                if (
                    attempt == 0 &&
                    dpopProofFactory != null &&
                    oauthError == USE_DPOP_NONCE &&
                    !suppliedNonce.isNullOrBlank()
                ) {
                    dpopNonce = suppliedNonce
                    return@repeat
                }
                if (
                    attempt == 0 &&
                    attestationHeadersFactory != null &&
                    oauthError == USE_ATTESTATION_CHALLENGE &&
                    !response.headers[CLIENT_ATTESTATION_CHALLENGE].isNullOrBlank()
                ) {
                    return@repeat
                }
                    throw TokenRequestException(
                        statusCode = response.status.value,
                        oauthError = oauthError,
                        // A body that is not an OAuth error object means the request failed before the
                        // grant was ever evaluated, so name that instead of leaving a bare status code
                        // that reads like a rejected grant.
                        oauthErrorDescription = errorResponse?.description
                            ?: NON_OAUTH_ERROR_BODY.takeIf { errorResponse == null },
                    )
            }

            return response.decodeTokenResponse().also { tokenResponse ->
                log.info {
                    "Successfully obtained access token - " +
                            "Type: ${tokenResponse.token_type}, " +
                            "Expires in: ${tokenResponse.expires_in ?: "not specified"} seconds, " +
                            "Refresh token: ${if (tokenResponse.refresh_token != null) "provided" else "none"}"
                }
            }
        }
        error("Token request retry exhausted")
    }

    private suspend fun HttpResponse.decodeTokenResponse(): TokenResponse {
        val responseBody = try {
            bodyAsText()
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            throw TokenRequestException(
                statusCode = 0,
                cause = error,
            )
        }

        return try {
            tokenResponseJson.decodeFromString<TokenResponse>(responseBody)
        } catch (_: Exception) {
            log.error { "Failed to parse token response" }
            throw TokenRequestException(statusCode = status.value)
        }
    }

    private suspend fun sendTokenRequestFollowingRedirects(
        tokenEndpoint: String,
        parameters: Parameters,
        additionalHeaders: Map<String, String>,
        attestationHeaders: ClientAttestationHeaders?,
        attestationHeadersFactory: ClientAttestationHeadersFactory?,
        dpopProofFactory: DPoPProofFactory?,
        dpopNonce: String?,
        onResponseHeaders: TokenResponseHeadersHandler,
    ): HttpResponse {
        suspend fun send(endpoint: String): HttpResponse {
            val requestAttestationHeaders = attestationHeadersFactory?.invoke() ?: attestationHeaders
            val dpopProof = dpopProofFactory?.invoke(endpoint, dpopNonce)
            return try {
                httpClient.post(endpoint) {
                    contentType(ContentType.Application.FormUrlEncoded)
                    setBody(parameters.formUrlEncode())
                    appendTokenRequestHeaders(additionalHeaders, requestAttestationHeaders, dpopProof)
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                throw TokenRequestException(statusCode = 0, cause = e)
            }
        }

        val initialResponse = send(tokenEndpoint)
        if (initialResponse.status.value !in REDIRECT_STATUS_CODES) {
            onResponseHeaders(initialResponse.headers)
            return initialResponse
        }

        val location = initialResponse.headers[HttpHeaders.Location] ?: run {
            onResponseHeaders(initialResponse.headers)
            return initialResponse
        }
        if (!isSameOrigin(tokenEndpoint, location)) {
            onResponseHeaders(initialResponse.headers)
            throw TokenRequestException(initialResponse.status.value, oauthError = "unsafe_redirect")
        }
        onResponseHeaders(initialResponse.headers)
        return send(location).also { onResponseHeaders(it.headers) }
    }

private suspend fun HttpResponse.oauthError(): OAuthError? {
    if (headers[HttpHeaders.WWWAuthenticate]?.contains(USE_DPOP_NONCE, ignoreCase = true) == true) {
        return OAuthError(USE_DPOP_NONCE)
    }
    return try {
        val body = Json.parseToJsonElement(bodyAsText()).jsonObject
        body["error"]?.jsonPrimitive?.contentOrNull?.let { error ->
            OAuthError(error, body["error_description"]?.jsonPrimitive?.contentOrNull)
        }
    } catch (e: CancellationException) {
        throw e
    } catch (_: Exception) {
        null
    }
    }

    private fun isSameOrigin(source: String, target: String): Boolean {
        val sourceUrl = Url(source)
        val targetUrl = Url(target)
        return sourceUrl.protocol == targetUrl.protocol &&
                sourceUrl.host == targetUrl.host &&
                sourceUrl.port == targetUrl.port
    }

    private fun HttpRequestBuilder.appendTokenRequestHeaders(
        additionalHeaders: Map<String, String>,
        attestationHeaders: ClientAttestationHeaders?,
        dpopProof: String? = null,
    ) {
        additionalHeaders.forEach { (name, value) -> header(name, value) }
        attestationHeaders?.let {
            header(ClientAttestationHeaders.HEADER_ATTESTATION, it.attestationJwt)
            header(ClientAttestationHeaders.HEADER_ATTESTATION_POP, it.popJwt)
        }
        dpopProof?.let { header(DPOP_HEADER, it) }
    }

    private companion object {
        const val USE_ATTESTATION_CHALLENGE = "use_attestation_challenge"
        val REDIRECT_STATUS_CODES = setOf(307, 308)
    }
}
