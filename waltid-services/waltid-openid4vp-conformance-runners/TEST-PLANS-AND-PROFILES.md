# OpenID4VC Conformance Test Plans & Profiles

> **Issuer2 users:** This document is a test-plan inventory, not the local issuer2 setup guide. For
> prerequisites, issuer2 configuration, Docker lifecycle, wrapper commands, and reports, follow
> [docs/VCI-ISSUER.md](docs/VCI-ISSUER.md). In particular, use
> `run-issuer-conformance-local.sh` for an issuer2 conformance run; a plain Gradle test command does not
> prepare the local suite, proxy, truststore, or issuer2.

## Naming Convention

Test plan classes follow this pattern:
```
[Protocol][Role][CredentialFormat][ClientIdScheme][RequestMethod][ResponseMode]
```

Examples:
- `WalletVariantMatrix` - VCI Wallet suite-plan matrix
- `Oid4vciIssuerVariantPlan` - VCI Issuer matrix variant
- `SdJwtVcX509SanDnsRequestUriSignedDirectPost` - VP Verifier for SD-JWT VC with X.509 client ID
- `VpWalletSdJwtVcX509SanDnsRequestUriSignedDirectPost` - VP Wallet for SD-JWT VC with X.509 client ID

---

## Test Plans by Interface

### 1. OpenID4VCI - Wallet Role

**Files:** `plans/vci/wallet/WalletVariantMatrix.kt`,
`plans/vci/wallet/Oid4vciWalletVariantPlan.kt`, and
`WalletConformanceTestRunner.kt`
**Test Class:** `VciWalletConformanceTests.kt`

The VCI wallet runner creates the suite-defined contexts instead of using fixed
profiles:

- Basic plan: `oid4vci-1_0-wallet-test-plan`, 1,728 valid contexts.
- HAIP plan: `oid4vci-1_0-wallet-haip-test-plan`, 6 plan contexts with the
  remaining immediate/deferred/encryption variants supplied by suite modules.

It runs modules through Wallet API2 and records supported, failed, skipped, and
blocked contexts. See [docs/VCI-WALLET.md](docs/VCI-WALLET.md) for the exact
axes, environment filters, wrapper commands, and current implementation limits.

---

### 2. OpenID4VCI - Issuer Role

#### Oid4vciIssuerVariantPlan
**File:** `src/main/kotlin/id/walt/openid4vp/conformance/testplans/plans/vci/issuer/Oid4vciIssuerVariantPlan.kt`
**Test Class:** `IssuerConformanceTests.kt`

**Configuration:**
- **Protocol:** OpenID4VCI 1.0
- **Role:** Issuer (Credential Provider)
- **Credential Formats:** SD-JWT VC and mdoc
- **Grant Types:** Authorization Code and Pre-Authorized Code
- **Authentication:** Client Attestation, private_key_jwt, and mTLS
- **Sender Constraints:** DPoP and mTLS

**Test Plan Names:**
- Base VCI: `oid4vci-1_0-issuer-test-plan`
- HAIP VCI: `oid4vci-1_0-issuer-haip-test-plan`

The runner generates the 288 valid `fapi_profile=vci` combinations described in
the README. Environment filters select subsets without introducing separate fixed
plan classes. Issuer-initiated variants receive a fresh issuer2 credential offer
for each module.

The runner also generates the 8 `fapi_profile=vci_haip` issuer variants supported
by the HAIP issuer plan: SD-JWT VC/mdoc, issuer-initiated/wallet-initiated
authorization code, plain/encrypted credential response, client attestation, DPoP,
simple authorization request, and unsigned request method. HAIP variants can use
dedicated credential configuration IDs so issuer2 selects profiles with `x5Chain`
and emits credential `x5c` material.

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

**Status:** 🚫 **Blocked** - Awaiting WAL-896 implementation

**Expected Modules (14):**
- `oid4vp-1final-wallet-happy-flow`
- `oid4vp-1final-wallet-alternate-request-object-claims`
- `oid4vp-1final-wallet-request-uri-method-post`
- `oid4vp-1final-wallet-dcql-sd-jwt-vc-happy-flow`
- `oid4vp-1final-wallet-dcql-sd-jwt-vc-credential-query`
- `oid4vp-1final-wallet-dcql-sd-jwt-vc-single-credential-multiple-queries`
- `oid4vp-1final-wallet-ensure-request-object-always-signed`
- `oid4vp-1final-wallet-ensure-request-uri-always-present`
- `oid4vp-1final-wallet-ensure-client-id-equals-client-id-scheme`
- `oid4vp-1final-wallet-ensure-client-id-x509-san-dns`
- `oid4vp-1final-wallet-ensure-response-type-always-vp-token`
- `oid4vp-1final-wallet-ensure-response-mode-direct-post-jwt`
- `oid4vp-1final-wallet-ensure-response-encrypted`
- `oid4vp-1final-wallet-ensure-nonce-always-present`

**HAIP Features to Test:**
- 🚫 Signed request authentication (JAR parsing)
- 🚫 Encrypted response generation (JWE)
- 🚫 KB-JWT holder binding
- 🚫 P-256 key curve enforcement
- 🚫 SHA-256 hash algorithm
- 🚫 X.509 certificate chain validation

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

**Status:** 🚫 **Blocked** - Awaiting WAL-896 implementation

**Expected Modules (6):**
- `oid4vp-1final-wallet-mdl-happy-flow`
- `oid4vp-1final-wallet-mdl-device-auth`
- `oid4vp-1final-wallet-mdl-session-transcript`
- `oid4vp-1final-wallet-mdl-invalid-mso-signature`
- `oid4vp-1final-wallet-mdl-invalid-device-signature`
- `oid4vp-1final-wallet-mdl-replay-protection`

**HAIP Features to Test:**
- 🚫 Signed request authentication (JAR parsing)
- 🚫 Encrypted response generation (JWE)
- 🚫 DeviceAuth holder binding
- 🚫 Session transcript validation (ISO 18013-7 Annex C)
- 🚫 X.509 certificate chain validation

---

#### VpWalletNegativeTests
**File:** `src/main/kotlin/id/walt/openid4vp/conformance/testplans/plans/vp/wallet/VpWalletNegativeTests.kt`  
**Test Class:** `VpWalletConformanceTests.kt`

**Configuration:**
- **Protocol:** OpenID4VP 1.0
- **Role:** Wallet (Presentation Provider)
- **Credential Format:** SD-JWT VC (`dc+sd-jwt`)
- **Test Type:** Negative / Security Validation

**Test Plan Name:** `oid4vp-1final-wallet-haip-test-plan`

**Variant:**
```json
{
  "credential_format": "sd_jwt_vc",
  "response_mode": "direct_post.jwt"
}
```

**Status:** 🚫 **Blocked** - Awaiting WAL-896 implementation

**Expected Modules (9):**
- `oid4vp-1final-wallet-reject-unsigned-request`
- `oid4vp-1final-wallet-reject-cleartext-response`
- `oid4vp-1final-wallet-reject-weak-curve`
- `oid4vp-1final-wallet-reject-weak-hash`
- `oid4vp-1final-wallet-reject-missing-holder-binding`
- `oid4vp-1final-wallet-reject-expired-certificate`
- `oid4vp-1final-wallet-reject-untrusted-ca`
- `oid4vp-1final-wallet-reject-wallet-nonce-mismatch`
- `oid4vp-1final-wallet-reject-insecure-origin`

**HAIP Security Requirements:**
- Must reject unsigned requests
- Must reject cleartext response requests
- Must reject weak cryptographic parameters
- Must reject untrusted certificates

---

## Summary by Status

### ⚠️ Validated Runner Profiles
1. **WalletVariantMatrix** - generated basic and HAIP VCI wallet contexts
2. **Oid4vciIssuerVariantPlan** - generated base VCI issuer matrix

### ⚠️ Mostly Working (Minor Issues)
1. **MdlX509SanDnsRequestUriSignedDirectPost** - mDL tests passing
2. **SdJwtVcX509SanDnsRequestUriSignedDirectPost** - needs trust anchor config

### 🚫 Framework Ready (Awaiting Implementation)
5. **VpWalletSdJwtVcX509SanDnsRequestUriSignedDirectPost** - awaiting WAL-896
6. **VpWalletMdlX509SanDnsRequestUriSignedDirectPost** - awaiting WAL-896
7. **VpWalletNegativeTests** - awaiting WAL-896

---

## Test Coverage Matrix

| Interface | Role | Credential Format | Client Auth | Status |
|-----------|------|------------------|-------------|--------|
| OpenID4VCI | Wallet | SD-JWT VC, mdoc | Suite-defined client authentication and sender constraints | ⚠️ Generated matrix; support is reported per module |
| OpenID4VCI | Issuer | SD-JWT VC | DPoP + Client Attestation | ⚠️ 53/55 |
| OpenID4VP | Verifier | SD-JWT VC | x509_san_dns | ⚠️ Config |
| OpenID4VP | Verifier | mDL | x509_san_dns | ✅ Passing |
| OpenID4VP | Wallet | SD-JWT VC | x509_san_dns | 🚫 WAL-896 |
| OpenID4VP | Wallet | mDL | x509_san_dns | 🚫 WAL-896 |
| OpenID4VP | Wallet | Negative Tests | x509_san_dns | 🚫 WAL-896 |

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

Except for the issuer2 wrapper command below, these Gradle commands are run
from the `waltid-identity` repository root.

### Run All Tests
```bash
./gradlew :waltid-services:waltid-openid4vp-conformance-runners:test
```

### Run by Interface
```bash
# VCI Wallet, terminal 1: start Wallet API2 with the local conformance truststore
cd waltid-services/waltid-openid4vp-conformance-runners
./run-wallet-api2-conformance-local.sh
```

```bash
# VCI Wallet, terminal 2: run the selected plan from the conformance-runner module
cd waltid-services/waltid-openid4vp-conformance-runners
./run-wallet-conformance-local.sh
```

```bash
# VCI Issuer: from the conformance-runner module after completing docs/VCI-ISSUER.md setup
cd waltid-services/waltid-openid4vp-conformance-runners
./run-issuer-conformance-local.sh
```

```bash
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

### Selected Test Plan Classes
```
src/main/kotlin/id/walt/openid4vp/conformance/testplans/plans/
├── vci/
│   ├── wallet/
│   │   ├── WalletVariantMatrix.kt                                     ⚠️
│   │   └── Oid4vciWalletVariantPlan.kt                                ⚠️
│   └── issuer/
│       └── Oid4vciIssuerVariantPlan.kt                                 ⚠️
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
├── VciWalletConformanceTests.kt       ⚠️ Matrix runner
├── IssuerConformanceTests.kt          ⚠️ 53/55
├── VerifierConformanceTests.kt        ⚠️ Partial
└── VpWalletConformanceTests.kt        🚫 Ready
```
