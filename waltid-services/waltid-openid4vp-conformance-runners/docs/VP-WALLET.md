# VP Wallet Conformance Tests

Complete guide for running OpenID4VP Wallet conformance tests against the OpenID Foundation conformance suite.

## Current Status (2026-07-09)

| Component | Status | Notes |
|-----------|--------|-------|
| Test runner framework | ✅ Working | Creates plans, runs modules, reports results |
| Test plan creation | ✅ Working | Plans created with 12 modules via `/api/plan` |
| Test module execution | ✅ Working | Uses `/api/runner` endpoint (same as verifier) |
| Conformance logs | ✅ Working | Tests visible at `logs.html` |
| Wallet integration | ⏳ Pending | **Awaiting wallet-side HAIP implementation** |

**What the test runner does:**
1. Creates test plan on conformance suite
2. Starts wallet adapter (HTTP bridge on port 7006)
3. For each module: creates test instance, waits for WAITING state, triggers wallet, waits for result
4. Reports pass/fail summary

**What the wallet needs to implement (WAL-896):**
- JAR (JWT-Secured Authorization Request) parsing
- JWE response encryption (`direct_post.jwt` response mode)
- KB-JWT (Key Binding JWT) generation for SD-JWT VC
- DCQL (Digital Credentials Query Language) processing

---

## HAIP Test Coverage

### Test Modules (12 total)

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

### HAIP Requirements Tested

| Req ID | Requirement | Covered? |
|--------|-------------|----------|
| P-02 | `x509_hash` client identification | ✅ Test variant uses `x509_hash` |
| W-27 | JAR with `request_uri` | ✅ All tests use `request_uri_signed` |
| W-28 | `direct_post.jwt` response mode | ✅ Tests encrypted responses |
| W-29 | Support same-device flow | ✅ Session-bound presentation |
| W-36 | KB-JWT holder binding | ✅ SD-JWT VC tests require KB-JWT |
| CF-02 | P-256 + SHA-256 (ES256) | ✅ Mandatory crypto |

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

### Run Tests (Without Wallet)

This validates the test runner framework (creates plans, doesn't need wallet):

```bash
cd ~/dev/walt-id/waltid-unified-build

./gradlew :waltid-services:waltid-openid4vp-conformance-runners:test \
    --tests "IsolatedWalletConformanceTest" --rerun-tasks
```

### Run Full Tests (With Wallet)

**Step 1:** Start wallet-api2 (new terminal):
```bash
./gradlew :waltid-services:waltid-wallet-api2:run
```

**Step 2:** Run conformance tests (new terminal):
```bash
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

### No logs visible on conformance suite

**Symptom:** Tests run but nothing appears at logs.html.

**Cause:** Test plans set `"publish": "everything"` so logs should appear.

**Fix:** Query the plan directly:
```bash
curl -sk "https://localhost.emobix.co.uk:8443/api/plan?length=5" | jq '.data[] | {name: .planName, id: ._id}'
```

### All tests show INTERRUPTED

**Symptom:** Tests created but status is INTERRUPTED.

**Cause:** Wallet didn't respond in time (or at all).

**Fix:** This is expected until wallet implements WAL-896 features.

---

## Code Structure

```
waltid-openid4vp-conformance-runners/
├── docs/
│   └── VP-WALLET.md                       # This file
├── src/main/kotlin/.../
│   ├── adapter/
│   │   └── VpWalletConformanceAdapter.kt  # HTTP bridge
│   ├── config/
│   │   └── ConformanceConfig.kt           # Environment config
│   └── testplans/
│       ├── http/ConformanceInterface.kt   # Conformance API client
│       ├── httpdata/*.kt                  # API response DTOs
│       ├── runner/WalletTestPlanRunner.kt # Test orchestration
│       └── plans/vp/wallet/
│           ├── WalletTestPlan.kt          # Base interface
│           ├── Vp*X509Hash*.kt            # HAIP-strict plans
│           └── Vp*X509SanDns*.kt          # Baseline plans
└── src/test/kotlin/.../
    ├── IsolatedWalletConformanceTest.kt   # Framework validation
    └── VpWalletConformanceTests.kt        # Full test suite
```

---

## Handover to Wallet Team

The conformance test runner is ready. To complete HAIP compliance, the wallet team needs to implement:

1. **JAR parsing** (`request_uri` → fetch signed JWT → validate signature against `x5c`)
2. **JWE encryption** (`direct_post.jwt` → encrypt response with verifier's ephemeral key)
3. **KB-JWT generation** (for SD-JWT VC presentations, bind to holder key)
4. **DCQL processing** (parse Digital Credentials Query Language queries)

Once implemented, run:
```bash
./gradlew :waltid-services:waltid-openid4vp-conformance-runners:test \
    --tests "VpWalletConformanceTests" --rerun-tasks
```

All 12 modules should pass for HAIP certification.
