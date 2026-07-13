# VP-Wallet Conformance Test Documentation

## Overview

This document covers **OpenID4VP Wallet** conformance testing — validating that our wallet correctly:
- Parses signed authorization requests (JAR)
- Encrypts VP responses (`direct_post.jwt`)
- Generates KB-JWT for SD-JWT VC holder binding
- Processes DCQL queries

> **Note:** For **Verifier** conformance testing, see [VP-VERIFIER.md](VP-VERIFIER.md).

---

## Current Status (2026-07-13)

| Component | Status | Notes |
|-----------|--------|-------|
| Test runner framework | ✅ Working | Creates plans, runs modules, reports results |
| Test plan creation | ✅ Working | Plans created with 12 modules via `/api/plan` |
| Wallet adapter | ✅ Working | HTTP bridge on port 7006 |
| **Wallet integration** | ⏳ **NOT VALIDATED** | Code implemented, tests not yet run |

---

## Quick Start

### Prerequisites

1. **Hosts entry**: Add to `/etc/hosts`:
   ```
   127.0.0.1 localhost.emobix.co.uk
   ```

2. **Conformance suite running**:
   ```bash
   cd ~/dev/openid/conformance-suite
   docker compose -f docker-compose-walt.yml up -d
   # Wait 30s, then verify:
   curl -k https://localhost.emobix.co.uk:8443/api/runner/available
   ```

3. **JVM truststore**: Configured automatically by `build.gradle.kts`

### Run Wallet Conformance Tests

**Step 1:** Start wallet-api2 (Terminal 1):
```bash
cd ~/dev/walt-id/waltid-unified-build
./gradlew :waltid-services:waltid-wallet-api2:run
```

**Step 2:** Run conformance tests (Terminal 2):
```bash
cd ~/dev/walt-id/waltid-unified-build
./gradlew :waltid-services:waltid-openid4vp-conformance-runners:test \
    --tests "VpWalletConformanceTests" --rerun-tasks
```

**Step 3:** View logs at https://localhost.emobix.co.uk:8443/logs.html

---

## Test Plans

### Available Test Plans

| Class | Client ID | HAIP Strict? | Use For |
|-------|-----------|--------------|---------|
| `VpWalletSdJwtVcX509HashRequestUriSignedDirectPostHaip` | `x509_hash` | ✅ Yes | **HAIP certification** |
| `VpWalletMdlX509HashRequestUriSignedDirectPostHaip` | `x509_hash` | ✅ Yes | **HAIP certification** |
| `VpWalletSdJwtVcX509SanDnsRequestUriSignedDirectPost` | `x509_san_dns` | ❌ Baseline | Development/debugging |
| `VpWalletMdlX509SanDnsRequestUriSignedDirectPost` | `x509_san_dns` | ❌ Baseline | Development/debugging |

**Note:** For official HAIP compliance (P-02), use the `x509_hash` variants.

### Switching Test Plans

Edit `VpWalletConformanceTests.kt` to use HAIP-strict plans:

```kotlin
val testPlans: List<WalletTestPlan> = listOf(
    VpWalletSdJwtVcX509HashRequestUriSignedDirectPostHaip(adapterUrl, conformanceHost, conformancePort),
    VpWalletMdlX509HashRequestUriSignedDirectPostHaip(adapterUrl, conformanceHost, conformancePort),
)
```

---

## Test Modules (12 total)

| Module | Type | HAIP Requirement |
|--------|------|------------------|
| `oid4vp-1final-wallet-happy-flow` | Happy path | Basic VP flow |
| `oid4vp-1final-wallet-alternate-happy-flow` | Happy path | Alternate request claims |
| `oid4vp-1final-wallet-request-uri-method-post` | Happy path | POST for `request_uri` |
| `oid4vp-1final-wallet-fewer-claims-than-available` | Happy path | Selective disclosure |
| `oid4vp-1final-wallet-optional-credential-set` | Happy path | Optional credentials |
| `oid4vp-1final-wallet-no-claims-in-dcql-query` | Happy path | DCQL without claims |
| `oid4vp-1final-wallet-negative-test-invalid-request-object-signature` | Negative | MUST reject bad signature |
| `oid4vp-1final-wallet-negative-test-mismatched-client-id` | Negative | MUST reject mismatch |
| `oid4vp-1final-wallet-negative-test-redirect-uri-with-direct-post` | Negative | MUST reject redirect_uri |
| `oid4vp-1final-wallet-negative-test-missing-nonce` | Negative | MUST reject missing nonce |
| `oid4vp-1final-wallet-negative-test-invalid-client-id-prefix` | Negative | MUST reject invalid prefix |
| `oid4vp-1final-wallet-negative-test-unknown-transaction-data-type` | Negative | MUST reject unknown type |

---

## HAIP Requirements Tested

| Req ID | Requirement | Covered? |
|--------|-------------|----------|
| P-02 | `x509_hash` client identification | ✅ Test variant uses `x509_hash` |
| W-27 | JAR with `request_uri` | ✅ All tests use `request_uri_signed` |
| W-28 | `direct_post.jwt` response mode | ✅ Tests encrypted responses |
| W-29 | Support same-device flow | ✅ Session-bound presentation |
| W-36 | KB-JWT holder binding | ✅ SD-JWT VC tests require KB-JWT |
| CF-02 | P-256 + SHA-256 (ES256) | ✅ Mandatory crypto |

---

## Architecture

```
┌─────────────────────────┐
│   Conformance Suite     │  (Acts as Verifier)
│   port 8443 (HTTPS)     │
└───────────┬─────────────┘
            │ 1. Authorization request
            ▼
┌─────────────────────────┐
│   Wallet Adapter        │  (HTTP bridge, started by test)
│   port 7006             │
└───────────┬─────────────┘
            │ 2. Forward to wallet
            ▼
┌─────────────────────────┐
│   Wallet API2           │  (Your wallet implementation)
│   port 7001             │
└───────────┬─────────────┘
            │ 3. VP response
            ▼
     Back to Conformance Suite
```

---

## Environment Variables

| Variable | Default | Description |
|----------|---------|-------------|
| `WALLET_API_URL` | `http://127.0.0.1:7001` | Wallet API base URL |
| `WALLET_ADAPTER_PORT` | `7006` | Adapter port |
| `CONFORMANCE_HOST` | `localhost.emobix.co.uk` | Suite hostname |
| `CONFORMANCE_PORT` | `8443` | Suite HTTPS port |

---

## Troubleshooting

### Tests show ERROR with JSON parsing

**Symptom:** `Unexpected 'null' value instead of string literal`

**Cause:** Conformance API response has nullable fields. Fixed in commit `60235fa74`.

**Fix:** Pull latest code and rebuild.

### Tests timeout on HTTP calls

**Symptom:** Tests hang for 60s then fail.

**Cause:** Usually conformance suite not reachable.

**Fix:**
```bash
# Check Docker
docker ps | grep conformance

# Check connectivity
curl -k https://localhost.emobix.co.uk:8443/api/runner/available
```

### All tests show INTERRUPTED

**Symptom:** Tests created but status is INTERRUPTED.

**Cause:** Wallet didn't respond in time (or at all).

**Fix:** Check wallet-api2 logs for errors processing the authorization request.

### No logs visible on conformance suite

**Symptom:** Tests run but nothing appears at logs.html.

**Fix:** Query the plan directly:
```bash
curl -sk "https://localhost.emobix.co.uk:8443/api/plan?length=5" | jq '.data[] | {name: .planName, id: ._id}'
```

---

## What the Wallet Must Implement

To pass these tests, the wallet needs:

1. **JAR parsing** (`request_uri` → fetch signed JWT → validate signature against `x5c`)
2. **JWE encryption** (`direct_post.jwt` → encrypt response with verifier's ephemeral key)
3. **KB-JWT generation** (for SD-JWT VC presentations, bind to holder key)
4. **DCQL processing** (parse Digital Credentials Query Language queries)

### WAL-896 Implementation Status

| Feature | File | Status |
|---------|------|--------|
| JWE Encryption | `ResponseEncryptionHandler.kt` | ✅ Implemented |
| JAR Validation | `SignedRequestValidator.kt` | ✅ Implemented |
| Wallet Metadata | `AuthorizationRequestResolver.kt` | ✅ Implemented |
| DCQL mdoc support | `ClaimsQuery.kt`, `DcqlMatcher.kt` | ✅ Implemented |

---

## Code Structure

```
waltid-openid4vp-conformance-runners/
├── docs/
│   ├── VP-WALLET.md                       # This file
│   └── VP-VERIFIER.md                     # Verifier conformance docs
├── src/main/kotlin/.../
│   ├── adapter/
│   │   └── VpWalletConformanceAdapter.kt  # HTTP bridge
│   ├── config/
│   │   └── ConformanceConfig.kt           # Environment config
│   └── testplans/
│       ├── runner/
│       │   └── WalletTestPlanRunner.kt    # Wallet test orchestration
│       └── plans/vp/wallet/
│           ├── WalletTestPlan.kt          # Base interface
│           └── Vp*.kt                     # Wallet test plans
└── src/test/kotlin/.../
    ├── IsolatedWalletConformanceTest.kt   # Framework validation (no wallet needed)
    └── VpWalletConformanceTests.kt        # Full wallet test suite
```

---

## References

- [OpenID4VP 1.0 Final Spec](https://openid.net/specs/openid-4-verifiable-presentations-1_0.html)
- [HAIP Profile](https://openid.net/specs/openid4vc-high-assurance-interoperability-profile-1_0-final.html)
- [Conformance Suite](https://openid.net/certification/faq/)
