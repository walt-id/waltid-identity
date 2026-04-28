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
import java.io.ByteArrayInputStream
import java.security.cert.CertificateFactory
import java.security.cert.X509Certificate
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
                // Security check: if the trusted certificate is not the leaf (index 0),
                // we must verify the chain from leaf to this trusted certificate
                if (index > 0) {
                    val chainValidation = validateCertificateChainToIndex(certificateChain, index)
                    if (!chainValidation.first) {
                        log.warn { "Certificate chain validation failed: ${chainValidation.second}" }
                        // Continue checking other certificates in the chain
                        continue
                    }
                    log.debug { "Certificate chain validated from leaf to trusted cert at index $index" }
                }
                
                log.debug { "Found trusted certificate at chain index $index" }
                return evaluateAndBuildResult(decision, allowStaleSource, requireAuthenticated)
            }
            
            log.debug { "Certificate at index $index not trusted (decision: $decisionCode)" }
        }
        
        return Result.failure(ETSITrustListPolicyException(
            "Certificate chain not trusted: no certificate in the chain (length: ${certificateChain.size}) " +
            "was found in the inline trust lists (${trustLists.size} sources loaded), or chain validation failed"
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

/**
 * JVM implementation of certificate chain validation.
 * Validates that each certificate in the chain [0..trustedIndex] is signed by the next certificate.
 */
actual fun validateCertificateChainToIndex(
    certificateChain: List<String>,
    trustedIndex: Int
): Pair<Boolean, String?> {
    if (trustedIndex == 0) {
        // The leaf certificate itself is trusted, no chain to validate
        return true to null
    }
    
    if (trustedIndex >= certificateChain.size) {
        return false to "Trusted index $trustedIndex is out of bounds (chain size: ${certificateChain.size})"
    }
    
    try {
        val certFactory = CertificateFactory.getInstance("X.509")
        
        // Parse all certificates in the chain up to and including the trusted one
        val certs = certificateChain.take(trustedIndex + 1).map { pem ->
            val pemContent = pem
                .replace("-----BEGIN CERTIFICATE-----", "")
                .replace("-----END CERTIFICATE-----", "")
                .replace("\\s".toRegex(), "")
            val derBytes = java.util.Base64.getDecoder().decode(pemContent)
            certFactory.generateCertificate(ByteArrayInputStream(derBytes)) as X509Certificate
        }
        
        // Validate the chain: each cert should be signed by the next one
        for (i in 0 until certs.size - 1) {
            val subject = certs[i]
            val issuer = certs[i + 1]
            
            try {
                // Verify that subject is signed by issuer's public key
                subject.verify(issuer.publicKey)
            } catch (e: Exception) {
                return false to "Certificate at index $i is not signed by certificate at index ${i + 1}: ${e.message}"
            }
            
            // Also verify issuer DN matches
            if (subject.issuerX500Principal != issuer.subjectX500Principal) {
                return false to "Certificate at index $i has issuer DN '${subject.issuerX500Principal}' " +
                        "but certificate at index ${i + 1} has subject DN '${issuer.subjectX500Principal}'"
            }
        }
        
        return true to null
    } catch (e: Exception) {
        return false to "Certificate chain validation error: ${e.message}"
    }
}
