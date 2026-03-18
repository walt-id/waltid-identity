package id.waltid.openid4vci.wallet.metadata

import id.walt.openid4vci.metadata.issuer.CredentialIssuerMetadata
import id.walt.openid4vci.metadata.oauth.AuthorizationServerMetadata
import id.walt.openid4vci.metadata.oidc.OpenIDProviderMetadata
import io.github.oshai.kotlinlogging.KotlinLogging
import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*

private val log = KotlinLogging.logger {}

/**
 * Resolves issuer metadata from well-known endpoints.
 * Implements §11.2 of OpenID4VCI 1.0 specification (Credential Issuer Metadata).
 * 
 * @property httpClient The HTTP client to use for fetching metadata
 */
class IssuerMetadataResolver(
    private val httpClient: HttpClient,
) {

    companion object {
        const val CREDENTIAL_ISSUER_WELL_KNOWN_PATH = "/.well-known/openid-credential-issuer"
        const val OAUTH_AUTHORIZATION_SERVER_WELL_KNOWN_PATH = "/.well-known/oauth-authorization-server"
        const val OPENID_CONFIGURATION_WELL_KNOWN_PATH = "/.well-known/openid-configuration"
    }

    /**
     * Resolves credential issuer metadata from the issuer's well-known endpoint
     * 
     * @param credentialIssuerUrl The credential issuer identifier URL
     * @return CredentialIssuerMetadata
     * @throws Exception if metadata cannot be fetched or parsed
     */
    suspend fun resolveCredentialIssuerMetadata(credentialIssuerUrl: String): CredentialIssuerMetadata {
        require(credentialIssuerUrl.isNotBlank()) { "Credential issuer URL cannot be blank" }

        val metadataUrl = buildMetadataUrl(credentialIssuerUrl, CREDENTIAL_ISSUER_WELL_KNOWN_PATH)
        log.debug { "Fetching credential issuer metadata from: $metadataUrl" }

        val response: HttpResponse = try {
            httpClient.get(metadataUrl)
        } catch (e: Exception) {
            log.error(e) { "Failed to fetch credential issuer metadata from: $metadataUrl" }
            throw Exception("Failed to fetch credential issuer metadata from: $metadataUrl", e)
        }

        if (!response.status.isSuccess()) {
            val errorBody = response.bodyAsText()
            log.error { "Failed to fetch credential issuer metadata. Status: ${response.status}, Body: $errorBody" }
            throw Exception("Failed to fetch credential issuer metadata. Status: ${response.status}")
        }

        return try {
            response.body<CredentialIssuerMetadata>()
        } catch (e: Exception) {
            val responseBody = response.bodyAsText()
            log.error(e) { "Failed to parse credential issuer metadata. Body: $responseBody" }
            throw Exception("Failed to parse credential issuer metadata", e)
        }
    }

    /**
     * Resolves authorization server metadata from the well-known endpoint
     * 
     * @param authorizationServerUrl The authorization server URL (can be issuer URL or separate AS URL)
     * @return AuthorizationServerMetadata
     * @throws Exception if metadata cannot be fetched or parsed
     */
    suspend fun resolveAuthorizationServerMetadata(authorizationServerUrl: String): AuthorizationServerMetadata {
        require(authorizationServerUrl.isNotBlank()) { "Authorization server URL cannot be blank" }

        val metadataUrl = buildMetadataUrl(authorizationServerUrl, OAUTH_AUTHORIZATION_SERVER_WELL_KNOWN_PATH)
        log.debug { "Fetching authorization server metadata from: $metadataUrl" }

        val response: HttpResponse = try {
            httpClient.get(metadataUrl)
        } catch (e: Exception) {
            log.error(e) { "Failed to fetch authorization server metadata from: $metadataUrl" }
            throw Exception("Failed to fetch authorization server metadata from: $metadataUrl", e)
        }

        if (!response.status.isSuccess()) {
            val errorBody = response.bodyAsText()
            log.error { "Failed to fetch authorization server metadata. Status: ${response.status}, Body: $errorBody" }
            throw Exception("Failed to fetch authorization server metadata. Status: ${response.status}")
        }

        return try {
            response.body<AuthorizationServerMetadata>()
        } catch (e: Exception) {
            val responseBody = response.bodyAsText()
            log.error(e) { "Failed to parse authorization server metadata. Body: $responseBody" }
            throw Exception("Failed to parse authorization server metadata", e)
        }
    }

    /**
     * Resolves OpenID Provider metadata from the well-known endpoint
     * This is an alternative to the OAuth authorization server metadata
     * 
     * @param providerUrl The OpenID Provider URL
     * @return OpenIDProviderMetadata
     * @throws Exception if metadata cannot be fetched or parsed
     */
    suspend fun resolveOpenIDProviderMetadata(providerUrl: String): OpenIDProviderMetadata {
        require(providerUrl.isNotBlank()) { "Provider URL cannot be blank" }

        val metadataUrl = buildMetadataUrl(providerUrl, OPENID_CONFIGURATION_WELL_KNOWN_PATH)
        log.debug { "Fetching OpenID provider metadata from: $metadataUrl" }

        val response: HttpResponse = try {
            httpClient.get(metadataUrl)
        } catch (e: Exception) {
            log.error(e) { "Failed to fetch OpenID provider metadata from: $metadataUrl" }
            throw Exception("Failed to fetch OpenID provider metadata from: $metadataUrl", e)
        }

        if (!response.status.isSuccess()) {
            val errorBody = response.bodyAsText()
            log.error { "Failed to fetch OpenID provider metadata. Status: ${response.status}, Body: $errorBody" }
            throw Exception("Failed to fetch OpenID provider metadata. Status: ${response.status}")
        }

        return try {
            response.body<OpenIDProviderMetadata>()
        } catch (e: Exception) {
            val responseBody = response.bodyAsText()
            log.error(e) { "Failed to parse OpenID provider metadata. Body: $responseBody" }
            throw Exception("Failed to parse OpenID provider metadata", e)
        }
    }

    /**
     * Resolves authorization server metadata with fallback logic:
     * 1. Try authorization_servers from credential issuer metadata
     * 2. Fall back to using the credential issuer URL itself
     * 3. Fall back to OpenID provider metadata
     * 
     * @param credentialIssuerMetadata The credential issuer metadata
     * @return AuthorizationServerMetadata
     */
    suspend fun resolveAuthorizationServerMetadataWithFallback(
        credentialIssuerMetadata: CredentialIssuerMetadata,
    ): AuthorizationServerMetadata {
        // Try authorization_servers if present
        val authorizationServers = credentialIssuerMetadata.authorizationServers
        if (!authorizationServers.isNullOrEmpty()) {
            val authServerUrl = authorizationServers.first()
            log.debug { "Using authorization server from metadata: $authServerUrl" }
            return try {
                resolveAuthorizationServerMetadata(authServerUrl)
            } catch (e: Exception) {
                log.warn(e) { "Failed to resolve authorization server metadata from: $authServerUrl. Trying fallback..." }
                // Continue to fallback
            } as AuthorizationServerMetadata
        }

        // Fallback: use credential issuer URL as authorization server
        val credentialIssuerUrl = credentialIssuerMetadata.credentialIssuer
        log.debug { "Trying credential issuer URL as authorization server: $credentialIssuerUrl" }

        return try {
            resolveAuthorizationServerMetadata(credentialIssuerUrl)
        } catch (e: Exception) {
            log.warn(e) { "Failed to resolve authorization server metadata. Trying OpenID configuration..." }

            // Final fallback: try OpenID configuration
            try {
                val oidcMetadata = resolveOpenIDProviderMetadata(credentialIssuerUrl)
                // Convert OpenIDProviderMetadata to AuthorizationServerMetadata
                // (they share most fields through inheritance)
                AuthorizationServerMetadata(
                    issuer = oidcMetadata.issuer,
                    authorizationEndpoint = oidcMetadata.authorizationEndpoint,
                    tokenEndpoint = oidcMetadata.tokenEndpoint,
                    jwksUri = oidcMetadata.jwksUri,
                    registrationEndpoint = oidcMetadata.registrationEndpoint,
                    scopesSupported = oidcMetadata.scopesSupported,
                    responseTypesSupported = oidcMetadata.responseTypesSupported,
                    responseModesSupported = oidcMetadata.responseModesSupported,
                    grantTypesSupported = oidcMetadata.grantTypesSupported,
                    tokenEndpointAuthMethodsSupported = oidcMetadata.tokenEndpointAuthMethodsSupported,
                    tokenEndpointAuthSigningAlgValuesSupported = oidcMetadata.tokenEndpointAuthSigningAlgValuesSupported,
                    serviceDocumentation = oidcMetadata.serviceDocumentation,
                    uiLocalesSupported = oidcMetadata.uiLocalesSupported,
                    opPolicyUri = oidcMetadata.opPolicyUri,
                    opTosUri = oidcMetadata.opTosUri,
                    pushedAuthorizationRequestEndpoint = null,
                    requirePushedAuthorizationRequests = false,
                    dpopSigningAlgValuesSupported = null
                )
            } catch (oidcError: Exception) {
                log.error(oidcError) { "All metadata resolution attempts failed" }
                throw Exception("Failed to resolve authorization server metadata from any source", oidcError)
            }
        }
    }

    /**
     * Builds a full metadata URL from a base URL and well-known path
     */
    private fun buildMetadataUrl(baseUrl: String, wellKnownPath: String): String {
        val normalizedBase = baseUrl.trimEnd('/')
        return "$normalizedBase$wellKnownPath"
    }
}
