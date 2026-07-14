# VP-Verifier Conformance Test Documentation

## Overview

This document covers OpenID4VP Verifier conformance testing against the OpenID Foundation Conformance Suite.

## Test Profiles

| Profile | Plan | Format | Client ID | Response Mode | Status |
|---------|------|--------|-----------|---------------|--------|
| MdlX509SanDnsRequestUriSignedDirectPost | `oid4vp-1final-verifier-test-plan` | mDL (mso_mdoc) | x509_san_dns | direct_post | ✅ **PASSED** |
| SdJwtVcX509SanDnsRequestUriSignedDirectPostPlain | `oid4vp-1final-verifier-test-plan` | SD-JWT VC | x509_san_dns | direct_post | ⏳ Not yet tested |
| SdJwtVcX509SanDnsRequestUriSignedDirectPost | `oid4vp-1final-verifier-haip-test-plan` | SD-JWT VC | x509_san_dns | direct_post.jwt | ❌ **FAILED** |
| MdlX509HashRequestUriSignedDirectPostHaip | `oid4vp-1final-verifier-test-plan` | mDL (mso_mdoc) | x509_hash | direct_post.jwt | ❌ **FAILED** |
| SdJwtVcX509HashRequestUriSignedDirectPostHaip | `oid4vp-1final-verifier-haip-test-plan` | SD-JWT VC | x509_hash | direct_post.jwt | ❌ **FAILED** |

## Current Status

**Summary: 1 passed, 3 failed out of 4 tests (as of 2026-07-08)**

### ✅ Passing Tests

#### MdlX509SanDnsRequestUriSignedDirectPost
- **Plan**: `oid4vp-1final-verifier-test-plan`
- **Variant**: `iso_mdl`, `x509_san_dns`, `request_uri_signed`, `plain_vp`, `direct_post`
- **Result**: PASSED
- **Notes**: Non-HAIP baseline test. Proves the mdoc DCQL claims parsing fix works.

### ❌ Failing Tests

All HAIP tests fail with the same root cause: **audience mismatch**.

#### SdJwtVcX509SanDnsRequestUriSignedDirectPost (HAIP)
- **Plan**: `oid4vp-1final-verifier-haip-test-plan`
- **Variant**: `sd_jwt_vc`, `response_mode=direct_post.jwt`
- **Result**: FAILED
- **Error**: `AUDIENCE_MISMATCH: KB-JWT 'aud' claim mismatch. Expected verifier2, got x509_hash:L8zOHpvIslIfw3enc7DpZtmZhBUh9OY3DPCdEUz9KPc`

#### MdlX509HashRequestUriSignedDirectPostHaip
- **Plan**: `oid4vp-1final-verifier-test-plan`  
- **Variant**: `iso_mdl`, `x509_hash`, `request_uri_signed`, `haip`, `direct_post.jwt`
- **Result**: FAILED (same audience mismatch)

#### SdJwtVcX509HashRequestUriSignedDirectPostHaip
- **Plan**: `oid4vp-1final-verifier-haip-test-plan`
- **Variant**: `sd_jwt_vc`, `x509_hash`, `direct_post.jwt`
- **Result**: FAILED (same audience mismatch)

## Root Cause Analysis

### Audience Mismatch in HAIP Mode

In HAIP profile, the conformance suite's wallet uses the **X.509 certificate hash** as the audience:
```
aud: x509_hash:L8zOHpvIslIfw3enc7DpZtmZhBUh9OY3DPCdEUz9KPc
```

But the verifier-api2 is validating against a static `client_id`:
```
expected: verifier2
```

**HAIP Requirement**: Per HAIP §5, when using `x509_hash` client_id scheme, the audience in KB-JWT/DeviceAuth must be the SHA-256 hash of the verifier's leaf certificate, prefixed with `x509_hash:`.

### Required Fix

The verifier-api2 `AudienceCheckSdJwtVPPolicy` needs to:
1. Detect when `x509_hash` client_id scheme is in use
2. Calculate the expected audience from the verifier's certificate chain
3. Accept `x509_hash:<hash>` format as valid audience

**Location**: `waltid-libraries/credentials/waltid-verification-policies2-vp/src/commonMain/kotlin/id/walt/policies2/vp/policies/AudienceCheckSdJwtVPPolicy.kt`

## Prerequisites

### Services Required
1. **verifier-api2** running on port 7003
2. **ngrok** exposing port 7003 to the internet
3. **Conformance Suite** Docker container running

### Start Commands

```bash
# Terminal 1: Start verifier-api2
cd ~/dev/walt-id/waltid-unified-build
./gradlew :waltid-services:waltid-verifier-api2:run

# Terminal 2: Start ngrok
ngrok http 7003

# Terminal 3: Run tests
export VERIFIER_NGROK_URL="https://<your-ngrok-url>.ngrok-free.app"
./gradlew :waltid-services:waltid-openid4vp-conformance-runners:test --tests "VerifierConformanceTests"
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
| Signed Authorization Request (JAR) | ✅ | Working |
| x509_san_dns client_id scheme | ✅ | Working in plain_vp mode |
| x509_hash client_id scheme | ❌ | Audience validation broken |
| direct_post response mode | ✅ | Working |
| direct_post.jwt (encrypted) response | ⚠️ | Needs x509_hash fix first |
| P-256 key curve | ✅ | Configured correctly |
| SHA-256 hash algorithm | ✅ | Used for certificate hash |

## Next Steps

1. **Fix audience validation** in `AudienceCheckSdJwtVPPolicy` to support `x509_hash` scheme
2. **Rerun HAIP tests** after the fix
3. **Add plain SD-JWT test** to confirm baseline SD-JWT functionality
4. **Update documentation** with final test results

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
