package id.waltid.openid4vci.wallet.metadata

import id.walt.crypto.keys.jwk.JWKKey
import id.walt.crypto.utils.JwsUtils.decodeJws
import id.walt.openid4vci.metadata.issuer.CredentialIssuerMetadata
import id.walt.openid4vci.metadata.oauth.AuthorizationServerMetadata
import id.walt.openid4vci.metadata.oidc.OpenIDProviderMetadata
import io.github.oshai.kotlinlogging.KotlinLogging
import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonPrimitive

private val log = KotlinLogging.logger {}

/**
 * Result of resolving credential issuer metadata, including information about
 * whether the metadata was signed (JWT format) and the x5c certificate chain if present.
 */
@Serializable
data class SignedIssuerMetadataResult(
    val metadata: CredentialIssuerMetadata,
    val x5cCertificateChain: List<String>? = null,
    val isSignedMetadata: Boolean = false
)

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
        
        private val metadataJson = Json {
            ignoreUnknownKeys = true
            isLenient = true
        }
    }

    /**
     * Resolves credential issuer metadata from the issuer's well-known endpoint.
     * Supports both JSON and JWT (signed) metadata formats per OID4VCI Section 11.2.3.
     * 
     * @param credentialIssuerUrl The credential issuer identifier URL
     * @return SignedIssuerMetadataResult containing metadata and signing info
     * @throws Exception if metadata cannot be fetched or parsed
     */
    suspend fun resolveCredentialIssuerMetadataWithSigningInfo(credentialIssuerUrl: String): SignedIssuerMetadataResult {
        require(credentialIssuerUrl.isNotBlank()) { "Credential issuer URL cannot be blank" }

        log.info { "Resolving credential issuer metadata with signing info" }
        log.trace { "Credential issuer URL: $credentialIssuerUrl" }

        val urlsToTry = if (credentialIssuerUrl.contains(CREDENTIAL_ISSUER_WELL_KNOWN_PATH)) {
            mutableListOf(credentialIssuerUrl)
        } else {
            mutableListOf(buildMetadataUrl(credentialIssuerUrl, CREDENTIAL_ISSUER_WELL_KNOWN_PATH))
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
                log.trace { "Error body: $errorBody" }
            }
        }

        log.error {
            "Failed to resolve credential issuer metadata for issuer: $credentialIssuerUrl - " +
                    "Tried ${urlsToTry.distinct().size} endpoints"
        }
        throw Exception("Failed to resolve credential issuer metadata for $credentialIssuerUrl from any of: $urlsToTry")
    }

    /**
     * Parses the metadata response, handling both JSON and JWT formats.
     */
    private suspend fun parseMetadataResponse(response: HttpResponse): SignedIssuerMetadataResult {
        val contentType = response.contentType()
        val responseBody = response.bodyAsText()
        
        return when {
            contentType?.match(ContentType("application", "jwt")) == true ||
            responseBody.startsWith("ey") && responseBody.count { it == '.' } == 2 -> {
                log.info { "Detected JWT-formatted (signed) metadata" }
                parseJwtMetadata(responseBody)
            }
            else -> {
                log.info { "Detected JSON-formatted (unsigned) metadata" }
                val metadata = metadataJson.decodeFromString<CredentialIssuerMetadata>(responseBody)
                logMetadataSuccess(metadata)
                SignedIssuerMetadataResult(
                    metadata = metadata,
                    x5cCertificateChain = null,
                    isSignedMetadata = false
                )
            }
        }
    }

    /**
     * Parses JWT-formatted metadata, extracting the x5c certificate chain from the header.
     */
    private fun parseJwtMetadata(jwt: String): SignedIssuerMetadataResult {
        val jwsParts = jwt.decodeJws()
        
        val x5cCertificateChain = jwsParts.header["x5c"]?.jsonArray?.map { 
            it.jsonPrimitive.content 
        }
        
        if (x5cCertificateChain.isNullOrEmpty()) {
            log.warn { "Signed metadata JWT does not contain x5c certificate chain in header" }
        } else {
            log.debug { "Extracted x5c certificate chain with ${x5cCertificateChain.size} certificate(s)" }
        }
        
        val metadata = metadataJson.decodeFromString<CredentialIssuerMetadata>(
            jwsParts.payload.toString()
        )
        
        logMetadataSuccess(metadata)
        
        return SignedIssuerMetadataResult(
            metadata = metadata,
            x5cCertificateChain = x5cCertificateChain,
            isSignedMetadata = true
        )
    }

    /**
     * Verifies the signature of a signed metadata JWT using the x5c certificate chain.
     * Per HAIP requirements:
     * - The leaf certificate MUST NOT be self-signed
     * - The trust anchor certificate MUST NOT be included in x5c
     * 
     * @param jwt The signed metadata JWT
     * @param x5cCertificateChain The x5c certificate chain from the JWT header
     * @return Result indicating success or failure with error details
     */
    suspend fun verifySignedMetadataSignature(
        jwt: String,
        x5cCertificateChain: List<String>
    ): Result<Unit> = runCatching {
        require(x5cCertificateChain.isNotEmpty()) { 
            "x5c certificate chain is required for signature verification" 
        }
        
        val leafCertBase64 = x5cCertificateChain.first()
        log.debug { "Extracting public key from leaf certificate for signature verification" }
        
        val key = JWKKey.importDERorPEM(leafCertBase64).getOrElse { e ->
            throw IllegalArgumentException("Failed to import leaf certificate from x5c: ${e.message}", e)
        }
        
        log.trace { "Verifying JWT signature with extracted key" }
        key.verifyJws(jwt).getOrElse { e ->
            throw IllegalArgumentException("JWT signature verification failed: ${e.message}", e)
        }
        
        log.info { "Signed metadata JWT signature verified successfully" }
    }

    private fun logMetadataSuccess(metadata: CredentialIssuerMetadata) {
        log.info {
            "Successfully resolved credential issuer metadata - " +
            "Issuer: ${metadata.credentialIssuer}, " +
            "Configurations: ${metadata.credentialConfigurationsSupported.size}"
        }
        log.trace { "Supported credential configurations: ${metadata.credentialConfigurationsSupported.keys.joinToString()}" }
    }

    private fun buildMetadataUrls(credentialIssuerUrl: String): List<String> {
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

        return urlsToTry
    }

    /**
     * Resolves credential issuer metadata from the issuer's well-known endpoint
     * 
     * @param credentialIssuerUrl The credential issuer identifier URL
     * @return CredentialIssuerMetadata
     * @throws Exception if metadata cannot be fetched or parsed
     */
    suspend fun resolveCredentialIssuerMetadata(credentialIssuerUrl: String): CredentialIssuerMetadata {
        return resolveCredentialIssuerMetadataWithSigningInfo(credentialIssuerUrl).metadata
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

        val urlsToTry = if (authorizationServerUrl.contains(OAUTH_AUTHORIZATION_SERVER_WELL_KNOWN_PATH)) {
            mutableListOf(authorizationServerUrl)
        } else {
            mutableListOf(buildMetadataUrl(authorizationServerUrl, OAUTH_AUTHORIZATION_SERVER_WELL_KNOWN_PATH))
        }

        for (metadataUrl in urlsToTry.distinct()) {
            log.debug { "Fetching authorization server metadata from: $metadataUrl" }
            val response: HttpResponse = try {
                httpClient.get(metadataUrl)
            } catch (e: Exception) {
                log.warn(e) { "Network error fetching authorization server metadata from: $metadataUrl" }
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
                log.debug { "Failed to fetch authorization server metadata from $metadataUrl. Status: ${response.status}" }
                log.trace { "Error body: $errorBody" }
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

        val urlsToTry = if (providerUrl.contains(OPENID_CONFIGURATION_WELL_KNOWN_PATH)) {
            mutableListOf(providerUrl)
        } else {
            mutableListOf(buildMetadataUrl(providerUrl, OPENID_CONFIGURATION_WELL_KNOWN_PATH))
        }

        for (metadataUrl in urlsToTry.distinct()) {
            log.debug { "Fetching OpenID provider metadata from: $metadataUrl" }
            val response: HttpResponse = try {
                httpClient.get(metadataUrl)
            } catch (e: Exception) {
                log.warn(e) { "Network error fetching OpenID provider metadata from: $metadataUrl" }
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
                log.debug { "Failed to fetch OpenID provider metadata from $metadataUrl. Status: ${response.status}" }
                log.trace { "Error body: $errorBody" }
            }
        }

        throw Exception("Failed to resolve OpenID provider metadata for $providerUrl from any of: $urlsToTry")
    }

    /**
     * Resolves authorization server metadata :
     * @param credentialIssuerMetadata The credential issuer metadata
     * @return AuthorizationServerMetadata
     */
    suspend fun resolveAuthorizationServerMetadataWithFallback(
        credentialIssuerMetadata: CredentialIssuerMetadata,
    ): AuthorizationServerMetadata {
        log.info { "Resolving authorization server metadata" }

        val authorizationServers = credentialIssuerMetadata.authorizationServers
        val authServerUrl = authorizationServers?.first() ?: credentialIssuerMetadata.credentialIssuer
        log.info { "Attempting to use authorization server from issuer metadata: $authServerUrl" }

        return resolveAuthorizationServerMetadata(authServerUrl)

    }

    /**
     * Builds a full metadata URL from a base URL and well-known path
     */
    private fun buildMetadataUrl(baseUrl: String, wellKnownSuffix: String): String {
        val url = Url(baseUrl)
        val pathSuffix = url.encodedPath.trimEnd('/').takeIf { it.isNotEmpty() && it != "/" } ?: ""

        return buildString {
            append(url.protocol.name)
            append("://")
            append(url.hostWithPort)
            append(wellKnownSuffix)
            append(pathSuffix)
        }
    }
}