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
| Test plan creation | ✅ Working | Plans created with modules via `/api/plan` |
| Test plan configuration | ✅ Fixed | JWKS, DCQL, trust anchors now included |
| Wallet adapter | ✅ Working | HTTP bridge on port 7006 |
| **Wallet integration** | ⏳ **PENDING VALIDATION** | Config fixed, awaiting test run |

### Configuration Fixes Applied (Jul 13)

The following issues were identified and fixed during testing:

| Issue | Fix | Commit |
|-------|-----|--------|
| Missing `client.jwks` | Added JWKS with x5c to wallet test plans | `c10a773fd` |
| Variant conflict | Removed `client_id_prefix` from variant (plan defines it) | `ed740fb84` |
| Wrapped config | Send configuration directly without `{"configuration":...}` | `0fffcd212` |
| Missing DCQL/trust anchors | Added `client.dcql`, `credential.trust_anchor` | `7afae19fa` |

**Next Step:** Re-run wallet conformance tests with fixed configuration:
```bash
./gradlew :waltid-services:waltid-openid4vp-conformance-runners:test \
    --tests "VpWalletConformanceTests" --rerun-tasks
```

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

3. **SSL Trust Configuration**: The conformance suite uses a self-signed certificate.
   The JDK must trust it for the wallet to connect.
   
   **Add conformance suite certificate to JDK truststore:**
   ```bash
   # Extract the certificate
   echo | openssl s_client -connect localhost.emobix.co.uk:8443 \
       -servername localhost.emobix.co.uk 2>/dev/null | \
       openssl x509 -out /tmp/conformance-suite.crt
   
   # Add to JDK 21 truststore (adjust path if needed)
   JAVA_HOME=/home/pp/.gradle/jdks/eclipse_adoptium-21-amd64-linux.2
   $JAVA_HOME/bin/keytool -import -trustcacerts \
       -alias conformance-suite \
       -file /tmp/conformance-suite.crt \
       -keystore $JAVA_HOME/lib/security/cacerts \
       -storepass changeit -noprompt
   ```

### Run Wallet Conformance Tests

The wallet conformance tests require multiple components running:

**Terminal 1 — Wallet API:**
```bash
cd ~/dev/walt-id/waltid-unified-build
./gradlew :waltid-services:waltid-wallet-api2:run
# Runs on port 7005 (default for wallet-api2)
```

**Terminal 2 — Issuer API (for test credentials):**
```bash
cd ~/dev/walt-id/waltid-unified-build
./gradlew :waltid-services:waltid-issuer-api2:run
# Runs on port 7002
```

**Terminal 3 — Set up test wallet and credentials:**
```bash
# Create a wallet with credential store
WALLET_ID=$(curl -s -X POST "http://127.0.0.1:7005/wallet" \
    -H "Content-Type: application/json" \
    -d '{"credentialStoreIds": ["conformance-store"]}' | jq -r '.walletId')
echo "Created wallet: $WALLET_ID"

# Create a DID for the wallet
curl -s -X POST "http://127.0.0.1:7005/wallet/$WALLET_ID/dids/create" \
    -H "Content-Type: application/json" \
    -d '{"method": "key"}' | jq '.did'

# Create a credential offer from issuer
OFFER=$(curl -s -X POST "http://127.0.0.1:7002/issuer2/credential-offers" \
    -H "Content-Type: application/json" \
    -d '{
      "profileId": "identityCredentialSdJwt",
      "authMethod": "PRE_AUTHORIZED",
      "valueMode": "BY_VALUE",
      "expiresInSeconds": 3600
    }' | jq -r '.credentialOffer')
echo "Credential offer created"

# Receive credential in wallet
curl -s -X POST "http://127.0.0.1:7005/wallet/$WALLET_ID/credentials/receive" \
    -H "Content-Type: application/json" \
    -d "{\"offerUrl\": \"$OFFER\"}" | jq '.'
echo "Credential received!"

# Verify credential is in wallet
curl -s "http://127.0.0.1:7005/wallet/$WALLET_ID/credentials" | jq '.[].format'
```

**Terminal 4 — Run conformance tests:**
```bash
cd ~/dev/walt-id/waltid-unified-build

# Set wallet ID if not using auto-discovery
export CONFORMANCE_WALLET_ID=$WALLET_ID

# Run tests
./gradlew :waltid-services:waltid-openid4vp-conformance-runners:test \
    --tests "VpWalletConformanceTests" --rerun-tasks
```

**Step 5:** View logs at https://localhost.emobix.co.uk:8443/logs.html

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

### Test Plan Configuration

Each wallet test plan now includes the required HAIP configuration:

```json
{
  "client": {
    "client_id_scheme": "x509_hash",
    "jwks": { "keys": [{ "kty": "EC", "x5c": ["...leaf...", "...ca..."], ... }] },
    "dcql": {
      "credentials": [
        { "id": "pid", "format": "dc+sd-jwt", "claims": [...] }
      ]
    }
  },
  "credential": {
    "trust_anchor": "...base64 cert...",
    "status_list_trust_anchor": "...base64 cert..."
  }
}
```

### Switching Test Plans

Edit `VpWalletConformanceTests.kt` to use HAIP-strict plans:

```kotlin
val testPlans: List<WalletTestPlan> = listOf(
    VpWalletSdJwtVcX509HashRequestUriSignedDirectPostHaip(adapterUrl, conformanceHost, conformancePort),
    VpWalletMdlX509HashRequestUriSignedDirectPostHaip(adapterUrl, conformanceHost, conformancePort),
)
```

---

## Test Modules

The HAIP wallet test plan includes the following modules:

### Happy Path Tests

| Module | Description |
|--------|-------------|
| `oid4vp-1final-wallet-happy-flow` | Baseline VP flow |
| `oid4vp-1final-wallet-alternate-happy-flow` | Alternate request claims, longer nonce |
| `oid4vp-1final-wallet-request-uri-method-post` | POST method for `request_uri` |
| `oid4vp-1final-wallet-fewer-claims-than-available` | Selective disclosure |
| `oid4vp-1final-wallet-optional-credential-set` | Optional credentials |
| `oid4vp-1final-wallet-no-claims-in-dcql-query` | DCQL without claims |

### Negative Tests (Wallet MUST Reject)

| Module | Description |
|--------|-------------|
| `oid4vp-1final-wallet-negative-test-invalid-request-object-signature` | Invalid JAR signature |
| `oid4vp-1final-wallet-negative-test-mismatched-client-id` | client_id mismatch |
| `oid4vp-1final-wallet-negative-test-redirect-uri-with-direct-post` | redirect_uri with direct_post |
| `oid4vp-1final-wallet-negative-test-missing-nonce` | Missing nonce |
| `oid4vp-1final-wallet-negative-test-invalid-client-id-prefix` | Invalid client_id prefix |
| `oid4vp-1final-wallet-negative-test-unknown-transaction-data-type` | Unknown transaction type |

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
                              ┌──────────────────────┐
                              │   Issuer API2        │  (Issues test credentials)
                              │   port 7002          │
                              └──────────┬───────────┘
                                         │ OpenID4VCI (pre-auth)
                                         ▼
┌─────────────────────────┐    ┌──────────────────────┐
│   Conformance Suite     │    │   Wallet API2        │  (Your wallet implementation)
│   port 8443 (HTTPS)     │    │   port 7005          │
│   (Acts as Verifier)    │    └──────────┬───────────┘
└───────────┬─────────────┘               │
            │                             │
            │ 1. Authorization request (JAR via request_uri)
            │                             │
            ▼                             │
┌─────────────────────────┐               │
│   Wallet Adapter        │───────────────┘
│   port 7006             │  2. Forward openid4vp:// URL
│   (HTTP bridge)         │     to wallet /present endpoint
└───────────┬─────────────┘
            │
            │ 3. Wallet fetches JAR from conformance suite
            │ 4. Wallet sends encrypted VP response (JWE)
            ▼
     Back to Conformance Suite
```

**Component Ports:**
- **7002** - Issuer API2 (for issuing test credentials)
- **7005** - Wallet API2 (wallet implementation under test)
- **7006** - Wallet Adapter (HTTP bridge for conformance suite)
- **8443** - Conformance Suite (HTTPS, acts as verifier)

---

## Environment Variables

| Variable | Default | Description |
|----------|---------|-------------|
| `WALLET_API_URL` | `http://127.0.0.1:7005` | Wallet API base URL |
| `WALLET_ADAPTER_PORT` | `7006` | Adapter port |
| `WALLET_ADAPTER_URL` | `http://127.0.0.1:7006/openid4vp/authorize` | Full adapter URL (override for ngrok) |
| `CONFORMANCE_HOST` | `localhost.emobix.co.uk` | Suite hostname |
| `CONFORMANCE_PORT` | `8443` | Suite HTTPS port |
| `CONFORMANCE_WALLET_ID` | (auto-detect) | Specific wallet ID to use |

### Using ngrok for Docker networking

If the conformance suite runs in Docker and can't reach `host.docker.internal`,
use ngrok to expose the adapter:

```bash
# Start ngrok tunnel to adapter
ngrok http 7006

# Run tests with ngrok URL
WALLET_ADAPTER_URL="https://your-ngrok-id.ngrok-free.app/openid4vp/authorize" \
./gradlew :waltid-services:waltid-openid4vp-conformance-runners:test \
    --tests "VpWalletConformanceTests" --rerun-tasks
```

---

## Troubleshooting

### VCT Mismatch Error

**Symptom:** Conformance test fails because credential VCT doesn't match DCQL query.

**Cause:** The conformance suite's DCQL query expects a specific `vct` (e.g., `https://credentials.example.com/identity_credential`), but the credential issued by issuer-api2 has `http://localhost:7002/openid4vci/identity_credential`.

**Solutions:**
1. **Update the DCQL query** in the test plan to match your issuer's VCT
2. **Configure issuer** to use the expected VCT in credential configuration
3. **Use a different test profile** that matches available credentials

### SSL Certificate Error (PKIX path building failed)

**Symptom:** `sun.security.validator.ValidatorException: PKIX path building failed`

**Cause:** The wallet API can't verify the conformance suite's self-signed certificate.

**Fix:** Add the certificate to Java's truststore (see Prerequisites above).

### Test plan creation fails with "Variant already set"

**Symptom:** `Variant 'client_id_prefix' has been set by user, but test plan already sets this variant`

**Cause:** The HAIP test plan defines `client_id_prefix` per-module.

**Fix:** Don't include `client_id_prefix` in the variant map. Fixed in commit `ed740fb84`.

### Tests fail with "client.jwks is missing"

**Symptom:** `SetClientIdToX509Hash: client.jwks is missing from configuration`

**Cause:** Wallet test plan missing JWKS with x5c certificate chain.

**Fix:** Fixed in commit `c10a773fd`. Ensure test plans include `client.jwks`.

### Tests fail with "dcql not found" or "Trust Anchor missing"

**Symptom:** 
- `ExtractDCQLQueryFromClientConfiguration: dcql not found`
- `EnsureCredentialTrustAnchorConfigured: 'Credential Trust Anchor' field is missing`

**Cause:** HAIP requires DCQL query and trust anchors in configuration.

**Fix:** Fixed in commit `7afae19fa`. Test plans now include:
- `client.dcql` - DCQL query for credential request
- `credential.trust_anchor` - PEM for credential validation
- `credential.status_list_trust_anchor` - PEM for status list

### Tests timeout or show INTERRUPTED

**Symptom:** Tests created but status is INTERRUPTED.

**Cause:** Wallet didn't respond in time (or at all).

**Fix:** Check wallet-api2 logs for errors processing the authorization request.

### No logs visible on conformance suite

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
5. **x509_hash validation** (compute SHA-256 of DER-encoded leaf certificate)

### WAL-896 Implementation Status

| Feature | File | Status |
|---------|------|--------|
| JWE Encryption | `ResponseEncryptionHandler.kt` | ✅ Implemented |
| JAR Validation | `SignedRequestValidator.kt` | ✅ Implemented |
| x509_hash Computation | `X509HashUtils.kt` | ✅ Implemented |
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
│   ├── keys/
│   │   └── TestKeyMaterial.kt             # Test certificates and keys
│   └── testplans/
│       ├── runner/
│       │   └── WalletTestPlanRunner.kt    # Wallet test orchestration
│       └── plans/vp/wallet/
│           ├── WalletTestPlan.kt          # Base interface
│           ├── VpWalletSdJwtVcX509HashRequestUriSignedDirectPostHaip.kt
│           ├── VpWalletMdlX509HashRequestUriSignedDirectPostHaip.kt
│           ├── VpWalletSdJwtVcX509SanDnsRequestUriSignedDirectPost.kt
│           └── VpWalletMdlX509SanDnsRequestUriSignedDirectPost.kt
└── src/test/kotlin/.../
    ├── IsolatedWalletConformanceTest.kt   # Framework validation (no wallet needed)
    └── VpWalletConformanceTests.kt        # Full wallet test suite
```

---

## References

- [OpenID4VP 1.0 Final Spec](https://openid.net/specs/openid-4-verifiable-presentations-1_0.html)
- [HAIP Profile](https://openid.net/specs/openid4vc-high-assurance-interoperability-profile-1_0-final.html)
- [Conformance Suite](https://openid.net/certification/faq/)
