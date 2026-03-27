package id.waltid.openid4vci.wallet.authorization

import id.walt.openid4vci.ResponseType
import id.walt.openid4vci.metadata.oauth.AuthorizationServerMetadata
import id.waltid.openid4vci.wallet.oauth.ClientConfiguration
import id.waltid.openid4vci.wallet.oauth.PKCEManager
import id.waltid.openid4vci.wallet.oauth.StateManager
import io.github.oshai.kotlinlogging.KotlinLogging
import io.ktor.http.*
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

private val log = KotlinLogging.logger {}

/**
 * Builds OAuth 2.0 authorization requests with OpenID4VCI extensions.
 * Implements §5 of OpenID4VCI 1.0 specification (Authorization Endpoint).
 * 
 * @property clientConfig The OAuth 2.0 client configuration
 */
class AuthorizationRequestBuilder(
    private val clientConfig: ClientConfiguration,
) {

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = false
        prettyPrint = false
    }

    /**
     * Authorization details for OpenID4VCI (RFC 9396)
     */
    @Serializable
    data class AuthorizationDetails(
        val type: String = "openid_credential",
        val credential_configuration_id: String,
        val locations: List<String>? = null,
    )

    /**
     * Result of building an authorization request
     */
    @Serializable
    data class AuthorizationRequest(
        val url: String,
        val state: String,
        val pkceData: PKCEManager.PKCEData?,
    )

    /**
     * Builds an authorization request URL
     * 
     * @param authorizationEndpoint The authorization endpoint URL from metadata
     * @param credentialConfigurationId The credential configuration ID to request
     * @param issuerState Optional issuer state from credential offer (for authorization code grant)
     * @param scope Optional OAuth scope
     * @param usePKCE Whether to use PKCE (default: true)
     * @param metadata Authorization server metadata (for PKCE validation)
     * @return AuthorizationRequest containing the URL, state, and PKCE data
     */
    fun buildAuthorizationRequest(
        authorizationEndpoint: String,
        credentialConfigurationId: String,
        issuerState: String? = null,
        scope: String? = null,
        usePKCE: Boolean = true,
        metadata: AuthorizationServerMetadata? = null,
    ): AuthorizationRequest {
        require(authorizationEndpoint.isNotBlank()) { "Authorization endpoint cannot be blank" }
        require(credentialConfigurationId.isNotBlank()) { "Credential configuration ID cannot be blank" }

        log.info { "Building authorization request for credential configuration: $credentialConfigurationId" }
        log.trace { "Authorization endpoint: $authorizationEndpoint" }
        log.trace { "Issuer state: ${issuerState ?: "none"}, Scope: $scope" }

        // Generate state for CSRF protection
        val state = StateManager.generateState()
        log.trace { "Generated state for CSRF protection" }

        // Generate PKCE data if enabled
        var pkceData: PKCEManager.PKCEData? = null
        if (usePKCE) {
            // Determine PKCE method based on metadata
            val method = determinePKCEMethod(metadata)
            pkceData = PKCEManager.generatePKCEData(method)
            log.debug { "Generated PKCE challenge using method: ${pkceData.codeChallengeMethod.value}" }
        } else {
            log.debug { "PKCE disabled for this authorization request" }
        }

        // Build authorization_details
        val authzDetails = AuthorizationDetails(
            credential_configuration_id = credentialConfigurationId
        )
        val authzDetailsJson = json.encodeToString(listOf(authzDetails))
        log.trace { "Authorization details JSON: ${authzDetailsJson.take(100)}${if (authzDetailsJson.length > 100) "..." else ""}" }

        // Build URL with query parameters
        val urlBuilder = URLBuilder(authorizationEndpoint)
        urlBuilder.parameters.apply {
            append("response_type", ResponseType.CODE.value)
            append("client_id", clientConfig.clientId)
            append("redirect_uri", clientConfig.primaryRedirectUri)
            append("state", state)
            append("authorization_details", authzDetailsJson)

            // Add issuer_state if present (from authorization code grant offer)
            issuerState?.let {
                append("issuer_state", it)
                log.trace { "Added issuer_state parameter to authorization request" }
            }

            // Add scope if present
            scope?.let { append("scope", it) }

            // Add PKCE parameters if enabled
            pkceData?.let {
                append("code_challenge", it.codeChallenge)
                append("code_challenge_method", it.codeChallengeMethod.value)
            }
        }

        val authorizationUrl = urlBuilder.buildString()
        log.info { "Successfully built authorization request - Client: ${clientConfig.clientId}, PKCE: $usePKCE" }
        log.trace { "Authorization URL length: ${authorizationUrl.length} characters" }

        return AuthorizationRequest(
            url = authorizationUrl,
            state = state,
            pkceData = pkceData
        )
    }

    /**
     * Builds a Pushed Authorization Request (PAR) body
     * 
     * @param credentialConfigurationId The credential configuration ID to request
     * @param issuerState Optional issuer state from credential offer
     * @param scope Optional OAuth scope
     * @param usePKCE Whether to use PKCE
     * @param metadata Authorization server metadata
     * @return Map of parameters for PAR request and PKCE data
     */
    fun buildPushedAuthorizationRequest(
        credentialConfigurationId: String,
        issuerState: String? = null,
        scope: String? = null,
        usePKCE: Boolean = true,
        metadata: AuthorizationServerMetadata? = null,
    ): Pair<Map<String, String>, PKCEManager.PKCEData?> {
        require(credentialConfigurationId.isNotBlank()) { "Credential configuration ID cannot be blank" }

        val state = StateManager.generateState()

        var pkceData: PKCEManager.PKCEData? = null
        if (usePKCE) {
            val method = determinePKCEMethod(metadata)
            pkceData = PKCEManager.generatePKCEData(method)
        }

        val authzDetails = AuthorizationDetails(
            credential_configuration_id = credentialConfigurationId
        )
        val authzDetailsJson = json.encodeToString(listOf(authzDetails))

        val parameters = mutableMapOf<String, String>()
        parameters["response_type"] = ResponseType.CODE.value
        parameters["client_id"] = clientConfig.clientId
        parameters["redirect_uri"] = clientConfig.primaryRedirectUri
        parameters["state"] = state
        parameters["authorization_details"] = authzDetailsJson

        issuerState?.let { parameters["issuer_state"] = it }
        scope?.let { parameters["scope"] = it }

        pkceData?.let {
            parameters["code_challenge"] = it.codeChallenge
            parameters["code_challenge_method"] = it.codeChallengeMethod.value
        }

        log.debug { "Built PAR request parameters for credential: $credentialConfigurationId" }
        return Pair(parameters, pkceData)
    }

    /**
     * Determines the appropriate PKCE code challenge method based on server metadata
     */
    private fun determinePKCEMethod(metadata: AuthorizationServerMetadata?): PKCEManager.CodeChallengeMethod {
        val supportedMethods = metadata?.codeChallengeMethodsSupported ?: emptyList()

        return when {
            supportedMethods.isEmpty() -> {
                log.debug { "No PKCE methods in metadata, using S256" }
                PKCEManager.CodeChallengeMethod.S256
            }

            "S256" in supportedMethods || "s256" in supportedMethods -> {
                log.debug { "Using PKCE method: S256" }
                PKCEManager.CodeChallengeMethod.S256
            }

            "plain" in supportedMethods -> {
                log.warn { "Only plain PKCE method supported, using plain (not recommended)" }
                PKCEManager.CodeChallengeMethod.PLAIN
            }

            else -> {
                log.warn { "Unknown PKCE methods in metadata: $supportedMethods, defaulting to S256" }
                PKCEManager.CodeChallengeMethod.S256
            }
        }
    }
}
