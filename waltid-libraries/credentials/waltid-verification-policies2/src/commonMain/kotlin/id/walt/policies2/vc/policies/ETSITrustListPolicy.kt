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
 * ETSI Trust List (TSL/LoTE) via the waltid-trust-registry-service.
 *
 * This policy extracts the signing certificate from the credential's x5c chain,
 * computes its SHA-256 fingerprint, and queries the trust-registry service to
 * determine if the certificate belongs to a trusted entity.
 *
 * ## Configuration
 *
 * @property trustRegistryUrl The base URL of the waltid-trust-registry-service
 *   (e.g., `http://localhost:8080` or `https://trust.example.com`).
 *   The policy will call `POST {trustRegistryUrl}/trust-registry/resolve/certificate`.
 *
 * @property expectedEntityType Optional filter to require a specific entity type
 *   (e.g., `PID_PROVIDER`, `WALLET_PROVIDER`, `ATTESTATION_PROVIDER`).
 *   If set, the policy will fail if the matched entity is of a different type.
 *
 * @property expectedServiceType Optional filter to require a specific service type.
 *   If set, the policy will fail if the matched service is of a different type.
 *
 * @property allowStaleSource If `true`, credentials from stale (but not expired)
 *   trust sources will still be considered trusted with a warning.
 *   If `false` (default), stale sources cause policy failure.
 *
 * @property requireAuthenticated If `true`, the trust source must have
 *   `authenticityState = VALIDATED`. If `false` (default for MVP/demo),
 *   `SKIPPED_DEMO` is also accepted.
 *
 * ## Usage Example
 *
 * ```json
 * {
 *   "policy": "etsi-trust-list",
 *   "trustRegistryUrl": "http://localhost:7000",
 *   "expectedEntityType": "PID_PROVIDER"
 * }
 * ```
 *
 * ## Supported Credential Types
 *
 * - **mDoc credentials** with COSE signatures containing an x5c chain
 * - **SD-JWT credentials** with x5c header in the JWT
 * - **W3C JWT VCs** with x5c header in the JWT
 *
 * Note: Credentials using DID-based or .well-known issuer resolution without x5c
 * are not currently supported for trust list verification.
 *
 * @see <a href="https://www.etsi.org/deliver/etsi_ts/119600_119699/119612/">ETSI TS 119 612</a>
 * @see <a href="https://www.etsi.org/deliver/etsi_ts/119600_119699/119602/">ETSI TS 119 602</a>
 */
@Serializable
@SerialName("etsi-trust-list")
data class ETSITrustListPolicy(
    val trustRegistryUrl: String,
    val expectedEntityType: String? = null,
    val expectedServiceType: String? = null,
    val allowStaleSource: Boolean = false,
    val requireAuthenticated: Boolean = false
) : CredentialVerificationPolicy2() {

    override val id = "etsi-trust-list"

    init {
        require(trustRegistryUrl.isNotBlank()) {
            "ETSITrustListPolicy: 'trustRegistryUrl' must be provided"
        }
        require(trustRegistryUrl.startsWith("http://") || trustRegistryUrl.startsWith("https://")) {
            "ETSITrustListPolicy: 'trustRegistryUrl' must be an http or https URL, got: $trustRegistryUrl"
        }
    }

    override suspend fun verify(credential: DigitalCredential): Result<JsonElement> {
        log.debug { "Verifying credential with ETSI Trust List policy" }
        
        try {
            // Extract the full certificate chain from credential
            val certificateChain = extractCertificateChain(credential)
            
            log.debug { "Certificate chain has ${certificateChain.size} certificate(s)" }
            
            // Query trust registry with each certificate in the chain, starting from leaf
            // This handles the mDoc model where:
            // - Trust list contains IACA (root CA / trust anchor)
            // - Credential is signed by Document Signer (leaf, issued by IACA)
            for ((index, certPem) in certificateChain.withIndex()) {
                log.debug { "Checking certificate at index $index in chain" }
                
                val decision = queryTrustRegistry(certPem)
                
                // If we find a trusted certificate in the chain, the credential is trusted
                if (decision.decision == "TRUSTED" || 
                    (decision.decision == "STALE_SOURCE" && allowStaleSource)) {
                    log.debug { "Found trusted certificate at chain index $index" }
                    return evaluateDecision(decision)
                }
                
                log.debug { "Certificate at index $index not in trust list (decision: ${decision.decision})" }
            }
            
            // None of the certificates in the chain were found in the trust list
            return Result.failure(ETSITrustListPolicyException(
                "Certificate chain not trusted: no certificate in the chain (length: ${certificateChain.size}) " +
                "was found in the ETSI trust list"
            ))
            
        } catch (e: Exception) {
            log.error(e) { "ETSI Trust List policy verification failed" }
            return Result.failure(ETSITrustListPolicyException(
                message = "Trust list verification failed: ${e.message}",
                cause = e
            ))
        }
    }

    /**
     * Extracts the full certificate chain from the credential.
     * Returns a list of certificates in PEM format, starting with the leaf (signing cert)
     * and ending with the root (if present).
     *
     * Supports:
     * - mDoc credentials with COSE signatures (x5c in COSE header)
     * - SD-JWT credentials with x5c in JWT header
     * - W3C JWT VCs with x5c in JWT header
     */
    private suspend fun extractCertificateChain(credential: DigitalCredential): List<String> {
        val signature = credential.signature
        
        return when {
            // mDoc with COSE signature
            credential is MdocsCredential && signature is CoseCredentialSignature -> {
                extractChainFromCoseSignature(signature)
            }
            
            // SD-JWT or JWT-based credentials with x5c header
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

    /**
     * Extracts the full certificate chain from a COSE signature's x5c (mDoc).
     * Returns certificates in order: leaf first, then intermediates, then root.
     */
    private fun extractChainFromCoseSignature(signature: CoseCredentialSignature): List<String> {
        val x5cList = signature.x5cList
            ?: throw ETSITrustListPolicyException("mDoc credential has no x5c certificate chain in COSE header")

        if (x5cList.x5c.isEmpty()) {
            throw ETSITrustListPolicyException("mDoc x5c chain is empty")
        }

        // Convert all certificates to PEM format
        // x5c order is: [leaf, intermediate..., root] per RFC 7515 / COSE conventions
        return x5cList.x5c.map { certEntry ->
            val derBytes = certEntry.base64Der.decodeFromBase64()
            buildPemCertificate(derBytes)
        }
    }

    /**
     * Extracts the full certificate chain from a JWT header's x5c array.
     * Per RFC 7515 Section 4.1.6, the certificate containing the public key
     * used to sign the JWS MUST be the first certificate, followed by the
     * certificate chain leading to a trust anchor.
     */
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
        
        // Convert all certificates to PEM format
        return x5cArray.map { certElement ->
            val certBase64 = certElement.jsonPrimitive.content
            val derBytes = certBase64.decodeFromBase64()
            buildPemCertificate(derBytes)
        }
    }

    /**
     * Queries the trust registry service to resolve the certificate.
     */
    private suspend fun queryTrustRegistry(certificatePem: String): TrustDecisionResponse {
        val url = "${trustRegistryUrl.trimEnd('/')}/trust-registry/resolve/certificate"
        
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

    /**
     * Evaluates the trust decision and returns the policy result.
     */
    private fun evaluateDecision(decision: TrustDecisionResponse): Result<JsonElement> {
        log.debug { "Trust decision: ${decision.decision}, freshness: ${decision.sourceFreshness}, authenticity: ${decision.authenticity}" }
        
        // Check decision code
        return when (decision.decision) {
            "TRUSTED" -> {
                // Check freshness
                if (decision.sourceFreshness == "STALE" && !allowStaleSource) {
                    Result.failure(ETSITrustListPolicyException(
                        "Trust source is stale (set allowStaleSource=true to allow)"
                    ))
                } else if (decision.sourceFreshness == "EXPIRED") {
                    Result.failure(ETSITrustListPolicyException(
                        "Trust source has expired"
                    ))
                }
                // Check authenticity
                else if (requireAuthenticated && decision.authenticity != "VALIDATED") {
                    Result.failure(ETSITrustListPolicyException(
                        "Trust source authenticity not validated (got: ${decision.authenticity})"
                    ))
                } else {
                    // Success!
                    Result.success(buildSuccessResult(decision))
                }
            }
            
            "STALE_SOURCE" -> {
                if (allowStaleSource) {
                    // Treat as trusted with warning
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
    // Request/Response DTOs for trust-registry API
    // ---------------------------------------------------------------------------

    // ---------------------------------------------------------------------------
    // Request/Response DTOs for trust-registry API
    // These mirror the models from waltid-trust-registry but are kept local
    // to avoid adding a JVM-only dependency to this multiplatform module.
    // ---------------------------------------------------------------------------

    @Serializable
    private data class TrustResolveRequest(
        val certificatePemOrDer: String? = null,
        val certificateSha256Hex: String? = null,
        val instant: String? = null,
        val expectedEntityType: String? = null,
        val expectedServiceType: String? = null
    )

    @Serializable
    private data class TrustDecisionResponse(
        val decision: String,                          // TrustDecisionCode enum name
        val sourceFreshness: String = "UNKNOWN",       // FreshnessState enum name
        val authenticity: String = "UNKNOWN",          // AuthenticityState enum name
        val matchedSource: MatchedSourceDto? = null,
        val matchedEntity: MatchedEntityDto? = null,
        val matchedService: MatchedServiceDto? = null,
        val evidence: List<TrustEvidenceDto> = emptyList(),
        val warnings: List<String> = emptyList()
    )

    @Serializable
    private data class MatchedSourceDto(
        val sourceId: String,
        val sourceFamily: String,
        val displayName: String,
        val sourceUrl: String? = null,
        val territory: String? = null
    )

    @Serializable
    private data class MatchedEntityDto(
        val entityId: String,
        val sourceId: String,
        val entityType: String,                        // TrustedEntityType enum name
        val legalName: String,
        val tradeName: String? = null,
        val country: String? = null
    )

    @Serializable
    private data class MatchedServiceDto(
        val serviceId: String,
        val sourceId: String,
        val entityId: String,
        val serviceType: String,
        val status: String                             // TrustStatus enum name
    )

    @Serializable
    private data class TrustEvidenceDto(
        val type: String,
        val value: String
    )
}

/**
 * Exception thrown when ETSI Trust List policy verification fails.
 */
class ETSITrustListPolicyException(
    message: String,
    cause: Throwable? = null
) : Exception(message, cause)
