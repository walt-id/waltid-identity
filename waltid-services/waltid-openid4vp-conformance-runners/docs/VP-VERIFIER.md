# VP-Verifier Conformance Test Documentation

## Overview

This document covers OpenID4VP Verifier conformance testing against the OpenID Foundation Conformance Suite.

## Scope

This document covers **Verifier** conformance testing — validating that our verifier correctly:
- Issues signed authorization requests (JAR)
- Accepts encrypted VP responses (direct_post.jwt)
- Validates x509_san_dns and x509_hash client_id schemes
- Processes both SD-JWT VC and mDL (mso_mdoc) credentials

> **Note:** For **Wallet** conformance testing (wallet creating presentations), see [VP-WALLET.md](VP-WALLET.md).

## Current Status

**Summary: 4 passed, 0 failed out of 4 tests (as of 2026-07-13)** ✅

### Test Matrix

| Test Plan | Credential Format | Client ID Scheme | VP Profile | Response Mode | Signed Request | Encrypted Response | Result |
|-----------|------------------|------------------|------------|---------------|----------------|-------------------|--------|
| `MdlX509SanDnsRequestUriSignedDirectPost` | mDL (mso_mdoc) | x509_san_dns | plain_vp | direct_post | ✅ JAR | ❌ None | ✅ PASS |
| `SdJwtVcX509SanDnsRequestUriSignedDirectPost` | SD-JWT VC | x509_san_dns | haip | direct_post.jwt | ✅ JAR | ✅ JWE | ✅ PASS |
| `SdJwtVcX509HashRequestUriSignedDirectPostHaip` | SD-JWT VC | x509_hash | haip | direct_post.jwt | ✅ JAR | ✅ JWE | ✅ PASS |
| `MdlX509HashRequestUriSignedDirectPostHaip` | mDL (mso_mdoc) | x509_hash | haip | direct_post.jwt | ✅ JAR | ✅ JWE | ✅ PASS |

### What Each Test Validates

#### MdlX509SanDnsRequestUriSignedDirectPost (Baseline)
- **Purpose**: Non-HAIP baseline mDL verification
- **Credential**: ISO 18013-5 mobile Driving License (mso_mdoc)
- **Client ID**: DNS name from certificate SAN (`x509_san_dns:verifier.example.com`)
- **Request**: Signed (JAR) with X.509 certificate chain in `x5c` header
- **Response**: Plain `direct_post` (unencrypted)
- **Validates**: Basic mDL DCQL query parsing, signed request verification, direct_post flow

#### SdJwtVcX509SanDnsRequestUriSignedDirectPost (HAIP)
- **Purpose**: HAIP-compliant SD-JWT verification with DNS-based client ID
- **Credential**: SD-JWT Verifiable Credential
- **Client ID**: DNS name from certificate SAN
- **Request**: Signed (JAR)
- **Response**: Encrypted `direct_post.jwt` (JWE with ECDH-ES + A256GCM)
- **Validates**: SD-JWT KB-JWT verification, encrypted response decryption, HAIP response mode

#### SdJwtVcX509HashRequestUriSignedDirectPostHaip (HAIP + x509_hash)
- **Purpose**: Full HAIP compliance with certificate hash client ID
- **Credential**: SD-JWT Verifiable Credential
- **Client ID**: SHA-256 hash of leaf certificate (`x509_hash:<base64url-hash>`)
- **Request**: Signed (JAR)
- **Response**: Encrypted `direct_post.jwt`
- **Validates**: x509_hash client_id derivation, KB-JWT audience matching hash, HAIP §5 P-02 compliance

#### MdlX509HashRequestUriSignedDirectPostHaip (HAIP + mDL + x509_hash)
- **Purpose**: Full HAIP compliance for mDL with certificate hash client ID
- **Credential**: ISO 18013-5 mobile Driving License (mso_mdoc)
- **Client ID**: SHA-256 hash of leaf certificate
- **Request**: Signed (JAR)
- **Response**: Encrypted `direct_post.jwt`
- **Validates**: mDL DeviceAuth verification, x509_hash client_id, encrypted mDL response handling

## Root Cause Analysis

### All Issues Resolved ✅

The following issues were fixed to achieve 100% pass rate:

1. **DCQL Claims Format** (mDL tests): Changed from `{"namespace": "...", "claim_name": "..."}` to `{"path": ["namespace", "claim"]}` format per DCQL spec.

2. **x509_hash Client ID Derivation**: Fixed `OSSVerifier2Manager.kt` to compute `client_id` as SHA-256 hash of leaf certificate when using x509_hash scheme.

3. **Trust Anchor Configuration**: Added proper root CA PEM for request object signature chain validation.

## Prerequisites

### Services Required
1. **verifier-api2** running on port 7003
2. **ngrok** exposing port 7003 to the internet
3. **Conformance Suite** Docker container running

### Hosts Entry

Add to `/etc/hosts`:
```
127.0.0.1 localhost.emobix.co.uk
```

### Conformance Suite

```bash
cd ~/dev/openid/conformance-suite
docker compose -f docker-compose-walt.yml up -d
# Wait 30s, then verify:
curl -k https://localhost.emobix.co.uk:8443/api/runner/available
```

### Run Verifier Conformance Tests

```bash
# Terminal 1: Start verifier-api2
cd ~/dev/walt-id/waltid-unified-build
./gradlew :waltid-services:waltid-verifier-api2:run

# Terminal 2: Start ngrok
ngrok http 7003

# Terminal 3: Run tests
export VERIFIER_NGROK_URL="https://<your-ngrok-url>.ngrok-free.app"
./gradlew :waltid-services:waltid-openid4vp-conformance-runners:test --tests "VerifierConformanceTests" --rerun-tasks
```

## Environment Variables

| Variable | Description | Example |
|----------|-------------|---------|
| `VERIFIER_NGROK_URL` | ngrok HTTPS URL for verifier-api2 | `https://844a-xxx.ngrok-free.app` |

## Test Configuration Details

### Certificate Chain

All tests use the same verifier certificate chain:
- **Leaf**: `CN=verifier.example.com` (P-256, signed by Intermediate CA)
- **Intermediate**: `CN=walt.id Verifier Intermediate CA` (signed by Root CA)
- **Root**: `CN=walt.id Verifier Root CA` (self-signed, not included in chain)

**Important**: The conformance suite validates that the leaf certificate is NOT self-signed.

### DCQL Queries

#### mDL Query
```json
{
  "credentials": [{
    "id": "my_mdl",
    "format": "mso_mdoc",
    "meta": { "doctype_value": "org.iso.18013.5.1.mDL" },
    "claims": [
      { "path": ["org.iso.18013.5.1", "family_name"] },
      { "path": ["org.iso.18013.5.1", "given_name"] },
      ...
    ]
  }]
}
```

#### SD-JWT VC Query
```json
{
  "credentials": [{
    "id": "pid",
    "format": "dc+sd-jwt",
    "meta": { "vct_values": ["https://credentials.example.com/identity_credential"] },
    "claims": [
      { "path": ["given_name"] },
      { "path": ["family_name"] },
      { "path": ["birthdate"] },
      { "path": ["age_in_years"] }
    ]
  }]
}
```

## Troubleshooting

### "Waited for 30 tries, but test is still not ready"

This occurs when the conformance suite doesn't receive the authorization request from the verifier. Check:
1. ngrok is running and URL is correct
2. verifier-api2 is listening on port 7003
3. VERIFIER_NGROK_URL environment variable is set

### Audience Mismatch in HAIP Tests

This is a **known issue** requiring a code fix. The verifier needs to:
1. Support `x509_hash` client_id scheme
2. Calculate expected audience from certificate chain
3. Accept `x509_hash:<sha256>` format in audience check

### Test Logs

View detailed test logs in the conformance suite UI:
```
https://localhost.emobix.co.uk:8443/log-detail.html?log=<test_id>
```

## HAIP Requirements Checklist

| Requirement | Status | Notes |
|-------------|--------|-------|
| Signed Authorization Request (JAR) | ✅ | All 4 tests use signed requests |
| x509_san_dns client_id scheme | ✅ | Working in plain_vp and HAIP modes |
| x509_hash client_id scheme (SD-JWT) | ✅ | Working with correct client_id derivation |
| x509_hash client_id scheme (mDL) | ✅ | Working with correct DCQL path format |
| direct_post response mode | ✅ | Working (baseline test) |
| direct_post.jwt (encrypted) response | ✅ | Working for both SD-JWT and mDL |
| P-256 key curve | ✅ | Configured correctly |
| SHA-256 hash algorithm | ✅ | Used for certificate hash |


## Code Fixes Made (2026-07-08)

### ClaimsQuery Model
- Added `namespace` and `claimName` fields for mdoc credential queries
- Made `path` nullable to support both SD-JWT (path-based) and mdoc (namespace-based) formats

**Files Modified**:
- `waltid-dcql/src/commonMain/kotlin/id/walt/dcql/models/ClaimsQuery.kt`
- `waltid-dcql/src/commonMain/kotlin/id/walt/dcql/DcqlMatcher.kt`
- `waltid-digital-credentials/src/commonMain/kotlin/id/walt/credentials/presentations/formats/DcSdJwtPresentation.kt`
- `waltid-openid4vp-wallet/src/commonMain/kotlin/id/waltid/openid4vp/wallet/presentation/MdocPresenter.kt`

## References

- [OpenID4VP 1.0 Final Spec](https://openid.net/specs/openid-4-verifiable-presentations-1_0.html)
- [HAIP Profile](https://openid.net/specs/openid4vc-high-assurance-interoperability-profile-sd-jwt-vc-1_0.html)
- [Conformance Suite](https://openid.net/certification/faq/)
