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
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

private val log = KotlinLogging.logger {}
private val tokenResponseJson = Json { ignoreUnknownKeys = true }

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

        return executeTokenRequest(tokenEndpoint, parameters, additionalHeaders, attestationHeaders)
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

        return executeTokenRequest(tokenEndpoint, parameters, additionalHeaders, attestationHeaders)
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

        return executeTokenRequest(tokenEndpoint, parameters, additionalHeaders, attestationHeaders)
    }

    /**
     * Executes a token request and parses the response
     */
    private suspend fun executeTokenRequest(
        tokenEndpoint: String,
        parameters: Parameters,
        additionalHeaders: Map<String, String> = emptyMap(),
        attestationHeaders: ClientAttestationHeaders? = null,
    ): TokenResponse {
        log.debug { "Sending token request to authorization server" }
        log.trace { "Request parameters count: ${parameters.names().size}" }

        var response: HttpResponse = try {
            httpClient.post(tokenEndpoint) {
                contentType(ContentType.Application.FormUrlEncoded)
                setBody(parameters.formUrlEncode())
                appendTokenRequestHeaders(additionalHeaders, attestationHeaders)
            }
        } catch (e: Exception) {
            log.error(e) { "Network error sending token request to: $tokenEndpoint" }
            throw Exception("Failed to send token request", e)
        }

        if (response.status.value in listOf(301, 302, 303, 307, 308)) {
            val location = response.headers[HttpHeaders.Location]
            if (location != null) {
                log.debug { "Following redirect to: $location" }
                val isSameOrigin = isSameOrigin(tokenEndpoint, location)
                if (!isSameOrigin && (additionalHeaders.isNotEmpty() || attestationHeaders != null)) {
                    error(
                        "Cross-origin redirect from $tokenEndpoint to $location is not supported when token request " +
                            "headers are present"
                    )
                }
                response = httpClient.post(location) {
                    contentType(ContentType.Application.FormUrlEncoded)
                    setBody(parameters.formUrlEncode())
                    if (isSameOrigin) {
                        appendTokenRequestHeaders(additionalHeaders, attestationHeaders)
                    }
                }
            }
        }

        if (!response.status.isSuccess()) {
            val errorBody = response.bodyAsText()
            log.error {
                "Token request failed - Status: ${response.status.value} ${response.status.description}"
            }
            throw IllegalArgumentException("Token request failed. Status: ${response.status}, Body: $errorBody")
        }

        log.trace { "Received successful token response (${response.status.value}), parsing" }
        
        val responseBody = response.bodyAsText()
        return try {
            val tokenResponse = tokenResponseJson.decodeFromString<TokenResponse>(responseBody)
            log.info {
                "Successfully obtained access token - " +
                "Type: ${tokenResponse.token_type}, " +
                "Expires in: ${tokenResponse.expires_in ?: "not specified"} seconds, " +
                "Refresh token: ${if (tokenResponse.refresh_token != null) "provided" else "none"}"
            }
            log.trace { "Token scope: ${tokenResponse.scope ?: "not specified"}" }
            tokenResponse
        } catch (_: Exception) {
            log.error { "Failed to parse token response" }
            throw Exception("Failed to parse token response")
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
    ) {
        additionalHeaders.forEach { (name, value) -> header(name, value) }
        attestationHeaders?.let {
            header(ClientAttestationHeaders.HEADER_ATTESTATION, it.attestationJwt)
            header(ClientAttestationHeaders.HEADER_ATTESTATION_POP, it.popJwt)
        }
    }
}
