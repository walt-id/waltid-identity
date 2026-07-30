package id.waltid.openid4vci.wallet.metadata

import id.walt.openid4vci.metadata.issuer.CredentialIssuerMetadata
import id.walt.openid4vci.metadata.issuer.CredentialIssuerMetadataJwt
import id.walt.openid4vci.metadata.oauth.AuthorizationServerMetadata
import id.walt.openid4vci.metadata.oidc.OpenIDProviderMetadata
import id.walt.openid4vci.tokens.jwt.JwtHeaderParams
import id.walt.openid4vci.tokens.jwt.JwtPayloadClaims
import id.walt.crypto.utils.JwsUtils.decodeJws
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
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import kotlin.time.Clock

private val log = KotlinLogging.logger {}

/**
 * Resolves Credential Issuer Metadata from its well-known endpoint.
 *
 * Supports unsigned metadata defined by OpenID4VCI 1.0 section 11.2 and signed metadata defined by section 12.2.3.
 * Signed metadata is accepted only when [metadataTrustResolver] verifies both its JWS and signer authority.
 *
 * @property httpClient HTTP client used to fetch metadata.
 * @property metadataTrustResolver Optional trust boundary for signed metadata. Without it, only unsigned metadata is accepted.
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
     * Resolves credential issuer metadata from the issuer's well-known endpoint.
     *
     * @param credentialIssuerUrl Credential issuer identifier URL.
     * @return Metadata together with whether it was unsigned or verified signed metadata.
     * @throws Exception If metadata cannot be fetched, parsed, or trusted.
     */
    suspend fun resolveCredentialIssuerMetadata(
        credentialIssuerUrl: String,
    ): ResolvedCredentialIssuerMetadata {
        require(credentialIssuerUrl.isNotBlank()) { "Credential issuer URL cannot be blank" }

        log.info { "Resolving credential issuer metadata" }
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
                continue
            }

            if (response.status.isSuccess()) {
                log.trace { "Received successful response (${response.status.value}), parsing metadata" }
                return try {
                    val body = response.bodyAsText()
                    when (response.contentType()?.withoutParameters()) {
                        ContentType.Application.Json -> ResolvedCredentialIssuerMetadata.Unsigned(
                            parseAndValidateMetadata(body, credentialIssuerUrl),
                        )
                        ContentType.parse(CredentialIssuerMetadataJwt.MEDIA_TYPE),
                        ContentType.parse(CredentialIssuerMetadataJwt.TYPED_MEDIA_TYPE) ->
                            parseSignedMetadata(body, credentialIssuerUrl)
                        else -> throw IllegalArgumentException(
                            "Unsupported Credential Issuer Metadata content type: ${response.contentType()}",
                        )
                    }.also { resolved ->
                        val metadata = resolved.metadata
                    log.info {
                        "Successfully resolved credential issuer metadata - " +
                                "Issuer: ${metadata.credentialIssuer}, " +
                                "Configurations: ${metadata.credentialConfigurationsSupported.size}"
                    }
                    log.trace { "Supported credential configurations: ${metadata.credentialConfigurationsSupported.keys.joinToString()}" }
                    }
                } catch (e: Exception) {
                    if (e is CancellationException) throw e
                    log.error(e) {
                        "Failed to parse credential issuer metadata from $metadataUrl"
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

    private suspend fun parseSignedMetadata(
        compactJwt: String,
        expectedCredentialIssuer: String,
    ): ResolvedCredentialIssuerMetadata.Signed {
        val decoded = runCatching { compactJwt.decodeJws() }
            .getOrElse { throw IllegalArgumentException("Invalid signed Credential Issuer Metadata", it) }
        val algorithm = decoded.header.requiredString(JwtHeaderParams.ALGORITHM, "alg")
        require(!algorithm.equals("none", true) && !algorithm.startsWith("HS", true)) {
            "Signed Credential Issuer Metadata must use an asymmetric JWS algorithm"
        }
        require(decoded.header.requiredString(JwtHeaderParams.TYPE, "typ") == CredentialIssuerMetadataJwt.TYPE) {
            "Signed Credential Issuer Metadata has an invalid typ"
        }
        val signer = requireNotNull(metadataTrustResolver) {
            "Signed Credential Issuer Metadata requires a configured trust resolver"
        }.verify(compactJwt, expectedCredentialIssuer)
        require(signer.algorithm == algorithm) { "Trusted signer algorithm does not match JWS alg" }

        val payload = decoded.payload
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
        require(subject == metadata.credentialIssuer) { "Signed Credential Issuer Metadata sub must match credential_issuer" }
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
            mutableListOf(authorizationServerUrl)
        } else {
            mutableListOf(buildMetadataUrl(authorizationServerUrl, OAUTH_AUTHORIZATION_SERVER_WELL_KNOWN_PATH))
        }

        for (metadataUrl in urlsToTry.distinct()) {
            log.debug { "Fetching authorization server metadata from: $metadataUrl" }
            val response: HttpResponse = try {
                httpClient.get(metadataUrl)
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                log.warn(e) { "Network error fetching authorization server metadata from: $metadataUrl" }
                continue
            }

            if (response.status.isSuccess()) {
                return try {
                    response.body<AuthorizationServerMetadata>()
                } catch (e: Exception) {
                    if (e is CancellationException) throw e
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
                if (e is CancellationException) throw e
                log.warn(e) { "Network error fetching OpenID provider metadata from: $metadataUrl" }
                continue
            }

            if (response.status.isSuccess()) {
                return try {
                    response.body<OpenIDProviderMetadata>()
                } catch (e: Exception) {
                    if (e is CancellationException) throw e
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

private val signedMetadataReservedPayloadClaims = setOf(
    JwtPayloadClaims.ISSUER,
    JwtPayloadClaims.SUBJECT,
    JwtPayloadClaims.ISSUED_AT,
    JwtPayloadClaims.EXPIRATION,
)

private val lenientJson = Json { ignoreUnknownKeys = true }

private fun JsonElement?.strictLongOrNull(): Long? =
    (this as? JsonPrimitive)?.takeIf { !it.isString }?.longOrNull
