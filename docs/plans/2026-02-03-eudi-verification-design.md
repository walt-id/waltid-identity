# EUDI Wallet Verification Flow Design

## Overview

OID4VP 1.0 verification flow in `waltid-verifier-api2` enabling EUDI Reference Wallet holders to present PID (mDoc or SD-JWT) and mDL credentials to relying parties.

### Key Capabilities

| Feature | Description |
|---------|-------------|
| Credential formats | PID mDoc, PID SD-JWT (dc+sd-jwt), mDL |
| Presentation flows | QR code (in-person) + deep link (online) |
| Claim selection | Relying party specifies which claims to request |
| Selective disclosure | Wallet reveals only requested claims |
| Protocol | OID4VP 1.0 with DCQL query language |

### User Journey (In-Person Example)

1. Relying party creates verification request via API (e.g., "verify age from PID")
2. Verifier displays QR code containing `openid4vp://` URI
3. User scans QR with EUDI wallet
4. Wallet shows consent screen: "Share birth_date with Acme Corp?"
5. User approves, wallet sends VP token to verifier
6. Verifier validates credential and returns result to relying party

### Out of Scope (For Now)

- Predicate proofs (age > 18 without revealing birth_date)
- Cross-device flows with push notifications
- Credential status checks during verification

## Architecture

```
┌─────────────────┐     ┌─────────────────────┐     ┌─────────────────┐
│  Relying Party  │────▶│  waltid-verifier-   │◀────│   EUDI Wallet   │
│  (Web Portal)   │     │      api2           │     │  (Android/iOS)  │
└─────────────────┘     └─────────────────────┘     └─────────────────┘
        │                        │                          │
        │ 1. Create request      │                          │
        │ 2. Get QR/deep link    │                          │
        │                        │ 3. Wallet fetches        │
        │                        │    request               │
        │                        │ 4. Wallet sends          │
        │                        │    VP token              │
        │ 5. Poll for result     │                          │
        └────────────────────────┘                          │
```

### Key Files to Create/Modify

| File | Purpose |
|------|---------|
| `EudiPresentationHandler.kt` | DCQL query builder for EUDI credential types |
| `MdocVerificationHandler.kt` | mDoc/COSE signature verification |
| `SdJwtVerificationHandler.kt` | SD-JWT (dc+sd-jwt) verification |
| `OID4VPApi.kt` | Endpoint updates for EUDI compatibility |

### Existing Infrastructure to Leverage

- `waltid-verifier-api2` already has OID4VP 1.0 core
- `waltid-openid4vp` library handles protocol flow
- DCQL query language support exists
- Session management and result polling already implemented

### New Work Required

- DCQL query templates for PID and mDL claim requests
- Format-specific credential validation (mDoc COSE, SD-JWT signatures)
- EUDI wallet response parsing (credentials array format)

## API Design

### Verification Request

```http
POST /openid4vc/verify
Content-Type: application/json

{
  "request_credentials": [
    {
      "format": "mso_mdoc",
      "doctype": "eu.europa.ec.eudi.pid.1",
      "claims": ["family_name", "given_name", "birth_date"]
    }
  ],
  "presentation_mode": "qr_code",
  "callback_url": "https://relying-party.com/callback"
}
```

### Response

```json
{
  "session_id": "abc123",
  "request_uri": "openid4vp://...",
  "qr_code_data": "openid4vp://...",
  "expires_in": 300
}
```

### DCQL Query Examples

**PID mDoc (age verification claims):**

```json
{
  "credentials": [{
    "id": "pid_mdoc",
    "format": "mso_mdoc",
    "meta": { "doctype_value": "eu.europa.ec.eudi.pid.1" },
    "claims": [
      { "namespace": "eu.europa.ec.eudi.pid.1", "claim_name": "birth_date" }
    ]
  }]
}
```

**PID SD-JWT:**

```json
{
  "credentials": [{
    "id": "pid_sdjwt",
    "format": "dc+sd-jwt",
    "meta": { "vct_values": ["urn:eudi:pid:1"] },
    "claims": [{ "path": ["birth_date"] }]
  }]
}
```

### Result Polling

```http
GET /openid4vc/session/{session_id}
```

Returns verification status, presented claims, and validation results.

## Credential Validation

### Validation Pipeline

Each presented credential goes through these checks:

| Step | mDoc | SD-JWT |
|------|------|--------|
| 1. Format parsing | CBOR decode | JWT parse + disclosures |
| 2. Signature verification | COSE Sign1 | JWS ES256 |
| 3. Issuer trust | Issuer certificate chain | DID resolution + key match |
| 4. Holder binding | Device key in MSO | cnf claim verification |
| 5. Claim extraction | Namespace/element lookup | Disclosed claim collection |

### mDoc Verification

```kotlin
class MdocVerificationHandler {
    fun verify(mdocResponse: ByteArray): VerificationResult {
        // 1. Parse DeviceResponse CBOR
        // 2. Extract IssuerSigned from Document
        // 3. Verify MSO (Mobile Security Object) signature
        // 4. Check validity period (validFrom/validUntil)
        // 5. Verify device signature if present
        // 6. Extract requested claims from namespaces
    }
}
```

### SD-JWT Verification

```kotlin
class SdJwtVerificationHandler {
    fun verify(sdJwtPresentation: String): VerificationResult {
        // 1. Split: jwt~disclosure1~disclosure2~...~kb_jwt
        // 2. Verify issuer JWT signature
        // 3. Check vct matches expected type
        // 4. Process disclosures, rebuild claims
        // 5. Verify key binding JWT if present
    }
}
```

### Error Responses

| Error | HTTP | Description |
|-------|------|-------------|
| `invalid_credential` | 400 | Signature or format invalid |
| `untrusted_issuer` | 400 | Issuer not in trust list |
| `expired_credential` | 400 | Credential past validity |
| `missing_claims` | 400 | Required claims not disclosed |

## Testing Strategy

### Unit Tests

Location: `waltid-verifier-api2/src/test/kotlin/id/walt/eudi/`

| Test file | Coverage |
|-----------|----------|
| `DcqlQueryBuilderTest.kt` | DCQL query generation for PID/mDL |
| `MdocVerificationTest.kt` | mDoc CBOR parsing, COSE signature validation |
| `SdJwtVerificationTest.kt` | SD-JWT parsing, disclosure processing, VCT matching |
| `EudiPresentationTest.kt` | Request/response format handling |

### E2E Tests

Location: `waltid-e2e-tests/src/test/kotlin/id/walt/eudi/`

| Test file | Scenario |
|-----------|----------|
| `EudiVerificationClient.kt` | Test client mimicking EUDI wallet VP flow |
| `EudiPidMdocVerifyE2ETest.kt` | Full flow: request → present → verify PID mDoc |
| `EudiPidSdJwtVerifyE2ETest.kt` | Full flow for PID SD-JWT |
| `EudiMdlVerifyE2ETest.kt` | Full flow for mDL verification |

### E2E Test Flow

1. Issue credential using existing `EudiWalletClient`
2. Create verification request via verifier API
3. Parse authorization request like wallet would
4. Build VP token with credential + holder binding
5. Submit to verifier response endpoint
6. Assert verification result and extracted claims

### Manual Wallet Testing Checklist

- [ ] QR code scan works from EUDI wallet
- [ ] Consent screen shows correct claims
- [ ] Verification succeeds after approval
- [ ] Portal displays verified claims correctly

## Implementation Sequence

### Phase 1: Core Verification Infrastructure

1. Add DCQL query builder for EUDI credential types
2. Implement mDoc verification handler (CBOR/COSE)
3. Implement SD-JWT verification handler (dc+sd-jwt)
4. Unit tests for all handlers

### Phase 2: API and Session Management

5. Update OID4VP endpoints for EUDI compatibility
6. Add presentation request builder (QR + deep link)
7. Wire up credential validation pipeline
8. Session result storage and polling

### Phase 3: E2E Testing

9. Create `EudiVerificationClient` (builds on issuance client)
10. E2E tests for all three credential formats
11. Integration with Web Portal for QR display

### Phase 4: Manual Validation

12. Test with real EUDI Reference Wallet
13. Verify both QR and deep link flows
14. Document any wallet-specific quirks

## Files to Create

```
waltid-services/waltid-verifier-api2/src/main/kotlin/id/walt/verifier/
├── eudi/
│   ├── DcqlQueryBuilder.kt
│   ├── MdocVerificationHandler.kt
│   └── SdJwtVerificationHandler.kt
└── oidc/
    └── (updates to existing OID4VP endpoints)

waltid-services/waltid-verifier-api2/src/test/kotlin/id/walt/eudi/
├── DcqlQueryBuilderTest.kt
├── MdocVerificationTest.kt
├── SdJwtVerificationTest.kt
└── EudiPresentationTest.kt

waltid-services/waltid-e2e-tests/src/test/kotlin/id/walt/eudi/
├── EudiVerificationClient.kt
├── EudiPidMdocVerifyE2ETest.kt
├── EudiPidSdJwtVerifyE2ETest.kt
└── EudiMdlVerifyE2ETest.kt
```
