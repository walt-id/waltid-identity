package id.walt.trust.service

import id.walt.trust.fetcher.SourceFetcher
import id.walt.trust.fetcher.SourceFormat
import id.walt.trust.model.*
import id.walt.trust.parser.lote.LoteJsonParser
import id.walt.trust.parser.lote.LoteXmlParser
import id.walt.trust.parser.tsl.TslXmlParser
import id.walt.trust.signature.CompactJwsValidator
import id.walt.trust.signature.SignatureValidationConfig
import id.walt.trust.store.TrustStore
import id.walt.trust.utils.HashUtils.computeCertificateSha256
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.toList
import java.io.ByteArrayInputStream
import java.security.cert.CertPathBuilder
import java.security.cert.CertPathValidator
import java.security.cert.CertStore
import java.security.cert.CertificateFactory
import java.security.cert.CollectionCertStoreParameters
import java.security.cert.PKIXBuilderParameters
import java.security.cert.PKIXCertPathBuilderResult
import java.security.cert.TrustAnchor
import java.security.cert.X509CertSelector
import java.security.cert.X509Certificate
import java.util.Date
import java.util.concurrent.ConcurrentHashMap
import kotlin.io.encoding.Base64
import kotlin.time.Clock
import kotlin.time.Instant

private val log = KotlinLogging.logger {}

/**
 * Default implementation of [TrustRegistryService].
 * Storage is supplied by the caller, allowing either in-memory or persistent implementations.
 */
class DefaultTrustRegistryService(
    private val store: TrustStore
) : TrustRegistryService {

    // Configured source URLs (for refresh). Concurrent to tolerate registration
    // from arbitrary coroutine contexts while resolve/refresh calls read it.
    private val configuredSources = ConcurrentHashMap<String, SourceConfig>()

    data class SourceConfig(
        val sourceId: String,
        val url: String,
        val sourceFamily: SourceFamily,
        val options: SourceLoadOptions
    )

    fun registerSource(
        sourceId: String,
        url: String,
        sourceFamily: SourceFamily,
        options: SourceLoadOptions
    ) {
        configuredSources[sourceId] = SourceConfig(sourceId, url, sourceFamily, options)
    }

    @Deprecated("Use registerSource with SourceLoadOptions")
    fun registerSource(
        sourceId: String,
        url: String,
        sourceFamily: SourceFamily,
        validateSignature: Boolean,
        trustedSignerCertificates: List<String>
    ) = registerSource(
        sourceId,
        url,
        sourceFamily,
        legacyOptions(validateSignature, trustedSignerCertificates)
    )

    // ---------------------------------------------------------------------------
    // Resolve operations
    // ---------------------------------------------------------------------------

    override suspend fun resolveCertificateChain(
        certificateChainPemOrDer: List<String>,
        instant: Instant,
        expectedEntityType: TrustedEntityType?,
        expectedServiceType: String?
    ): TrustDecision {
        if (certificateChainPemOrDer.isEmpty()) {
            return TrustDecision(
                decision = TrustDecisionCode.PROCESSING_ERROR,
                warnings = listOf("Certificate chain is empty")
            )
        }

        val presentedCertificates = runCatching {
            certificateChainPemOrDer.map(::parseCertificate)
        }.getOrElse { error ->
            return TrustDecision(
                decision = TrustDecisionCode.PROCESSING_ERROR,
                warnings = listOf("Failed to parse certificate chain: ${error.message}")
            )
        }

        val identitiesByAnchor = store.listCertificateIdentities().toList().mapNotNull { identity ->
            val encoded = identity.certificateDerBase64 ?: return@mapNotNull null
            runCatching { identity to parseCertificate(encoded) }
                .onFailure { log.warn(it) { "Ignoring invalid stored certificate for identity ${identity.identityId}" } }
                .getOrNull()
        }

        val pathMatches = identitiesByAnchor.filter { (_, anchor) ->
            validateCertificatePath(presentedCertificates, anchor, instant)
        }.map { it.first }

        if (pathMatches.isNotEmpty()) {
            val decision = buildDecisionFromIdentities(
                pathMatches,
                instant,
                expectedEntityType,
                expectedServiceType
            )
            if (decision.decision != TrustDecisionCode.NOT_TRUSTED) {
                return decision.copy(
                    evidence = decision.evidence + TrustEvidence(
                        type = "CERTIFICATE_PATH",
                        value = "Validated presented chain against registry-owned trust anchor"
                    )
                )
            }
        }

        // Backwards compatibility for fingerprint-only sources and credentials that
        // still contain their registered root. New sources should retain certificate DER.
        for (certificate in certificateChainPemOrDer) {
            val decision = resolveByCertificate(
                certificate,
                instant,
                expectedEntityType,
                expectedServiceType
            )
            if (decision.decision == TrustDecisionCode.TRUSTED ||
                decision.decision == TrustDecisionCode.STALE_SOURCE
            ) {
                return decision.copy(
                    warnings = decision.warnings +
                        "Trust was resolved by exact certificate match; no registry-owned path was built"
                )
            }
        }

        return TrustDecision(
            decision = TrustDecisionCode.NOT_TRUSTED,
            evidence = listOf(
                TrustEvidence(
                    "CERTIFICATE_PATH",
                    "No valid path from the presented leaf to an eligible registry certificate"
                )
            )
        )
    }

    override suspend fun resolveByCertificate(
        certificatePemOrDer: String,
        instant: Instant,
        expectedEntityType: TrustedEntityType?,
        expectedServiceType: String?
    ): TrustDecision {
        val sha256 = computeCertificateSha256(certificatePemOrDer)
            ?: return TrustDecision(
                decision = TrustDecisionCode.PROCESSING_ERROR,
                warnings = listOf("Failed to parse certificate")
            )

        return resolveByCertificateSha256(sha256, instant, expectedEntityType, expectedServiceType)
    }

    override suspend fun resolveByCertificateSha256(
        sha256Hex: String,
        instant: Instant,
        expectedEntityType: TrustedEntityType?,
        expectedServiceType: String?
    ): TrustDecision {
        val normalizedSha256 = sha256Hex.lowercase().removePrefix("sha256:").removePrefix("sha256").replace(":", "")

        val matchedIdentities = store.findIdentitiesByCertificateSha256(normalizedSha256).toList()

        if (matchedIdentities.isEmpty()) {
            return TrustDecision(
                decision = TrustDecisionCode.NOT_TRUSTED,
                evidence = listOf(
                    TrustEvidence("LOOKUP", "No identity found for certificate SHA-256: $normalizedSha256")
                )
            )
        }

        return buildDecisionFromIdentities(matchedIdentities, instant, expectedEntityType, expectedServiceType)
    }

    override suspend fun resolveByPublicKey(
        jwk: String,
        instant: Instant,
        expectedEntityType: TrustedEntityType?,
        expectedServiceType: String?
    ): TrustDecision {
        // JWK thumbprint matching is not yet implemented.
        return TrustDecision(
            decision = TrustDecisionCode.UNSUPPORTED_SOURCE,
            warnings = listOf("JWK-based lookup is not yet implemented")
        )
    }

    override suspend fun resolveByProviderId(
        providerId: String,
        instant: Instant,
        expectedEntityType: TrustedEntityType?
    ): TrustDecision {
        val entity = store.getEntity(providerId)
            ?: return TrustDecision(
                decision = TrustDecisionCode.NOT_TRUSTED,
                evidence = listOf(TrustEvidence("LOOKUP", "Entity not found: $providerId"))
            )

        if (expectedEntityType != null && entity.entityType != expectedEntityType) {
            return TrustDecision(
                decision = TrustDecisionCode.NOT_TRUSTED,
                matchedEntity = entity,
                evidence = listOf(
                    TrustEvidence("TYPE_MISMATCH", "Expected $expectedEntityType but found ${entity.entityType}")
                )
            )
        }

        val source = store.getSource(entity.sourceId)
        if (source?.assurance?.accepted != true) {
            return sourceNotAcceptedDecision(source, entity)
        }
        val trustedService = store.listServicesForEntity(entity.entityId).firstOrNull { it.status in TRUSTED_STATUSES }

        val freshness = evaluateFreshness(source, instant)

        return if (trustedService != null) {
            TrustDecision(
                decision = if (freshness == FreshnessState.EXPIRED) TrustDecisionCode.STALE_SOURCE else TrustDecisionCode.TRUSTED,
                sourceFreshness = freshness,
                sourceAssurance = source.assurance,
                matchedSource = source,
                matchedEntity = entity,
                matchedService = trustedService,
                evidence = listOf(
                    TrustEvidence("ENTITY_MATCH", "Provider ID: ${entity.entityId}"),
                    TrustEvidence("STATUS", "Service status: ${trustedService.status}")
                ),
                warnings = if (freshness == FreshnessState.STALE) listOf("Source is stale") else emptyList()
            )
        } else {
            TrustDecision(
                decision = TrustDecisionCode.NOT_TRUSTED,
                sourceFreshness = freshness,
                matchedSource = source,
                matchedEntity = entity,
                evidence = listOf(
                    TrustEvidence("STATUS", "No service with trusted status found for entity")
                )
            )
        }
    }

    // ---------------------------------------------------------------------------
    // List operations
    // ---------------------------------------------------------------------------

    override suspend fun listTrustedEntities(filter: EntityFilter): Flow<TrustedEntity> =
        store.listEntities(filter)

    override suspend fun listSources(): Flow<TrustSource> =
        store.listSources()

    override suspend fun getSourceHealth(): Flow<TrustSourceHealth> =
        store.listSources().map { source ->
            TrustSourceHealth(
                sourceId = source.sourceId,
                displayName = source.displayName,
                sourceFamily = source.sourceFamily,
                freshnessState = evaluateFreshness(source, now()),
                assurance = source.assurance,
                nextUpdate = source.nextUpdate,
                entityCount = store.countEntities(source.sourceId),
                serviceCount = store.countServices(source.sourceId)
            )
        }

    // ---------------------------------------------------------------------------
    // Refresh operations
    // ---------------------------------------------------------------------------

    override suspend fun refreshSource(sourceId: String): RefreshResult {
        val config = configuredSources[sourceId]
            ?: return RefreshResult(sourceId, success = false, error = "Source not configured: $sourceId")

        val fetchResult = SourceFetcher.fetch(config.url)
        if (!fetchResult.success || fetchResult.content == null) {
            return RefreshResult(sourceId, success = false, error = fetchResult.error ?: "Fetch failed")
        }

        return loadSourceFromContent(sourceId, fetchResult.content, config.url, config.options)
    }

    override suspend fun loadSourceFromContent(
        sourceId: String,
        content: String,
        sourceUrl: String?,
        options: SourceLoadOptions
    ): RefreshResult = loadSourceFromContentInternal(sourceId, content, sourceUrl, options)

    override suspend fun loadSourceFromUrl(
        sourceId: String,
        url: String,
        options: SourceLoadOptions
    ): RefreshResult {
        log.info { "Loading trust source from URL: $url" }
        val fetchResult = SourceFetcher.fetch(url)
        if (!fetchResult.success || fetchResult.content == null) {
            return RefreshResult(
                sourceId = sourceId,
                success = false,
                error = fetchResult.error ?: "Failed to fetch from $url",
                errorCode = SourceLoadErrorCode.FETCH_FAILED
            )
        }

        registerSource(sourceId, url, detectSourceFamily(fetchResult.content), options)
        return loadSourceFromContentInternal(sourceId, fetchResult.content, url, options)
    }

    @Deprecated("Use the SourceLoadOptions overload with an explicit acceptance policy")
    override suspend fun loadSourceFromContent(
        sourceId: String,
        content: String,
        sourceUrl: String?,
        validateSignature: Boolean,
        trustedSignerCertificates: List<String>
    ): RefreshResult {
        return loadSourceFromContentInternal(
            sourceId,
            content,
            sourceUrl,
            legacyOptions(validateSignature, trustedSignerCertificates)
        )
    }

    @Deprecated("Use the SourceLoadOptions overload with an explicit acceptance policy")
    override suspend fun loadSourceFromUrl(
        sourceId: String,
        url: String,
        validateSignature: Boolean,
        trustedSignerCertificates: List<String>
    ): RefreshResult {
        return loadSourceFromUrl(
            sourceId,
            url,
            legacyOptions(validateSignature, trustedSignerCertificates)
        )
    }

    private suspend fun loadSourceFromContentInternal(
        sourceId: String,
        content: String,
        sourceUrl: String?,
        options: SourceLoadOptions
    ): RefreshResult {
        val signedEnvelope = CompactJwsValidator.isCompactJws(content)
        val verifySignatures = options.acceptancePolicy != SourceAcceptancePolicy.ALLOW_UNVERIFIED
        val envelope = try {
            when {
                !signedEnvelope -> SourceEnvelope(
                    content,
                    SourceAssurance(
                        signatureStatus = SignatureStatus.NOT_PRESENT,
                        signerTrust = SignerTrust.NOT_APPLICABLE,
                        authenticityState = AuthenticityState.UNVERIFIED,
                        details = "Source has no signed envelope"
                    )
                )
                verifySignatures -> {
                    val requireTrustedSigner =
                        options.acceptancePolicy == SourceAcceptancePolicy.REQUIRE_AUTHENTICATED
                    val validation = CompactJwsValidator.validate(
                        content,
                        options.trustedSignerCertificates,
                        requireTrustedSigner = requireTrustedSigner
                    )
                    SourceEnvelope(
                        content = validation.payload,
                        assurance = SourceAssurance(
                            signatureStatus = SignatureStatus.VALID,
                            signerTrust = if (requireTrustedSigner) SignerTrust.TRUSTED else SignerTrust.NOT_EVALUATED,
                            authenticityState = if (requireTrustedSigner) {
                                AuthenticityState.AUTHENTICATED
                            } else {
                                AuthenticityState.INTEGRITY_VERIFIED
                            },
                            details = if (requireTrustedSigner) {
                                "Compact JWS signature and signer trust validated"
                            } else {
                                "Compact JWS signature integrity validated; signer trust was not evaluated"
                            }
                        ),
                        validationMetadata = validation.metadata
                    )
                }
                else -> SourceEnvelope(
                    content = CompactJwsValidator.decodePayloadWithoutValidation(content),
                    assurance = SourceAssurance(
                        signatureStatus = SignatureStatus.NOT_CHECKED,
                        signerTrust = SignerTrust.NOT_EVALUATED,
                        authenticityState = AuthenticityState.UNVERIFIED,
                        details = "Compact JWS verification was explicitly disabled"
                    ),
                    validationMetadata = mapOf("signatureFormat" to "JWS_COMPACT_UNVERIFIED")
                )
            }
        } catch (e: Exception) {
            log.error(e) { "Failed to validate source signature: $sourceId" }
            return RefreshResult(
                sourceId = sourceId,
                success = false,
                error = e.message ?: "Signature validation failed",
                errorCode = SourceLoadErrorCode.SIGNATURE_VALIDATION_FAILED,
                assurance = SourceAssurance(
                    signatureStatus = SignatureStatus.INVALID,
                    signerTrust = SignerTrust.UNTRUSTED,
                    authenticityState = AuthenticityState.FAILED,
                    acceptancePolicy = options.acceptancePolicy,
                    accepted = false,
                    details = e.message
                )
            )
        }

        return try {
            val sourceContent = envelope.content
            val format = SourceFetcher.detectFormat(null, sourceContent)

            val parsed = when {
                // Detect if it's TSL (TrustServiceStatusList) or LoTE
                sourceContent.contains("TrustServiceStatusList") || sourceContent.contains("TrustServiceProviderList") -> {
                    log.info { "Parsing TSL XML for source: $sourceId (policy=${options.acceptancePolicy})" }
                    val requireTrustedSigner = options.acceptancePolicy == SourceAcceptancePolicy.REQUIRE_AUTHENTICATED
                    val config = id.walt.trust.parser.tsl.TslParseConfig(
                        validateSignature = verifySignatures,
                        signatureConfig = SignatureValidationConfig(
                            requireTrustedCertificate = requireTrustedSigner,
                            trustedAnchors = options.trustedSignerCertificates.map(::parseCertificate).toSet()
                        ),
                        strictSignatureValidation = false,
                        requireSignature = options.acceptancePolicy in setOf(
                            SourceAcceptancePolicy.REQUIRE_AUTHENTICATED,
                            SourceAcceptancePolicy.REQUIRE_VALID_SIGNATURE
                        )
                    )
                    val result = TslXmlParser.parse(sourceContent, sourceId, sourceUrl, config)
                    ParsedContent(result.source, result.entities, result.services, result.identities)
                }

                (sourceContent.contains("ListOfTrustedEntities") || sourceContent.contains("TrustedEntity")) && format == SourceFormat.XML -> {
                    log.info { "Parsing LoTE XML for source: $sourceId" }
                    val result = LoteXmlParser.parse(sourceContent, sourceId, sourceUrl)
                    ParsedContent(result.source, result.entities, result.services, result.identities)
                }

                format == SourceFormat.JSON -> {
                    log.info { "Parsing LoTE JSON for source: $sourceId" }
                    val result = LoteJsonParser.parse(
                        sourceContent,
                        sourceId,
                        sourceUrl,
                        envelope.assurance,
                        envelope.validationMetadata
                    )
                    ParsedContent(result.source, result.entities, result.services, result.identities)
                }

                else -> {
                    return RefreshResult(
                        sourceId,
                        success = false,
                        error = "Unknown source format",
                        errorCode = SourceLoadErrorCode.UNKNOWN_FORMAT
                    )
                }
            }

            val evaluatedAssurance = parsed.source.assurance.copy(
                acceptancePolicy = options.acceptancePolicy
            )
            val accepted = isAccepted(evaluatedAssurance, options.acceptancePolicy)
            val finalAssurance = evaluatedAssurance.copy(accepted = accepted)
            if (!accepted) {
                return RefreshResult(
                    sourceId = sourceId,
                    success = false,
                    error = rejectionReason(finalAssurance),
                    errorCode = if (finalAssurance.authenticityState == AuthenticityState.FAILED) {
                        SourceLoadErrorCode.SIGNATURE_VALIDATION_FAILED
                    } else {
                        SourceLoadErrorCode.SOURCE_NOT_ACCEPTED
                    },
                    assurance = finalAssurance
                )
            }

            val freshness = evaluateFreshness(parsed.source, now())
            val sourceWithFreshness = parsed.source.copy(
                assurance = finalAssurance,
                freshnessState = freshness
            )

            // Store everything atomically to prevent partial state on failure
            store.replaceSourceData(
                source = sourceWithFreshness,
                entities = parsed.entities,
                services = parsed.services,
                identities = parsed.identities
            )

            log.info { "Loaded source $sourceId: ${parsed.entities.size} entities, ${parsed.services.size} services, ${parsed.identities.size} identities" }

            RefreshResult(
                sourceId = sourceId,
                success = true,
                entitiesLoaded = parsed.entities.size,
                servicesLoaded = parsed.services.size,
                identitiesLoaded = parsed.identities.size,
                assurance = finalAssurance
            )
        } catch (e: Exception) {
            log.error(e) { "Failed to parse source: $sourceId" }
            RefreshResult(
                sourceId = sourceId,
                success = false,
                error = e.message ?: "Parse error",
                errorCode = SourceLoadErrorCode.PARSE_FAILED,
                assurance = envelope.assurance.copy(
                    acceptancePolicy = options.acceptancePolicy,
                    accepted = false,
                    details = e.message
                )
            )
        }
    }

    // ---------------------------------------------------------------------------
    // Helpers
    // ---------------------------------------------------------------------------

    private data class ParsedContent(
        val source: TrustSource,
        val entities: List<TrustedEntity>,
        val services: List<TrustedService>,
        val identities: List<ServiceIdentity>
    )

    private data class SourceEnvelope(
        val content: String,
        val assurance: SourceAssurance,
        val validationMetadata: Map<String, String> = emptyMap()
    )

    private fun isAccepted(assurance: SourceAssurance, policy: SourceAcceptancePolicy): Boolean {
        if (assurance.authenticityState == AuthenticityState.FAILED ||
            assurance.signatureStatus in setOf(SignatureStatus.INVALID, SignatureStatus.UNSUPPORTED)
        ) return false

        return when (policy) {
            SourceAcceptancePolicy.REQUIRE_AUTHENTICATED ->
                assurance.authenticityState == AuthenticityState.AUTHENTICATED
            SourceAcceptancePolicy.REQUIRE_VALID_SIGNATURE ->
                assurance.authenticityState in setOf(
                    AuthenticityState.AUTHENTICATED,
                    AuthenticityState.INTEGRITY_VERIFIED
                )
            SourceAcceptancePolicy.ALLOW_UNSIGNED ->
                assurance.signatureStatus != SignatureStatus.NOT_CHECKED
            SourceAcceptancePolicy.ALLOW_UNVERIFIED -> true
        }
    }

    private fun rejectionReason(assurance: SourceAssurance): String = when {
        assurance.authenticityState == AuthenticityState.FAILED ->
            assurance.details ?: "Source signature validation failed"
        assurance.acceptancePolicy == SourceAcceptancePolicy.REQUIRE_AUTHENTICATED ->
            "Source is not authenticated by an independently trusted signer"
        assurance.acceptancePolicy == SourceAcceptancePolicy.REQUIRE_VALID_SIGNATURE ->
            "Source does not contain a valid signature"
        else -> "Source does not satisfy ${assurance.acceptancePolicy}"
    }

    private fun legacyOptions(
        validateSignature: Boolean,
        trustedSignerCertificates: List<String>
    ): SourceLoadOptions = SourceLoadOptions(
        acceptancePolicy = when {
            !validateSignature -> SourceAcceptancePolicy.ALLOW_UNVERIFIED
            trustedSignerCertificates.isNotEmpty() -> SourceAcceptancePolicy.REQUIRE_AUTHENTICATED
            else -> SourceAcceptancePolicy.ALLOW_UNSIGNED
        },
        trustedSignerCertificates = trustedSignerCertificates
    )

    private suspend fun buildDecisionFromIdentities(
        identities: List<ServiceIdentity>,
        instant: Instant,
        expectedEntityType: TrustedEntityType?,
        expectedServiceType: String?
    ): TrustDecision {
        if (identities.size > 1) {
            // Multiple matches — check if they're all from the same entity
            val uniqueEntities = identities.map { it.entityId }.distinct()
            if (uniqueEntities.size > 1) {
                return TrustDecision(
                    decision = TrustDecisionCode.MULTIPLE_MATCHES,
                    evidence = listOf(
                        TrustEvidence("MULTIPLE_ENTITIES", "Found ${uniqueEntities.size} different entities matching certificate")
                    ),
                    warnings = uniqueEntities.map { "Matched entity: $it" }
                )
            }
        }

        // Evaluate ALL matching identities and pick the best result
        // Priority: TRUSTED > STALE_SOURCE > NOT_TRUSTED (with matching types preferred)
        val candidates = identities.mapNotNull { identity ->
            evaluateIdentity(identity, instant, expectedEntityType, expectedServiceType)
        }

        if (candidates.isEmpty()) {
            // All identities failed to resolve
            return TrustDecision(
                decision = TrustDecisionCode.NOT_TRUSTED,
                evidence = listOf(
                    TrustEvidence("NO_VALID_IDENTITY", "No valid identity could be resolved")
                )
            )
        }

        // Sort by decision priority: TRUSTED first, then STALE_SOURCE, then NOT_TRUSTED
        // Within same decision, prefer type-matched results
        return candidates.sortedWith(
            compareBy(
                { decisionPriority(it.decision) },
                { if (it.evidence.any { e -> e.type.contains("MISMATCH") }) 1 else 0 }
            )
        ).first()
    }

    private fun decisionPriority(decision: TrustDecisionCode): Int = when (decision) {
        TrustDecisionCode.TRUSTED -> 0
        TrustDecisionCode.STALE_SOURCE -> 1
        TrustDecisionCode.NOT_TRUSTED -> 2
        TrustDecisionCode.MULTIPLE_MATCHES -> 3
        else -> 4
    }

    private suspend fun evaluateIdentity(
        identity: ServiceIdentity,
        instant: Instant,
        expectedEntityType: TrustedEntityType?,
        expectedServiceType: String?
    ): TrustDecision? {
        val entity = store.getEntity(identity.entityId) ?: return null
        val source = store.getSource(entity.sourceId)
        val service = identity.serviceId?.let { store.getService(it) }

        if (source?.assurance?.accepted != true) {
            return sourceNotAcceptedDecision(source, entity, service)
        }

        // Type filtering
        if (expectedEntityType != null && entity.entityType != expectedEntityType) {
            return TrustDecision(
                decision = TrustDecisionCode.NOT_TRUSTED,
                matchedEntity = entity,
                evidence = listOf(
                    TrustEvidence("TYPE_MISMATCH", "Expected $expectedEntityType but found ${entity.entityType}")
                )
            )
        }

        if (expectedServiceType != null && service != null && service.serviceType != expectedServiceType) {
            return TrustDecision(
                decision = TrustDecisionCode.NOT_TRUSTED,
                matchedEntity = entity,
                matchedService = service,
                evidence = listOf(
                    TrustEvidence("SERVICE_TYPE_MISMATCH", "Expected $expectedServiceType but found ${service.serviceType}")
                )
            )
        }

        val freshness = evaluateFreshness(source, instant)
        val isTrusted = service?.status in TRUSTED_STATUSES

        // Don't return STALE_SOURCE for untrusted services - they're simply NOT_TRUSTED
        // STALE_SOURCE should only be returned when the service *would be* trusted but the source is expired
        return TrustDecision(
            decision = when {
                !isTrusted -> TrustDecisionCode.NOT_TRUSTED
                freshness == FreshnessState.EXPIRED -> TrustDecisionCode.STALE_SOURCE
                else -> TrustDecisionCode.TRUSTED
            },
            sourceFreshness = freshness,
            sourceAssurance = source.assurance,
            matchedSource = source,
            matchedEntity = entity,
            matchedService = service,
            evidence = buildList {
                add(TrustEvidence("CERTIFICATE_MATCH", "Identity: ${identity.identityId}"))
                service?.let { add(TrustEvidence("STATUS", "Service status: ${it.status}")) }
            },
            warnings = if (freshness == FreshnessState.STALE) listOf("Source is stale") else emptyList()
        )
    }

    private fun sourceNotAcceptedDecision(
        source: TrustSource?,
        entity: TrustedEntity? = null,
        service: TrustedService? = null
    ): TrustDecision = TrustDecision(
        decision = TrustDecisionCode.NOT_TRUSTED,
        sourceAssurance = source?.assurance ?: SourceAssurance(),
        matchedSource = source,
        matchedEntity = entity,
        matchedService = service,
        evidence = listOf(
            TrustEvidence(
                type = "SOURCE_NOT_ACCEPTED",
                value = source?.assurance?.details ?: "Trust source was not admitted"
            )
        )
    )

    private fun evaluateFreshness(source: TrustSource?, instant: Instant): FreshnessState {
        if (source == null) return FreshnessState.UNKNOWN
        val nextUpdate = source.nextUpdate ?: return FreshnessState.UNKNOWN

        return when {
            instant > nextUpdate -> FreshnessState.EXPIRED
            // Consider "stale" if within 24 hours of nextUpdate
            instant > nextUpdate.minus(kotlin.time.Duration.parse("24h")) -> FreshnessState.STALE
            else -> FreshnessState.FRESH
        }
    }

    companion object {
        private val TRUSTED_STATUSES = setOf(
            TrustStatus.GRANTED,
            TrustStatus.RECOGNIZED,
            TrustStatus.ACCREDITED,
            TrustStatus.SUPERVISED
        )

        private fun now(): Instant = Clock.System.now()
    }

    private fun detectSourceFamily(content: String): SourceFamily {
        return when {
            content.contains("TrustServiceStatusList") || content.contains("TrustServiceProviderList") -> SourceFamily.TSL
            content.contains("ListOfTrustedEntities") || content.contains("TrustedEntity") -> SourceFamily.LOTE
            content.trimStart().startsWith("{") || content.trimStart().startsWith("[") -> SourceFamily.LOTE
            else -> SourceFamily.PILOT
        }
    }

    private fun parseCertificate(pemOrDer: String): X509Certificate {
        val encoded = if (pemOrDer.contains("BEGIN CERTIFICATE")) {
            pemOrDer
                .replace("-----BEGIN CERTIFICATE-----", "")
                .replace("-----END CERTIFICATE-----", "")
                .replace("\\s".toRegex(), "")
        } else {
            pemOrDer.replace("\\s".toRegex(), "")
        }
        val der = Base64.decode(encoded)
        return CertificateFactory.getInstance("X.509")
            .generateCertificate(ByteArrayInputStream(der)) as X509Certificate
    }

    private fun validateCertificatePath(
        presentedCertificates: List<X509Certificate>,
        anchor: X509Certificate,
        instant: Instant
    ): Boolean = runCatching {
        val leaf = presentedCertificates.first()

        // An exact certificate pin is a valid terminal trust decision. Check its
        // validity explicitly because an empty PKIX path does not always do so.
        if (leaf.encoded.contentEquals(anchor.encoded)) {
            leaf.checkValidity(Date(instant.toEpochMilliseconds()))
            return@runCatching true
        }

        val intermediates = presentedCertificates.drop(1)
            .filterNot { it.encoded.contentEquals(anchor.encoded) }
        val selector = X509CertSelector().apply { certificate = leaf }
        val certStore = CertStore.getInstance(
            "Collection",
            CollectionCertStoreParameters(intermediates)
        )
        val parameters = PKIXBuilderParameters(setOf(TrustAnchor(anchor, null)), selector).apply {
            addCertStore(certStore)
            date = Date(instant.toEpochMilliseconds())
            isRevocationEnabled = false
        }
        val result = CertPathBuilder.getInstance("PKIX").build(parameters) as PKIXCertPathBuilderResult
        CertPathValidator.getInstance("PKIX").validate(result.certPath, parameters)
        true
    }.getOrElse { error ->
        log.debug { "Certificate path did not validate against candidate anchor: ${error.message}" }
        false
    }
}
