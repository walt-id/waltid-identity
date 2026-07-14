# OpenID4VC Conformance Test Plans & Profiles

## Naming Convention

Test plan classes follow this pattern:
```
[Protocol][Role][CredentialFormat][ClientIdScheme][RequestMethod][ResponseMode]
```

Examples:
- `VciWalletSdJwtDpop` - VCI Wallet with SD-JWT and DPoP
- `Oid4vciIssuerClientAttestationDpop` - VCI Issuer with Client Attestation and DPoP
- `SdJwtVcX509SanDnsRequestUriSignedDirectPost` - VP Verifier for SD-JWT VC with X.509 client ID
- `VpWalletSdJwtVcX509SanDnsRequestUriSignedDirectPost` - VP Wallet for SD-JWT VC with X.509 client ID

---

## Test Plans by Interface

### 1. OpenID4VCI - Wallet Role

#### VciWalletSdJwtDpop
**File:** `src/main/kotlin/id/walt/openid4vp/conformance/testplans/plans/vci/wallet/VciWalletSdJwtDpop.kt`  
**Test Class:** `VciWalletConformanceTests.kt`

**Configuration:**
- **Protocol:** OpenID4VCI 1.0
- **Role:** Wallet (Credential Receiver)
- **Credential Format:** SD-JWT VC (`vc+sd-jwt`)
- **Authentication:** DPoP + private_key_jwt
- **Flow:** Authorization Code
- **Key Binding:** openid4vci-proof+jwt

**Conformance Plan:** `oid4vci-1_0-wallet-test-plan`

**Status:** Stable SD-JWT VC reference profile

**HAIP Features:**
- ✅ DPoP (Demonstrating Proof-of-Possession)
- ✅ private_key_jwt client authentication
- ✅ Key binding proofs (openid4vci-proof+jwt)
- ✅ Nonce handling (c_nonce + nonce_endpoint)
- ✅ Authorization code flow

#### VciWalletSdJwtHaip
**File:** `src/main/kotlin/id/walt/openid4vp/conformance/testplans/plans/vci/wallet/VciWalletSdJwtHaip.kt`  
**Test Class:** `VciWalletConformanceTests.kt`

**Configuration:**
- **Protocol:** OpenID4VCI 1.0
- **Role:** Wallet (Credential Receiver)
- **Credential Format:** SD-JWT VC (`vc+sd-jwt`)
- **Authentication:** DPoP + private_key_jwt
- **Flow:** Authorization Code
- **Profile:** HAIP full target (`fapi_profile=vci_haip`)
- **Conformance Plan:** `oid4vci-1_0-wallet-haip-test-plan`

**Purpose:** Alternative wallet-side profile aligned to the currently implemented HAIP issuance criteria in OSS. Covers the HAIP-aligned auth-code, DPoP, offer, scope, and SD-JWT validation path without requiring wallet attestation interoperability yet.

#### VciWalletMdocDpop
**File:** `src/main/kotlin/id/walt/openid4vp/conformance/testplans/plans/vci/wallet/VciWalletMdocDpop.kt`  
**Test Class:** `VciWalletConformanceTests.kt`

**Configuration:**
- **Protocol:** OpenID4VCI 1.0
- **Role:** Wallet (Credential Receiver)
- **Credential Format:** ISO mdoc (suite variant `mdoc`, issued format `mso_mdoc`)
- **Authentication:** DPoP + private_key_jwt
- **Flow:** Authorization Code
- **Conformance Plan:** `oid4vci-1_0-wallet-test-plan`

**Purpose:** Wallet-side mdoc issuance profile using the standard VCI wallet test plan with suite variant `credential_format=mdoc` for issued format `mso_mdoc`.

---

### 2. OpenID4VCI - Issuer Role

#### Oid4vciIssuerClientAttestationDpop
**File:** `src/main/kotlin/id/walt/openid4vp/conformance/testplans/plans/vci/issuer/Oid4vciIssuerClientAttestationDpop.kt`  
**Test Class:** `IssuerConformanceTests.kt`

**Configuration:**
- **Protocol:** OpenID4VCI 1.0
- **Role:** Issuer (Credential Provider)
- **Credential Format:** SD-JWT VC (`vc+sd-jwt`)
- **Authentication:** DPoP + Client Attestation
- **Flow:** Authorization Code
- **Key Binding:** openid4vci-proof+jwt

**Test Plan Name:** `oid4vci-1_0-issuer-test-credential-issuance-dpop-client_attestation-sd_jwt_vc-issuer_initiated-simple-immediate-unsigned-authorization_code-by_value-plain`

**Status:** ⚠️ **53/55 tests passing**

**Known Issues:**
1. Missing RFC 9207 `iss` in authorization response
2. Unexpected HTTP status on credential endpoint

**HAIP Features:**
- ✅ DPoP support
- ✅ Client attestation
- ✅ Authorization code flow
- ✅ SD-JWT VC issuance

---

### 3. OpenID4VP - Verifier Role

#### SdJwtVcX509SanDnsRequestUriSignedDirectPost
**File:** `src/main/kotlin/id/walt/openid4vp/conformance/testplans/plans/vp/verifier/SdJwtVcX509SanDnsRequestUriSignedDirectPost.kt`  
**Test Class:** `VerifierConformanceTests.kt`

**Configuration:**
- **Protocol:** OpenID4VP 1.0
- **Role:** Verifier (Presentation Requestor)
- **Credential Format:** SD-JWT VC (`dc+sd-jwt`)
- **Client ID Scheme:** `x509_san_dns`
- **Request Method:** `request_uri_signed` (JAR)
- **Response Mode:** `direct_post.jwt` (encrypted)

**Test Plan Name:** `oid4vp-1final-verifier-sd-jwt-vc-haip-test-plan`

**Status:** ⚠️ Needs trust anchor configuration

**HAIP Features:**
- ✅ X.509 certificate-based client ID
- ✅ Signed authorization requests (JAR)
- ✅ Encrypted response handling
- ⚠️ Trust anchor configuration needed

---

#### MdlX509SanDnsRequestUriSignedDirectPost
**File:** `src/main/kotlin/id/walt/openid4vp/conformance/testplans/plans/vp/verifier/MdlX509SanDnsRequestUriSignedDirectPost.kt`  
**Test Class:** `VerifierConformanceTests.kt`

**Configuration:**
- **Protocol:** OpenID4VP 1.0
- **Role:** Verifier (Presentation Requestor)
- **Credential Format:** mDL / ISO 18013-5 (`mso_mdoc`)
- **Client ID Scheme:** `x509_san_dns`
- **Request Method:** `request_uri_signed` (JAR)
- **Response Mode:** `direct_post.jwt` (encrypted)

**Test Plan Name:** `oid4vp-1final-verifier-mdl-haip-test-plan`

**Status:** ✅ **Tests passing**

**HAIP Features:**
- ✅ X.509 certificate-based client ID
- ✅ Signed authorization requests (JAR)
- ✅ Encrypted response handling
- ✅ mDL (ISO 18013-5) validation
- ✅ DeviceAuth verification

---

### 4. OpenID4VP - Wallet Role

#### VpWalletSdJwtVcX509SanDnsRequestUriSignedDirectPost
**File:** `src/main/kotlin/id/walt/openid4vp/conformance/testplans/plans/vp/wallet/VpWalletSdJwtVcX509SanDnsRequestUriSignedDirectPost.kt`  
**Test Class:** `VpWalletConformanceTests.kt`

**Configuration:**
- **Protocol:** OpenID4VP 1.0
- **Role:** Wallet (Presentation Provider)
- **Credential Format:** SD-JWT VC (`dc+sd-jwt`)
- **Client ID Scheme:** `x509_san_dns`
- **Request Method:** `request_uri_signed` (JAR)
- **Response Mode:** `direct_post.jwt` (encrypted)

**Test Plan Name:** `oid4vp-1final-wallet-haip-test-plan`

**Variant:**
```json
{
  "credential_format": "sd_jwt_vc",
  "response_mode": "direct_post.jwt"
}
```

**Status:** ✅ **11/12 tests passing (92%)**

**Modules (12):**
| # | Module | Status | Notes |
|---|--------|--------|-------|
| 1 | `oid4vp-1final-wallet-happy-flow` | ✅ PASSED | Baseline VP flow |
| 2 | `oid4vp-1final-wallet-alternate-happy-flow` | ⚠️ SKIPPED | Test automation limitation (requires browser redirect) |
| 3 | `oid4vp-1final-wallet-request-uri-method-post` | ✅ PASSED | POST method works |
| 4 | `oid4vp-1final-wallet-fewer-claims-than-available` | ✅ PASSED | Selective disclosure |
| 5 | `oid4vp-1final-wallet-optional-credential-set` | ✅ PASSED | Optional credentials |
| 6 | `oid4vp-1final-wallet-no-claims-in-dcql-query` | ✅ PASSED | DCQL without claims |
| 7 | `oid4vp-1final-wallet-negative-test-invalid-request-signature` | ✅ REJECTED | Correctly rejects invalid JAR signature |
| 8 | `oid4vp-1final-wallet-negative-test-mismatched-client-id` | ✅ REJECTED | Correctly rejects client_id mismatch |
| 9 | `oid4vp-1final-wallet-negative-test-redirect-uri-with-direct-post` | ✅ REJECTED | Correctly rejects redirect_uri with direct_post |
| 10 | `oid4vp-1final-wallet-negative-test-missing-nonce` | ✅ REJECTED | Correctly rejects missing nonce |
| 11 | `oid4vp-1final-wallet-negative-test-invalid-client-id-prefix` | ✅ REJECTED | Correctly rejects invalid client_id prefix |
| 12 | `oid4vp-1final-wallet-negative-test-unknown-transaction-data-type` | ✅ REJECTED | Correctly rejects unknown transaction_data types |

**HAIP Features Validated:**
- ✅ Signed request authentication (JAR parsing)
- ✅ Encrypted response generation (JWE)
- ✅ KB-JWT holder binding
- ✅ P-256 key curve enforcement
- ✅ SHA-256 hash algorithm
- ✅ X.509 certificate chain validation (x509_hash)
- ✅ Negative test rejection (6/6 security validations)

**Known Limitation:**
The `alternate-happy-flow` test requires a real browser to navigate to `redirect_uri#fragment`. Our headless test runner cannot simulate browser redirects. The wallet correctly returns the `redirect_uri`, but the conformance suite waits for actual browser navigation.

---

#### VpWalletMdlX509SanDnsRequestUriSignedDirectPost
**File:** `src/main/kotlin/id/walt/openid4vp/conformance/testplans/plans/vp/wallet/VpWalletMdlX509SanDnsRequestUriSignedDirectPost.kt`  
**Test Class:** `VpWalletConformanceTests.kt`

**Configuration:**
- **Protocol:** OpenID4VP 1.0
- **Role:** Wallet (Presentation Provider)
- **Credential Format:** mDL / ISO 18013-5 (`mso_mdoc`)
- **Client ID Scheme:** `x509_san_dns`
- **Request Method:** `request_uri_signed` (JAR)
- **Response Mode:** `direct_post.jwt` (encrypted)

**Test Plan Name:** `oid4vp-1final-wallet-haip-test-plan`

**Variant:**
```json
{
  "credential_format": "iso_mdl",
  "response_mode": "direct_post.jwt"
}
```

**Status:** 🚧 **Pending validation** - Runner ready, needs mDL credential setup

---

#### VpWalletNegativeTests
**Note:** Negative tests are included as part of the HAIP test plans above, not as a separate plan.

The OIDF conformance suite includes negative tests within the main `oid4vp-1final-wallet` test plan. These are identified by module names containing `negative-test`.

**Negative Test Behavior:**
- Conformance suite sends an invalid/malformed request
- Wallet must **reject** the request (return error, NOT call `response_uri`)
- Suite status shows `REVIEW` (awaiting screenshot for certification)
- Our runner treats `REVIEW` as `REJECTED` ✅ (wallet behaved correctly)

**Why REVIEW instead of PASSED?**
For official OIDF certification, a screenshot of the wallet's error UI is required. Since we run headless automation, there's no UI to screenshot. However, the protocol behavior is validated:
- ✅ Wallet detects invalid request
- ✅ Wallet returns HTTP 400 to caller  
- ✅ Wallet does NOT call verifier's `response_uri`

---

## Summary by Status

### ✅ Passing
1. **VpWalletSdJwtVcX509HashRequestUriSignedDirectPostHaip** - 11/12 tests (92%), 6/6 negative tests
2. **MdlX509SanDnsRequestUriSignedDirectPost** (Verifier) - mDL tests passing

### ✅ Validated Runner Profiles
1. **VciWalletSdJwtDpop** - stable SD-JWT VC reference profile
2. **VciWalletMdocDpop** - stable ISO mdoc reference profile
3. **VciWalletSdJwtHaip** - HAIP-oriented baseline profile

### ⚠️ Mostly Working (Minor Issues)
1. **Oid4vciIssuerClientAttestationDpop** - 53/55 tests passing
2. **SdJwtVcX509SanDnsRequestUriSignedDirectPost** (Verifier) - needs trust anchor config

### 🚧 Pending Validation
1. **VpWalletMdlX509SanDnsRequestUriSignedDirectPost** - Runner ready, needs mDL credential setup

---

## Test Coverage Matrix

| Interface | Role | Credential Format | Client Auth | Status |
|-----------|------|------------------|-------------|--------|
| OpenID4VCI | Wallet | SD-JWT VC | DPoP + private_key_jwt | ⚠️ Runner currently executes first module only |
| OpenID4VCI | Wallet | ISO mdoc | DPoP + private_key_jwt | ⚠️ Runner currently executes first module only |
| OpenID4VCI | Issuer | SD-JWT VC | DPoP + Client Attestation | ⚠️ 53/55 |
| OpenID4VP | Verifier | SD-JWT VC | x509_san_dns | ⚠️ Config |
| OpenID4VP | Verifier | mDL | x509_san_dns | ✅ Passing |
| OpenID4VP | Wallet | SD-JWT VC | x509_hash (HAIP) | ✅ 11/12 (92%) |
| OpenID4VP | Wallet | mDL | x509_san_dns | 🚧 Pending |

---

## HAIP Coverage

All test plans validate HAIP (High Assurance Interoperability Profile) requirements:

### Mandatory Features
- ✅ Signed requests (JAR for VP, DPoP for VCI)
- ✅ Encrypted responses (direct_post.jwt)
- ✅ P-256 key curve
- ✅ SHA-256 hash algorithm
- ✅ Holder binding (KB-JWT or DeviceAuth)

### Client Authentication Methods
- ✅ DPoP (Demonstrating Proof-of-Possession)
- ✅ private_key_jwt
- ✅ Client Attestation
- ✅ X.509 SAN DNS

---

## Test Execution

### Run All Tests
```bash
./gradlew :waltid-services:waltid-openid4vp-conformance-runners:test
```

### Run by Interface
```bash
# VCI Wallet (profile runner)
./gradlew :waltid-services:waltid-openid4vp-conformance-runners:test --tests "VciWalletConformanceTests"

# VCI Issuer (mostly passing)
export OPENID4VCI_CONFORMANCE_CREDENTIAL_ISSUER_URL="https://YOUR-NGROK.ngrok-free.app/openid4vc"
./gradlew :waltid-services:waltid-openid4vp-conformance-runners:test --tests "IssuerConformanceTests"

# VP Verifier (partial)
export VERIFIER_NGROK_URL="https://YOUR-NGROK.ngrok-free.app"
./gradlew :waltid-services:waltid-openid4vp-conformance-runners:test --tests "VerifierConformanceTests"

# VP Wallet (will skip until WAL-896)
./gradlew :waltid-services:waltid-openid4vp-conformance-runners:test --tests "VpWalletConformanceTests"
```

### Run Specific Test Plan
```bash
# SD-JWT VC VP Wallet
./gradlew :waltid-services:waltid-openid4vp-conformance-runners:test \
    --tests "VpWalletConformanceTests.VP Wallet - SD-JWT VC*"

# mDL VP Wallet
./gradlew :waltid-services:waltid-openid4vp-conformance-runners:test \
    --tests "VpWalletConformanceTests.VP Wallet - mDL*"

# Negative Tests
./gradlew :waltid-services:waltid-openid4vp-conformance-runners:test \
    --tests "VpWalletConformanceTests.VP Wallet - Negative*"
```

---

## Files

### Test Plan Classes (7 total)
```
src/main/kotlin/id/walt/openid4vp/conformance/testplans/plans/
├── vci/
│   ├── wallet/
│   │   ├── VciWalletSdJwtDpop.kt                                      ✅
│   │   ├── VciWalletMdocDpop.kt                                       ✅
│   │   └── VciWalletSdJwtHaip.kt                                      ⚠️
│   └── issuer/
│       └── Oid4vciIssuerClientAttestationDpop.kt                       ⚠️
└── vp/
    ├── verifier/
    │   ├── SdJwtVcX509SanDnsRequestUriSignedDirectPost.kt              ⚠️
    │   └── MdlX509SanDnsRequestUriSignedDirectPost.kt                  ✅
    └── wallet/
        ├── VpWalletSdJwtVcX509SanDnsRequestUriSignedDirectPost.kt     🚫
        ├── VpWalletMdlX509SanDnsRequestUriSignedDirectPost.kt         🚫
        └── VpWalletNegativeTests.kt                                   🚫
```

### Test Classes (4 total)
```
src/test/kotlin/id/walt/openid4vp/conformance/
├── VciWalletConformanceTests.kt       ⚠️ Profile runner
├── IssuerConformanceTests.kt          ⚠️ 53/55
├── VerifierConformanceTests.kt        ⚠️ Partial
└── VpWalletConformanceTests.kt        🚫 Ready
```
# Specific VCI wallet profiles
./gradlew :waltid-services:waltid-openid4vp-conformance-runners:vciWalletSdJwtVcDpopAuthorizationCode \
    -PrunIntegrationTests

./gradlew :waltid-services:waltid-openid4vp-conformance-runners:vciWalletIsoMdocDpopAuthorizationCode \
    -PrunIntegrationTests

./gradlew :waltid-services:waltid-openid4vp-conformance-runners:vciWalletSdJwtVcAuthorizationCodeHaipFullTarget \
    -PrunIntegrationTests
