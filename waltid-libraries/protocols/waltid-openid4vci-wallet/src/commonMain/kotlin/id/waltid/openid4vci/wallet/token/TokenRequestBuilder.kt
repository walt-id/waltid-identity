package id.waltid.openid4vci.wallet.token

import id.walt.openid4vci.GrantType
import id.waltid.openid4vci.wallet.oauth.ClientConfiguration
import io.github.oshai.kotlinlogging.KotlinLogging
import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import kotlinx.serialization.Serializable

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
        val expires_in: Int? = null,
        val refresh_token: String? = null,
        val scope: String? = null
    )

    /**
     * Exchanges an authorization code for an access token
     * 
     * @param tokenEndpoint The token endpoint URL from metadata
     * @param code The authorization code received from authorization endpoint
     * @param codeVerifier The PKCE code verifier (if PKCE was used)
     * @return TokenResponse containing access token and optional c_nonce
     * @throws Exception if token request fails
     */
    suspend fun exchangeAuthorizationCode(
        tokenEndpoint: String,
        code: String,
        codeVerifier: String? = null,
    ): TokenResponse {
        require(tokenEndpoint.isNotBlank()) { "Token endpoint cannot be blank" }
        require(code.isNotBlank()) { "Authorization code cannot be blank" }

        log.debug { "Exchanging authorization code for access token" }

        val parameters = Parameters.build {
            append("grant_type", GrantType.AuthorizationCode.value)
            append("code", code)
            append("redirect_uri", clientConfig.primaryRedirectUri)
            append("client_id", clientConfig.clientId)
            codeVerifier?.let { append("code_verifier", it) }
        }

        return executeTokenRequest(tokenEndpoint, parameters)
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
    ): TokenResponse {
        require(tokenEndpoint.isNotBlank()) { "Token endpoint cannot be blank" }
        require(preAuthorizedCode.isNotBlank()) { "Pre-authorized code cannot be blank" }

        log.debug { "Exchanging pre-authorized code for access token" }

        val parameters = Parameters.build {
            append("grant_type", "urn:ietf:params:oauth:grant-type:pre-authorized_code")
            append("pre-authorized_code", preAuthorizedCode)
            append("client_id", clientConfig.clientId)
            txCode?.let { append("tx_code", it) }
        }

        return executeTokenRequest(tokenEndpoint, parameters)
    }

    /**
     * Executes a token request and parses the response
     */
    private suspend fun executeTokenRequest(
        tokenEndpoint: String,
        parameters: Parameters,
    ): TokenResponse {
        val response: HttpResponse = try {
            httpClient.post(tokenEndpoint) {
                contentType(ContentType.Application.FormUrlEncoded)
                setBody(parameters.formUrlEncode())
            }
        } catch (e: Exception) {
            log.error(e) { "Failed to send token request to: $tokenEndpoint" }
            throw Exception("Failed to send token request", e)
        }

        if (!response.status.isSuccess()) {
            val errorBody = response.bodyAsText()
            log.error { "Token request failed. Status: ${response.status}, Body: $errorBody" }
            throw Exception("Token request failed. Status: ${response.status}, Body: $errorBody")
        }

        return try {
            println("the Token exc response is ${response.body<TokenResponse>()}")
            response.body<TokenResponse>()
            
        } catch (e: Exception) {
            val responseBody = response.bodyAsText()
            log.error(e) { "Failed to parse token response. Body: $responseBody" }
            throw Exception("Failed to parse token response", e)
        }
    }
}
