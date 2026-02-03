# EUDI Wallet Compatibility Testing Plan

> **For Claude:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** Verify existing EUDI wallet compatibility implementation works correctly with deployed services.

**Architecture:** This is a testing plan that validates the existing EUDI wallet support through three layers: unit tests, integration tests, and live service verification. Tests progress from fast unit tests to slower integration tests to manual verification with the running Docker services.

**Tech Stack:** Kotlin, JUnit 5, Gradle, Docker Compose, curl, EUDI Reference Wallet App

---

## Prerequisites

Before running any tests, ensure:
1. You are in the correct worktree: `/Users/adambradley/Projects/Mastercard/India/waltid-identity/.worktrees/eudi-compatibility`
2. **CRITICAL:** You must use the **custom-built Docker image** (standard Docker Hub images do NOT work)
3. Docker services are running: `docker compose --profile identity ps` (from `docker-compose/` directory)
4. Java 21 is available: `java -version`

### Building the Custom Issuer Image

```bash
# From the worktree root (NOT docker-compose directory)
cd /Users/adambradley/Projects/Mastercard/India/waltid-identity/.worktrees/eudi-compatibility

# Build the custom issuer image
./gradlew :waltid-services:waltid-issuer-api:jibDockerBuild

# Tag to match docker-compose VERSION_TAG (default: stable)
docker tag waltid/issuer-api:latest waltid/issuer-api:stable

# Force recreate the container with the new image
cd docker-compose
docker compose up -d --force-recreate issuer-api
```

**Why custom image is required:** The EUDI wallet compatibility fixes (dc+sd-jwt format support, VCT handling, credential request processing) are NOT in the upstream walt.id Docker Hub images.

---

## Task 1: Run Existing EUDI Unit Tests

**Files:**
- Test: `waltid-services/waltid-issuer-api/src/test/kotlin/id/walt/eudi/EudiIssuanceTest.kt`
- Test: `waltid-services/waltid-issuer-api/src/test/kotlin/id/walt/eudi/Draft13FormatTest.kt`
- Test: `waltid-services/waltid-issuer-api/src/test/kotlin/id/walt/eudi/DPoPHandlerTest.kt`

**Step 1: Navigate to worktree root**

```bash
cd /Users/adambradley/Projects/Mastercard/India/waltid-identity/.worktrees/eudi-compatibility
```

**Step 2: Run EUDI unit tests**

Run: `./gradlew :waltid-services:waltid-issuer-api:test --tests "id.walt.eudi.*" -i`
Expected: All tests PASS

**Step 3: Review test output**

The tests verify:
- DPoP JWK thumbprint calculation (RFC 7638)
- DPoP access token hash calculation
- Draft 13+ credential request format (`credential_configuration_id`, `proofs`)
- Legacy proof format compatibility
- PID mDoc credential offer creation
- mDL credential offer creation
- Issuance session with DPoP thumbprint
- CIProvider metadata has DPoP support
- Full PID issuance offer flow
- Batch PID and mDL issuance

**Step 4: Commit test run confirmation**

```bash
git add -A
git commit -m "test: verify EUDI unit tests pass"
```

---

## Task 2: Run mDoc E2E Test Suite

**Files:**
- Test: `waltid-services/waltid-e2e-tests/src/test/kotlin/MDocTestSuite.kt`
- Test: `waltid-services/waltid-e2e-tests/src/test/kotlin/MDocPreparedWallet.kt`

**Step 1: Run mDoc E2E tests**

Run: `./gradlew :waltid-services:waltid-e2e-tests:test --tests "MDocTestSuite" -i`
Expected: All tests PASS

Note: These tests require the full service stack to be running. If tests fail due to missing services, ensure Docker services are up.

**Step 2: Review E2E test coverage**

The mDoc E2E tests verify:
- Issuer metadata mDL entry validation
- mDL credential issuance flow
- mDoc CBOR structure
- COSE signature validation
- Device response handling
- Presentation flow with mDoc

---

## Task 3: Verify Issuer Metadata Endpoint (Live)

**Files:**
- Config: `docker-compose/issuer-api/config/credential-issuer-metadata.conf`

**Step 1: Test well-known endpoint**

Run: `curl -s http://localhost:7002/.well-known/openid-credential-issuer | jq '.credential_configurations_supported | keys | length'`
Expected: `42` (or similar count of credentials)

**Step 2: Verify EUDI PID mDoc configuration**

Run: `curl -s http://localhost:7002/.well-known/openid-credential-issuer | jq '.credential_configurations_supported["eu.europa.ec.eudi.pid.1"]'`
Expected output:
```json
{
  "format": "mso_mdoc",
  "scope": "eu.europa.ec.eudi.pid.1",
  "cryptographic_binding_methods_supported": ["jwk"],
  "credential_signing_alg_values_supported": ["ES256"],
  "proof_types_supported": {
    "jwt": {
      "proof_signing_alg_values_supported": ["ES256", "ES384", "ES512"]
    }
  },
  "doctype": "eu.europa.ec.eudi.pid.1"
}
```

**Step 3: Verify mDL configuration**

Run: `curl -s http://localhost:7002/.well-known/openid-credential-issuer | jq '.credential_configurations_supported["org.iso.18013.5.1.mDL"]'`
Expected: Similar structure with `format: "mso_mdoc"`, `doctype: "org.iso.18013.5.1.mDL"`, JWT proofs

**Step 4: Verify DC+SD-JWT PID configuration**

Run: `curl -s http://localhost:7002/.well-known/openid-credential-issuer | jq '.credential_configurations_supported["urn:eudi:pid:1"]'`
Expected: `format: "dc+sd-jwt"`, `vct: "urn:eudi:pid:1"`, JWT proofs

**Step 5: Verify DPoP support advertised**

Run: `curl -s http://localhost:7002/.well-known/openid-credential-issuer | jq '.dpop_signing_alg_values_supported'`
Expected: Array containing `["ES256", "ES384", "ES512", "RS256", "RS384", "RS512"]` or similar

---

## Task 4: Verify Draft 13 URL Rewriting (Caddy)

**Files:**
- Config: `docker-compose/Caddyfile`

**Step 1: Test RFC 8414 style URL rewrite**

Run: `curl -s http://localhost:7002/.well-known/openid-credential-issuer/draft13 | jq '.credential_issuer'`
Expected: Returns the issuer URL (proves Caddy rewrite works)

**Step 2: Test OAuth authorization server metadata rewrite**

Run: `curl -s -o /dev/null -w "%{http_code}" http://localhost:7002/.well-known/oauth-authorization-server/draft13`
Expected: `200` (not 404)

**Step 3: Verify root well-known redirect**

Run: `curl -s http://localhost:7002/.well-known/openid-credential-issuer | jq '.credential_configurations_supported | has("eu.europa.ec.eudi.pid.1")'`
Expected: `true`

---

## Task 5: Test Credential Offer Creation API

**Files:**
- API: `waltid-services/waltid-issuer-api/src/main/kotlin/id/walt/issuer/issuance/OidcApi.kt`

**Step 1: Create mDoc PID credential offer**

> **CRITICAL:** The issuer key MUST be a valid P-256 EC key with correct curve coordinates.
> Invalid x/y coordinates will cause: `ParseException: Invalid EC JWK: The 'x' and 'y' public coordinates are not on the P-256 curve`

Run:
```bash
curl -s -X POST http://localhost:7002/openid4vc/mdoc/issue \
  -H "Content-Type: application/json" \
  -d '{
    "issuerKey": {
      "type": "jwk",
      "jwk": {
        "kty": "EC",
        "x": "Ww_ry_HG_m1f6mvreCYQ5WZkGdYmxPz-ILOA1J8hzeo",
        "y": "kJFeftMI2vmBoKy8mKp28G4bcN4DrpI-otTjM_i1FAw",
        "crv": "P-256",
        "d": "yiM5fWb6rd4wXRzompXUKo2_dnWktT5gM7JHQEsNOHU"
      }
    },
    "credentialConfigurationId": "eu.europa.ec.eudi.pid.1",
    "mdocData": {
      "eu.europa.ec.eudi.pid.1": {
        "family_name": "DOE",
        "given_name": "JOHN",
        "birth_date": "1990-01-15",
        "age_over_18": true,
        "issuing_country": "AU",
        "issuing_authority": "Australia Government"
      }
    }
  }'
```
Expected: Returns `openid-credential-offer://...` URL

**To generate a new valid P-256 key:**
```bash
node -e "const c=require('crypto');const{privateKey}=c.generateKeyPairSync('ec',{namedCurve:'P-256'});console.log(JSON.stringify(privateKey.export({format:'jwk'}),null,2))"
```

**Step 2: Verify offer URL structure**

The returned URL should:
- Start with `openid-credential-offer://`
- Contain `credential_offer_uri=` parameter
- Be URL-encoded properly

**Step 3: Fetch the credential offer**

Extract the `credential_offer_uri` from previous step and fetch it:
Run: `curl -s "<extracted-credential-offer-uri>" | jq '.'`
Expected: JSON with `credential_issuer`, `credential_configuration_ids`, `grants`

---

## Task 6: Verify Portal Credential List (Optional)

**Files:**
- Portal: `waltid-applications/waltid-web-portal/`

**Step 1: Check portal is accessible**

Run: `curl -s -o /dev/null -w "%{http_code}" http://localhost:7102/`
Expected: `200`

**Step 2: Manual verification**

Open browser to `http://localhost:7102/` and verify:
- Credential types are listed
- mDoc formats are available (if portal has been rebuilt with latest changes)
- Can generate credential offer URLs

---

## Task 7: Test with EUDI Reference Wallet (Manual)

**Prerequisites:**
- EUDI Reference Wallet app installed on iOS/Android device
- Device on same network as Docker host, or services exposed via tunneling

**Step 1: Generate credential offer**

Use Task 5 Step 1 to generate a credential offer URL.

**Step 2: Convert to QR code**

Use any QR code generator to create QR from the offer URL, or access the portal.

**Step 3: Scan with EUDI wallet**

1. Open EUDI Reference Wallet app
2. Tap "Add Document" or similar
3. Scan QR code
4. Wallet should discover issuer metadata
5. Wallet should request credential with JWT proof
6. Issuer should respond with mDoc credential

**Step 4: Verify credential received**

Check that:
- Credential appears in wallet
- Credential displays correct claims (family_name, given_name, etc.)
- Credential format is mDoc

---

## Verification Checklist

After completing all tasks, verify:

| Check | Status |
|-------|--------|
| EUDI unit tests pass | ☐ |
| mDoc E2E tests pass | ☐ |
| Issuer metadata shows EUDI credentials | ✅ |
| JWT proofs configured (not CWT) | ✅ |
| Caddy URL rewrites work | ✅ |
| Credential offer creation works | ✅ |
| Portal accessible | ✅ |
| EUDI wallet can receive mDoc PID | ✅ |
| EUDI wallet can receive mDoc mDL | ✅ |
| EUDI wallet can receive SD-JWT PID | ✅ |

---

## Troubleshooting

**Tests fail with "connection refused":**
- Ensure Docker services are running: `docker compose --profile identity ps`
- Check service logs: `docker compose logs issuer-api`

**CWT proof errors:**
- Verify `proof_types_supported` only contains `jwt`, not `cwt`
- EUDI SDK doesn't support CWT proofs

**Nonce validation errors:**
- Check `OpenID4VCI.kt` allows missing nonce (EUDI compatibility)
- Review issuer logs for nonce debug info
- **FIX:** In `CIProvider.kt`, the nonce check in `doGenerateMDoc()` must be made optional:
  ```kotlin
  // EUDI compatibility: nonce is optional in proof
  val nonce = OpenID4VCI.getNonceFromProof(credentialRequest.proof!!)
  if (nonce == null) {
      log.info { "No nonce in proof - using credential_configuration_id matching (EUDI compatibility)" }
  }
  ```

**mDoc issuance fails:**
- Verify `extractHolderKey()` in `CIProvider.kt` handles JWT proofs
- Check issuer has valid signing key configured

**"Invalid EC JWK: The 'x' and 'y' public coordinates are not on the P-256 curve":**
- The issuer key has invalid curve coordinates
- Generate a new valid P-256 key using Node.js or OpenSSL
- Do NOT use placeholder/test values for x, y, d parameters

**EUDI wallet shows generic error but server logs show 500:**
- Use `adb logcat | grep -i "eu.europa"` to see wallet-side errors
- Common cause: server returning non-standard error response format
- Check `docker compose logs issuer-api` for the actual exception

---

## Verified Working Configuration (2026-02-03)

### EUDI-Compatible Credential Formats

| Credential | Config ID | Format | VCT/DocType | Status |
|------------|-----------|--------|-------------|--------|
| PID mDoc | `eu.europa.ec.eudi.pid.1` | `mso_mdoc` | `eu.europa.ec.eudi.pid.1` | ✅ Works |
| mDL | `org.iso.18013.5.1.mDL` | `mso_mdoc` | `org.iso.18013.5.1.mDL` | ✅ Works |
| PID SD-JWT | `eu.europa.ec.eudi.pid_vc_sd_jwt` | `dc+sd-jwt` | `urn:eudi:pid:1` | ✅ Works |

### Required Code Changes

**1. `credential-issuer-metadata.conf`:**
- Use `proof_types_supported.jwt` (not cwt) for all EUDI credentials
- Use `dc+sd-jwt` format for SD-JWT (NOT `vc+sd-jwt`)
- VCT must be `urn:eudi:pid:1` for SD-JWT PID

**2. `CIProvider.kt`:**
- Make nonce optional in `doGenerateMDoc()` for EUDI wallet compatibility
- Add `sd_jwt_dc` format to credential generation routing
- Add `sd_jwt_dc` format to VCT matching in `findMatchingIssuanceRequest`

**3. `IssuerApi.kt`:**
- Add `sd_jwt_dc` format to VCT handling when creating issuance requests

**4. `OidcApi.kt`:**
- Set `vct` (not `doctype`) for SD-JWT formats in credential request processing

**5. Keys:**
- Use valid P-256 EC keys with correct curve coordinates

### Successful Tests

**mDoc PID:**
- EUDI Reference Wallet (Android) ✅
- Config: `eu.europa.ec.eudi.pid.1`
- Format: `mso_mdoc` with JWT proof

**mDoc mDL:**
- EUDI Reference Wallet (Android) ✅
- Config: `org.iso.18013.5.1.mDL`
- Format: `mso_mdoc` with JWT proof

**SD-JWT PID:**
- EUDI Reference Wallet (Android) ✅
- Config: `eu.europa.ec.eudi.pid_vc_sd_jwt`
- Format: `dc+sd-jwt` with JWT proof
- VCT: `urn:eudi:pid:1`

### Issuer URL
`https://issuer.theaustraliahack.com/draft13`
