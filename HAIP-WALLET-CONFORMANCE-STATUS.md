# VP Wallet Conformance Test Status

**Date:** 2026-07-13
**Branch:** feature/wal-896
**Conformance Suite:** OpenID Foundation OID4VP 1.0 Final

## Test Environment

- **Wallet API:** localhost:7005
- **Issuer API:** localhost:7002
- **Conformance Suite:** localhost.emobix.co.uk:8443
- **Adapter:** ngrok tunnel to localhost:7006

## SD-JWT VC Tests (HAIP Profile)

### ✅ PASSING: `sd_jwt_vc-x509_hash-request_uri_signed-haip-direct_post.jwt`

| Test Module | Status | Notes |
|-------------|--------|-------|
| happy-flow | ✅ PASSED | Full presentation flow works |
| fewer-claims-than-available | ✅ PASSED | Selective disclosure works |
| no-claims-in-dcql-query | ✅ PASSED | DCQL without claims works |

**What's Working:**
- Wallet receives and parses authorization request
- Wallet validates signed JAR (request object)
- Wallet encrypts VP response (JWE)
- KB-JWT generated correctly
- VCT matches DCQL query
- X.509 certificate chain validation (x5c header, leaf only)
- Trust anchor validation with Issuer CA

### ⏳ PENDING: `sd_jwt_vc-x509_san_dns-request_uri_signed-direct_post`

Not yet tested with current configuration.

## mDL Tests

### ❓ Status Unknown

mDL tests use different credential format (mDOC/CBOR) and may have different requirements.
Currently configured with VERIFIER_CA_PEM trust anchor which may not match issued credentials.

## Configuration Changes Made

### 1. SdJwtVcCredentialSigner.kt
- **Change:** When `x5Chain` is provided, use `x5c` header instead of `kid`
- **Reason:** HAIP-6.1.1 requires x5c for X.509-signed credentials
- **Before:** Both `kid` (DID-based) and `x5c` were included
- **After:** Only `x5c` with leaf certificate (no trust anchor per HAIP-6.1.1)

### 2. issuer2-profiles.conf
- **New issuer key:** P-256 key with matching certificate chain
- **x5Chain:** Leaf certificate only (CA excluded per HAIP-6.1.1)
- **Profile:** `identityCredentialSdJwt` uses x5Chain instead of issuerDid

### 3. TestKeyMaterial.kt
- **Added:** `ISSUER_CA_CERT`, `ISSUER_CA_PEM`, `ISSUER_CA_PEM_JSON`
- **Added:** `ISSUER_LEAF_CERT`, `ISSUER_KEY_JWK`
- **Added:** `getIssuerCertificateChain()` helper

### 4. Wallet Test Plans
- **VpWalletSdJwtVcX509HashRequestUriSignedDirectPostHaip.kt:** Updated trust anchor to `ISSUER_CA_PEM_JSON`
- **VpWalletSdJwtVcX509SanDnsRequestUriSignedDirectPost.kt:** Updated trust anchor to `ISSUER_CA_PEM_JSON`

## Credential Structure

### JWT Header (HAIP Compliant)
```json
{
  "typ": "dc+sd-jwt",
  "alg": "ES256",
  "x5c": ["<leaf-cert-base64>"]
}
```

**Note:** No `kid` header when using x5c (correct for HAIP compliance).

### Trust Chain
```
Credential JWT
  └── x5c[0]: Issuer Leaf Cert (CN=issuer.walt.id)
        └── Signed by: walt.id Issuer CA (trust anchor - NOT in x5c)
```

## Test Execution

### Prerequisites
```bash
# 1. Start services
./gradlew :waltid-services:waltid-wallet-api2:run  # port 7005
./gradlew :waltid-services:waltid-issuer-api2:run  # port 7002

# 2. Start ngrok tunnel
ngrok http 7006

# 3. Setup test wallet with credential
./waltid-identity/waltid-services/waltid-openid4vp-conformance-runners/scripts/setup-test-wallet.sh
```

### Running Tests
Tests are run through the conformance suite web UI:
1. Open https://localhost.emobix.co.uk:8443
2. Create test plan with wallet configuration
3. Execute test modules

## Next Steps

1. ✅ SD-JWT VC x509_hash HAIP tests passing
2. ⏳ Test SD-JWT VC x509_san_dns variant
3. ⏳ Investigate mDL test requirements
4. ⏳ Run negative/security tests
5. ⏳ Automate test execution via Gradle

## Commits

- `ddbaa755a`: HAIP-compliant SD-JWT VC issuance with x5c certificate chain
