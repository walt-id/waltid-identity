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

        val urlsToTry = if (credentialIssuerUrl.contains(CREDENTIAL_ISSUER_WELL_KNOWN_PATH)) {
            listOf(credentialIssuerUrl)
        } else {
            listOf(buildMetadataUrl(credentialIssuerUrl, CREDENTIAL_ISSUER_WELL_KNOWN_PATH, preserveTrailingSlash = true))
        }.distinct()

        log.debug { "Attempting to fetch metadata from ${urlsToTry.size} well-known endpoints" }
        log.trace { "Metadata URLs to try: ${urlsToTry.joinToString()}" }

        val failures = mutableListOf<ResolveFailure>()
        for ((index, metadataUrl) in urlsToTry.withIndex()) {
            log.debug { "Attempt ${index + 1}/${urlsToTry.size}: Fetching from $metadataUrl" }

            val response: HttpResponse = try {
                httpClient.get(metadataUrl)
            } catch (e: Exception) {
                log.warn(e) { "Network error fetching credential issuer metadata from: $metadataUrl" }
                failures += ResolveFailure.Network(metadataUrl, e)
                continue
            }

            if (response.status.isSuccess()) {
                log.trace { "Received successful response (${response.status.value}), parsing metadata" }
                try {
                    val metadata = response.body<CredentialIssuerMetadata>()
                    log.info {
                        "Successfully resolved credential issuer metadata - " +
                                "Issuer: ${metadata.credentialIssuer}, " +
                                "Configurations: ${metadata.credentialConfigurationsSupported.size}"
                    }
                    log.trace { "Supported credential configurations: ${metadata.credentialConfigurationsSupported.keys.joinToString()}" }
                    return metadata
                } catch (e: Exception) {
                    val responseBody = runCatching { response.bodyAsText() }.getOrDefault("")
                    log.error(e) {
                        "Failed to parse credential issuer metadata from $metadataUrl - " +
                                "Body preview: ${bodyPreview(responseBody)}"
                    }
                    failures += ResolveFailure.Parse(metadataUrl, e, bodyPreview(responseBody))
                    continue
                }
            } else {
                val errorBody = runCatching { response.bodyAsText() }.getOrDefault("")
                log.debug {
                    "Failed to fetch credential issuer metadata from $metadataUrl - " +
                            "Status: ${response.status.value} ${response.status.description}"
                }
                log.trace { "Error body: $errorBody" }
                failures += ResolveFailure.HttpStatus(metadataUrl, response.status, bodyPreview(errorBody))
            }
        }

        log.error {
            "Failed to resolve credential issuer metadata for issuer: $credentialIssuerUrl - " +
                    "Tried ${urlsToTry.size} endpoints"
        }
        throw resolutionException(
            "credential issuer metadata",
            credentialIssuerUrl,
            urlsToTry,
            failures,
        )
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
            listOf(authorizationServerUrl)
        } else {
            // RFC 8414 §3 defines oauth-authorization-server, but many authorization servers - in
            // particular OpenID Providers reused as OpenID4VCI authorization servers - publish only
            // the OpenID Connect Discovery document. Both are read as AuthorizationServerMetadata:
            // its deserializer takes the fields it knows and keeps the rest as custom parameters, and
            // a discovery document is a superset. Previously only the first was attempted, so those
            // servers failed metadata resolution outright.
            listOf(
                buildMetadataUrl(authorizationServerUrl, OAUTH_AUTHORIZATION_SERVER_WELL_KNOWN_PATH),
                buildMetadataUrl(authorizationServerUrl, OPENID_CONFIGURATION_WELL_KNOWN_PATH),
            )
        }.distinct()

        val failures = mutableListOf<ResolveFailure>()
        for (metadataUrl in urlsToTry) {
            log.debug { "Fetching authorization server metadata from: $metadataUrl" }
            val response: HttpResponse = try {
                httpClient.get(metadataUrl)
            } catch (e: Exception) {
                log.warn(e) { "Network error fetching authorization server metadata from: $metadataUrl" }
                failures += ResolveFailure.Network(metadataUrl, e)
                continue
            }

            if (response.status.isSuccess()) {
                try {
                    return response.body<AuthorizationServerMetadata>()
                } catch (e: Exception) {
                    val responseBody = runCatching { response.bodyAsText() }.getOrDefault("")
                    log.error(e) { "Failed to parse authorization server metadata from $metadataUrl. Body: $responseBody" }
                    failures += ResolveFailure.Parse(metadataUrl, e, bodyPreview(responseBody))
                    continue
                }
            } else {
                val errorBody = runCatching { response.bodyAsText() }.getOrDefault("")
                log.debug { "Failed to fetch authorization server metadata from $metadataUrl. Status: ${response.status}" }
                log.trace { "Error body: $errorBody" }
                failures += ResolveFailure.HttpStatus(metadataUrl, response.status, bodyPreview(errorBody))
            }
        }

        throw resolutionException(
            "authorization server metadata",
            authorizationServerUrl,
            urlsToTry,
            failures,
        )
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
            listOf(providerUrl)
        } else {
            listOf(buildMetadataUrl(providerUrl, OPENID_CONFIGURATION_WELL_KNOWN_PATH))
        }.distinct()

        val failures = mutableListOf<ResolveFailure>()
        for (metadataUrl in urlsToTry) {
            log.debug { "Fetching OpenID provider metadata from: $metadataUrl" }
            val response: HttpResponse = try {
                httpClient.get(metadataUrl)
            } catch (e: Exception) {
                log.warn(e) { "Network error fetching OpenID provider metadata from: $metadataUrl" }
                failures += ResolveFailure.Network(metadataUrl, e)
                continue
            }

            if (response.status.isSuccess()) {
                try {
                    return response.body<OpenIDProviderMetadata>()
                } catch (e: Exception) {
                    val responseBody = runCatching { response.bodyAsText() }.getOrDefault("")
                    log.error(e) { "Failed to parse OpenID provider metadata from $metadataUrl. Body: $responseBody" }
                    failures += ResolveFailure.Parse(metadataUrl, e, bodyPreview(responseBody))
                    continue
                }
            } else {
                val errorBody = runCatching { response.bodyAsText() }.getOrDefault("")
                log.debug { "Failed to fetch OpenID provider metadata from $metadataUrl. Status: ${response.status}" }
                log.trace { "Error body: $errorBody" }
                failures += ResolveFailure.HttpStatus(metadataUrl, response.status, bodyPreview(errorBody))
            }
        }

        throw resolutionException(
            "OpenID provider metadata",
            providerUrl,
            urlsToTry,
            failures,
        )
    }

    /**
     * Resolves authorization server metadata for a credential issuer.
     *
     * Uses the first entry of the issuer's `authorization_servers`, falling back to the Credential
     * Issuer Identifier itself when the issuer advertises none - OpenID4VCI 1.0 §11.2.3 makes
     * `authorization_servers` optional and treats the issuer as its own authorization server when it
     * is absent.
     *
     * The "fallback" in the name refers to that issuer fallback. Each candidate URL is additionally
     * tried against both well-known endpoints; see [resolveAuthorizationServerMetadata].
     *
     * @param credentialIssuerMetadata The credential issuer metadata
     * @return AuthorizationServerMetadata
     */
    suspend fun resolveAuthorizationServerMetadataWithFallback(
        credentialIssuerMetadata: CredentialIssuerMetadata,
    ): AuthorizationServerMetadata {
        log.info { "Resolving authorization server metadata" }

        // firstOrNull, not first: defence in depth only - the metadata model refuses to deserialize
        // an empty authorization_servers array, so this cannot currently be reached.
        val authServerUrl = credentialIssuerMetadata.authorizationServers?.firstOrNull()
            ?: credentialIssuerMetadata.credentialIssuer
        log.info { "Attempting to use authorization server from issuer metadata: $authServerUrl" }

        return resolveAuthorizationServerMetadata(authServerUrl)
    }

    /**
     * Builds a well-known metadata URL by inserting [wellKnownSuffix] between the host and the
     * issuer identifier's path component.
     *
     * [preserveTrailingSlash] selects between the two incompatible rules the specifications give:
     * - OpenID4VCI 1.0 §12.2.2 (`openid-credential-issuer`) appends the path verbatim, so an issuer
     *   identifier ending in `/` yields a metadata URL ending in `/`.
     * - RFC 8414 §3.1 (`oauth-authorization-server`) and OpenID Connect Discovery require the
     *   terminating `/` to be removed first; a request that retains it is non-compliant.
     */
    internal fun buildMetadataUrl(
        baseUrl: String,
        wellKnownSuffix: String,
        preserveTrailingSlash: Boolean = false,
    ): String {
        val url = Url(baseUrl)
        // The issuer's path component is inserted after the well-known segment. Whether its
        // terminating "/" survives is spec-dependent - see [preserveTrailingSlash].
        val rawPath = if (preserveTrailingSlash) url.encodedPath else url.encodedPath.trimEnd('/')
        val pathSuffix = rawPath.takeIf { it.isNotEmpty() && it != "/" } ?: ""
        val hostAndPort = if (url.specifiedPort != DEFAULT_PORT && url.specifiedPort != url.protocol.defaultPort) {
            "${url.host}:${url.specifiedPort}"
        } else {
            url.host
        }

        return buildString {
            append(url.protocol.name)
            append("://")
            append(hostAndPort)
            append(wellKnownSuffix)
            append(pathSuffix)
        }
    }
}

/**
 * Per-URL failure captured while iterating candidate well-known endpoints. Used to build the
 * final resolution error so wallet integrators can distinguish HTTP status, network, and
 * parse failures at a glance instead of only seeing the URL list.
 */
private sealed class ResolveFailure {
    abstract val url: String
    abstract fun describe(): String
    abstract val throwable: Throwable?

    data class Network(override val url: String, val error: Throwable) : ResolveFailure() {
        override val throwable: Throwable get() = error
        override fun describe(): String {
            val name = error::class.simpleName ?: "Exception"
            val message = error.message?.takeIf { it.isNotBlank() } ?: "no message"
            return "$url → network error: $name: $message"
        }
    }

    data class HttpStatus(
        override val url: String,
        val status: HttpStatusCode,
        val bodyPreview: String,
    ) : ResolveFailure() {
        override val throwable: Throwable? = null
        override fun describe(): String {
            val bodySuffix = if (bodyPreview.isNotBlank()) " body: $bodyPreview" else ""
            return "$url → HTTP ${status.value} ${status.description};$bodySuffix"
        }
    }

    data class Parse(
        override val url: String,
        val error: Throwable,
        val bodyPreview: String,
    ) : ResolveFailure() {
        override val throwable: Throwable get() = error
        override fun describe(): String {
            val name = error::class.simpleName ?: "Exception"
            val message = error.message?.takeIf { it.isNotBlank() } ?: "no message"
            val bodySuffix = if (bodyPreview.isNotBlank()) " body: $bodyPreview" else ""
            return "$url → parse error: $name: $message;$bodySuffix"
        }
    }
}

private const val BODY_PREVIEW_LIMIT = 500

private fun bodyPreview(body: String): String =
    if (body.length <= BODY_PREVIEW_LIMIT) body else body.take(BODY_PREVIEW_LIMIT) + "…"

/**
 * Builds a final resolution [Exception] whose message names each attempted URL, the outcome for
 * each, and which chains the first underlying [Throwable] as `cause` for full-stack diagnostics
 * (parse errors are the common case for external issuers that ship spec-non-compliant metadata).
 */
private fun resolutionException(
    what: String,
    target: String,
    urlsToTry: List<String>,
    failures: List<ResolveFailure>,
): Exception {
    val summary = buildString {
        append("Failed to resolve ")
        append(what)
        append(" for ")
        append(target)
        append(". Tried ${urlsToTry.size} endpoint(s):")
        if (failures.isEmpty()) {
            append(" ")
            append(urlsToTry.joinToString())
        } else {
            failures.forEach { failure ->
                append('\n')
                append("  - ")
                append(failure.describe())
            }
        }
    }
    val cause = failures.firstNotNullOfOrNull { it.throwable }
    return Exception(summary, cause)
}