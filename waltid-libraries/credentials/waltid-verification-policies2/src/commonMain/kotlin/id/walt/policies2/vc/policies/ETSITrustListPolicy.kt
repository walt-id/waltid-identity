package id.walt.policies2.vc.policies

import id.walt.credentials.formats.DigitalCredential
import id.walt.credentials.formats.MdocsCredential
import id.walt.credentials.keyresolver.Crypto2JwtKeyResolver
import id.walt.credentials.signatures.CoseCredentialSignature
import id.walt.credentials.signatures.JwtBasedSignature
import id.walt.crypto.utils.Base64Utils.decodeFromBase64
import id.walt.crypto.utils.Base64Utils.encodeToBase64
import id.walt.crypto2.keys.Key
import id.walt.crypto2.keys.toPublicJwk
import io.github.oshai.kotlinlogging.KotlinLogging
import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.plugins.*
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
 * 3. **Injected Resolver Mode**: When neither is provided, the policy looks up a
 *    [TrustRegistryServiceResolver] in the [PolicyExecutionContext] passed to
 *    [verify] under the [PolicyServiceKey.TRUST_REGISTRY_RESOLVER] key. The enterprise
 *    verifier supplies such a resolver per request.
 *
 * ## Configuration
 *
 * @property trustRegistryUrl Optional base URL of the waltid-trust-registry-service
 *   (e.g., `http://localhost:8080` or `https://trust.example.com`).
 *   The policy will call `POST {trustRegistryUrl}/trust-registry/resolve/certificate-chain`.
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
 *   `authenticityState = AUTHENTICATED`. Default: `false`.
 *
 * @property validateSignatures If `true`, XMLDSig or supported signed-source envelopes
 *   are validated when loading trust lists in inline mode. Default: `true`.
 * @property trustedSourceSignerCertificates PEM or Base64-DER certificates trusted to
 *   sign compact-JWS LoTE sources in inline mode.
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
 *     "https://www.signatur.rtr.at/vertrauensliste.xml",
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
    val validateSignatures: Boolean = true,
    val trustedSourceSignerCertificates: List<String> = emptyList()
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

    override suspend fun verify(
        credential: DigitalCredential,
        context: PolicyExecutionContext
    ): Result<JsonElement> {
        log.debug { "Verifying credential with ETSI Trust List policy" }

        try {
            // Extract the issuer's certificate chain (x5c) or, when absent, resolve the
            // issuer's public key (DID, HTTPS well-known metadata) for JWT-based credentials.
            val trustMaterial = extractTrustMaterial(credential)
            log.debug { "Extracted issuer trust material: $trustMaterial" }

            // Look up an injected TrustRegistryServiceResolver (e.g. provided by the enterprise verifier)
            val injectedResolver =
                context.getService<TrustRegistryServiceResolver>(PolicyServiceKey.TRUST_REGISTRY_RESOLVER)

            // Determine resolution mode (in order of precedence)
            return when {
                // 1. Remote service mode (explicit URL takes precedence)
                trustRegistryUrl != null -> {
                    log.debug { "Using remote trust registry: $trustRegistryUrl" }
                    resolveViaRemoteService(trustMaterial)
                }

                // 2. Inline trust lists mode
                !trustLists.isNullOrEmpty() -> {
                    log.debug { "Using inline trust lists (${trustLists.size} sources)" }
                    resolveViaInlineTrustLists(trustMaterial)
                }

                // 3. Injected resolver mode (supplied via PolicyExecutionContext by e.g. the enterprise verifier)
                injectedResolver != null -> {
                    log.debug { "Using injected trust registry service resolver" }
                    resolveViaInjectedResolver(trustMaterial, injectedResolver)
                }

                // 4. No trust source configured
                else -> {
                    Result.failure(ETSITrustListPolicyException(
                        "No trust source configured. Provide either 'trustRegistryUrl', 'trustLists', " +
                        "or pass a TrustRegistryServiceResolver via PolicyExecutionContext."
                    ))
                }
            }

        } catch (e: ETSITrustListPolicyException) {
            // Return as-is — preserve the original message rather than re-wrapping.
            return Result.failure(e)
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

    private suspend fun resolveViaRemoteService(trustMaterial: IssuerTrustMaterial): Result<JsonElement> {
        val decision = when (trustMaterial) {
            is IssuerTrustMaterial.CertificateChain -> queryTrustRegistry(trustMaterial.pemChain, trustRegistryUrl!!)
            is IssuerTrustMaterial.PublicKey -> queryTrustRegistryByPublicKey(trustMaterial.jwk, trustRegistryUrl!!)
        }
        return evaluateDecision(decision)
    }

    // ---------------------------------------------------------------------------
    // Resolution Mode: Inline Trust Lists (JVM only - uses waltid-trust-registry lib)
    // ---------------------------------------------------------------------------

    private suspend fun resolveViaInlineTrustLists(trustMaterial: IssuerTrustMaterial): Result<JsonElement> {
        // This uses expect/actual to delegate to JVM implementation
        return ETSITrustListInlineResolver.resolve(
            certificateChain = (trustMaterial as? IssuerTrustMaterial.CertificateChain)?.pemChain ?: emptyList(),
            publicKeyJwk = (trustMaterial as? IssuerTrustMaterial.PublicKey)?.jwk,
            trustLists = trustLists!!,
            expectedEntityType = expectedEntityType,
            expectedServiceType = expectedServiceType,
            allowStaleSource = allowStaleSource,
            requireAuthenticated = requireAuthenticated,
            validateSignatures = validateSignatures,
            trustedSourceSignerCertificates = trustedSourceSignerCertificates
        )
    }

    // ---------------------------------------------------------------------------
    // Resolution Mode: Injected Resolver (supplied via PolicyExecutionContext)
    // ---------------------------------------------------------------------------

    private suspend fun resolveViaInjectedResolver(
        trustMaterial: IssuerTrustMaterial,
        resolver: TrustRegistryServiceResolver
    ): Result<JsonElement> {
        val decision = when (trustMaterial) {
            is IssuerTrustMaterial.CertificateChain -> resolver.resolveCertificateChain(
                certificateChainPem = trustMaterial.pemChain,
                expectedEntityType = expectedEntityType,
                expectedServiceType = expectedServiceType
            )
            is IssuerTrustMaterial.PublicKey -> resolver.resolveByPublicKey(
                publicKeyJwk = trustMaterial.jwk,
                expectedEntityType = expectedEntityType,
                expectedServiceType = expectedServiceType
            )
        }
        return evaluateDecision(decision)
    }

    // ---------------------------------------------------------------------------
    // Issuer Trust Material Extraction
    // ---------------------------------------------------------------------------

    private suspend fun extractTrustMaterial(credential: DigitalCredential): IssuerTrustMaterial {
        val signature = credential.signature

        return when {
            credential is MdocsCredential && signature is CoseCredentialSignature -> {
                IssuerTrustMaterial.CertificateChain(extractChainFromCoseSignature(signature))
            }
            signature is JwtBasedSignature -> {
                extractJwtTrustMaterial(signature, credential.credentialData)
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

    /**
     * Extracts trust material for a JWT-based credential (SD-JWT VC, JWT VC).
     *
     * When the JWT header carries an `x5c` certificate chain, that chain is used as before.
     * Otherwise — e.g. an issuer that signs with a `did:web` DID and identifies its signing
     * key only via `kid` + `iss` — the issuer's public key is resolved via [Crypto2JwtKeyResolver]
     * (DID resolution, or HTTPS well-known JWT VC issuer metadata) and matched against the trust
     * list by JWK thumbprint instead. An inline `jwk` header is intentionally not trusted here
     * (`allowInlineJwk = false`): it is self-asserted and establishes no issuer identity on its own.
     *
     * Signer binding: this trust check is only sound if it resolves the *same* key that the base
     * `signature` policy verified the credential against — otherwise an attacker could sign with a
     * self-asserted key while trust is matched against a different, trust-listed key. That binding
     * holds structurally rather than by convention: for both W3C JWT VCs and SD-JWT VCs, the base
     * signature policy resolves its verification key via
     * `JwsSignatureScheme.getIssuerCrypto2KeyInfo(compact)`, whose default `resolver` parameter is
     * `Crypto2JwtKeyResolver()` — the same class, with the same `allowInlineJwk = false` default, run
     * over the same JWT header/payload as [keyResolver] here. Given identical (deterministic) inputs
     * and an identical resolver configuration, both policies always resolve to the same key.
     */
    private suspend fun extractJwtTrustMaterial(
        signature: JwtBasedSignature,
        credentialData: JsonObject
    ): IssuerTrustMaterial {
        val jwtHeader = signature.jwtHeader
            ?: throw ETSITrustListPolicyException("JWT credential has no header")

        val x5cElement = jwtHeader["x5c"]
        if (x5cElement != null) {
            val x5cArray = x5cElement.jsonArray
            if (x5cArray.isEmpty()) {
                throw ETSITrustListPolicyException("JWT x5c header is empty")
            }
            log.debug { "Extracted ${x5cArray.size} certificate(s) from JWT x5c header" }
            return IssuerTrustMaterial.CertificateChain(
                x5cArray.map { certElement ->
                    val derBytes = certElement.jsonPrimitive.content.decodeFromBase64()
                    buildPemCertificate(derBytes)
                }
            )
        }

        val resolved = runCatching {
            signature.getCrypto2JwtBasedIssuer(credentialData, keyResolver)
        }.getOrElse { cause ->
            throw ETSITrustListPolicyException(
                "JWT has no x5c certificate chain in header, and the issuer's public key could not be " +
                "resolved: ${cause.message}. ETSI Trust List verification requires either an x5c header " +
                "or a resolvable issuer key (DID or HTTPS well-known JWT VC issuer metadata).",
                cause
            )
        } ?: throw ETSITrustListPolicyException(
            "JWT has no x5c certificate chain in header, and the issuer's public key could not be resolved " +
            "(no DID or HTTPS issuer identifier found). ETSI Trust List verification requires either an " +
            "x5c header or a resolvable issuer key."
        )

        log.debug { "Resolved issuer public key via ${resolved.source} for trust list matching" }
        return IssuerTrustMaterial.PublicKey(exportIssuerJwk(resolved.key))
    }

    private suspend fun exportIssuerJwk(key: Key): String {
        val exporter = key.capabilities.publicKeyExporter
            ?: throw ETSITrustListPolicyException("Resolved issuer key does not support public key export")
        val jwk = exporter.exportPublicKey().toPublicJwk(key.spec)
        return jwk.data.toByteArray().decodeToString()
    }

    // ---------------------------------------------------------------------------
    // Remote Service Query
    // ---------------------------------------------------------------------------

    private suspend fun queryTrustRegistry(certificateChain: List<String>, baseUrl: String): TrustDecisionResponse {
        val url = "${baseUrl.trimEnd('/')}/trust-registry/resolve/certificate-chain"

        log.debug { "Querying trust registry at: $url" }

        val response = sharedHttpClient.post(url) {
            contentType(ContentType.Application.Json)
            setBody(TrustResolveChainRequest(
                certificateChainPemOrDer = certificateChain,
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

    private suspend fun queryTrustRegistryByPublicKey(jwk: String, baseUrl: String): TrustDecisionResponse {
        val url = "${baseUrl.trimEnd('/')}/trust-registry/resolve/public-key"

        log.debug { "Querying trust registry (public key) at: $url" }

        val response = sharedHttpClient.post(url) {
            contentType(ContentType.Application.Json)
            setBody(TrustResolvePublicKeyRequest(
                publicKeyJwk = jwk,
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

    // ---------------------------------------------------------------------------
    // Decision Evaluation
    // ---------------------------------------------------------------------------

    internal fun evaluateDecision(decision: TrustDecisionResponse): Result<JsonElement> {
        val authenticity = decision.sourceAssurance?.authenticityState
            ?: decision.authenticity
            ?: "UNKNOWN"
        log.debug { "Trust decision: ${decision.decision}, freshness: ${decision.sourceFreshness}, authenticity: $authenticity" }

        if (authenticity == "FAILED") {
            return Result.failure(ETSITrustListPolicyException("Trust source authenticity validation failed"))
        }
        if (requireAuthenticated && authenticity != "AUTHENTICATED") {
            return Result.failure(ETSITrustListPolicyException(
                "Trust source is not authenticated (got: $authenticity)"
            ))
        }
        
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
            put("authenticity", JsonPrimitive(
                decision.sourceAssurance?.authenticityState
                    ?: decision.authenticity
                    ?: "UNKNOWN"
            ))
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
    data class TrustResolveChainRequest(
        val certificateChainPemOrDer: List<String>,
        val instant: String? = null,
        val expectedEntityType: String? = null,
        val expectedServiceType: String? = null
    )

    @Serializable
    data class TrustResolvePublicKeyRequest(
        val publicKeyJwk: String,
        val instant: String? = null,
        val expectedEntityType: String? = null,
        val expectedServiceType: String? = null
    )

    @Serializable
    data class TrustDecisionResponse(
        val decision: String,
        val sourceFreshness: String = "UNKNOWN",
        /** Legacy flat field returned by older trust-registry services. */
        val authenticity: String? = null,
        val sourceAssurance: SourceAssuranceDto? = null,
        val matchedSource: MatchedSourceDto? = null,
        val matchedEntity: MatchedEntityDto? = null,
        val matchedService: MatchedServiceDto? = null,
        val evidence: List<TrustEvidenceDto> = emptyList(),
        val warnings: List<String> = emptyList()
    )

    @Serializable
    data class SourceAssuranceDto(
        val signatureStatus: String = "NOT_CHECKED",
        val signerTrust: String = "NOT_EVALUATED",
        val authenticityState: String = "UNKNOWN",
        val acceptancePolicy: String = "REQUIRE_AUTHENTICATED",
        val accepted: Boolean = false,
        val details: String? = null
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
         * Shared HTTP client for remote trust-registry queries.
         * Reused across calls to avoid repeated TLS setup overhead.
         * Timeout values are chosen to bound worst-case latency per certificate lookup.
         */
        private val sharedHttpClient: HttpClient by lazy {
            HttpClient {
                install(ContentNegotiation) {
                    json(Json { ignoreUnknownKeys = true })
                }
                install(HttpTimeout) {
                    connectTimeoutMillis = 5_000
                    requestTimeoutMillis = 10_000
                    socketTimeoutMillis = 10_000
                }
            }
        }

        /**
         * Resolves the issuer's public key from a JWT header/payload that has no x5c chain
         * (DID, or HTTPS well-known JWT VC issuer metadata). Inline `jwk` headers are not
         * trusted for trust-list matching, as they are self-asserted.
         */
        private val keyResolver: Crypto2JwtKeyResolver by lazy { Crypto2JwtKeyResolver(allowInlineJwk = false) }
    }
}

/**
 * The issuer credential material extracted from a [DigitalCredential], used to resolve trust
 * against an ETSI trust list — either an X.509 certificate chain (`x5c`), or a public key
 * (JWK) resolved for issuers that identify their signing key via a DID or HTTPS issuer
 * metadata instead of a certificate.
 */
private sealed class IssuerTrustMaterial {
    data class CertificateChain(val pemChain: List<String>) : IssuerTrustMaterial()
    data class PublicKey(val jwk: String) : IssuerTrustMaterial()
}

/**
 * Bridges [ETSITrustListPolicy] to an external (typically enterprise-backed) trust
 * registry service without requiring the policy to depend on enterprise code.
 *
 * Callers of the verification pipeline (e.g. an enterprise verifier) supply an
 * implementation per request through [PolicyExecutionContext] under the
 * [PolicyServiceKey.TRUST_REGISTRY_RESOLVER] key; [ETSITrustListPolicy] looks it up
 * and invokes [resolveCertificateChain] when neither `trustRegistryUrl` nor `trustLists`
 * is configured on the policy itself.
 */
interface TrustRegistryServiceResolver {
    suspend fun resolveCertificateChain(
        certificateChainPem: List<String>,
        expectedEntityType: String?,
        expectedServiceType: String?
    ): ETSITrustListPolicy.TrustDecisionResponse

    /**
     * Resolves trust for an issuer identified only by its public key JWK — no certificate
     * chain is available (e.g. a DID-based issuer registered in the trust list by public
     * key alone, per ETSI TS 119 602).
     */
    suspend fun resolveByPublicKey(
        publicKeyJwk: String,
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
        validateSignatures: Boolean,
        trustedSourceSignerCertificates: List<String>,
        publicKeyJwk: String? = null
    ): Result<JsonElement>
}

/**
 * Certificate chain validation - uses waltid-x509 library for PKIX path validation.
 * Validates that the leaf certificate chains up to the trusted certificate.
 * 
 * @param certificateChain List of PEM-encoded certificates
 * @param trustedIndex Index of the trusted certificate in the chain
 * @return Pair of (isValid, errorMessage)
 */
@Deprecated("Why trust some parts in the Chain? Not tested, never used")
expect fun validateCertificateChainToIndex(
    certificateChain: List<String>,
    trustedIndex: Int
): Pair<Boolean, String?>
