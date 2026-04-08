package id.walt.trust.service.openapi

import id.walt.trust.model.*
import id.walt.trust.service.routes.LoadSourceRequest
import id.walt.trust.service.routes.ResolveCertificateRequest
import id.walt.trust.service.routes.ResolveProviderRequest
import kotlin.time.Instant

/**
 * OpenAPI example objects for the Trust Registry Service.
 */
object TrustRegistryExamples {

    // ---------------------------------------------------------------------------
    // Resolve requests
    // ---------------------------------------------------------------------------

    val resolveBySha256 = ResolveCertificateRequest(
        certificateSha256Hex = "9f3df3b70633c3d23f5ef04d5d1e7f1d715b9683d8744cd38ec1a8114ec99f00",
        instant = "2026-06-01T00:00:00Z",
    )

    val resolveByPem = ResolveCertificateRequest(
        certificatePemOrDer = "MIIBkTCB+wIJAKHBfpEaYDcxMA0GCSqGSIb3DQEBCwUA...",
        instant = "2026-06-01T00:00:00Z",
    )

    val resolveWithEntityFilter = ResolveCertificateRequest(
        certificateSha256Hex = "9f3df3b70633c3d23f5ef04d5d1e7f1d715b9683d8744cd38ec1a8114ec99f00",
        instant = "2026-06-01T00:00:00Z",
        expectedEntityType = TrustedEntityType.WALLET_PROVIDER,
        expectedServiceType = "WALLET_INSTANCE_ATTESTATION",
    )

    val resolveProviderSimple = ResolveProviderRequest(
        providerId = "AT-WALLET-001",
        instant = "2026-06-01T00:00:00Z",
    )

    val resolveProviderFiltered = ResolveProviderRequest(
        providerId = "AT-PID-001",
        instant = "2026-06-01T00:00:00Z",
        expectedEntityType = TrustedEntityType.PID_PROVIDER,
    )

    // ---------------------------------------------------------------------------
    // Trust decisions
    // ---------------------------------------------------------------------------

    val trustDecisionTrusted = TrustDecision(
        decision = TrustDecisionCode.TRUSTED,
        sourceFreshness = FreshnessState.FRESH,
        authenticity = AuthenticityState.SKIPPED_DEMO,
        matchedSource = TrustSource(
            sourceId = "eu-wallets",
            sourceFamily = SourceFamily.LOTE,
            displayName = "EU Wallet Providers",
            sourceUrl = "https://trust.example.eu/wallet-providers.json",
            territory = "EU",
            issueDate = Instant.parse("2026-01-01T00:00:00Z"),
            nextUpdate = Instant.parse("2026-07-01T00:00:00Z"),
            authenticityState = AuthenticityState.SKIPPED_DEMO,
            freshnessState = FreshnessState.FRESH,
        ),
        matchedEntity = TrustedEntity(
            entityId = "AT-WALLET-001",
            sourceId = "eu-wallets",
            entityType = TrustedEntityType.WALLET_PROVIDER,
            legalName = "Demo Wallet Provider GmbH",
            country = "AT",
        ),
        matchedService = TrustedService(
            serviceId = "wallet-service",
            sourceId = "eu-wallets",
            entityId = "AT-WALLET-001",
            serviceType = "WALLET_INSTANCE_ATTESTATION",
            status = TrustStatus.GRANTED,
            statusStart = Instant.parse("2026-01-01T00:00:00Z"),
        ),
    )

    val trustDecisionNotTrusted = TrustDecision(
        decision = TrustDecisionCode.NOT_TRUSTED,
        sourceFreshness = FreshnessState.FRESH,
        authenticity = AuthenticityState.SKIPPED_DEMO,
        warnings = listOf("No matching identity found across 2 loaded sources"),
    )

    val trustDecisionStale = TrustDecision(
        decision = TrustDecisionCode.STALE_SOURCE,
        sourceFreshness = FreshnessState.EXPIRED,
        authenticity = AuthenticityState.UNKNOWN,
        warnings = listOf("Source 'eu-wallets' has expired nextUpdate: 2025-12-31T00:00:00Z"),
    )

    // ---------------------------------------------------------------------------
    // Source management
    // ---------------------------------------------------------------------------

    val loadSourceLoteJson = LoadSourceRequest(
        sourceId = "eu-wallet-providers",
        content = """{"listMetadata":{"listId":"eu-wallet-providers","territory":"EU"},"trustedEntities":[]}""",
        sourceUrl = "https://trust.example.eu/wallet-providers.json",
    )

    val loadSourceTslXml = LoadSourceRequest(
        sourceId = "at-national-tl",
        content = """<?xml version="1.0"?><TrustServiceStatusList xmlns="http://uri.etsi.org/02231/v2#"><SchemeInformation><SchemeTerritory>AT</SchemeTerritory></SchemeInformation></TrustServiceStatusList>""",
        sourceUrl = "https://ec.europa.eu/tools/lotl/at-tl.xml",
    )

    val refreshResultSuccess = RefreshResult(
        sourceId = "eu-wallet-providers",
        success = true,
        entitiesLoaded = 3,
        servicesLoaded = 4,
        identitiesLoaded = 4,
    )

    val refreshResultFailure = RefreshResult(
        sourceId = "broken-source",
        success = false,
        error = "Failed to parse content: unexpected token at position 42",
    )

    val sourcesList = listOf(
        TrustSource(
            sourceId = "eu-wallets",
            sourceFamily = SourceFamily.LOTE,
            displayName = "EU Wallet Providers",
            sourceUrl = "https://trust.example.eu/wallet-providers.json",
            territory = "EU",
            issueDate = Instant.parse("2026-01-01T00:00:00Z"),
            nextUpdate = Instant.parse("2026-07-01T00:00:00Z"),
            authenticityState = AuthenticityState.SKIPPED_DEMO,
            freshnessState = FreshnessState.FRESH,
        ),
        TrustSource(
            sourceId = "at-national-tl",
            sourceFamily = SourceFamily.TSL,
            displayName = "Austria National Trusted List",
            sourceUrl = "https://ec.europa.eu/tools/lotl/at-tl.xml",
            territory = "AT",
            issueDate = Instant.parse("2026-01-15T00:00:00Z"),
            nextUpdate = Instant.parse("2026-07-15T00:00:00Z"),
            authenticityState = AuthenticityState.SKIPPED_DEMO,
            freshnessState = FreshnessState.FRESH,
        ),
    )

    // ---------------------------------------------------------------------------
    // Entities
    // ---------------------------------------------------------------------------

    val entitiesList = listOf(
        TrustedEntity(
            entityId = "AT-WALLET-001",
            sourceId = "eu-wallets",
            entityType = TrustedEntityType.WALLET_PROVIDER,
            legalName = "Demo Wallet Provider GmbH",
            country = "AT",
        ),
        TrustedEntity(
            entityId = "DE-WALLET-002",
            sourceId = "eu-wallets",
            entityType = TrustedEntityType.WALLET_PROVIDER,
            legalName = "Beispiel Wallet AG",
            country = "DE",
        ),
        TrustedEntity(
            entityId = "AT-PID-001",
            sourceId = "eu-wallets",
            entityType = TrustedEntityType.PID_PROVIDER,
            legalName = "Demo PID Provider",
            country = "AT",
        ),
    )

    // ---------------------------------------------------------------------------
    // Health
    // ---------------------------------------------------------------------------

    val healthResponse = listOf(
        TrustSourceHealth(
            sourceId = "eu-wallets",
            displayName = "EU Wallet Providers",
            sourceFamily = SourceFamily.LOTE,
            freshnessState = FreshnessState.FRESH,
            authenticityState = AuthenticityState.SKIPPED_DEMO,
            nextUpdate = Instant.parse("2026-07-01T00:00:00Z"),
            entityCount = 3,
            serviceCount = 4,
        ),
        TrustSourceHealth(
            sourceId = "at-national-tl",
            displayName = "Austria National Trusted List",
            sourceFamily = SourceFamily.TSL,
            freshnessState = FreshnessState.FRESH,
            authenticityState = AuthenticityState.SKIPPED_DEMO,
            nextUpdate = Instant.parse("2026-07-15T00:00:00Z"),
            entityCount = 12,
            serviceCount = 28,
        ),
    )
}
