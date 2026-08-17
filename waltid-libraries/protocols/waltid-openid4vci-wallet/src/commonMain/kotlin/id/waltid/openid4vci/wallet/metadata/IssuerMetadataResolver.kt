package id.waltid.openid4vci.wallet.metadata

import id.walt.openid4vci.metadata.issuer.CredentialIssuerMetadata
import id.walt.openid4vci.metadata.issuer.CredentialIssuerMetadataJwt
import id.walt.openid4vci.metadata.oauth.AuthorizationServerMetadata
import id.walt.openid4vci.metadata.oidc.OpenIDProviderMetadata
import id.walt.openid4vci.tokens.jwt.JwtPayloadClaims
import id.walt.crypto2.jose.CompactJws
import io.github.oshai.kotlinlogging.KotlinLogging
import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import kotlin.time.Clock

private val log = KotlinLogging.logger {}

/**
 * Resolves unsigned or signed Credential Issuer Metadata from its well-known endpoint.
 *
 * Implements OpenID4VCI 1.0 section 12.2 and signed metadata in section 12.2.3.
 * A signed response is accepted only after [metadataTrustResolver] independently establishes
 * both the JWS signature and the signer's authority for the credential issuer.
 *
 * @property httpClient HTTP client used to fetch metadata.
 * @property metadataTrustResolver Optional trust boundary for signed metadata. Without it,
 * only unsigned metadata is accepted.
 */
class IssuerMetadataResolver(
    private val httpClient: HttpClient,
    private val metadataTrustResolver: CredentialIssuerMetadataTrustResolver? = null,
) {

    companion object {
        const val CREDENTIAL_ISSUER_WELL_KNOWN_PATH = "/.well-known/openid-credential-issuer"
        const val OAUTH_AUTHORIZATION_SERVER_WELL_KNOWN_PATH = "/.well-known/oauth-authorization-server"
        const val OPENID_CONFIGURATION_WELL_KNOWN_PATH = "/.well-known/openid-configuration"
    }

    /**
     * Resolves credential issuer metadata and retains its signed/unsigned provenance.
     *
     * @param credentialIssuerUrl Credential issuer identifier URL.
     * @return Parsed metadata with explicit unsigned or verified signed provenance.
     */
    suspend fun resolveCredentialIssuerMetadata(
        credentialIssuerUrl: String,
    ): ResolvedCredentialIssuerMetadata {
        require(credentialIssuerUrl.isNotBlank()) { "Credential issuer URL cannot be blank" }

        log.info { "Resolving credential issuer metadata" }
        log.trace { "Credential issuer URL: $credentialIssuerUrl" }

        val urlsToTry = if (credentialIssuerUrl.contains(CREDENTIAL_ISSUER_WELL_KNOWN_PATH)) {
            listOf(credentialIssuerUrl)
        } else {
            listOf(buildMetadataUrl(credentialIssuerUrl, CREDENTIAL_ISSUER_WELL_KNOWN_PATH))
        }.distinct()

        log.debug { "Attempting to fetch metadata from ${urlsToTry.size} well-known endpoints" }
        log.trace { "Metadata URLs to try: ${urlsToTry.joinToString()}" }

        val failures = mutableListOf<ResolveFailure>()
        for ((index, metadataUrl) in urlsToTry.withIndex()) {
            log.debug { "Attempt ${index + 1}/${urlsToTry.size}: Fetching from $metadataUrl" }

            val response: HttpResponse = try {
                httpClient.get(metadataUrl) {
                    header(
                        HttpHeaders.Accept,
                        metadataTrustResolver?.let {
                            "${CredentialIssuerMetadataJwt.MEDIA_TYPE}, ${ContentType.Application.Json}"
                        } ?: ContentType.Application.Json.toString(),
                    )
                }
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                log.warn(e) { "Network error fetching credential issuer metadata from: $metadataUrl" }
                failures += ResolveFailure.Network(metadataUrl, e)
                continue
            }

            if (response.status.isSuccess()) {
                log.trace { "Received successful response (${response.status.value}), parsing metadata" }
                try {
                    val body = response.bodyAsText()
                    val metadata = when (response.contentType()?.withoutParameters()) {
                        ContentType.Application.Json -> ResolvedCredentialIssuerMetadata.Unsigned(
                            parseAndValidateMetadata(body, credentialIssuerUrl),
                        )
                        ContentType.parse(CredentialIssuerMetadataJwt.MEDIA_TYPE),
                        ContentType.parse(CredentialIssuerMetadataJwt.TYPED_MEDIA_TYPE) ->
                            parseSignedMetadata(body, credentialIssuerUrl)
                        else -> throw IllegalArgumentException(
                            "Unsupported Credential Issuer Metadata content type: ${response.contentType()}",
                        )
                    }
                    log.info {
                        "Successfully resolved credential issuer metadata - " +
                                "Issuer: ${metadata.metadata.credentialIssuer}, " +
                                "Configurations: ${metadata.metadata.credentialConfigurationsSupported.size}"
                    }
                    log.trace {
                        "Supported credential configurations: " +
                            metadata.metadata.credentialConfigurationsSupported.keys.joinToString()
                    }
                    return metadata
                } catch (e: Exception) {
                    if (e is CancellationException) throw e
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

    private suspend fun parseSignedMetadata(
        compactJwt: String,
        expectedCredentialIssuer: String,
    ): ResolvedCredentialIssuerMetadata.Signed {
        val decoded = runCatching { CompactJws.decodeUnverified(compactJwt) }
            .getOrElse { throw IllegalArgumentException("Invalid signed Credential Issuer Metadata", it) }
        val algorithm = decoded.algorithm.identifier
        require(!algorithm.equals("none", ignoreCase = true) && !algorithm.startsWith("HS", ignoreCase = true)) {
            "Signed Credential Issuer Metadata must use an asymmetric JWS algorithm"
        }
        require(decoded.protectedHeader.requiredString("typ", "typ") == CredentialIssuerMetadataJwt.TYPE) {
            "Signed Credential Issuer Metadata has an invalid typ"
        }
        val signer = requireNotNull(metadataTrustResolver) {
            "Signed Credential Issuer Metadata requires a configured trust resolver"
        }.verify(compactJwt, expectedCredentialIssuer)
        require(signer.algorithm == algorithm) { "Trusted signer algorithm does not match JWS alg" }

        val payload = runCatching {
            lenientJson.parseToJsonElement(decoded.payload.decodeToString()).jsonObject
        }.getOrElse { throw IllegalArgumentException("Signed Credential Issuer Metadata payload is not a JSON object", it) }
        val subject = payload.requiredString(JwtPayloadClaims.SUBJECT, "sub")
        payload.optionalString(JwtPayloadClaims.ISSUER, "iss")
        val issuedAt = payload.requiredLong(JwtPayloadClaims.ISSUED_AT, "iat")
        val now = Clock.System.now().epochSeconds
        require(issuedAt <= now + 60) { "Signed Credential Issuer Metadata iat is in the future" }
        payload.optionalLong(JwtPayloadClaims.EXPIRATION, "exp")?.let { expiry ->
            require(now < expiry) { "Signed Credential Issuer Metadata has expired" }
        }
        val metadata = parseAndValidateMetadata(
            JsonObject(payload.filterKeys { it !in signedMetadataReservedPayloadClaims }).toString(),
            expectedCredentialIssuer,
        )
        require(subject == metadata.credentialIssuer) {
            "Signed Credential Issuer Metadata sub must match credential_issuer"
        }
        return ResolvedCredentialIssuerMetadata.Signed(metadata, compactJwt, signer)
    }

    private fun JsonObject.requiredString(claim: String, label: String): String =
        this[claim]?.jsonPrimitive?.takeIf { it.isString }?.content
            ?: throw IllegalArgumentException("Signed Credential Issuer Metadata is missing or malformed $label")

    private fun JsonObject.requiredLong(claim: String, label: String): Long =
        this[claim].strictLongOrNull()
            ?: throw IllegalArgumentException("Signed Credential Issuer Metadata is missing or malformed $label")

    private fun JsonObject.optionalLong(claim: String, label: String): Long? = when (val value = this[claim]) {
        null -> null
        else -> value.strictLongOrNull()
            ?: throw IllegalArgumentException("Signed Credential Issuer Metadata has malformed $label")
    }

    private fun JsonObject.optionalString(claim: String, label: String): String? = when (val value = this[claim]) {
        null -> null
        else -> (value as? JsonPrimitive)?.takeIf { it.isString }?.content
            ?: throw IllegalArgumentException("Signed Credential Issuer Metadata has malformed $label")
    }

    private fun parseAndValidateMetadata(body: String, expectedCredentialIssuer: String): CredentialIssuerMetadata {
        val metadata = lenientJson.decodeFromString(CredentialIssuerMetadata.serializer(), body)
        require(metadata.credentialIssuer == expectedCredentialIssuer) {
            "Credential Issuer Metadata credential_issuer does not match requested issuer"
        }
        return metadata
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
            listOf(buildMetadataUrl(authorizationServerUrl, OAUTH_AUTHORIZATION_SERVER_WELL_KNOWN_PATH))
        }.distinct()

        val failures = mutableListOf<ResolveFailure>()
        for (metadataUrl in urlsToTry) {
            log.debug { "Fetching authorization server metadata from: $metadataUrl" }
            val response: HttpResponse = try {
                httpClient.get(metadataUrl)
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                log.warn(e) { "Network error fetching authorization server metadata from: $metadataUrl" }
                failures += ResolveFailure.Network(metadataUrl, e)
                continue
            }

            if (response.status.isSuccess()) {
                try {
                    return response.body<AuthorizationServerMetadata>()
                } catch (e: Exception) {
                    if (e is CancellationException) throw e
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
                if (e is CancellationException) throw e
                log.warn(e) { "Network error fetching OpenID provider metadata from: $metadataUrl" }
                failures += ResolveFailure.Network(metadataUrl, e)
                continue
            }

            if (response.status.isSuccess()) {
                try {
                    return response.body<OpenIDProviderMetadata>()
                } catch (e: Exception) {
                    if (e is CancellationException) throw e
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
     */
    internal fun buildMetadataUrl(baseUrl: String, wellKnownSuffix: String): String {
        val url = Url(baseUrl)
        val pathSuffix = url.encodedPath.trimEnd('/').takeIf { it.isNotEmpty() && it != "/" } ?: ""
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

private val signedMetadataReservedPayloadClaims = setOf(
    JwtPayloadClaims.ISSUER,
    JwtPayloadClaims.SUBJECT,
    JwtPayloadClaims.ISSUED_AT,
    JwtPayloadClaims.EXPIRATION,
)

private val lenientJson = Json { ignoreUnknownKeys = true }

private fun JsonElement?.strictLongOrNull(): Long? =
    (this as? JsonPrimitive)?.takeIf { !it.isString }?.longOrNull

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
    return if (cause is IllegalArgumentException) {
        IllegalArgumentException(summary, cause)
    } else {
        Exception(summary, cause)
    }
}
