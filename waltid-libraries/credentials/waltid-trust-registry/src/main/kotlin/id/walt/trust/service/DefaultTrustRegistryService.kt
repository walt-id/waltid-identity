package id.walt.trust.service

import id.walt.trust.fetcher.SourceFetcher
import id.walt.trust.model.*
import id.walt.trust.parser.lote.LoteJsonParser
import id.walt.trust.parser.lote.LoteXmlParser
import id.walt.trust.parser.tsl.TslXmlParser
import id.walt.trust.store.TrustStore
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlin.time.Instant
import java.security.MessageDigest
import java.util.*

private val log = KotlinLogging.logger {}

/**
 * Default implementation of [TrustRegistryService].
 * Uses in-memory store for MVP; enterprise persistence is added separately.
 */
class DefaultTrustRegistryService(
    private val store: TrustStore
) : TrustRegistryService {

    // Configured source URLs (for refresh)
    private val configuredSources = mutableMapOf<String, SourceConfig>()

    data class SourceConfig(
        val sourceId: String,
        val url: String,
        val sourceFamily: SourceFamily
    )

    fun registerSource(sourceId: String, url: String, sourceFamily: SourceFamily) {
        configuredSources[sourceId] = SourceConfig(sourceId, url, sourceFamily)
    }

    // ---------------------------------------------------------------------------
    // Resolve operations
    // ---------------------------------------------------------------------------

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
        
        val matchedIdentities = store.findIdentitiesByCertificateSha256(normalizedSha256)
        
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
        // MVP: JWK thumbprint matching not yet implemented
        return TrustDecision(
            decision = TrustDecisionCode.UNSUPPORTED_SOURCE,
            warnings = listOf("JWK-based lookup not yet implemented in MVP")
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
        val services = store.listServicesForEntity(entity.entityId)
        val trustedService = services.find { it.status in TRUSTED_STATUSES }

        val freshness = evaluateFreshness(source, instant)

        return if (trustedService != null) {
            TrustDecision(
                decision = if (freshness == FreshnessState.EXPIRED) TrustDecisionCode.STALE_SOURCE else TrustDecisionCode.TRUSTED,
                sourceFreshness = freshness,
                authenticity = source?.authenticityState ?: AuthenticityState.UNKNOWN,
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

    override suspend fun listTrustedEntities(filter: EntityFilter): List<TrustedEntity> {
        return store.listEntities(filter)
    }

    override suspend fun listSources(): List<TrustSource> {
        return store.listSources()
    }

    override suspend fun getSourceHealth(): List<TrustSourceHealth> {
        return store.listSources().map { source ->
            TrustSourceHealth(
                sourceId = source.sourceId,
                displayName = source.displayName,
                sourceFamily = source.sourceFamily,
                freshnessState = evaluateFreshness(source, now()),
                authenticityState = source.authenticityState,
                nextUpdate = source.nextUpdate,
                entityCount = store.countEntities(source.sourceId),
                serviceCount = store.countServices(source.sourceId)
            )
        }
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

        return loadSourceFromContent(sourceId, fetchResult.content, config.url)
    }

    override suspend fun loadSourceFromContent(
        sourceId: String,
        content: String,
        sourceUrl: String?
    ): RefreshResult {
        return loadSourceFromContentInternal(sourceId, content, sourceUrl, validateSignature = true)
    }

    override suspend fun loadSourceFromUrl(
        sourceId: String,
        url: String,
        validateSignature: Boolean
    ): RefreshResult {
        log.info { "Loading trust source from URL: $url" }
        
        val fetchResult = SourceFetcher.fetch(url)
        if (!fetchResult.success || fetchResult.content == null) {
            return RefreshResult(
                sourceId = sourceId,
                success = false,
                error = fetchResult.error ?: "Failed to fetch from $url"
            )
        }
        
        // Register this source for future refreshes
        val sourceFamily = detectSourceFamily(fetchResult.content)
        registerSource(sourceId, url, sourceFamily)
        
        return loadSourceFromContentInternal(
            sourceId = sourceId,
            content = fetchResult.content,
            sourceUrl = url,
            validateSignature = validateSignature
        )
    }

    private suspend fun loadSourceFromContentInternal(
        sourceId: String,
        content: String,
        sourceUrl: String?,
        validateSignature: Boolean
    ): RefreshResult {
        return try {
            val format = SourceFetcher.detectFormat(null, content)
            
            val parsed = when {
                // Detect if it's TSL (TrustServiceStatusList) or LoTE
                content.contains("TrustServiceStatusList") || content.contains("TrustServiceProviderList") -> {
                    log.info { "Parsing TSL XML for source: $sourceId (validateSignature=$validateSignature)" }
                    val config = id.walt.trust.parser.tsl.TslParseConfig(
                        validateSignature = validateSignature
                    )
                    val result = TslXmlParser.parse(content, sourceId, sourceUrl, config)
                    ParsedContent(result.source, result.entities, result.services, result.identities)
                }
                content.contains("ListOfTrustedEntities") || content.contains("TrustedEntity") && format == SourceFetcher.SourceFormat.XML -> {
                    log.info { "Parsing LoTE XML for source: $sourceId" }
                    val result = LoteXmlParser.parse(content, sourceId, sourceUrl)
                    ParsedContent(result.source, result.entities, result.services, result.identities)
                }
                format == SourceFetcher.SourceFormat.JSON -> {
                    log.info { "Parsing LoTE JSON for source: $sourceId" }
                    val result = LoteJsonParser.parse(content, sourceId, sourceUrl)
                    ParsedContent(result.source, result.entities, result.services, result.identities)
                }
                else -> {
                    return RefreshResult(sourceId, success = false, error = "Unknown source format")
                }
            }

            // Update freshness based on nextUpdate
            val freshness = evaluateFreshness(parsed.source, now())
            val sourceWithFreshness = parsed.source.copy(freshnessState = freshness)

            // Store everything
            store.upsertSource(sourceWithFreshness)
            store.upsertEntities(parsed.entities)
            store.upsertServices(parsed.services)
            store.upsertIdentities(parsed.identities)

            log.info { "Loaded source $sourceId: ${parsed.entities.size} entities, ${parsed.services.size} services, ${parsed.identities.size} identities" }

            RefreshResult(
                sourceId = sourceId,
                success = true,
                entitiesLoaded = parsed.entities.size,
                servicesLoaded = parsed.services.size,
                identitiesLoaded = parsed.identities.size
            )
        } catch (e: Exception) {
            log.error(e) { "Failed to parse source: $sourceId" }
            RefreshResult(sourceId, success = false, error = e.message ?: "Parse error")
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

        val identity = identities.first()
        val entity = store.getEntity(identity.entityId)
        val source = entity?.let { store.getSource(it.sourceId) }
        val service = identity.serviceId?.let { store.getService(it) }

        // Type filtering
        if (expectedEntityType != null && entity != null && entity.entityType != expectedEntityType) {
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

        return TrustDecision(
            decision = when {
                freshness == FreshnessState.EXPIRED -> TrustDecisionCode.STALE_SOURCE
                isTrusted -> TrustDecisionCode.TRUSTED
                else -> TrustDecisionCode.NOT_TRUSTED
            },
            sourceFreshness = freshness,
            authenticity = source?.authenticityState ?: AuthenticityState.UNKNOWN,
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

    private fun computeCertificateSha256(pemOrDer: String): String? {
        return try {
            val certBytes = if (pemOrDer.contains("BEGIN CERTIFICATE")) {
                // PEM format
                val base64 = pemOrDer
                    .replace("-----BEGIN CERTIFICATE-----", "")
                    .replace("-----END CERTIFICATE-----", "")
                    .replace("\\s".toRegex(), "")
                Base64.getDecoder().decode(base64)
            } else {
                // Assume base64-encoded DER
                Base64.getDecoder().decode(pemOrDer.replace("\\s".toRegex(), ""))
            }
            
            MessageDigest.getInstance("SHA-256")
                .digest(certBytes)
                .joinToString("") { "%02x".format(it) }
        } catch (e: Exception) {
            log.warn(e) { "Failed to compute certificate SHA-256" }
            null
        }
    }

    companion object {
        private val TRUSTED_STATUSES = setOf(
            TrustStatus.GRANTED,
            TrustStatus.RECOGNIZED,
            TrustStatus.ACCREDITED,
            TrustStatus.SUPERVISED
        )

        private fun now(): Instant = Instant.fromEpochMilliseconds(System.currentTimeMillis())
    }

    private fun detectSourceFamily(content: String): SourceFamily {
        return when {
            content.contains("TrustServiceStatusList") || content.contains("TrustServiceProviderList") -> SourceFamily.TSL
            content.contains("ListOfTrustedEntities") || content.contains("TrustedEntity") -> SourceFamily.LOTE
            content.trimStart().startsWith("{") || content.trimStart().startsWith("[") -> SourceFamily.LOTE
            else -> SourceFamily.PILOT
        }
    }
}
