package id.waltid.openid4vci.wallet.token

import id.walt.openid4vci.GrantType
import id.walt.openid4vci.requests.authorization.AuthorizationDetail
import id.waltid.openid4vci.wallet.oauth.ClientConfiguration
import io.github.oshai.kotlinlogging.KotlinLogging
import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject

private val log = KotlinLogging.logger {}

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
        val c_nonce: String? = null,
        val c_nonce_expires_in: Long? = null,
        val authorization_details: List<AuthorizationDetail>? = null
    )

    /**
     * Exchanges an authorization code for an access token
     * 
     * @param tokenEndpoint The token endpoint URL from metadata
     * @param code The authorization code received from authorization endpoint
     * @param codeVerifier The PKCE code verifier (if PKCE was used)
     * @param dpopProofGenerator Optional function to generate DPoP proof (for nonce retry support)
     * @return TokenResponse containing access token and optional c_nonce
     * @throws Exception if token request fails
     */
    suspend fun exchangeAuthorizationCode(
        tokenEndpoint: String,
        code: String,
        codeVerifier: String? = null,
        dpopProofGenerator: (suspend (nonce: String?) -> String)? = null,
        clientAssertion: String? = null,
    ): TokenResponse {
        require(tokenEndpoint.isNotBlank()) { "Token endpoint cannot be blank" }
        require(code.isNotBlank()) { "Authorization code cannot be blank" }

        log.info { "Exchanging authorization code for access token" }
        log.trace { "Token endpoint: $tokenEndpoint" }
        log.trace { "Code verifier present: ${codeVerifier != null}" }
        log.trace { "DPoP proof generator present: ${dpopProofGenerator != null}" }
        log.trace { "Client assertion present: ${clientAssertion != null}" }

        val parameters = Parameters.build {
            append("grant_type", GrantType.AuthorizationCode.value)
            append("code", code)
            append("redirect_uri", clientConfig.primaryRedirectUri)
            append("client_id", clientConfig.clientId)
            codeVerifier?.let {
                append("code_verifier", it)
                log.trace { "Including PKCE code verifier in token request" }
            }
            clientAssertion?.let {
                append("client_assertion_type", "urn:ietf:params:oauth:client-assertion-type:jwt-bearer")
                append("client_assertion", it)
                log.trace { "Including client_assertion for private_key_jwt auth" }
            }
        }

        return executeTokenRequest(tokenEndpoint, parameters, dpopProofGenerator)
    }

    /**
     * Exchanges a pre-authorized code for an access token
     * 
     * @param tokenEndpoint The token endpoint URL from metadata
     * @param preAuthorizedCode The pre-authorized code from credential offer
     * @param txCode Optional transaction code (PIN) if required by the issuer
     * @return TokenResponse containing access token and optional c_nonce
     * @throws Exception if token request fails
     */
    suspend fun exchangePreAuthorizedCode(
        tokenEndpoint: String,
        preAuthorizedCode: String,
        txCode: String? = null,
        additionalParameters: Map<String, String> = emptyMap(),
    ): TokenResponse {
        require(tokenEndpoint.isNotBlank()) { "Token endpoint cannot be blank" }
        require(preAuthorizedCode.isNotBlank()) { "Pre-authorized code cannot be blank" }

        log.info { "Exchanging pre-authorized code for access token" }
        log.trace { "Token endpoint: $tokenEndpoint" }
        log.trace { "Transaction code (PIN) present: ${txCode != null}" }
        log.trace { "Additional parameters: ${additionalParameters.keys}" }

        val parameters = Parameters.build {
            append("grant_type", "urn:ietf:params:oauth:grant-type:pre-authorized_code")
            append("pre-authorized_code", preAuthorizedCode)
            append("client_id", clientConfig.clientId)
            txCode?.let {
                append("tx_code", it)
                log.trace { "Including transaction code in token request" }
            }
            additionalParameters.forEach { (k, v) -> append(k, v) }
        }

        return executeTokenRequest(tokenEndpoint, parameters)
    }

    /**
     * Executes a token request and parses the response.
     * Handles DPoP nonce retry per RFC 9449 §5.
     * 
     * @param tokenEndpoint The token endpoint URL
     * @param parameters The form parameters for the request
     * @param dpopProofGenerator Optional function to generate DPoP proof with optional nonce
     */
    private suspend fun executeTokenRequest(
        tokenEndpoint: String,
        parameters: Parameters,
        dpopProofGenerator: (suspend (nonce: String?) -> String)? = null,
    ): TokenResponse {
        log.debug { "Sending token request to authorization server" }
        log.trace { "Request parameters count: ${parameters.names().size}" }
        
        // First attempt (without nonce)
        val dpopProof = dpopProofGenerator?.invoke(null)
        if (dpopProof != null) {
            log.debug { "Including DPoP proof header (initial request without nonce)" }
        }
        
        val response: HttpResponse = try {
            httpClient.post(tokenEndpoint) {
                contentType(ContentType.Application.FormUrlEncoded)
                setBody(parameters.formUrlEncode())
                dpopProof?.let {
                    header("DPoP", it)
                }
            }
        } catch (e: Exception) {
            log.error(e) { "Network error sending token request to: $tokenEndpoint" }
            throw Exception("Failed to send token request", e)
        }
        
        // Check for DPoP nonce requirement (RFC 9449 §5)
        if (response.status == HttpStatusCode.BadRequest && dpopProofGenerator != null) {
            val errorBody = response.bodyAsText()
            if (errorBody.contains("use_dpop_nonce")) {
                val dpopNonce = response.headers["DPoP-Nonce"]
                if (dpopNonce != null) {
                    log.info { "Server requires DPoP nonce, retrying with nonce: $dpopNonce" }
                    return executeTokenRequestWithNonce(tokenEndpoint, parameters, dpopProofGenerator, dpopNonce)
                } else {
                    log.error { "Server requires DPoP nonce but didn't provide DPoP-Nonce header" }
                }
            }
        }

        if (!response.status.isSuccess()) {
            val errorBody = response.bodyAsText()
            log.error {
                "Token request failed - Status: ${response.status.value} ${response.status.description}, " +
                "Response body: ${errorBody.take(200)}${if (errorBody.length > 200) "..." else ""}"
            }
            throw Exception("Token request failed. Status: ${response.status}, Body: $errorBody")
        }

        log.trace { "Received successful token response (${response.status.value}), parsing" }
        
        return try {
            val tokenResponse = response.body<TokenResponse>()
            log.info {
                "Successfully obtained access token - " +
                "Type: ${tokenResponse.token_type}, " +
                "Expires in: ${tokenResponse.expires_in ?: "not specified"} seconds, " +
                "Refresh token: ${if (tokenResponse.refresh_token != null) "provided" else "none"}"
            }
            log.trace { "Token scope: ${tokenResponse.scope ?: "not specified"}" }
            tokenResponse
        } catch (e: Exception) {
            val responseBody = response.bodyAsText()
            log.error(e) {
                "Failed to parse token response - " +
                "Body preview: ${responseBody.take(200)}${if (responseBody.length > 200) "..." else ""}"
            }
            throw Exception("Failed to parse token response", e)
        }
    }
    
    /**
     * Retry token request with DPoP nonce (RFC 9449 §5)
     */
    private suspend fun executeTokenRequestWithNonce(
        tokenEndpoint: String,
        parameters: Parameters,
        dpopProofGenerator: suspend (nonce: String?) -> String,
        nonce: String
    ): TokenResponse {
        val dpopProof = dpopProofGenerator(nonce)
        log.debug { "Retrying with DPoP proof containing nonce" }
        
        val response: HttpResponse = try {
            httpClient.post(tokenEndpoint) {
                contentType(ContentType.Application.FormUrlEncoded)
                setBody(parameters.formUrlEncode())
                header("DPoP", dpopProof)
            }
        } catch (e: Exception) {
            log.error(e) { "Network error on DPoP nonce retry to: $tokenEndpoint" }
            throw Exception("Failed to send token request with nonce", e)
        }
        
        if (!response.status.isSuccess()) {
            val errorBody = response.bodyAsText()
            log.error {
                "Token request failed after nonce retry - Status: ${response.status.value}, Body: $errorBody"
            }
            throw Exception("Token request failed. Status: ${response.status}, Body: $errorBody")
        }
        
        return response.body<TokenResponse>()
    }
}
