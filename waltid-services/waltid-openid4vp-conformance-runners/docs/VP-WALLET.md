# Wallet Conformance Tests

## Overview

This document describes the wallet-side OpenID4VP conformance tests for the walt.id wallet implementation.

These tests validate that the walt.id wallet correctly:
- Authenticates signed authorization requests (JAR)
- Generates encrypted presentation responses (JWE)
- Enforces cryptographic requirements (P-256, SHA-256)
- Implements holder binding (KB-JWT for SD-JWT, DeviceAuth for mdoc)
- Handles X.509 certificate-based verifier authentication

## HAIP Compliance

The wallet conformance tests include HAIP (High Assurance Interoperability Profile) validation for eIDAS 2.0 compliance.

**HAIP Requirements for Wallet:**
- Signed request authentication (MANDATORY)
- Encrypted response generation (MANDATORY)
- P-256 key curve enforcement (MANDATORY)
- SHA-256 hash algorithm (MANDATORY)
- Holder binding (KB-JWT or DeviceAuth)

## Architecture

```
[OpenID Conformance Suite]    <->    [Adapter]    <->    [walt.id Wallet API]
       (Verifier)                    (7006)              (7005)
       
  Generates requests         Bridges HTTP to         Processes requests
  Validates responses        Wallet API             Generates responses
```

Unlike verifier conformance tests (where the suite acts as a wallet), wallet conformance tests **reverse the roles**:
- The conformance suite acts as the **verifier**
- The local wallet instance acts as the **presenter**

### Wallet Conformance Adapter

The adapter (`WalletConformanceAdapter.kt`) bridges the conformance suite with the wallet API:

1. **Receives** authorization requests from conformance suite
2. **Fetches** the signed JAR from `request_uri`
3. **Calls** wallet API programmatically (resolve -> match -> present)
4. **POSTs** VP response to conformance suite's `response_uri`

This is **test infrastructure only** - production wallets use mobile URL schemes, deep links, or Digital Credentials API.

## Prerequisites

### 1. Setup /etc/hosts

```bash
echo "127.0.0.1 localhost.emobix.co.uk" | sudo tee -a /etc/hosts
```

### 2. Clone and Setup Conformance Suite

```bash
git clone https://gitlab.com/openid/conformance-suite.git ~/dev/openid/conformance-suite

# Copy walt.id configuration
cp ~/dev/walt-id/waltid-unified-build/waltid-identity/waltid-services/waltid-openid4vp-conformance-runners/docker-compose-walt.yml ~/dev/openid/conformance-suite/

cp -r ~/dev/walt-id/waltid-unified-build/waltid-identity/waltid-services/waltid-openid4vp-conformance-runners/nginx ~/dev/openid/conformance-suite/
```

### 3. Start Conformance Suite

```bash
cd ~/dev/openid/conformance-suite
docker compose -f docker-compose-walt.yml up -d

# Wait ~30 seconds, then verify:
curl -k https://localhost.emobix.co.uk:8443/
```

### 4. Wallet API Running

The wallet API must be running on port 7005:

```bash
# Check wallet status
curl http://127.0.0.1:7005/health
```

## Running Tests

### Run All Wallet Tests

```bash
cd ~/dev/walt-id/waltid-unified-build/waltid-identity

./gradlew :waltid-services:waltid-openid4vp-conformance-runners:test \
    --tests "WalletConformanceTests"
```

### Run Specific Test Plan

```bash
# Plan 1: SD-JWT VC Baseline (HAIP)
./gradlew :waltid-services:waltid-openid4vp-conformance-runners:test \
    --tests "WalletConformanceTests.Plan 1*"

# Plan 2: mDL Baseline (HAIP)
./gradlew :waltid-services:waltid-openid4vp-conformance-runners:test \
    --tests "WalletConformanceTests.Plan 2*"

# Plan 7: Negative Tests
./gradlew :waltid-services:waltid-openid4vp-conformance-runners:test \
    --tests "WalletConformanceTests.Plan 7*"
```

### Run Complete Suite

```bash
./gradlew :waltid-services:waltid-openid4vp-conformance-runners:test \
    --tests "WalletConformanceTests.runAllWalletConformanceTests"
```

## Test Plans

### Currently Implemented

| Plan | Format | Client ID | Response Mode | HAIP | Status |
|------|--------|-----------|---------------|------|--------|
| **1** | SD-JWT VC | x509_san_dns | direct_post.jwt | Yes | Infrastructure ready |
| **2** | mDL | x509_san_dns | direct_post.jwt | Yes | Infrastructure ready |
| **7** | SD-JWT VC | x509_san_dns | direct_post.jwt | Yes | Infrastructure ready (Negative) |

### Planned Extensions

| Plan | Format | Client ID | Response Mode | Description |
|------|--------|-----------|---------------|-------------|
| **3** | PhotoID | x509_san_dns | direct_post.jwt | ISO 23220 PhotoID |
| **4** | SD-JWT + mdoc | x509_san_dns | direct_post.jwt | Multi-format presentation |
| **5** | mdoc | x509_hash | dc_api.jwt | Digital Credentials API |
| **6.1** | SD-JWT VC | x509_san_uri | direct_post.jwt | Alternative client ID scheme |
| **6.2** | SD-JWT VC | x509_hash | direct_post.jwt | Certificate hash scheme |
| **6.3** | SD-JWT VC | did | direct_post.jwt | DID-based authentication |
| **6.4** | SD-JWT VC | verifier_attestation | direct_post.jwt | Attestation-based |

## Test Plan Details

### Plan 1: SD-JWT VC Baseline (HAIP)

**Class:** `WalletPlan1`

Tests wallet's ability to process SD-JWT VC presentations with HAIP requirements.

| Property | Value |
|----------|-------|
| **Credential Format** | `sd_jwt_vc` (dc+sd-jwt) |
| **Client ID Scheme** | `x509_san_dns` |
| **Request Method** | `request_uri_signed` (JAR) |
| **Response Mode** | `direct_post.jwt` (encrypted) |

**Expected Test Modules (14 actual from conformance suite):**

| Module | Type | Description |
|--------|------|-------------|
| `oid4vp-1final-wallet-happy-flow` | Positive | Standard successful presentation flow |
| `oid4vp-1final-wallet-request-uri-method-post` | Positive | Request URI fetched via POST |
| `oid4vp-1final-wallet-minimal-cnf-jwk` | Positive | Minimal confirmation key |
| `oid4vp-1final-wallet-verify-kb-jwt-signature` | Negative | Verify KB-JWT signature validation |
| `oid4vp-1final-wallet-verify-credential-signature` | Negative | Verify credential signature validation |
| `oid4vp-1final-wallet-verify-sd-hash` | Negative | Verify SD hash validation |
| `oid4vp-1final-wallet-verify-kb-jwt-nonce` | Negative | Verify nonce validation |
| `oid4vp-1final-wallet-verify-kb-jwt-aud` | Negative | Verify audience validation |
| `oid4vp-1final-wallet-verify-kb-jwt-iat-past` | Negative | Verify issued-at (past) validation |
| `oid4vp-1final-wallet-verify-kb-jwt-iat-future` | Negative | Verify issued-at (future) validation |
| `oid4vp-1final-wallet-dcql-happy-flow` | Positive | DCQL query handling |
| `oid4vp-1final-wallet-transaction-data` | Positive | Transaction data validation |
| `oid4vp-1final-wallet-wrong-alg-response` | Negative | Wrong encryption algorithm |
| `oid4vp-1final-wallet-no-enc-response` | Negative | Missing encryption |

### Plan 2: mDL (Mobile Driving License) Baseline (HAIP)

**Class:** `WalletPlan2`

Tests wallet's ability to present ISO mDL (mso_mdoc) credentials.

| Property | Value |
|----------|-------|
| **Credential Format** | `iso_mdl` (mso_mdoc) |
| **Client ID Scheme** | `x509_san_dns` |
| **Request Method** | `request_uri_signed` (JAR) |
| **Response Mode** | `direct_post.jwt` (encrypted) |

**Expected Test Modules:**

| Module | Type | Description |
|--------|------|-------------|
| `oid4vp-1final-wallet-mdl-happy-flow` | Positive | Standard mDL presentation |
| `oid4vp-1final-wallet-mdl-device-auth` | Positive | DeviceAuth holder binding |
| `oid4vp-1final-wallet-mdl-session-transcript` | Positive | Session transcript validation (ISO 18013-7 Annex C) |
| `oid4vp-1final-wallet-mdl-invalid-mso` | Negative | Invalid MSO signature detection |
| `oid4vp-1final-wallet-mdl-invalid-device-sig` | Negative | Invalid device signature detection |
| `oid4vp-1final-wallet-mdl-replay` | Negative | Replay attack prevention |

### Plan 7: Negative Tests (Security Validation) (HAIP)

**Class:** `WalletPlan7`

Tests that wallet correctly **rejects** non-HAIP-compliant requests.

| Property | Value |
|----------|-------|
| **Credential Format** | `sd_jwt_vc` |
| **Response Mode** | `direct_post.jwt` |
| **Expected Behavior** | Wallet must reject |

**Expected Test Modules:**

| Module | Description |
|--------|-------------|
| `oid4vp-1final-wallet-reject-unsigned-request` | Must reject unsigned authorization requests |
| `oid4vp-1final-wallet-reject-cleartext-response` | Must reject requests not requiring encryption |
| `oid4vp-1final-wallet-reject-weak-curve` | Must reject non-P-256 curves |
| `oid4vp-1final-wallet-reject-weak-hash` | Must reject non-SHA-256 hashes |
| `oid4vp-1final-wallet-reject-missing-holder-binding` | Must reject credentials without holder binding |
| `oid4vp-1final-wallet-reject-expired-certificate` | Must reject expired verifier certificates |
| `oid4vp-1final-wallet-reject-untrusted-ca` | Must reject untrusted certificate chains |
| `oid4vp-1final-wallet-reject-wallet-nonce-mismatch` | Must reject wallet_nonce mismatches |
| `oid4vp-1final-wallet-reject-insecure-origin` | Must reject non-HTTPS origins |

## HAIP Requirements Validated

All test plans validate these HAIP mandatory requirements:

| Requirement | Implementation | Spec Reference |
|-------------|----------------|----------------|
| Signed Request | `request_uri_signed` with JAR | HAIP 4.2 |
| Encrypted Response | `direct_post.jwt` (JWE) | HAIP 4.3 |
| Response Encryption Alg | ECDH-ES | HAIP 4.3.1 |
| Response Encryption Enc | A256GCM | HAIP 4.3.1 |
| P-256 Keys | secp256r1 curve enforcement | HAIP 5.1 |
| SHA-256 | Hash algorithm enforcement | HAIP 5.2 |
| Holder Binding | KB-JWT (SD-JWT) or DeviceAuth (mdoc) | HAIP 4.4 |
| Replay Protection | wallet_nonce validation | HAIP 4.5 |

## Configuration

### Test Configuration

```kotlin
// ConformanceConfig.kt
object ConformanceConfig {
    const val CONFORMANCE_HOST = "localhost.emobix.co.uk"
    const val CONFORMANCE_PORT = 8443
    const val WALLET_API_URL = "http://127.0.0.1:7005"
    const val WALLET_ADAPTER_PORT = 7006
}
```

### Test Plan Configuration

Each test plan specifies:

```kotlin
class WalletPlan1(...) : WalletTestPlan {
    override val planName = "oid4vp-1final-wallet-haip-test-plan"
    override val variant = mapOf(
        "credential_format" to "sd_jwt_vc",
        "response_mode" to "direct_post.jwt"
    )
    override val configuration = JsonObject {
        // JWE encryption parameters
        "client" to {
            "authorization_encrypted_response_alg" to "ECDH-ES"
            "authorization_encrypted_response_enc" to "A256GCM"
        }
    }
}
```

## Expected Output

When tests execute successfully:

```
================================================================================
Wallet Conformance Tests (HAIP)
================================================================================

Conformance suite available: 5.2.0

Test plans:
  - Wallet Plan 1: SD-JWT VC Baseline (HAIP - x509_hash + direct_post.jwt)
  - Wallet Plan 2: mDL Baseline (HAIP - x509_hash + direct_post.jwt)
  - Wallet Plan 7: Negative Tests - Security Validation (HAIP)

================================================================================

[Adapter] Starting Wallet Conformance Adapter on port 7006
[Adapter] Wallet API URL: http://127.0.0.1:7005
[Adapter] Started successfully

================================================================================
Running: Wallet Plan 1: SD-JWT VC Baseline (HAIP - x509_hash + direct_post.jwt)
================================================================================

Plan created: abc123xyz
Modules: 14

[1/14] oid4vp-1final-wallet-happy-flow
       Conformance: PASSED
       Wallet:      SUCCESS

[2/14] oid4vp-1final-wallet-verify-kb-jwt-signature
       Conformance: PASSED
       Wallet:      REJECTED (expected)

...

================================================================================
```

## Current Status

Tests will **skip** until WAL-896 wallet HAIP features are implemented:

- Signed request authentication (JAR parsing and validation)
- Encrypted response generation (JWE with ECDH-ES + A256GCM)
- HAIP policy enforcement
- X.509 certificate chain validation

Once implemented, tests will execute and validate compliance.

## Troubleshooting

### Tests Skip

- Conformance suite not running
- Check: `curl -k https://localhost.emobix.co.uk:8443/`

### Connection Refused to Wallet API

- Wallet API not running on port 7005
- Check: `curl http://127.0.0.1:7005/health`

### Adapter Fails to Start

- Port 7006 already in use
- Check: `sudo lsof -i :7006`

### SSL Certificate Errors

Re-extract and import certificate:

```bash
cd ~/dev/walt-id/waltid-unified-build/waltid-identity/waltid-services/waltid-openid4vp-conformance-runners

openssl s_client -connect localhost.emobix.co.uk:8443 </dev/null 2>/dev/null | \
  openssl x509 -outform PEM > conformance-test.pem

keytool -delete -alias conformance-test-localhost -keystore conformance-truststore.jks \
  -storepass changeit 2>/dev/null || true
keytool -importcert -trustcacerts -alias conformance-test-localhost \
  -file conformance-test.pem -keystore conformance-truststore.jks \
  -storepass changeit -noprompt
```

### Test Timeouts

- Increase timeout in test class (default 10 minutes per plan)
- Check wallet logs for processing errors
- Check conformance suite logs: `docker logs conformance-suite-server-1`

## Test Results

Results are saved to:
```
waltid-services/waltid-openid4vp-conformance-runners/build/reports/tests/test/index.html
```

## Related Documentation

- [VCI-WALLET.md](VCI-WALLET.md) - VCI wallet conformance tests
- [VCI-ISSUER.md](VCI-ISSUER.md) - VCI issuer conformance tests
- [VP-VERIFIER.md](VP-VERIFIER.md) - VP verifier conformance tests
- [OpenID4VP Specification](https://openid.net/specs/openid-4-verifiable-presentations-1_0.html)
- [HAIP Specification](https://openid.net/specs/openid4vc-high-assurance-interoperability-profile-1_0.html)
- [Conformance Suite](https://gitlab.com/openid/conformance-suite)

## Support

- [GitHub Issues](https://github.com/walt-id/waltid-identity/issues)
- [walt.id Discord](https://discord.gg/AW8AgqJthZ)
- [Documentation](https://docs.walt.id)
