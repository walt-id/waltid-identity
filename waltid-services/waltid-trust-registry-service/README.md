<div align="center">
 <h1>walt.id Trust Registry Service</h1>
 <span>by </span><a href="https://walt.id">walt.id</a>
 <p>REST API for trust list resolution and management</p>

<a href="https://walt.id/community">
<img src="https://img.shields.io/badge/Join-The Community-blue.svg?style=flat" alt="Join community!" />
</a>
<a href="https://www.linkedin.com/company/walt-id/">
<img src="https://img.shields.io/badge/-LinkedIn-0072b1?style=flat&logo=linkedin" alt="Follow walt_id" />
</a>
<a href="https://docs.walt.id">
<img src="https://img.shields.io/badge/Docs-docs.walt.id-blue?style=flat" alt="Documentation" />
</a>

  <h2>Status</h2>
  <p align="center">
    <img src="https://img.shields.io/badge/🟢%20Actively%20Maintained-success?style=for-the-badge&logo=check-circle" alt="Status: Actively Maintained" />
    <br/>
    <em>This project is being actively maintained by the development team at walt.id.<br />Regular updates, bug fixes, and new features are being added.</em>
  </p>
</div>

A Ktor-based REST service that exposes the [waltid-trust-registry](../../waltid-libraries/credentials/waltid-trust-registry/) library as an HTTP API. It provides trust resolution, source management, entity listing, and health monitoring for EU Trusted Lists (TSL) and EUDI Lists of Trusted Entities (LoTE).

---

## Overview

The Trust Registry Service wraps the core trust registry library in a deployable HTTP service that can:

- **Resolve trust** for certificates, public keys, and provider IDs via REST endpoints
- **Load and refresh** trust list sources (TSL XML, LoTE JSON/XML)
- **List trusted entities** with filtering by type, country, and source family
- **Report health** for all loaded trust sources (freshness, authenticity, entity counts)
- **Bootstrap** with pre-configured trust sources on startup

It integrates with the walt.id service commons framework for configuration, feature flags, and lifecycle management.

## Standards

| Standard | Description |
|----------|-------------|
| **ETSI TS 119 612 V2.4.1** | EU Trusted List format (TSL XML) |
| **ETSI TS 119 615 V1.3.1** | Procedures for EU Member State national trusted lists |
| **ETSI TS 119 602 V1.1.1** | Lists of Trusted Entities (LoTE) data model |
| **EU LOTL** | List of Trusted Lists (aggregated EU trust anchors) |
| **EUDI LoTE** | EUDI Wallet Lists of Trusted Entities |

## Architecture

```
                     ┌──────────────────────────────────┐
                     │         HTTP Clients              │
                     │  (Verifier, Issuer, Wallet, CLI)  │
                     └──────────────┬───────────────────┘
                                    │
                     ┌──────────────▼───────────────────┐
                     │    Trust Registry Service         │
                     │    (Ktor REST API)                │
                     │                                   │
                     │  /trust-registry/resolve/*        │
                     │  /trust-registry/sources/*        │
                     │  /trust-registry/entities         │
                     │  /trust-registry/health           │
                     └──────────────┬───────────────────┘
                                    │
                     ┌──────────────▼───────────────────┐
                     │    waltid-trust-registry          │
                     │    (Core Library)                 │
                     │                                   │
                     │  Parsers: TSL XML, LoTE JSON/XML  │
                     │  Store: InMemoryTrustStore        │
                     │  Service: TrustRegistryService    │
                     └──────────────────────────────────┘
```

## Quick Start

### Running from Source

From the unified build root (`waltid-unified-build`):

```bash
# Run the trust registry service
./gradlew :waltid-services:waltid-trust-registry-service:run
```

The service starts on the default port and exposes all endpoints under `/trust-registry/`.

### Running from IntelliJ

Open `waltid-trust-registry-service/src/main/kotlin/id/walt/trust/service/Main.kt` and run it directly.

---

## API Reference

All endpoints are prefixed with `/trust-registry`.

### Trust Resolution

#### Resolve by Certificate

Resolve whether a certificate is trusted, using either a PEM/DER-encoded certificate or a SHA-256 fingerprint.

```
POST /trust-registry/resolve/certificate
```

**Request body:**

```json
{
  "certificateSha256Hex": "9f3df3b70633c3d23f5ef04d5d1e7f1d715b9683d8744cd38ec1a8114ec99f00",
  "instant": "2026-06-01T00:00:00Z",
  "expectedEntityType": "WALLET_PROVIDER"
}
```

Or with a PEM certificate:

```json
{
  "certificatePemOrDer": "-----BEGIN CERTIFICATE-----\nMIIBkTCB+w...\n-----END CERTIFICATE-----",
  "instant": "2026-06-01T00:00:00Z"
}
```

**Response:**

```json
{
  "decision": "TRUSTED",
  "sourceFreshness": "FRESH",
  "authenticity": "SKIPPED_DEMO",
  "matchedSource": {
    "sourceId": "eu-wallets",
    "sourceFamily": "LOTE",
    "displayName": "EU Wallet Providers",
    "territory": "EU"
  },
  "matchedEntity": {
    "entityId": "AT-WALLET-001",
    "sourceId": "eu-wallets",
    "entityType": "WALLET_PROVIDER",
    "legalName": "Demo Wallet Provider GmbH",
    "country": "AT"
  },
  "matchedService": {
    "serviceId": "wallet-service",
    "serviceType": "WALLET_INSTANCE_ATTESTATION",
    "status": "GRANTED"
  },
  "evidence": [],
  "warnings": []
}
```

**curl example:**

```bash
curl -X POST http://localhost:7004/trust-registry/resolve/certificate \
  -H "Content-Type: application/json" \
  -d '{
    "certificateSha256Hex": "9f3df3b70633c3d23f5ef04d5d1e7f1d715b9683d8744cd38ec1a8114ec99f00",
    "instant": "2026-06-01T00:00:00Z",
    "expectedEntityType": "WALLET_PROVIDER"
  }'
```

#### Resolve by Public Key

```
POST /trust-registry/resolve/public-key
```

Resolves trust based on a public key. Currently returns the trust decision using the current timestamp.

```bash
curl -X POST http://localhost:7004/trust-registry/resolve/public-key \
  -H "Content-Type: application/json"
```

#### Resolve by Provider ID

Resolve whether a provider/entity is trusted by its identifier.

```
POST /trust-registry/resolve/provider-id
```

**Request body:**

```json
{
  "providerId": "AT-WALLET-001",
  "instant": "2026-06-01T00:00:00Z",
  "expectedEntityType": "WALLET_PROVIDER"
}
```

**curl example:**

```bash
curl -X POST http://localhost:7004/trust-registry/resolve/provider-id \
  -H "Content-Type: application/json" \
  -d '{
    "providerId": "AT-WALLET-001",
    "instant": "2026-06-01T00:00:00Z"
  }'
```

---

### Source Management

#### List Sources

List all loaded trust sources with their metadata.

```
GET /trust-registry/sources
```

**curl example:**

```bash
curl http://localhost:7004/trust-registry/sources
```

**Response:**

```json
[
  {
    "sourceId": "eu-wallets",
    "sourceFamily": "LOTE",
    "displayName": "EU Wallet Providers",
    "sourceUrl": "https://example.com/lote-wallets.json",
    "territory": "EU",
    "issueDate": "2026-01-01T00:00:00Z",
    "nextUpdate": "2026-07-01T00:00:00Z",
    "authenticityState": "SKIPPED_DEMO",
    "freshnessState": "FRESH"
  }
]
```

#### Load Source from Content

Load a trust source by providing its content directly (TSL XML, LoTE JSON, or LoTE XML).

```
POST /trust-registry/sources/load
```

**Request body:**

```json
{
  "sourceId": "my-trust-list",
  "content": "{ ... LoTE JSON or <?xml ... TSL XML ... }",
  "sourceUrl": "https://example.com/source.json"
}
```

**curl example (LoTE JSON):**

```bash
curl -X POST http://localhost:7004/trust-registry/sources/load \
  -H "Content-Type: application/json" \
  -d '{
    "sourceId": "eu-wallets",
    "content": "{\"listMetadata\":{\"listId\":\"demo\",\"territory\":\"EU\"},\"trustedEntities\":[]}",
    "sourceUrl": "https://example.com/wallets.json"
  }'
```

**Response:**

```json
{
  "sourceId": "eu-wallets",
  "success": true,
  "entitiesLoaded": 3,
  "servicesLoaded": 4,
  "identitiesLoaded": 4,
  "error": null
}
```

#### Refresh Source

Refresh an already loaded source (re-fetches from its registered URL).

```
POST /trust-registry/sources/{sourceId}/refresh
```

```bash
curl -X POST http://localhost:7004/trust-registry/sources/eu-wallets/refresh
```

---

### Entity Listing

#### List Trusted Entities

List all trusted entities, optionally filtered by query parameters.

```
GET /trust-registry/entities
```

**Query parameters:**

| Parameter | Type | Description |
|-----------|------|-------------|
| `sourceFamily` | `TSL` \| `LOTE` \| `PILOT` | Filter by trust source family |
| `entityType` | `TRUST_SERVICE_PROVIDER` \| `WALLET_PROVIDER` \| `PID_PROVIDER` \| `ATTESTATION_PROVIDER` \| `ACCESS_CERTIFICATE_PROVIDER` \| `RELYING_PARTY_PROVIDER` \| `OTHER` | Filter by entity type |
| `country` | string | Filter by ISO 3166-1 country code (e.g., `AT`, `DE`) |
| `onlyCurrentlyTrusted` | boolean | If `true`, only return entities with at least one active (granted/recognized) service |

**curl examples:**

```bash
# List all entities
curl http://localhost:7004/trust-registry/entities

# List only wallet providers
curl "http://localhost:7004/trust-registry/entities?entityType=WALLET_PROVIDER"

# List Austrian PID providers
curl "http://localhost:7004/trust-registry/entities?entityType=PID_PROVIDER&country=AT"

# List only currently trusted entities from LoTE sources
curl "http://localhost:7004/trust-registry/entities?sourceFamily=LOTE&onlyCurrentlyTrusted=true"
```

**Response:**

```json
[
  {
    "entityId": "AT-WALLET-001",
    "sourceId": "eu-wallets",
    "entityType": "WALLET_PROVIDER",
    "legalName": "Demo Wallet Provider GmbH",
    "tradeName": null,
    "registrationNumber": null,
    "country": "AT",
    "metadata": {}
  }
]
```

---

### Health Monitoring

#### Get Source Health

Returns health status for all loaded trust sources.

```
GET /trust-registry/health
```

```bash
curl http://localhost:7004/trust-registry/health
```

**Response:**

```json
[
  {
    "sourceId": "eu-wallets",
    "displayName": "EU Wallet Providers",
    "sourceFamily": "LOTE",
    "freshnessState": "FRESH",
    "authenticityState": "SKIPPED_DEMO",
    "nextUpdate": "2026-07-01T00:00:00Z",
    "entityCount": 3,
    "serviceCount": 4
  }
]
```

---

## Configuration

The service is configured through the walt.id service commons configuration system.

### Configuration File

`config/trust-registry.conf`:

```json
{
  "bootstrapSources": [
    {
      "sourceId": "eu-lotl",
      "url": "https://ec.europa.eu/tools/lotl/eu-lotl.xml",
      "sourceFamily": "TSL"
    },
    {
      "sourceId": "eudi-wallet-providers",
      "url": "https://example.com/eudi-wallet-providers.json",
      "sourceFamily": "LOTE"
    }
  ],
  "enableSyntheticSources": true,
  "failOnStaleSource": false,
  "allowDemoSkipSignatureValidation": true
}
```

### Configuration Options

| Option | Type | Default | Description |
|--------|------|---------|-------------|
| `bootstrapSources` | `List<BootstrapSource>` | `[]` | Sources to load on startup |
| `enableSyntheticSources` | `boolean` | `true` | Enable synthetic/demo sources for testing |
| `failOnStaleSource` | `boolean` | `false` | Fail trust resolution if source is stale |
| `allowDemoSkipSignatureValidation` | `boolean` | `true` | Skip signature validation in demo mode |

### Bootstrap Source

| Field | Type | Default | Description |
|-------|------|---------|-------------|
| `sourceId` | `string` | — | Unique identifier for this source |
| `url` | `string` | — | URL to fetch the trust list from |
| `sourceFamily` | `string` | `"LOTE"` | Source type: `TSL`, `LOTE`, or `PILOT` |

---

## Trust Decision Codes

The service returns one of these decision codes in trust resolution responses:

| Code | Meaning |
|------|---------|
| `TRUSTED` | Certificate/provider is trusted in a loaded source |
| `NOT_TRUSTED` | No matching trust entry found |
| `UNKNOWN` | Cannot determine trust (insufficient data) |
| `STALE_SOURCE` | Source has expired `nextUpdate` — decision may be outdated |
| `MULTIPLE_MATCHES` | Ambiguous result — multiple entities matched |
| `UNSUPPORTED_SOURCE` | Source format is not supported |
| `PROCESSING_ERROR` | Internal error during resolution |

---

## Integration with walt.id Services

The Trust Registry Service is designed to integrate with other walt.id services:

- **Verifier** — Verify that a credential issuer is listed as a trusted entity before accepting presentations
- **Issuer** — Validate trust anchors and certificate chains during issuance
- **Wallet** — Check issuer/verifier trust before accepting or presenting credentials

### Example: Verifier Integration

```kotlin
// In a verifier policy, resolve the issuer certificate against the trust registry
val response = httpClient.post("http://localhost:7004/trust-registry/resolve/certificate") {
    contentType(ContentType.Application.Json)
    setBody(ResolveCertificateRequest(
        certificateSha256Hex = issuerCertSha256,
        expectedEntityType = TrustedEntityType.PID_PROVIDER
    ))
}

val decision = response.body<TrustDecision>()
if (decision.decision != TrustDecisionCode.TRUSTED) {
    throw VerificationException("Issuer is not trusted: ${decision.decision}")
}
```

---

## Dependencies

| Dependency | Purpose |
|------------|---------|
| `waltid-trust-registry` | Core trust model, parsers, store, and resolution logic |
| `waltid-service-commons` | Service framework (config, feature flags, lifecycle) |
| Ktor Server (CIO) | HTTP server with content negotiation, CORS, logging |
| kotlinx.serialization | JSON serialization |
| kotlinx.datetime | Timestamp handling |

---

## Join the community

* Connect and get the latest updates: [Discord](https://discord.gg/AW8AgqJthZ) | [Newsletter](https://walt.id/newsletter) | [YouTube](https://www.youtube.com/channel/UCXfOzrv3PIvmur_CmwwmdLA) | [LinkedIn](https://www.linkedin.com/company/walt-id/)
* Get help, request features and report bugs: [GitHub Issues](https://github.com/walt-id/waltid-identity/issues)
* Find more in-depth documentation on our [docs site](https://docs.walt.id)


## License

Licensed under the [Apache License, Version 2.0](https://github.com/walt-id/waltid-identity/blob/main/LICENSE)

<div align="center">
<img src="../../assets/walt-banner.png" alt="walt.id banner" />
</div>
