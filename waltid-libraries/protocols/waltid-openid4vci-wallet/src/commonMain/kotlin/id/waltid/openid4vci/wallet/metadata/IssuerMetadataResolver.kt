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

        log.info { "Resolving credential issuer metadata" }
        log.trace { "Credential issuer URL: $credentialIssuerUrl" }

        val urlsToTry = mutableListOf<String>()

        if (credentialIssuerUrl.contains("/v2/") && credentialIssuerUrl.contains("/issuer-service-api/openid4vci")) {
            val url = Url(credentialIssuerUrl)
            val path = url.encodedPath
            urlsToTry.add("${url.protocol.name}://${url.hostWithPort}$CREDENTIAL_ISSUER_WELL_KNOWN_PATH$path")
        }

        if (credentialIssuerUrl.contains(CREDENTIAL_ISSUER_WELL_KNOWN_PATH)) {
            urlsToTry.add(credentialIssuerUrl)
        } else {
            urlsToTry.add(buildMetadataUrl(credentialIssuerUrl, CREDENTIAL_ISSUER_WELL_KNOWN_PATH))
        }
        
        if (!credentialIssuerUrl.contains("/openid4vci/")) {
            if (credentialIssuerUrl.endsWith("/")) {
                urlsToTry.add("${credentialIssuerUrl}openid4vc/Draft13$CREDENTIAL_ISSUER_WELL_KNOWN_PATH")
            } else {
                urlsToTry.add("$credentialIssuerUrl/openid4vc/Draft13$CREDENTIAL_ISSUER_WELL_KNOWN_PATH")
            }
        }

        log.debug { "Attempting to fetch metadata from ${urlsToTry.size} well-known endpoints" }
        log.trace { "Metadata URLs to try: ${urlsToTry.joinToString()}" }

        for ((index, metadataUrl) in urlsToTry.distinct().withIndex()) {
            log.debug { "Attempt ${index + 1}/${urlsToTry.distinct().size}: Fetching from $metadataUrl" }
            
            val response: HttpResponse = try {
                httpClient.get(metadataUrl)
            } catch (e: Exception) {
                log.warn(e) { "Network error fetching credential issuer metadata from: $metadataUrl" }
                continue
            }

            if (response.status.isSuccess()) {
                log.trace { "Received successful response (${response.status.value}), parsing metadata" }
                return try {
                    val metadata = response.body<CredentialIssuerMetadata>()
                    log.info {
                        "Successfully resolved credential issuer metadata - " +
                        "Issuer: ${metadata.credentialIssuer}, " +
                        "Configurations: ${metadata.credentialConfigurationsSupported.size}"
                    }
                    log.trace { "Supported credential configurations: ${metadata.credentialConfigurationsSupported.keys.joinToString()}" }
                    metadata
                } catch (e: Exception) {
                    val responseBody = response.bodyAsText()
                    log.error(e) {
                        "Failed to parse credential issuer metadata from $metadataUrl - " +
                        "Body preview: ${responseBody.take(200)}${if (responseBody.length > 200) "..." else ""}"
                    }
                    continue
                }
            } else {
                val errorBody = response.bodyAsText()
                log.debug {
                    "Failed to fetch credential issuer metadata from $metadataUrl - " +
                    "Status: ${response.status.value} ${response.status.description}"
                }
                log.trace { "Error body: ${errorBody.take(200)}${if (errorBody.length > 200) "..." else ""}" }
            }
        }

        log.error {
            "Failed to resolve credential issuer metadata for issuer: $credentialIssuerUrl - " +
            "Tried ${urlsToTry.distinct().size} endpoints"
        }
        throw Exception("Failed to resolve credential issuer metadata for $credentialIssuerUrl from any of: $urlsToTry")
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

        val urlsToTry = mutableListOf<String>()

        if (authorizationServerUrl.contains("/v2/") && authorizationServerUrl.contains("/issuer-service-api/openid4vci")) {
            val url = Url(authorizationServerUrl)
            val path = url.encodedPath
            urlsToTry.add("${url.protocol.name}://${url.hostWithPort}$OAUTH_AUTHORIZATION_SERVER_WELL_KNOWN_PATH$path")
        }

        if (authorizationServerUrl.contains(OAUTH_AUTHORIZATION_SERVER_WELL_KNOWN_PATH)) {
            urlsToTry.add(authorizationServerUrl)
        } else {
            urlsToTry.add(buildMetadataUrl(authorizationServerUrl, OAUTH_AUTHORIZATION_SERVER_WELL_KNOWN_PATH))
        }

        if (!authorizationServerUrl.contains("/openid4vc/")) {
            if (authorizationServerUrl.endsWith("/")) {
                urlsToTry.add("${authorizationServerUrl}openid4vc/Draft13$OAUTH_AUTHORIZATION_SERVER_WELL_KNOWN_PATH")
            } else {
                urlsToTry.add("$authorizationServerUrl/openid4vc/Draft13$OAUTH_AUTHORIZATION_SERVER_WELL_KNOWN_PATH")
            }
        }

        for (metadataUrl in urlsToTry.distinct()) {
            log.info { "Fetching authorization server metadata from: $metadataUrl" }
            val response: HttpResponse = try {
                httpClient.get(metadataUrl)
            } catch (e: Exception) {
                log.error(e) { "Failed to fetch authorization server metadata from: $metadataUrl" }
                continue
            }

            if (response.status.isSuccess()) {
                return try {
                    response.body<AuthorizationServerMetadata>()
                } catch (e: Exception) {
                    val responseBody = response.bodyAsText()
                    log.error(e) { "Failed to parse authorization server metadata from $metadataUrl. Body: $responseBody" }
                    continue
                }
            } else {
                val errorBody = response.bodyAsText()
                log.debug { "Failed to fetch authorization server metadata from $metadataUrl. Status: ${response.status}, Body: $errorBody" }
            }
        }

        throw Exception("Failed to resolve authorization server metadata for $authorizationServerUrl from any of: $urlsToTry")
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

        val urlsToTry = mutableListOf<String>()

        if (providerUrl.contains("/v2/") && providerUrl.contains("/issuer-service-api/openid4vci")) {
            val url = Url(providerUrl)
            val path = url.encodedPath
            val newPath = path.replace("/issuer-service-api2/openid4vci", "/issuer-service-api2/openid4vc/v1")
            urlsToTry.add("${url.protocol.name}://${url.hostWithPort}$newPath$OPENID_CONFIGURATION_WELL_KNOWN_PATH")
        }

        if (providerUrl.contains(OPENID_CONFIGURATION_WELL_KNOWN_PATH)) {
            urlsToTry.add(providerUrl)
        } else {
            urlsToTry.add(buildMetadataUrl(providerUrl, OPENID_CONFIGURATION_WELL_KNOWN_PATH))
        }

        if (!providerUrl.contains("/openid4vc/")) {
            if (providerUrl.endsWith("/")) {
                urlsToTry.add("${providerUrl}openid4vc/Draft13$OPENID_CONFIGURATION_WELL_KNOWN_PATH")
            } else {
                urlsToTry.add("$providerUrl/openid4vc/Draft13$OPENID_CONFIGURATION_WELL_KNOWN_PATH")
            }
        }

        for (metadataUrl in urlsToTry.distinct()) {
            log.debug { "Fetching OpenID provider metadata from: $metadataUrl" }
            val response: HttpResponse = try {
                httpClient.get(metadataUrl)
            } catch (e: Exception) {
                log.error(e) { "Failed to fetch OpenID provider metadata from: $metadataUrl" }
                continue
            }

            if (response.status.isSuccess()) {
                return try {
                    response.body<OpenIDProviderMetadata>()
                } catch (e: Exception) {
                    val responseBody = response.bodyAsText()
                    log.error(e) { "Failed to parse OpenID provider metadata from $metadataUrl. Body: $responseBody" }
                    continue
                }
            } else {
                val errorBody = response.bodyAsText()
                log.debug { "Failed to fetch OpenID provider metadata from $metadataUrl. Status: ${response.status}, Body: $errorBody" }
            }
        }

        throw Exception("Failed to resolve OpenID provider metadata for $providerUrl from any of: $urlsToTry")
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
        log.info { "Resolving authorization server metadata with fallback logic" }
        
        // Try authorization_servers if present
        val authorizationServers = credentialIssuerMetadata.authorizationServers
        println("the authorizationServers are: $authorizationServers")
        if (!authorizationServers.isNullOrEmpty()) {
            val authServerUrl = authorizationServers.first()
            log.info { "Attempting to use authorization server from issuer metadata: ${authServerUrl}" }
            println("Attempting to use authorization server from issuer metadata: ${authServerUrl} - this is a test message, please ignore it.")
            return try {
                resolveAuthorizationServerMetadata(authServerUrl)
            } catch (e: Exception) {
                log.warn(e) { "Failed to resolve authorization server from issuer metadata, trying fallback strategies" }
                // Continue to fallback
            } as AuthorizationServerMetadata
        }

        // Fallback: use credential issuer URL as authorization server
        val credentialIssuerUrl = credentialIssuerMetadata.credentialIssuer
        log.info { "Fallback strategy 1: Using credential issuer URL as authorization server" }
        log.trace { "Credential issuer URL: $credentialIssuerUrl" }

        return try {
            resolveAuthorizationServerMetadata(credentialIssuerUrl)
        } catch (e: Exception) {
            log.warn(e) { "Failed to resolve as authorization server, trying OpenID configuration as final fallback" }

            // Final fallback: try OpenID configuration
            try {
                log.info { "Fallback strategy 2: Attempting to resolve OpenID Provider metadata" }
                val oidcMetadata = resolveOpenIDProviderMetadata(credentialIssuerUrl)
                log.trace { "Converting OpenID Provider metadata to Authorization Server metadata" }
                
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
                log.error(oidcError) {
                    "All authorization server metadata resolution strategies failed for issuer: $credentialIssuerUrl"
                }
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
