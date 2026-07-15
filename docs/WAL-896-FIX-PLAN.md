# WAL-896 Fix Plan — OID4VP 1.0 Final & HAIP Compliance

## Overview

PR #1885 (`feature/wal-896`) implements signed authorization requests and encrypted responses for OpenID4VP conformance. However, the implementation has **20 spec violations** identified in code review. This document tracks all required fixes.

**Spec References:**
- [OpenID4VP 1.0 Final](https://openid.net/specs/openid-4-verifiable-presentations-1_0.html)
- [HAIP (High Assurance Interoperability Profile)](https://openid.net/specs/openid4vc-high-assurance-interoperability-profile-1_0.html)

---

## Status Summary

| Category | Total | Fixed | Remaining |
|----------|-------|-------|-----------|
| Request Validation | 4 | 3 | 1 |
| Response Encryption | 4 | 0 | 4 |
| DCQL / Credentials | 2 | 0 | 2 |
| Compilation / Build | 2 | 0 | 2 |
| Configuration | 3 | 0 | 3 |
| Conformance Tests | 4 | 0 | 4 |
| Security | 1 | 0 | 1 |
| **Total** | **20** | **3** | **17** |

---

## Fixes Already Applied

### ✅ Fix 1: SignedRequestValidator not integrated
**File:** `SignedRequestValidator.kt`, `AuthorizationRequestResolver.kt`, `WalletPresentFunctionality2.kt`
**Issue:** `SignedRequestValidator` was defined but never called anywhere.
**Fix:** Integrated validator into both `AuthorizationRequestResolver.resolveFromRequestObject()` and `WalletPresentFunctionality2.walletPresentHandling()`. Removed ~100 lines of duplicate inline validation.

### ✅ Fix 2: Missing `typ` header validation
**File:** `SignedRequestValidator.kt`
**Spec:** OID4VP 1.0 §5.3
**Issue:** Did not check JOSE `typ` header.
**Fix:** Added validation that `typ` must equal `oauth-authz-req+jwt`. Rejects requests with missing or wrong typ.

### ✅ Fix 3: `aud` claim not mandatory / wrong handling
**File:** `SignedRequestValidator.kt`
**Spec:** OID4VP 1.0 §5.8
**Issue:** Accepted missing `aud`, logged instead of rejecting wrong values, allowed validation to be disabled.
**Fix:**
- Made `aud` mandatory (returns `Failure` if missing)
- Returns `Failure` for wrong values (no more logging-only)
- Removed `validateAudience` toggle parameter
- Added `walletAuthorizationEndpoint` parameter for dynamic audience validation
- Validates against either `https://self-issued.me/v2` (static) or wallet endpoint (dynamic)

### ✅ Fix 4: No outer/inner client_id equality check
**File:** `SignedRequestValidator.kt`
**Spec:** OID4VP 1.0 §5.1
**Issue:** Did not verify that `client_id` in URL parameters matches `client_id` in signed Request Object.
**Fix:** Added `outerClientId` parameter; returns `Failure` if outer and inner client_id don't match.

---

## Fixes Remaining

### Category: Request Validation

#### 🔴 Fix 5: X.509 trust chain not validated against wallet trust anchors
**File:** `SignedRequestValidator.kt`, client_id prefix authenticators
**Spec:** OID4VP 1.0 §5.2, HAIP §4.1
**Issue:** For `x509_san_dns` and `x509_hash` client_id schemes, the authenticator verifies the certificate chain internally but does not validate against the wallet's configured trust anchors.
**Required:**
- Add trust anchor configuration to wallet
- Validate certificate chain terminates at a trusted root
- For HAIP: validate against HAIP-specified trust anchors

---

### Category: Response Encryption

#### 🔴 Fix 6: JWK algorithm selection violates spec
**File:** `ResponseEncryptionHandler.kt`
**Spec:** OID4VP 1.0 §6.3
**Issue:** Selects first `use=enc` key or falls back to any first key. Does not require or evaluate JWK's `alg`, `kty`, `crv`, or wallet support.
**Required:**
- Selected JWK MUST contain `alg`
- Generated JWE `alg` MUST equal the JWK's `alg` value
- Selection must consider `kty`, `use`, `alg`, `crv`
- Wallet must verify it supports the specified algorithms

#### 🔴 Fix 7: Hardcoded ECDH-ES algorithm
**File:** `ResponseEncryptionHandler.kt`
**Spec:** OID4VP 1.0 §6.3
**Issue:** Algorithm is hardcoded to `ECDH-ES`. `EncryptionConfig.algAlgorithm` is never passed to encryption.
**Required:**
- Read `alg` from the selected JWK
- Pass to encryption function
- Remove hardcoded algorithm

#### 🔴 Fix 8: Encrypted mdoc uses wrong session transcript
**File:** `MdocPresenter.kt`
**Spec:** OID4VP 1.0 §7.4, ISO 18013-5
**Issue:** `MdocPresenter` constructs `OpenID4VPHandoverInfo` with null encryption-key thumbprint. For `direct_post.jwt`, the third handover element must contain the RFC 7638 thumbprint of the exact public key used to encrypt the response. Wallet and verifier sign/verify different transcripts.
**Required:**
- Select encryption key BEFORE generating mdoc VP
- Compute RFC 7638 thumbprint of selected key
- Pass thumbprint to `OpenID4VPHandoverInfo`
- Ensure wallet and verifier use identical transcript

#### 🔴 Fix 9: ResponseEncryptionHandlerTest doesn't verify encryption
**File:** `ResponseEncryptionHandlerTest.kt`
**Issue:** Tests only check configuration extraction. Never encrypts/decrypts or verifies JWE headers, transport encoding, or round-trip.
**Required:**
- Add tests that encrypt and decrypt responses
- Verify JWE `alg`, `enc`, `kid` headers
- Verify `application/x-www-form-urlencoded` transport
- Test wallet encryption → verifier decryption round-trip
- Test encrypted mdoc handover and device authentication

---

### Category: DCQL / Credentials

#### 🔴 Fix 10: ClaimsQuery uses draft-era fields
**File:** `ClaimsQuery.kt`, `DcqlMatcher.kt`, `DcSdJwtPresentation.kt`, `MdocPresenter.kt`
**Spec:** OID4VP 1.0 §7.2.2
**Issue:** Added nullable `path` plus separate `namespace` and `claimName` fields with synthetic `effectivePath()`. OID4VP 1.0 Final requires `path` in every Claims Query. For mdoc, `path` must contain exactly two strings: `[namespace, data_element_identifier]`.
**Required:**
- Remove `namespace` and `claimName` fields from `ClaimsQuery`
- Make `path` non-nullable (required)
- Remove `effectivePath()` and `pathKey()` helpers
- Update `MdocPresenter` to expect `path[0]` = namespace, `path[1]` = element ID
- Update `DcqlMatcher` and `DcSdJwtPresentation` to use `path` directly

#### 🔴 Fix 11: Request inspection exposes unauthenticated verifier info
**File:** `WalletPresentationHandler.kt`, `MobileWallet.kt`
**Spec:** Security best practice
**Issue:** `resolveRequest` fetches Request Objects using GET regardless of `request_uri_method`, and base64-decodes signed JWT payloads without validating signature, typ, audience, client ID, or certificate trust. Exposes `clientId`, `responseUri`, `nonce`, encryption details for consent UI. Attacker can present spoofed verifier information.
**Required:**
- Inspection must use the same authenticated resolver as presentation
- Or clearly mark inspection results as "unverified" in UI
- Consider: inspection should fully validate before showing to user

---

### Category: Compilation / Build

#### 🔴 Fix 12: X509HashUtils breaks JS/non-JVM compilation
**File:** `X509HashUtils.jvm.kt`
**Issue:** Added `expect fun computeX509HashAudience` but only JVM `actual` exists. Uses `java.util.Base64` and `java.security.MessageDigest` which are JVM-only.
**Required:**
- Use Kotlin stdlib Base64 (`kotlin.io.encoding.Base64`)
- Use multiplatform SHA-256 (e.g., from existing crypto dependencies)
- Add `actual` implementations for JS, native targets
- Or move to common code with multiplatform dependencies

#### 🔴 Fix 13: MobileWallet missing explicit visibility
**File:** `MobileWallet.kt`
**Issue:** New public declarations omit explicit visibility: `MobileWalletEncryptionInfo`, `MobileWalletRequestInspection`, `inspectRequest`. Causes `compileAndroidMain` error in explicit API mode.
**Required:**
- Add explicit `public` visibility to all new public declarations
- Or mark as `internal` if not intended for public API

---

### Category: Configuration

#### 🔴 Fix 14: Issuer2 key/DID mismatch
**File:** `issuer2-profiles.conf`
**Issue:** `defaultIssuerKey` was replaced, but `defaultIssuerDid` at line 27 still embeds the previous public key. Multiple profiles combine new signing key with stale DID. Credentials identify an issuer DID whose verification key does not match the signing key.
**Required:**
- Regenerate `defaultIssuerDid` from new `defaultIssuerKey`
- Or revert to original key
- Ensure all profiles use matching key/DID pairs

#### 🔴 Fix 15: Conformance certificate as global default
**File:** `issuer2-profiles.conf`
**Issue:** Short-lived conformance certificate (expires July 2027) set as global default for unrelated profiles.
**Required:**
- Isolate conformance-specific signing material to its own profile
- Restore original defaults for non-conformance profiles

#### 🔴 Fix 16: Wrong encryption metadata attribute names
**File:** `AuthorizationRequestResolver.kt`
**Spec:** OID4VP 1.0 §5.1
**Issue:** Emits `encrypted_response_alg_values_supported` and `encrypted_response_enc_values_supported`. OID4VP 1.0 uses `authorization_encryption_alg_values_supported` and `authorization_encryption_enc_values_supported`.
**Required:**
- Rename to spec-compliant attribute names
- Update `WalletCapabilities` data class
- Update `buildRequestUriPostWalletMetadata()`

---

### Category: Conformance Tests

#### 🔴 Fix 17: WalletTestPlanRunner fabricates results
**File:** `WalletTestPlanRunner.kt`
**Issue:** Treats generic strings like "NullPointerException" and "exception" as valid security rejection. Fabricates review result without reading conformance suite's final result.
**Required:**
- Only treat specific, expected security errors as valid rejections
- Always read and return conformance suite's actual final result
- Do not fabricate pass/fail status

#### 🔴 Fix 18: Conformance tests discard results
**File:** `VpWalletConformanceTests.kt`
**Issue:** JUnit tests discard the result returned by `WalletTestPlanRunner.test()`. Failed/errored/interrupted conformance modules don't fail Gradle.
**Required:**
- Assert on returned result
- Fail test if conformance module failed/errored/interrupted
- Log detailed failure information

#### 🔴 Fix 19: WalletConformanceAdapter uses legacy draft fields
**File:** `WalletConformanceAdapter.kt`
**Issue:** Calls Wallet2 endpoints with nonexistent request fields. Emits legacy OpenID draft `presentation_submission` parameter instead of OID4VP 1.0 Final format.
**Required:**
- Update to OID4VP 1.0 Final request/response format
- Remove `presentation_submission` if not required by 1.0
- Verify field names match spec

#### 🔴 Fix 20: ResponseEncryptionHandlerTest accepts non-conforming JWK
**File:** `ResponseEncryptionHandlerTest.kt`
**Issue:** Tests explicitly accept JWK without `alg` field, which is non-conforming behavior.
**Required:**
- Update tests to require `alg` in JWK
- Add test that rejects JWK without `alg`
- Ensure tests validate spec-compliant behavior

---

## Implementation Priority

### Phase 1: Critical / Blocks Merge (Compilation + Core Validation)
1. ~~Fix 1-4: SignedRequestValidator integration~~ ✅ DONE
2. Fix 12: X509HashUtils multiplatform
3. Fix 13: MobileWallet visibility
4. Fix 10: ClaimsQuery draft fields removal

### Phase 2: Spec Compliance (Response Encryption + Security)
5. Fix 6-7: JWK algorithm selection
6. Fix 8: Mdoc session transcript
7. Fix 11: Request inspection security
8. Fix 16: Encryption metadata attribute names

### Phase 3: Configuration + Tests
9. Fix 14-15: Issuer2 config isolation
10. Fix 17-20: Conformance test hardening
11. Fix 5: X.509 trust anchor validation
12. Fix 9: ResponseEncryptionHandler test coverage

---

## Spec Quick Reference

### OID4VP 1.0 Key Sections
- §5.1: Authorization Request — client_id, request_uri, request_uri_method
- §5.2: Client Identifier Schemes — x509_san_dns, x509_san_uri, x509_hash, did, etc.
- §5.3: Signed Authorization Request — typ MUST be `oauth-authz-req+jwt`
- §5.8: Audience — aud MUST be `https://self-issued.me/v2` or wallet endpoint
- §6.3: Response Encryption — JWK selection, algorithm requirements
- §7.2.2: DCQL ClaimsQuery — path is REQUIRED, format-specific interpretation

### HAIP Key Sections
- §4.1: Trust Framework — trust anchor requirements
- §4.2: Client Authentication — x509_san_dns requirements
- §5: Credential Formats — mdoc, SD-JWT VC requirements

---

## Change Log

| Date | Author | Changes |
|------|--------|---------|
| 2026-07-15 | walt-developer | Initial document; Fixes 1-4 applied |

