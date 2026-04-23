package id.walt.policies2.vc.policies

import id.walt.trust.model.TrustDecision
import id.walt.trust.model.TrustedEntityType
import id.walt.trust.service.DefaultTrustRegistryService
import id.walt.trust.store.InMemoryTrustStore
import io.github.oshai.kotlinlogging.KotlinLogging
import io.ktor.client.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import kotlinx.serialization.json.*
import kotlin.time.Clock

private val log = KotlinLogging.logger { }

/**
 * JVM implementation of inline trust list resolution using waltid-trust-registry library.
 */
actual object ETSITrustListInlineResolver {
    
    actual suspend fun resolve(
        certificateChain: List<String>,
        trustLists: List<String>,
        expectedEntityType: String?,
        expectedServiceType: String?,
        allowStaleSource: Boolean,
        requireAuthenticated: Boolean,
        validateSignatures: Boolean
    ): Result<JsonElement> {
        // Create an ephemeral trust registry for this verification request
        val trustStore = InMemoryTrustStore()
        val trustService = DefaultTrustRegistryService(trustStore)
        
        // Load all trust lists
        for ((index, source) in trustLists.withIndex()) {
            val sourceId = "inline-source-$index"
            
            try {
                val result = if (isUrl(source)) {
                    log.debug { "Loading trust list from URL: $source" }
                    trustService.loadSourceFromUrl(
                        sourceId = sourceId,
                        url = source,
                        validateSignature = validateSignatures
                    )
                } else {
                    log.debug { "Loading trust list from inline content (${source.length} chars)" }
                    trustService.loadSourceFromContent(
                        sourceId = sourceId,
                        content = source,
                        sourceUrl = null,
                        validateSignature = validateSignatures
                    )
                }
                
                if (!result.success) {
                    log.warn { "Failed to load trust list source $sourceId: ${result.error}" }
                } else {
                    log.debug { "Loaded source $sourceId: ${result.entitiesLoaded} entities, ${result.servicesLoaded} services" }
                }
            } catch (e: Exception) {
                log.warn(e) { "Error loading trust list source $sourceId" }
            }
        }
        
        // Parse expected entity type
        val entityType = expectedEntityType?.let {
            runCatching { TrustedEntityType.valueOf(it) }.getOrNull()
        }
        
        // Resolve certificate chain
        for ((index, certPem) in certificateChain.withIndex()) {
            log.debug { "Checking certificate at index $index via inline resolution" }
            
            val decision = trustService.resolveByCertificate(
                certificatePemOrDer = certPem,
                instant = Clock.System.now(),
                expectedEntityType = entityType,
                expectedServiceType = expectedServiceType
            )
            
            val decisionCode = decision.decision.name
            
            if (decisionCode == "TRUSTED" || (decisionCode == "STALE_SOURCE" && allowStaleSource)) {
                log.debug { "Found trusted certificate at chain index $index" }
                return evaluateAndBuildResult(decision, allowStaleSource, requireAuthenticated)
            }
            
            log.debug { "Certificate at index $index not trusted (decision: $decisionCode)" }
        }
        
        return Result.failure(ETSITrustListPolicyException(
            "Certificate chain not trusted: no certificate in the chain (length: ${certificateChain.size}) " +
            "was found in the inline trust lists (${trustLists.size} sources loaded)"
        ))
    }
    
    private fun isUrl(source: String): Boolean {
        val trimmed = source.trim()
        return trimmed.startsWith("http://") || trimmed.startsWith("https://")
    }
    
    private fun evaluateAndBuildResult(
        decision: TrustDecision,
        allowStaleSource: Boolean,
        requireAuthenticated: Boolean
    ): Result<JsonElement> {
        val decisionCode = decision.decision.name
        val freshness = decision.sourceFreshness.name
        val authenticity = decision.authenticity.name
        
        return when (decisionCode) {
            "TRUSTED" -> {
                if (freshness == "STALE" && !allowStaleSource) {
                    Result.failure(ETSITrustListPolicyException(
                        "Trust source is stale (set allowStaleSource=true to allow)"
                    ))
                } else if (freshness == "EXPIRED") {
                    Result.failure(ETSITrustListPolicyException(
                        "Trust source has expired"
                    ))
                } else if (requireAuthenticated && authenticity != "VALIDATED") {
                    Result.failure(ETSITrustListPolicyException(
                        "Trust source authenticity not validated (got: $authenticity)"
                    ))
                } else {
                    Result.success(buildSuccessJson(decision))
                }
            }
            
            "STALE_SOURCE" -> {
                if (allowStaleSource) {
                    Result.success(buildSuccessJson(decision, warning = "Trust source is stale"))
                } else {
                    Result.failure(ETSITrustListPolicyException(
                        "Trust source is stale or expired"
                    ))
                }
            }
            
            else -> {
                Result.failure(ETSITrustListPolicyException(
                    "Certificate not trusted: $decisionCode"
                ))
            }
        }
    }
    
    private fun buildSuccessJson(decision: TrustDecision, warning: String? = null): JsonElement {
        return buildJsonObject {
            put("trusted", JsonPrimitive(true))
            put("decision", JsonPrimitive(decision.decision.name))
            decision.matchedEntity?.let { entity ->
                put("matchedEntity", buildJsonObject {
                    put("entityId", JsonPrimitive(entity.entityId))
                    put("entityType", JsonPrimitive(entity.entityType.name))
                    put("legalName", JsonPrimitive(entity.legalName))
                    entity.country?.let { put("country", JsonPrimitive(it)) }
                })
            }
            decision.matchedService?.let { service ->
                put("matchedService", buildJsonObject {
                    put("serviceId", JsonPrimitive(service.serviceId))
                    put("serviceType", JsonPrimitive(service.serviceType))
                    put("status", JsonPrimitive(service.status.name))
                })
            }
            decision.matchedSource?.let { source ->
                put("matchedSource", buildJsonObject {
                    put("sourceId", JsonPrimitive(source.sourceId))
                    put("sourceFamily", JsonPrimitive(source.sourceFamily.name))
                    put("displayName", JsonPrimitive(source.displayName))
                })
            }
            put("sourceFreshness", JsonPrimitive(decision.sourceFreshness.name))
            put("authenticity", JsonPrimitive(decision.authenticity.name))
            if (decision.warnings.isNotEmpty() || warning != null) {
                put("warnings", buildJsonArray {
                    warning?.let { add(JsonPrimitive(it)) }
                    decision.warnings.forEach { add(JsonPrimitive(it)) }
                })
            }
        }
    }
}
