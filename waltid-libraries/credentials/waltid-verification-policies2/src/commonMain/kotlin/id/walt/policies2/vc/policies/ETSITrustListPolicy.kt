package id.walt.policies2.vc.policies

import id.walt.credentials.formats.DigitalCredential
import id.walt.credentials.formats.MdocsCredential
import id.walt.credentials.signatures.CoseCredentialSignature
import id.walt.credentials.signatures.JwtBasedSignature
import id.walt.crypto.utils.Base64Utils.decodeFromBase64
import id.walt.crypto.utils.Base64Utils.encodeToBase64
import io.github.oshai.kotlinlogging.KotlinLogging
import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.request.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.*

private val log = KotlinLogging.logger { }

/**
 * A verification policy that validates credential issuer certificates against an
 * ETSI Trust List (TSL/LoTE).
 *
 * This policy extracts the signing certificate from the credential's x5c chain,
 * computes its SHA-256 fingerprint, and resolves trust status.
 *
 * ## Trust Resolution Modes
 *
 * The policy supports multiple modes of operation (in order of precedence):
 *
 * 1. **Remote Service Mode** (`trustRegistryUrl`): Query a hosted trust-registry service
 * 2. **Inline Trust Lists Mode** (`trustLists`): Load and resolve within the request
 * 3. **Enterprise Mode**: When neither is provided and running in enterprise stack,
 *    uses the linked TrustRegistryEnterpriseService (set via `trustRegistryServiceResolver`)
 *
 * ## Configuration
 *
 * @property trustRegistryUrl Optional base URL of the waltid-trust-registry-service
 *   (e.g., `http://localhost:8080` or `https://trust.example.com`).
 *   The policy will call `POST {trustRegistryUrl}/trust-registry/resolve/certificate`.
 *
 * @property trustLists Optional list of trust list sources to load inline.
 *   Each entry can be either:
 *   - A URL (starting with `http://` or `https://`) to fetch the trust list
 *   - Raw content (XML or JSON) of a trust list
 *   Auto-detects format: TSL XML, LoTE JSON, LoTE XML.
 *
 * @property expectedEntityType Optional filter to require a specific entity type
 *   (e.g., `PID_PROVIDER`, `WALLET_PROVIDER`, `ATTESTATION_PROVIDER`).
 *
 * @property expectedServiceType Optional filter to require a specific service type.
 *
 * @property allowStaleSource If `true`, credentials from stale trust sources
 *   will still be considered trusted with a warning. Default: `false`.
 *
 * @property requireAuthenticated If `true`, the trust source must have
 *   `authenticityState = VALIDATED`. Default: `false` (SKIPPED_DEMO also accepted).
 *
 * @property validateSignatures If `true`, XMLDSig signatures are validated when
 *   loading trust lists in inline mode. Default: `true`.
 *
 * ## Usage Examples
 *
 * ### Remote Service (OSS or Enterprise)
 * ```json
 * {
 *   "policy": "etsi-trust-list",
 *   "trustRegistryUrl": "http://localhost:7000",
 *   "expectedEntityType": "PID_PROVIDER"
 * }
 * ```
 *
 * ### Inline Trust Lists (OSS - no service needed)
 * ```json
 * {
 *   "policy": "etsi-trust-list",
 *   "trustLists": [
 *     "https://www.signatur.rtr.at/currenttl.xml",
 *     "https://ewc-consortium.github.io/ewc-trust-list/EWC-TL"
 *   ],
 *   "expectedEntityType": "WALLET_PROVIDER"
 * }
 * ```
 *
 * ### Enterprise (linked service - no params needed)
 * ```json
 * {
 *   "policy": "etsi-trust-list",
 *   "expectedEntityType": "PID_PROVIDER"
 * }
 * ```
 *
 * @see <a href="https://www.etsi.org/deliver/etsi_ts/119600_119699/119612/">ETSI TS 119 612</a>
 * @see <a href="https://www.etsi.org/deliver/etsi_ts/119600_119699/119602/">ETSI TS 119 602</a>
 */
@Serializable
@SerialName("etsi-trust-list")
data class ETSITrustListPolicy(
    val trustRegistryUrl: String? = null,
    val trustLists: List<String>? = null,
    val expectedEntityType: String? = null,
    val expectedServiceType: String? = null,
    val allowStaleSource: Boolean = false,
    val requireAuthenticated: Boolean = false,
    val validateSignatures: Boolean = true
) : CredentialVerificationPolicy2() {

    override val id = "etsi-trust-list"

    init {
        // Validate trustRegistryUrl if provided
        if (trustRegistryUrl != null) {
            require(trustRegistryUrl.isNotBlank()) {
                "ETSITrustListPolicy: 'trustRegistryUrl' must not be blank if provided"
            }
            require(trustRegistryUrl.startsWith("http://") || trustRegistryUrl.startsWith("https://")) {
                "ETSITrustListPolicy: 'trustRegistryUrl' must be an http or https URL, got: $trustRegistryUrl"
            }
        }
    }

    override suspend fun verify(credential: DigitalCredential): Result<JsonElement> {
        log.debug { "Verifying credential with ETSI Trust List policy" }
        
        try {
            // Extract the full certificate chain from credential
            val certificateChain = extractCertificateChain(credential)
            log.debug { "Certificate chain has ${certificateChain.size} certificate(s)" }
            
            // Determine resolution mode (in order of precedence)
            return when {
                // 1. Remote service mode (explicit URL takes precedence)
                trustRegistryUrl != null -> {
                    log.debug { "Using remote trust registry: $trustRegistryUrl" }
                    resolveViaRemoteService(certificateChain)
                }
                
                // 2. Inline trust lists mode
                !trustLists.isNullOrEmpty() -> {
                    log.debug { "Using inline trust lists (${trustLists.size} sources)" }
                    resolveViaInlineTrustLists(certificateChain)
                }
                
                // 3. Enterprise service mode (via resolver)
                trustRegistryServiceResolver != null -> {
                    log.debug { "Using enterprise trust registry service" }
                    resolveViaEnterpriseService(certificateChain)
                }
                
                // 4. No trust source configured
                else -> {
                    Result.failure(ETSITrustListPolicyException(
                        "No trust source configured. Provide either 'trustRegistryUrl', 'trustLists', " +
                        "or ensure enterprise TrustRegistryService is available."
                    ))
                }
            }
            
        } catch (e: ETSITrustListPolicyException) {
            throw e
        } catch (e: Exception) {
            log.error(e) { "ETSI Trust List policy verification failed" }
            return Result.failure(ETSITrustListPolicyException(
                message = "Trust list verification failed: ${e.message}",
                cause = e
            ))
        }
    }

    // ---------------------------------------------------------------------------
    // Resolution Mode: Remote Service
    // ---------------------------------------------------------------------------

    private suspend fun resolveViaRemoteService(certificateChain: List<String>): Result<JsonElement> {
        // Query trust registry with each certificate in the chain, starting from leaf
        for ((index, certPem) in certificateChain.withIndex()) {
            log.debug { "Checking certificate at index $index via remote service" }
            
            val decision = queryTrustRegistry(certPem, trustRegistryUrl!!)
            
            if (decision.decision == "TRUSTED" || 
                (decision.decision == "STALE_SOURCE" && allowStaleSource)) {
                log.debug { "Found trusted certificate at chain index $index" }
                return evaluateDecision(decision)
            }
            
            log.debug { "Certificate at index $index not trusted (decision: ${decision.decision})" }
        }
        
        return Result.failure(ETSITrustListPolicyException(
            "Certificate chain not trusted: no certificate in the chain (length: ${certificateChain.size}) " +
            "was found in the ETSI trust list"
        ))
    }

    // ---------------------------------------------------------------------------
    // Resolution Mode: Inline Trust Lists (JVM only - uses waltid-trust-registry lib)
    // ---------------------------------------------------------------------------

    private suspend fun resolveViaInlineTrustLists(certificateChain: List<String>): Result<JsonElement> {
        // This uses expect/actual to delegate to JVM implementation
        return ETSITrustListInlineResolver.resolve(
            certificateChain = certificateChain,
            trustLists = trustLists!!,
            expectedEntityType = expectedEntityType,
            expectedServiceType = expectedServiceType,
            allowStaleSource = allowStaleSource,
            requireAuthenticated = requireAuthenticated,
            validateSignatures = validateSignatures
        )
    }

    // ---------------------------------------------------------------------------
    // Resolution Mode: Enterprise Service
    // ---------------------------------------------------------------------------

    private suspend fun resolveViaEnterpriseService(certificateChain: List<String>): Result<JsonElement> {
        val resolver = trustRegistryServiceResolver
            ?: return Result.failure(ETSITrustListPolicyException(
                "Enterprise trust registry service not available"
            ))
        
        for ((index, certPem) in certificateChain.withIndex()) {
            log.debug { "Checking certificate at index $index via enterprise service" }
            
            val decision = resolver.resolveCertificate(
                certificatePem = certPem,
                expectedEntityType = expectedEntityType,
                expectedServiceType = expectedServiceType
            )
            
            if (decision.decision == "TRUSTED" || 
                (decision.decision == "STALE_SOURCE" && allowStaleSource)) {
                log.debug { "Found trusted certificate at chain index $index" }
                return evaluateDecision(decision)
            }
            
            log.debug { "Certificate at index $index not trusted (decision: ${decision.decision})" }
        }
        
        return Result.failure(ETSITrustListPolicyException(
            "Certificate chain not trusted: no certificate in the chain (length: ${certificateChain.size}) " +
            "was found in the enterprise trust registry"
        ))
    }

    // ---------------------------------------------------------------------------
    // Certificate Extraction
    // ---------------------------------------------------------------------------

    private suspend fun extractCertificateChain(credential: DigitalCredential): List<String> {
        val signature = credential.signature
        
        return when {
            credential is MdocsCredential && signature is CoseCredentialSignature -> {
                extractChainFromCoseSignature(signature)
            }
            signature is JwtBasedSignature -> {
                extractChainFromJwtHeader(signature.jwtHeader)
            }
            else -> {
                throw ETSITrustListPolicyException(
                    "Unsupported credential type for ETSI Trust List verification. " +
                    "Supported: mDoc with COSE x5c, SD-JWT with x5c header, JWT VC with x5c header. " +
                    "Got: ${credential::class.simpleName} with ${signature?.let { it::class.simpleName } ?: "no signature"}"
                )
            }
        }
    }

    private fun extractChainFromCoseSignature(signature: CoseCredentialSignature): List<String> {
        val x5cList = signature.x5cList
            ?: throw ETSITrustListPolicyException("mDoc credential has no x5c certificate chain in COSE header")

        if (x5cList.x5c.isEmpty()) {
            throw ETSITrustListPolicyException("mDoc x5c chain is empty")
        }

        return x5cList.x5c.map { certEntry ->
            val derBytes = certEntry.base64Der.decodeFromBase64()
            buildPemCertificate(derBytes)
        }
    }

    private fun extractChainFromJwtHeader(jwtHeader: JsonObject?): List<String> {
        if (jwtHeader == null) {
            throw ETSITrustListPolicyException("JWT credential has no header")
        }
        
        val x5cElement = jwtHeader["x5c"]
            ?: throw ETSITrustListPolicyException(
                "JWT has no x5c certificate chain in header. " +
                "ETSI Trust List verification requires an x5c header with the issuer's certificate chain."
            )
        
        val x5cArray = x5cElement.jsonArray
        if (x5cArray.isEmpty()) {
            throw ETSITrustListPolicyException("JWT x5c header is empty")
        }
        
        log.debug { "Extracted ${x5cArray.size} certificate(s) from JWT x5c header" }
        
        return x5cArray.map { certElement ->
            val certBase64 = certElement.jsonPrimitive.content
            val derBytes = certBase64.decodeFromBase64()
            buildPemCertificate(derBytes)
        }
    }

    // ---------------------------------------------------------------------------
    // Remote Service Query
    // ---------------------------------------------------------------------------

    private suspend fun queryTrustRegistry(certificatePem: String, baseUrl: String): TrustDecisionResponse {
        val url = "${baseUrl.trimEnd('/')}/trust-registry/resolve/certificate"
        
        log.debug { "Querying trust registry at: $url" }
        
        HttpClient {
            install(ContentNegotiation) {
                json(Json { ignoreUnknownKeys = true })
            }
        }.use { client ->
            val response = client.post(url) {
                contentType(ContentType.Application.Json)
                setBody(TrustResolveRequest(
                    certificatePemOrDer = certificatePem,
                    expectedEntityType = expectedEntityType,
                    expectedServiceType = expectedServiceType
                ))
            }
            
            if (!response.status.isSuccess()) {
                throw ETSITrustListPolicyException(
                    "Trust registry returned HTTP ${response.status.value}: ${response.status.description}"
                )
            }
            
            return response.body()
        }
    }

    // ---------------------------------------------------------------------------
    // Decision Evaluation
    // ---------------------------------------------------------------------------

    private fun evaluateDecision(decision: TrustDecisionResponse): Result<JsonElement> {
        log.debug { "Trust decision: ${decision.decision}, freshness: ${decision.sourceFreshness}, authenticity: ${decision.authenticity}" }
        
        return when (decision.decision) {
            "TRUSTED" -> {
                if (decision.sourceFreshness == "STALE" && !allowStaleSource) {
                    Result.failure(ETSITrustListPolicyException(
                        "Trust source is stale (set allowStaleSource=true to allow)"
                    ))
                } else if (decision.sourceFreshness == "EXPIRED") {
                    Result.failure(ETSITrustListPolicyException(
                        "Trust source has expired"
                    ))
                } else if (requireAuthenticated && decision.authenticity != "VALIDATED") {
                    Result.failure(ETSITrustListPolicyException(
                        "Trust source authenticity not validated (got: ${decision.authenticity})"
                    ))
                } else {
                    Result.success(buildSuccessResult(decision))
                }
            }
            
            "STALE_SOURCE" -> {
                if (allowStaleSource) {
                    Result.success(buildSuccessResult(decision, warning = "Trust source is stale"))
                } else {
                    Result.failure(ETSITrustListPolicyException(
                        "Trust source is stale or expired"
                    ))
                }
            }
            
            "NOT_TRUSTED" -> {
                val reason = decision.evidence.firstOrNull()?.value ?: "Certificate not found in trust list"
                Result.failure(ETSITrustListPolicyException(
                    "Certificate not trusted: $reason"
                ))
            }
            
            "MULTIPLE_MATCHES" -> {
                Result.failure(ETSITrustListPolicyException(
                    "Ambiguous trust: certificate matches multiple entities"
                ))
            }
            
            "UNSUPPORTED_SOURCE", "PROCESSING_ERROR", "UNKNOWN" -> {
                val reason = decision.evidence.firstOrNull()?.value ?: decision.decision
                Result.failure(ETSITrustListPolicyException(
                    "Trust registry error: $reason"
                ))
            }
            
            else -> {
                Result.failure(ETSITrustListPolicyException(
                    "Unknown trust decision: ${decision.decision}"
                ))
            }
        }
    }

    private fun buildSuccessResult(decision: TrustDecisionResponse, warning: String? = null): JsonElement {
        return buildJsonObject {
            put("trusted", JsonPrimitive(true))
            put("decision", JsonPrimitive(decision.decision))
            decision.matchedEntity?.let { entity ->
                put("matchedEntity", buildJsonObject {
                    put("entityId", JsonPrimitive(entity.entityId))
                    put("entityType", JsonPrimitive(entity.entityType))
                    put("legalName", JsonPrimitive(entity.legalName))
                    entity.country?.let { put("country", JsonPrimitive(it)) }
                })
            }
            decision.matchedService?.let { service ->
                put("matchedService", buildJsonObject {
                    put("serviceId", JsonPrimitive(service.serviceId))
                    put("serviceType", JsonPrimitive(service.serviceType))
                    put("status", JsonPrimitive(service.status))
                })
            }
            decision.matchedSource?.let { source ->
                put("matchedSource", buildJsonObject {
                    put("sourceId", JsonPrimitive(source.sourceId))
                    put("sourceFamily", JsonPrimitive(source.sourceFamily))
                    put("displayName", JsonPrimitive(source.displayName))
                })
            }
            put("sourceFreshness", JsonPrimitive(decision.sourceFreshness))
            put("authenticity", JsonPrimitive(decision.authenticity))
            if (decision.warnings.isNotEmpty() || warning != null) {
                put("warnings", buildJsonArray {
                    warning?.let { add(JsonPrimitive(it)) }
                    decision.warnings.forEach { add(JsonPrimitive(it)) }
                })
            }
        }
    }

    private fun buildPemCertificate(derBytes: ByteArray): String {
        val base64 = derBytes.encodeToBase64()
        return buildString {
            appendLine("-----BEGIN CERTIFICATE-----")
            base64.chunked(64).forEach { appendLine(it) }
            appendLine("-----END CERTIFICATE-----")
        }
    }

    // ---------------------------------------------------------------------------
    // DTOs (public for TrustRegistryServiceResolver interface)
    // ---------------------------------------------------------------------------

    @Serializable
    data class TrustResolveRequest(
        val certificatePemOrDer: String? = null,
        val certificateSha256Hex: String? = null,
        val instant: String? = null,
        val expectedEntityType: String? = null,
        val expectedServiceType: String? = null
    )

    @Serializable
    data class TrustDecisionResponse(
        val decision: String,
        val sourceFreshness: String = "UNKNOWN",
        val authenticity: String = "UNKNOWN",
        val matchedSource: MatchedSourceDto? = null,
        val matchedEntity: MatchedEntityDto? = null,
        val matchedService: MatchedServiceDto? = null,
        val evidence: List<TrustEvidenceDto> = emptyList(),
        val warnings: List<String> = emptyList()
    )

    @Serializable
    data class MatchedSourceDto(
        val sourceId: String,
        val sourceFamily: String,
        val displayName: String,
        val sourceUrl: String? = null,
        val territory: String? = null
    )

    @Serializable
    data class MatchedEntityDto(
        val entityId: String,
        val sourceId: String,
        val entityType: String,
        val legalName: String,
        val tradeName: String? = null,
        val country: String? = null
    )

    @Serializable
    data class MatchedServiceDto(
        val serviceId: String,
        val sourceId: String,
        val entityId: String,
        val serviceType: String,
        val status: String
    )

    @Serializable
    data class TrustEvidenceDto(
        val type: String,
        val value: String
    )

    companion object {
        /**
         * Enterprise service resolver - set by enterprise stack to provide
         * TrustRegistryEnterpriseService resolution without explicit URL.
         */
        var trustRegistryServiceResolver: TrustRegistryServiceResolver? = null
    }
}

/**
 * Interface for enterprise service resolution.
 * Implemented by enterprise stack to wire up TrustRegistryEnterpriseService.
 */
interface TrustRegistryServiceResolver {
    suspend fun resolveCertificate(
        certificatePem: String,
        expectedEntityType: String?,
        expectedServiceType: String?
    ): ETSITrustListPolicy.TrustDecisionResponse
}

/**
 * Exception thrown when ETSI Trust List policy verification fails.
 */
class ETSITrustListPolicyException(
    message: String,
    cause: Throwable? = null
) : Exception(message, cause)

/**
 * Inline trust list resolution - uses expect/actual for platform-specific implementation.
 * JVM uses waltid-trust-registry library; other platforms throw UnsupportedOperationException.
 */
expect object ETSITrustListInlineResolver {
    suspend fun resolve(
        certificateChain: List<String>,
        trustLists: List<String>,
        expectedEntityType: String?,
        expectedServiceType: String?,
        allowStaleSource: Boolean,
        requireAuthenticated: Boolean,
        validateSignatures: Boolean
    ): Result<JsonElement>
}
