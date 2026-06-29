# Wallet HAIP Conformance Tests

## Overview

This directory contains wallet-side OpenID4VP conformance tests, focusing on **HAIP (High Assurance Interoperability Profile)** compliance.

These tests validate that the walt.id wallet implementation correctly:
- Authenticates signed authorization requests
- Generates encrypted presentation responses
- Enforces cryptographic requirements (P-256, SHA-256)
- Implements holder binding (KB-JWT for SD-JWT, DeviceAuth for mdocs)
- Handles all supported client authentication schemes

## Architecture

```
[OpenID Conformance Suite]    ←→    [walt.id Wallet]
       (Verifier)                        (Presenter)
       
  Generates requests                 Processes requests
  Validates responses                Generates encrypted responses
```

Unlike verifier conformance tests (where the suite acts as a wallet), wallet conformance tests **reverse the roles**:
- The conformance suite acts as the **verifier**
- The local wallet instance acts as the **presenter**

## Prerequisites

### 1. Install OpenID Conformance Suite

```bash
# Clone conformance suite
git clone https://gitlab.com/openid/conformance-suite.git ~/dev/openid/conformance-suite
cd ~/dev/openid/conformance-suite

# Start with Docker
docker compose -f docker-compose-local.yml up -d

# Wait ~30 seconds for startup
```

### 2. Configure /etc/hosts

```bash
echo "127.0.0.1 localhost.emobix.co.uk" | sudo tee -a /etc/hosts
```

### 3. Verify Conformance Suite

```bash
curl -k https://localhost.emobix.co.uk:8443/
```

You should see the conformance suite web interface.

## Running Tests

### Run All HAIP Tests

```bash
cd ~/dev/walt-id/waltid-unified-build

./gradlew :waltid-services:waltid-openid4vp-conformance-runners:test \
    --tests "*.WalletHAIPConformanceTests"
```

### Run Specific Test Plan

```bash
# Plan 1: SD-JWT VC Baseline
./gradlew :waltid-services:waltid-openid4vp-conformance-runners:test \
    --tests "*.WalletHAIPConformanceTests.HAIP Plan 1*"

# Plan 2: mDL Baseline
./gradlew :waltid-services:waltid-openid4vp-conformance-runners:test \
    --tests "*.WalletHAIPConformanceTests.HAIP Plan 2*"

# Plan 7: Negative Tests
./gradlew :waltid-services:waltid-openid4vp-conformance-runners:test \
    --tests "*.WalletHAIPConformanceTests.HAIP Plan 7*"
```

### Run All Plans (Comprehensive Suite)

```bash
./gradlew :waltid-services:waltid-openid4vp-conformance-runners:test \
    --tests "*.WalletHAIPConformanceTests.runAllHAIPConformanceTests"
```

This runs all 10+ test plans (expect ~60-90 minutes).

## Test Plans

### Phase 1: Core HAIP (MVP)

| Plan | Format | Client ID | Response Mode | Status |
|------|--------|-----------|---------------|--------|
| **1** | SD-JWT VC | x509_san_dns | direct_post.jwt | 🔴 MVP |
| **2** | mDL | x509_san_dns | direct_post.jwt | 🔴 MVP |
| **7** | SD-JWT VC | x509_san_dns | direct_post.jwt | 🔴 MVP (Negative) |

### Phase 2: Extended HAIP

| Plan | Format | Client ID | Response Mode | Status |
|------|--------|-----------|---------------|--------|
| **3** | PhotoID | x509_san_dns | direct_post.jwt | 🟡 Phase 2 |
| **4** | SD-JWT + mdoc | x509_san_dns | direct_post.jwt | 🟡 Phase 2 |
| **5** | mdoc | x509_hash | dc_api.jwt | 🟡 Phase 2 |

### Phase 3: Alternative Schemes

| Plan | Format | Client ID | Response Mode | Status |
|------|--------|-----------|---------------|--------|
| **6.1** | SD-JWT VC | x509_san_uri | direct_post.jwt | 🟢 Phase 3 |
| **6.2** | SD-JWT VC | x509_hash | direct_post.jwt | 🟢 Phase 3 |
| **6.3** | SD-JWT VC | did | direct_post.jwt | 🟢 Phase 3 (Optional) |
| **6.4** | SD-JWT VC | verifier_attestation | direct_post.jwt | 🟢 Phase 3 |

## HAIP Requirements

All test plans validate these HAIP mandates:

- ✅ **Signed Request:** `request_uri_signed` with `wallet_nonce` replay protection
- ✅ **Encrypted Response:** `direct_post.jwt` (JWE encryption)
- ✅ **P-256 Keys:** secp256r1 curve enforcement
- ✅ **SHA-256:** Hash algorithm enforcement
- ✅ **Holder Binding:** KB-JWT (SD-JWT) or DeviceAuth (mdoc)

## Test Modules per Plan

### Plan 1: SD-JWT VC Baseline (11 modules)

- `oid4vp-1final-wallet-haip-happy-flow`
- `oid4vp-1final-wallet-haip-minimal-cnf-jwk`
- `oid4vp-1final-wallet-haip-request-uri-method-post`
- `oid4vp-1final-wallet-haip-invalid-kb-jwt-signature`
- `oid4vp-1final-wallet-haip-invalid-credential-signature`
- `oid4vp-1final-wallet-haip-invalid-sd-hash`
- `oid4vp-1final-wallet-haip-invalid-kb-jwt-nonce`
- `oid4vp-1final-wallet-haip-invalid-kb-jwt-aud`
- `oid4vp-1final-wallet-haip-kb-jwt-iat-in-past`
- `oid4vp-1final-wallet-haip-kb-jwt-iat-in-future`
- `oid4vp-1final-wallet-haip-transaction-data-validation`

### Plan 2: mDL Baseline (6 modules)

- `oid4vp-1final-wallet-haip-mdl-happy-flow`
- `oid4vp-1final-wallet-haip-mdl-device-auth`
- `oid4vp-1final-wallet-haip-mdl-session-transcript`
- `oid4vp-1final-wallet-haip-mdl-invalid-mso-signature`
- `oid4vp-1final-wallet-haip-mdl-invalid-device-signature`
- `oid4vp-1final-wallet-haip-mdl-replay-protection`

### Plan 7: Negative Tests (9 modules)

- `oid4vp-1final-wallet-haip-reject-unsigned-request`
- `oid4vp-1final-wallet-haip-reject-cleartext-response`
- `oid4vp-1final-wallet-haip-reject-weak-curve`
- `oid4vp-1final-wallet-haip-reject-weak-hash`
- `oid4vp-1final-wallet-haip-reject-missing-holder-binding`
- `oid4vp-1final-wallet-haip-reject-expired-certificate`
- `oid4vp-1final-wallet-haip-reject-untrusted-ca`
- `oid4vp-1final-wallet-haip-reject-wallet-nonce-mismatch`
- `oid4vp-1final-wallet-haip-reject-insecure-origin`

## Expected Output

```
================================================================================
Test Plan: oid4vp-1final-wallet-haip-test-plan [credential_format=sd_jwt_vc, client_id_prefix=x509_san_dns, request_method=request_uri_signed, vp_profile=haip, response_mode=direct_post.jwt] (HAIP)
================================================================================
✅ Test plan created: abc123
📋 Test modules: 11
   - oid4vp-1final-wallet-haip-happy-flow
   - oid4vp-1final-wallet-haip-minimal-cnf-jwk
   ...

[1/11] Running module: oid4vp-1final-wallet-haip-happy-flow
   ✅ Result: PASSED

[2/11] Running module: oid4vp-1final-wallet-haip-minimal-cnf-jwk
   ✅ Result: PASSED

...

Test Results:
--------------------------------------------------------------------------------
  Total modules: 11
  ✅ Passed:  11
  
  [0] ✅ oid4vp-1final-wallet-haip-happy-flow
       Conformance: PASSED
       Wallet:      PASSED
  [1] ✅ oid4vp-1final-wallet-haip-minimal-cnf-jwk
       Conformance: PASSED
       Wallet:      PASSED
  ...
================================================================================
```

## Troubleshooting

### Conformance Suite Not Available

```
⚠️  Error getting server version: ...
💡 Make sure conformance suite is running:
   cd ~/dev/openid/conformance-suite
   docker compose -f docker-compose-local.yml up -d
```

**Solution:**
1. Check Docker containers: `docker ps | grep conformance`
2. Check /etc/hosts: `grep localhost.emobix.co.uk /etc/hosts`
3. Test HTTPS: `curl -k https://localhost.emobix.co.uk:8443/`

### SSL Certificate Errors

If you see certificate validation errors, ensure the trust store is configured:

```bash
# The project includes a pre-configured trust store
ls waltid-identity/waltid-services/waltid-openid4vp-conformance-runners/conformance-truststore.jks
```

The Gradle build automatically configures this trust store.

### Wallet Endpoint Not Reachable

If the conformance suite cannot reach the wallet endpoint:

1. Check wallet-api2 is running: `curl http://localhost:7002/health`
2. Check firewall allows localhost:7002
3. For Docker networking, use host network mode

### Test Timeouts

If tests timeout after 30 seconds:

1. Check wallet logs for errors
2. Increase timeout in `WalletTestPlanRunner.kt` (`maxAttempts`)
3. Check conformance suite logs: `docker logs conformance-suite`

## Command-Line Execution

You can also run individual test plans from the command line:

```bash
# SD-JWT VC + x509_san_dns + HAIP
./gradlew :waltid-services:waltid-openid4vp-conformance-runners:run \
    --args="haip sd_jwt_vc x509_san_dns"

# mDL + x509_san_dns + HAIP
./gradlew :waltid-services:waltid-openid4vp-conformance-runners:run \
    --args="haip iso_mdl x509_san_dns"

# PhotoID + x509_san_uri + HAIP
./gradlew :waltid-services:waltid-openid4vp-conformance-runners:run \
    --args="haip iso_photoid x509_san_uri"
```

## Test Results Location

Test results are saved to:

```
waltid-identity/waltid-services/waltid-openid4vp-conformance-runners/build/reports/tests/test/
```

Open `index.html` in a browser to view the full report.

## Certification

Once all HAIP tests pass, you can submit for official OpenID Foundation certification:

1. Generate test execution logs
2. Export conformance test results (JSON)
3. Document certificate chain used
4. Submit to: https://openid.net/certification/

## Related Documentation

- **Test Plans:** `../../../wal-896-haip-test-plans.md` — Detailed test specifications
- **Analysis:** `../../../wal-896-analysis.md` — Implementation analysis
- **OpenID4VP Spec:** https://openid.net/specs/openid-4-verifiable-presentations-1_0.html
- **HAIP Spec:** https://openid.net/specs/openid4vc-high-assurance-interoperability-profile-1_0.html
- **Conformance Suite:** https://gitlab.com/openid/conformance-suite

## Contributing

When adding new test plans:

1. Add test method in `WalletHAIPConformanceTests.kt`
2. Document test modules in `wal-896-haip-test-plans.md`
3. Update this README with new test plan details
4. Run locally before submitting PR

## Support

For issues:
- Check existing conformance suite documentation
- Review walt.id wallet implementation in `waltid-openid4vp-wallet`
- Open issue in walt.id GitHub repository
