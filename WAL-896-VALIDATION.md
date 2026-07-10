# WAL-896 Implementation Validation Guide

## Overview

WAL-896 implements **signed authorization requests** and **encrypted responses** for OpenID4VP conformance. This document provides steps to validate the implementation.

## Commit

```
1937e7ea8 feat(WAL-896): Add mdoc DCQL support for namespace/claimName queries
```

Branch: `feature/wal-896`

## Changes Summary

### 1. ClaimsQuery Model Enhancement
**File:** `waltid-libraries/credentials/waltid-dcql/src/commonMain/kotlin/id/walt/dcql/models/ClaimsQuery.kt`

Added support for mdoc credentials which use `namespace` + `claimName` instead of JSON path:
- `namespace: String?` - mdoc namespace (e.g., `org.iso.18013.5.1`)
- `claimName: String?` - mdoc claim name (e.g., `family_name`)
- `effectivePath()` - Returns path or constructs `listOf(namespace, claimName)` for mdoc
- `pathKey()` - Returns string for logging/grouping

### 2. DcqlMatcher Update
**File:** `waltid-libraries/credentials/waltid-dcql/src/commonMain/kotlin/id/walt/dcql/DcqlMatcher.kt`

Updated to use `effectivePath()` instead of direct `path` access.

### 3. DcSdJwtPresentation Update
**File:** `waltid-libraries/credentials/waltid-digital-credentials/src/commonMain/kotlin/id/walt/credentials/presentations/formats/DcSdJwtPresentation.kt`

Updated `validateClaimsAgainstCredential()` to use `effectivePath()` and `pathKey()`.

### 4. MdocPresenter Dual-Mode Support
**File:** `waltid-libraries/protocols/waltid-openid4vp-wallet/src/commonMain/kotlin/id/waltid/openid4vp/wallet/presentation/MdocPresenter.kt`

Now handles both query formats:
- New format: `namespace` + `claimName` fields
- Legacy format: `path` containing `[namespace, claimName]`

### 5. Conformance Test Infrastructure
- Restored `Verifier2Interface.kt` for verifier-api2 communication
- Fixed test runner to use `runBlocking` for real network calls

## How to Validate

### Prerequisites

1. **Start the conformance suite** (if not running):
   ```bash
   # Conformance suite should be at localhost.emobix.co.uk:8443
   ```

2. **Start ngrok tunnel**:
   ```bash
   ngrok http 7003
   # Note the https URL (e.g., https://xxxx.ngrok-free.app)
   ```

3. **Start verifier-api2**:
   ```bash
   cd ~/dev/walt-id/waltid-unified-build
   ./gradlew :waltid-services:waltid-verifier-api2:run
   # Runs on port 7003
   ```

### Run Conformance Test

```bash
cd ~/dev/walt-id/waltid-unified-build

# Set your ngrok URL
export VERIFIER_CALLBACK_URL="https://your-ngrok-url.ngrok-free.app"

# Run the verifier conformance test
./gradlew :waltid-services:waltid-openid4vp-conformance-runners:test \
  --tests "id.walt.openid4vp.conformance.VerifierConformanceTests" \
  --info
```

### Expected Results

| Test Plan | Format | Status | Notes |
|-----------|--------|--------|-------|
| MdlX509SanDnsRequestUriSignedDirectPost | mdoc | ✅ PASS | Validates WAL-896 core |
| SdJwtVcX509SanDnsRequestUriSignedDirectPost | SD-JWT | ❌ FAIL | Audience policy issue* |
| MdlX509HashRequestUriSignedDirectPostHaip | mdoc | TBD | HAIP variant |
| SdJwtVcX509HashRequestUriSignedDirectPostHaip | SD-JWT | TBD | HAIP variant |

*The SD-JWT test fails due to audience validation policy, not WAL-896 implementation. The conformance suite correctly sends `x509_hash:...` as KB-JWT audience per HAIP spec, but our `AudienceCheckSdJwtVPPolicy` expects exact match with original `client_id`.

### Verify mdoc Test Passed

Look for in test output:
```
POST /response -> 200 OK
```

And policy results showing:
```
mso_mdoc/device-auth: success=true
mso_mdoc/device_key_auth: success=true
mso_mdoc/issuer_auth: success=true
mso_mdoc/issuer_signed_integrity: success=true
mso_mdoc/mso: success=true
```

## What WAL-896 Validates

1. **Signed Authorization Request (JAR)** - Request is signed with x509 certificate
2. **Request URI POST** - Authorization request delivered via POST to request_uri
3. **Direct Post Response** - VP response sent directly to response_uri
4. **mdoc Credential Support** - DCQL queries with namespace/claimName

## Files Modified

```
waltid-libraries/credentials/waltid-dcql/
├── src/commonMain/kotlin/id/walt/dcql/
│   ├── DcqlMatcher.kt                    # Use effectivePath()
│   └── models/ClaimsQuery.kt             # Added namespace/claimName support

waltid-libraries/credentials/waltid-digital-credentials/
└── src/commonMain/kotlin/id/walt/credentials/presentations/formats/
    └── DcSdJwtPresentation.kt            # Use effectivePath()/pathKey()

waltid-libraries/protocols/waltid-openid4vp-wallet/
└── src/commonMain/kotlin/id/waltid/openid4vp/wallet/presentation/
    └── MdocPresenter.kt                  # Dual-mode namespace handling

waltid-services/waltid-openid4vp-conformance-runners/
├── src/main/kotlin/.../testplans/http/
│   └── Verifier2Interface.kt             # Restored verifier-api2 interface
└── src/test/kotlin/.../conformance/
    └── VerifierConformanceTests.kt       # Fixed test runner
```

## Known Issues / Future Work

1. **Audience Policy Enhancement** (not WAL-896 scope)
   - `AudienceCheckSdJwtVPPolicy` needs to accept both `x509_san_dns:...` and `x509_hash:...` when original client_id was `x509_san_dns`
   - Per HAIP spec, wallet may use certificate hash as audience

2. **HAIP Test Plans** (MdlX509Hash*, SdJwtVcX509Hash*)
   - Pending validation after audience policy fix
