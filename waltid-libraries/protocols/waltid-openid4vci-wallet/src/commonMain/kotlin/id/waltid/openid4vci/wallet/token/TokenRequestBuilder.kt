package id.waltid.openid4vci.wallet.token

import id.walt.openid4vci.GrantType
import id.walt.openid4vci.requests.authorization.AuthorizationDetail
import id.waltid.openid4vci.wallet.attestation.ClientAttestationHeaders
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

/** Sanitized token endpoint failure that never retains the response body. */
class TokenRequestException(
    val statusCode: Int,
    val oauthError: String? = null,
    cause: Throwable? = null,
) : Exception(
    buildString {
        append("Token request failed with HTTP ")
        append(statusCode)
        oauthError?.let { append(" (").append(it).append(')') }
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
    ) {
        override fun toString(): String =
            "TokenResponse(access_token=<redacted>, token_type=$token_type, expires_in=$expires_in, " +
                "refresh_token=${refresh_token?.let { "<redacted>" }}, scope=$scope, " +
                "authorization_details=$authorization_details)"
    }

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
    ): TokenResponse {
        require(tokenEndpoint.isNotBlank()) { "Token endpoint cannot be blank" }
        require(code.isNotBlank()) { "Authorization code cannot be blank" }

        log.info { "Exchanging authorization code for access token" }
        log.trace { "Token endpoint: $tokenEndpoint" }
        log.trace { "Code verifier present: ${codeVerifier != null}" }
        log.trace { "Additional headers: ${additionalHeaders.keys}" }
        log.trace { "Client attestation: ${attestationHeaders != null}" }

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
    ): TokenResponse {
        require(tokenEndpoint.isNotBlank()) { "Token endpoint cannot be blank" }
        require(preAuthorizedCode.isNotBlank()) { "Pre-authorized code cannot be blank" }
        require(!anonymous || (additionalHeaders.isEmpty() && attestationHeaders == null)) {
            "Anonymous pre-authorized code token requests cannot include client authentication headers"
        }

        log.info { "Exchanging pre-authorized code for access token" }
        log.trace { "Token endpoint: $tokenEndpoint" }
        log.trace { "Transaction code (PIN) present: ${txCode != null}" }
        log.trace { "Additional parameters: ${additionalParameters.keys}" }
        log.trace { "Additional headers: ${additionalHeaders.keys}" }
        log.trace { "Client attestation: ${attestationHeaders != null}" }
        log.trace { "Anonymous pre-authorized request: $anonymous" }

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

    /** Refreshes an access token while creating fresh DPoP proofs when requested. */
    suspend fun refreshAccessToken(
        tokenEndpoint: String,
        refreshToken: String,
        additionalParameters: Map<String, String> = emptyMap(),
        additionalHeaders: Map<String, String> = emptyMap(),
        attestationHeaders: ClientAttestationHeaders? = null,
        anonymous: Boolean = false,
        dpopProofFactory: DPoPProofFactory?,
    ): TokenResponse {
        require(tokenEndpoint.isNotBlank()) { "Token endpoint cannot be blank" }
        require(refreshToken.isNotBlank()) { "Refresh token cannot be blank" }
        require(!anonymous || (additionalHeaders.isEmpty() && attestationHeaders == null)) {
            "Anonymous refresh token requests cannot include client authentication headers"
        }

        log.info { "Refreshing access token" }
        log.trace { "Token endpoint: $tokenEndpoint" }
        log.trace { "Additional parameters: ${additionalParameters.keys}" }
        log.trace { "Additional headers: ${additionalHeaders.keys}" }
        log.trace { "Client attestation: ${attestationHeaders != null}" }
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
    ): TokenResponse {
        require(dpopProofFactory == null || additionalHeaders.keys.none { it.equals(DPOP_HEADER, ignoreCase = true) }) {
            "DPoP must be configured with either dpopProofFactory or an additional header, not both"
        }
        log.debug { "Sending token request to authorization server" }
        log.trace { "Request parameters count: ${parameters.names().size}" }

        var dpopNonce: String? = null
        repeat(2) { attempt ->
            val response = sendTokenRequestFollowingRedirects(
                tokenEndpoint = tokenEndpoint,
                parameters = parameters,
                additionalHeaders = additionalHeaders,
                attestationHeaders = attestationHeaders,
                dpopProofFactory = dpopProofFactory,
                dpopNonce = dpopNonce,
            )

            if (!response.status.isSuccess()) {
                val oauthError = response.oauthError()
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
                throw TokenRequestException(response.status.value, oauthError)
            }

            val responseBody = response.bodyAsText()
            return try {
                tokenResponseJson.decodeFromString<TokenResponse>(responseBody).also { tokenResponse ->
                    log.info {
                        "Successfully obtained access token - " +
                            "Type: ${tokenResponse.token_type}, " +
                            "Expires in: ${tokenResponse.expires_in ?: "not specified"} seconds, " +
                            "Refresh token: ${if (tokenResponse.refresh_token != null) "provided" else "none"}"
                    }
                }
            } catch (_: Exception) {
                log.error { "Failed to parse token response" }
                throw TokenRequestException(response.status.value)
            }
        }
        error("DPoP nonce retry exhausted")
    }

    private suspend fun sendTokenRequestFollowingRedirects(
        tokenEndpoint: String,
        parameters: Parameters,
        additionalHeaders: Map<String, String>,
        attestationHeaders: ClientAttestationHeaders?,
        dpopProofFactory: DPoPProofFactory?,
        dpopNonce: String?,
    ): HttpResponse {
        suspend fun send(endpoint: String): HttpResponse {
            val dpopProof = dpopProofFactory?.invoke(endpoint, dpopNonce)
            return httpClient.post(endpoint) {
                contentType(ContentType.Application.FormUrlEncoded)
                setBody(parameters.formUrlEncode())
                appendTokenRequestHeaders(additionalHeaders, attestationHeaders, dpopProof)
            }
        }

        val initialResponse = try {
            send(tokenEndpoint)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            throw TokenRequestException(statusCode = 0, cause = e)
        }
        if (initialResponse.status.value !in REDIRECT_STATUS_CODES) return initialResponse

        val location = initialResponse.headers[HttpHeaders.Location] ?: return initialResponse
        if (!isSameOrigin(tokenEndpoint, location)) {
            throw TokenRequestException(initialResponse.status.value, oauthError = "unsafe_redirect")
        }
        return send(location)
    }

    private suspend fun HttpResponse.oauthError(): String? {
        if (headers[HttpHeaders.WWWAuthenticate]?.contains(USE_DPOP_NONCE, ignoreCase = true) == true) {
            return USE_DPOP_NONCE
        }
        return runCatching {
            Json.parseToJsonElement(bodyAsText()).jsonObject["error"]?.jsonPrimitive?.contentOrNull
        }.getOrNull()
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
        const val DPOP_HEADER = "DPoP"
        const val DPOP_NONCE_HEADER = "DPoP-Nonce"
        const val USE_DPOP_NONCE = "use_dpop_nonce"
        val REDIRECT_STATUS_CODES = setOf(307, 308)
    }
}
