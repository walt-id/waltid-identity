<div align="center">
    <h1>Kotlin Trust Registry Library</h1>
    <span>by </span><a href="https://walt.id">walt.id</a>
    <p>Parse, store, and query EU Trusted Lists and Lists of Trusted Entities</p>
    <a href="https://walt.id/community">
        <img src="https://img.shields.io/badge/Join-The Community-blue.svg?style=flat" alt="Join community!" />
    </a>
    <a href="https://www.linkedin.com/company/walt-id/">
        <img src="https://img.shields.io/badge/-LinkedIn-0072b1?style=flat&logo=linkedin" alt="Follow walt_id" />
    </a>
    <h2>Status</h2>
    <p align="center">
        <img src="https://img.shields.io/badge/🟢%20Actively%20Maintained-success?style=for-the-badge&logo=check-circle" alt="Status: Actively Maintained" />
        <br/>
        <em>This project is being actively maintained by the development team at walt.id.<br />Regular updates, bug fixes, and new features are being added.</em>
    </p>
</div>

A Kotlin/JVM library for parsing and querying trust registries, enabling trust-list support for verifier, issuer, and wallet flows. This library provides the core functionality for resolving whether a certificate, public key, or provider ID is trusted according to EU Trusted Lists (TSL) and EUDI Lists of Trusted Entities (LoTE).

## Features

- **Multi-format parsing** — Parse EU Trusted Lists (TSL XML), LoTE JSON, and LoTE XML formats
- **XMLDSig signature validation** — Validate enveloped XMLDSig signatures on TSL documents (JSR-105 API)
- **Unified trust model** — Normalize diverse trust list formats into a consistent data model
- **Certificate resolution** — Resolve trust status by certificate SHA-256, subject DN, or subject key identifier
- **Provider ID lookup** — Query trust status by entity/provider identifier
- **Entity type filtering** — Filter by entity types: Wallet Provider, PID Provider, Attestation Provider, Trust Service Provider, and more
- **Freshness tracking** — Automatic staleness detection based on `nextUpdate` timestamps
- **In-memory store** — Thread-safe in-memory implementation for MVP and demo use
- **Extensible architecture** — Interface-based design for enterprise persistence backends

## Standards

This library implements support for the following standards:

| Standard | Description |
|----------|-------------|
| **ETSI TS 119 612 V2.4.1** | EU Trusted List format (TSL) |
| **ETSI TS 119 615 V1.3.1** | Procedures for EU Member State national trusted lists |
| **ETSI TS 119 602 V1.1.1** | Lists of Trusted Entities (LoTE) data model |
| **EU LOTL** | List of Trusted Lists (aggregated EU trust anchors) |
| **EUDI LoTE** | EUDI Wallet Lists of Trusted Entities |

## Installation

Add the dependency to your `build.gradle.kts`:

```kotlin
repositories {
    maven("https://maven.waltid.dev/releases")
}

dependencies {
    implementation("id.walt:waltid-trust-registry:<version>")
}
```

## Usage

### Initialize the Trust Registry Service

```kotlin
import id.walt.trust.service.DefaultTrustRegistryService
import id.walt.trust.store.InMemoryTrustStore

// Create the service with an in-memory store
val store = InMemoryTrustStore()
val trustService = DefaultTrustRegistryService(store)
```

### Parse and Load EU Trusted Lists (TSL)

```kotlin
import id.walt.trust.model.SourceFamily
import id.walt.trust.parser.tsl.TslParseConfig

// Register a source URL for periodic refresh
trustService.registerSource(
    sourceId = "at-tsl",
    url = "https://ec.europa.eu/tools/lotl/eu-lotl.xml",
    sourceFamily = SourceFamily.TSL
)

// Refresh the source (fetches and parses)
val result = trustService.refreshSource("at-tsl")
println("Loaded ${result.entitiesLoaded} entities, ${result.servicesLoaded} services")

// Or load directly from content with signature validation
val tslXml = """..."""

// Configure signature validation (enabled by default)
val config = TslParseConfig(
    validateSignature = true,           // Enable XMLDSig validation
    strictSignatureValidation = false   // false = continue parsing on failure, true = throw
)

val loadResult = trustService.loadSourceFromContent("at-tsl", tslXml, "https://example.com/at-tsl.xml", config)
println("Authenticity: ${loadResult.source.authenticityState}")
// VALIDATED, FAILED, or SKIPPED_DEMO
```

### Validate TSL Signatures Directly

```kotlin
import id.walt.trust.signature.XmlDsigValidator
import id.walt.trust.signature.SignatureValidationConfig
import id.walt.trust.model.AuthenticityState

// Fetch and validate a trust list signature
val tslXml = fetchUrl("https://ec.europa.eu/tools/lotl/eu-lotl.xml")
val result = XmlDsigValidator.validate(tslXml)

when (result.state) {
    AuthenticityState.VALIDATED -> {
        println("✅ Signature is valid")
        println("Signer: ${result.signerCertificate?.subjectX500Principal}")
    }
    AuthenticityState.FAILED -> {
        println("❌ Signature validation failed: ${result.details}")
    }
    else -> println("Unexpected state: ${result.state}")
}

// With custom configuration
val config = SignatureValidationConfig(
    requireTrustedCertificate = true,
    trustedAnchors = setOf(euRootCert),
    allowExpiredCertificates = false,
    secureValidation = true
)
val customResult = XmlDsigValidator.validate(tslXml, config)
```

### Parse and Load LoTE Sources (JSON)

```kotlin
val loteJson = """
{
    "listMetadata": {
        "listId": "eu-wallet-providers",
        "listType": "WALLET_PROVIDERS",
        "territory": "EU",
        "issueDate": "2026-01-01T00:00:00Z",
        "nextUpdate": "2026-07-01T00:00:00Z"
    },
    "trustedEntities": [
        {
            "entityId": "AT-WALLET-001",
            "entityType": "WALLET_PROVIDER",
            "legalName": "Demo Wallet Provider GmbH",
            "country": "AT",
            "services": [
                {
                    "serviceId": "wallet-service",
                    "serviceType": "WALLET_INSTANCE_ATTESTATION",
                    "status": "GRANTED",
                    "identities": [
                        {
                            "matchType": "CERTIFICATE_SHA256",
                            "value": "9f3df3b70633c3d23f5ef04d5d1e7f1d715b9683d8744cd38ec1a8114ec99f00"
                        }
                    ]
                }
            ]
        }
    ]
}
"""

val result = trustService.loadSourceFromContent("eu-wallets", loteJson)
println("Success: ${result.success}, Entities: ${result.entitiesLoaded}")
```

### Parse LoTE Sources (XML)

```kotlin
val loteXml = """
<?xml version="1.0" encoding="UTF-8"?>
<ListOfTrustedEntities>
    <ListMetadata>
        <ListId>eu-pid-providers</ListId>
        <Territory>EU</Territory>
        <IssueDate>2026-01-01T00:00:00Z</IssueDate>
        <NextUpdate>2026-07-01T00:00:00Z</NextUpdate>
    </ListMetadata>
    <TrustedEntity>
        <EntityId>AT-PID-001</EntityId>
        <EntityType>PID_PROVIDER</EntityType>
        <LegalName>Demo PID Provider</LegalName>
        <Country>AT</Country>
        <TrustedService>
            <ServiceId>pid-issuance</ServiceId>
            <ServiceType>PID_PROVIDER_ACCESS</ServiceType>
            <Status>GRANTED</Status>
            <Identity>
                <MatchType>CERTIFICATE_SHA256</MatchType>
                <Value>2f18886f6fd62dfd0f9015ec6b7b0af2870d3f6070c810f545f8d85f37eb8d11</Value>
            </Identity>
        </TrustedService>
    </TrustedEntity>
</ListOfTrustedEntities>
"""

val result = trustService.loadSourceFromContent("eu-pid", loteXml)
```

### Resolve Trust for a Certificate

```kotlin
import id.walt.trust.model.TrustDecisionCode
import id.walt.trust.model.TrustedEntityType
import kotlinx.datetime.Clock

// Resolve by certificate SHA-256 fingerprint
val decision = trustService.resolveByCertificateSha256(
    sha256Hex = "9f3df3b70633c3d23f5ef04d5d1e7f1d715b9683d8744cd38ec1a8114ec99f00",
    instant = Clock.System.now(),
    expectedEntityType = TrustedEntityType.WALLET_PROVIDER  // Optional filter
)

when (decision.decision) {
    TrustDecisionCode.TRUSTED -> {
        println("Certificate is trusted!")
        println("Entity: ${decision.matchedEntity?.legalName}")
        println("Service status: ${decision.matchedService?.status}")
    }
    TrustDecisionCode.NOT_TRUSTED -> {
        println("Certificate is NOT trusted")
        decision.evidence.forEach { println("  - ${it.type}: ${it.value}") }
    }
    TrustDecisionCode.STALE_SOURCE -> {
        println("Source is stale/expired - trust decision may be outdated")
    }
    TrustDecisionCode.MULTIPLE_MATCHES -> {
        println("Multiple entities matched - ambiguous result")
    }
    else -> println("Unknown/error: ${decision.decision}")
}

// Resolve by raw certificate (PEM or base64 DER)
val pemCertificate = """
-----BEGIN CERTIFICATE-----
MIIBkTCB+wIJAKHBfpE...
-----END CERTIFICATE-----
"""
val certDecision = trustService.resolveByCertificate(
    certificatePemOrDer = pemCertificate,
    instant = Clock.System.now()
)
```

### Resolve Trust by Provider ID

```kotlin
val decision = trustService.resolveByProviderId(
    providerId = "AT-WALLET-001",
    instant = Clock.System.now(),
    expectedEntityType = TrustedEntityType.WALLET_PROVIDER
)

if (decision.decision == TrustDecisionCode.TRUSTED) {
    println("Provider ${decision.matchedEntity?.legalName} is trusted")
    println("Country: ${decision.matchedEntity?.country}")
}
```

### Query the Trust Store

```kotlin
import id.walt.trust.model.EntityFilter
import id.walt.trust.model.SourceFamily

// List all trusted entities
val allEntities = trustService.listTrustedEntities()

// Filter by entity type
val walletProviders = trustService.listTrustedEntities(
    EntityFilter(entityType = TrustedEntityType.WALLET_PROVIDER)
)

// Filter by country
val austrianEntities = trustService.listTrustedEntities(
    EntityFilter(country = "AT")
)

// Filter by source family
val tslEntities = trustService.listTrustedEntities(
    EntityFilter(sourceFamily = SourceFamily.TSL)
)

// Filter only currently trusted (active status)
val activeOnly = trustService.listTrustedEntities(
    EntityFilter(onlyCurrentlyTrusted = true)
)

// Combine filters
val activePidProvidersInAT = trustService.listTrustedEntities(
    EntityFilter(
        entityType = TrustedEntityType.PID_PROVIDER,
        country = "AT",
        onlyCurrentlyTrusted = true
    )
)
```

### Check Source Health

```kotlin
// Get health status for all loaded sources
val healthReport = trustService.getSourceHealth()

healthReport.forEach { health ->
    println("Source: ${health.displayName} (${health.sourceId})")
    println("  Family: ${health.sourceFamily}")
    println("  Freshness: ${health.freshnessState}")
    println("  Authenticity: ${health.authenticityState}")
    println("  Next Update: ${health.nextUpdate}")
    println("  Entities: ${health.entityCount}")
    println("  Services: ${health.serviceCount}")
}

// List all registered sources
val sources = trustService.listSources()
sources.forEach { source ->
    println("${source.displayName}: ${source.territory} - ${source.freshnessState}")
}
```

## Architecture

The library follows a layered architecture:

```
┌─────────────────────────────────────────────────────────────┐
│                    TrustRegistryService                     │
│  (resolve trust, list entities, manage sources)             │
├─────────────────────────────────────────────────────────────┤
│                        TrustStore                           │
│  (storage abstraction - InMemoryTrustStore for MVP)         │
├─────────────────────────────────────────────────────────────┤
│                         Parsers                             │
│  ┌─────────────┐  ┌──────────────┐  ┌──────────────┐       │
│  │ TslXmlParser│  │LoteJsonParser│  │ LoteXmlParser│       │
│  │   (TSL)     │  │   (LoTE)     │  │   (LoTE)     │       │
│  └─────────────┘  └──────────────┘  └──────────────┘       │
│        │                                                    │
│        ▼                                                    │
│  ┌──────────────────┐                                       │
│  │ XmlDsigValidator │  ← Validates TSL XMLDSig signatures   │
│  │  (JSR-105 API)   │                                       │
│  └──────────────────┘                                       │
├─────────────────────────────────────────────────────────────┤
│                      Trust Model                            │
│  TrustSource, TrustedEntity, TrustedService,               │
│  ServiceIdentity, TrustDecision, EntityFilter              │
└─────────────────────────────────────────────────────────────┘
```

### Key Types

| Type | Description |
|------|-------------|
| `TrustSource` | Metadata about a trust list (URL, territory, freshness, timestamps) |
| `TrustedEntity` | An organization in the trust list (TSP, Wallet Provider, PID Provider, etc.) |
| `TrustedService` | A service offered by an entity with its trust status |
| `ServiceIdentity` | Cryptographic identifiers (cert SHA-256, subject DN, SKI) for matching |
| `TrustDecision` | Result of a trust resolution query with evidence and warnings |
| `EntityFilter` | Query filter for listing entities |
| `SignatureValidationResult` | Result of XMLDSig signature validation |
| `TrustSourceHealth` | Health metrics for a loaded source |

### Trust Status Values

| Status | Description |
|--------|-------------|
| `GRANTED` | Service is currently trusted |
| `RECOGNIZED` | Service is recognized under mutual recognition |
| `ACCREDITED` | Service is accredited |
| `SUPERVISED` | Service is under supervision |
| `SUSPENDED` | Service is temporarily suspended |
| `REVOKED` | Service trust has been revoked |
| `WITHDRAWN` | Service has been withdrawn |
| `DEPRECATED` | Service is deprecated |
| `EXPIRED` | Service trust has expired |

### Authenticity States

| State | Description |
|-------|-------------|
| `VALIDATED` | XMLDSig signature verified successfully |
| `FAILED` | Signature validation failed or signature missing |
| `SKIPPED_DEMO` | Signature validation was disabled |
| `UNKNOWN` | Authenticity not determined |

### Entity Types

| Type | Description |
|------|-------------|
| `TRUST_SERVICE_PROVIDER` | Traditional qualified trust service provider |
| `WALLET_PROVIDER` | EUDI Wallet provider |
| `PID_PROVIDER` | Person Identification Data provider |
| `ATTESTATION_PROVIDER` | Attestation provider |
| `ACCESS_CERTIFICATE_PROVIDER` | Access certificate provider |
| `RELYING_PARTY_PROVIDER` | Relying party provider |

## Related Modules

- **waltid-trust-registry-service** — Ktor-based REST API exposing trust registry functionality

## Join the community

* Connect and get the latest updates: [Discord](https://discord.gg/AW8AgqJthZ) | [Newsletter](https://walt.id/newsletter) | [YouTube](https://www.youtube.com/channel/UCXfOzrv3PIvmur_CmwwmdLA) | [LinkedIn](https://www.linkedin.com/company/walt-id/)
* Get help, request features and report bugs: [GitHub Issues](https://github.com/walt-id/waltid-identity/issues)
* Find more in-depth documentation on our [docs site](https://docs.walt.id)

## License

Licensed under the [Apache License, Version 2.0](https://github.com/walt-id/waltid-identity/blob/main/LICENSE)

<div align="center">
<img src="../../../assets/walt-banner.png" alt="walt.id banner" />
</div>
