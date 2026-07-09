# VP Wallet Conformance Tests

This document covers setup, execution, and status of OpenID4VP Wallet conformance tests.

## Test Profiles

### HAIP-Compliant Profiles (Recommended)

| Profile | Test Plan | Format | Client ID | Response Mode | Status |
|---------|-----------|--------|-----------|---------------|--------|
| SD-JWT VC HAIP | `oid4vp-1final-wallet-haip-test-plan` | SD-JWT VC | **x509_hash** | direct_post.jwt | 🔄 Not yet tested |
| mDL HAIP | `oid4vp-1final-wallet-haip-test-plan` | ISO mDL | **x509_hash** | direct_post.jwt | 🔄 Not yet tested |
| Negative Security | `oid4vp-1final-wallet-haip-test-plan` | SD-JWT VC | **x509_hash** | direct_post.jwt | 🔄 Not yet tested |

### Baseline Profiles (Non-HAIP)

| Profile | Test Plan | Format | Client ID | Response Mode | Status |
|---------|-----------|--------|-----------|---------------|--------|
| SD-JWT VC Baseline | `oid4vp-1final-wallet-haip-test-plan` | SD-JWT VC | x509_san_dns | direct_post.jwt | 🔄 Not yet tested |
| mDL Baseline | `oid4vp-1final-wallet-haip-test-plan` | ISO mDL | x509_san_dns | direct_post.jwt | 🔄 Not yet tested |

> ⚠️ **HAIP Requirement P-02**: For full HAIP compliance, `x509_hash` MUST be used instead of `x509_san_dns`.
> The baseline profiles are provided for incremental testing but do NOT satisfy HAIP certification.

**Last tested:** Not yet run

---

## Current Status

### Test Results

_Tests have not been run yet. This section will be updated with actual results._

### Architecture

The VP Wallet conformance tests use an **adapter pattern**:

```
┌─────────────────┐    ┌─────────────────┐    ┌─────────────────┐
│ Conformance     │    │ Wallet Adapter  │    │ Wallet API2     │
│ Suite (Verifier)│───▶│ (port 7006)     │───▶│ (port 7001)     │
└─────────────────┘    └─────────────────┘    └─────────────────┘
        │                      │
        │                      ▼
        │              1. Receive auth request
        │              2. Resolve request (JAR)
        │              3. Match credentials
        │              4. Generate presentation
        │              5. Encrypt response (JWE)
        │                      │
        ◀──────────────────────┘
        6. POST encrypted VP response to response_uri
```

The **VpWalletConformanceAdapter** bridges the conformance suite with the walt.id wallet API by:
1. Exposing an HTTP endpoint that conformance suite invokes
2. Fetching authorization requests from conformance suite
3. Calling wallet API to resolve, match, and present credentials
4. Sending encrypted VP responses back to conformance suite

---

## Prerequisites

1. **Conformance Suite** running at `https://localhost.emobix.co.uk:8443`
   ```bash
   cd ~/dev/openid/conformance-suite
   docker compose -f docker-compose-walt.yml up -d
   ```

2. **Wallet API2** running on port 7001

3. **Test Credentials** provisioned in wallet
   - SD-JWT VC matching `identity_credential` vct
   - mDL (ISO 18013-5.1) credential

---

## Setup

### 1. Start Wallet API2

```bash
cd ~/dev/walt-id/waltid-unified-build
./gradlew :waltid-services:waltid-wallet-api2:run
```

Verify wallet is running:
```bash
curl -s http://127.0.0.1:7001/health | jq .
```

### 2. Provision Test Credentials

The wallet needs credentials that match what the conformance suite will request:

**SD-JWT VC:**
```json
{
  "vct": "https://credentials.example.com/identity_credential",
  "given_name": "Test",
  "family_name": "User",
  "birthdate": "1990-01-01"
}
```

**mDL:**
```json
{
  "docType": "org.iso.18013.5.1.mDL",
  "org.iso.18013.5.1": {
    "family_name": "User",
    "given_name": "Test",
    "birth_date": "1990-01-01"
  }
}
```

### 3. Configure Test Wallet

Ensure a test wallet exists with ID `conformance-test-wallet` or update the adapter configuration.

---

## Running Tests

```bash
cd ~/dev/walt-id/waltid-unified-build

# Run all VP Wallet tests (HAIP-compliant with x509_hash)
./gradlew :waltid-services:waltid-openid4vp-conformance-runners:test --tests "VpWalletConformanceTests"

# Run HAIP SD-JWT VC test specifically
./gradlew :waltid-services:waltid-openid4vp-conformance-runners:test \
  --tests "VpWalletConformanceTests.VP Wallet - SD-JWT VC HAIP*"

# Run HAIP mDL test specifically
./gradlew :waltid-services:waltid-openid4vp-conformance-runners:test \
  --tests "VpWalletConformanceTests.VP Wallet - mDL HAIP*"
```

### Test Execution Flow

1. **Adapter starts** on port 7006
2. **Test plan created** on conformance suite with `x509_hash` client_id_scheme
3. **Conformance suite** sends JAR authorization request to adapter
4. **Adapter** calls wallet API2 to process request
5. **Wallet** validates x509_hash, generates encrypted VP response with KB-JWT/DeviceAuth
6. **Adapter** POSTs JWE-encrypted response to conformance suite
7. **Conformance suite** validates response

---

## Environment Variables

| Variable | Description | Default |
|----------|-------------|---------|
| `WALLET_API_URL` | Wallet API2 base URL | `http://127.0.0.1:7001` |
| `WALLET_ADAPTER_PORT` | Adapter port | `7006` |
| `CONFORMANCE_HOST` | Conformance suite host | `localhost.emobix.co.uk` |
| `CONFORMANCE_PORT` | Conformance suite port | `8443` |

---

## Test Plans

### SD-JWT VC HAIP Test Plan (x509_hash)

**File:** `VpWalletSdJwtVcX509HashRequestUriSignedDirectPostHaip.kt`

Tests wallet's ability to:
- Authenticate signed authorization requests (**x509_hash** — HAIP §5 P-02)
- Validate x509_hash matches SHA-256(DER(leaf_cert))
- Generate encrypted VP responses (direct_post.jwt)
- Include KB-JWT holder binding
- Use P-256 keys and SHA-256 hashing

**HAIP Requirements Covered:**
- P-02: x509_hash client identification (MANDATORY)
- W-27: JAR with request_uri
- W-28: direct_post.jwt response mode
- W-36: KB-JWT holder binding
- CF-02: P-256 + SHA-256

**Expected test modules (14):**
- `oid4vp-1final-wallet-happy-flow`
- `oid4vp-1final-wallet-alternate-request-object-claims`
- `oid4vp-1final-wallet-request-uri-method-post`
- `oid4vp-1final-wallet-dcql-sd-jwt-vc-happy-flow`
- `oid4vp-1final-wallet-dcql-sd-jwt-vc-credential-query`
- `oid4vp-1final-wallet-dcql-sd-jwt-vc-single-credential-multiple-queries`
- `oid4vp-1final-wallet-ensure-request-object-always-signed`
- `oid4vp-1final-wallet-ensure-request-uri-always-present`
- `oid4vp-1final-wallet-ensure-client-id-equals-client-id-scheme`
- `oid4vp-1final-wallet-ensure-client-id-x509-hash`
- `oid4vp-1final-wallet-ensure-response-type-always-vp-token`
- `oid4vp-1final-wallet-ensure-response-mode-direct-post-jwt`
- `oid4vp-1final-wallet-ensure-response-encrypted`
- `oid4vp-1final-wallet-ensure-nonce-always-present`

### mDL HAIP Test Plan (x509_hash)

**File:** `VpWalletMdlX509HashRequestUriSignedDirectPostHaip.kt`

Tests wallet's ability to:
- Authenticate signed authorization requests (**x509_hash** — HAIP §5 P-02)
- Validate x509_hash matches SHA-256(DER(leaf_cert))
- Generate encrypted VP responses with mdoc (direct_post.jwt)
- Include DeviceAuth holder binding (MSO + DeviceSignature)
- Validate session transcript per ISO 18013-7 Annex C

**HAIP Requirements Covered:**
- P-02: x509_hash client identification (MANDATORY)
- W-27: JAR with request_uri
- W-28: direct_post.jwt response mode
- W-35: Same-device flow
- CF-02: P-256 + SHA-256

**Expected test modules (6):**
- `oid4vp-1final-wallet-mdl-happy-flow`
- `oid4vp-1final-wallet-mdl-device-auth`
- `oid4vp-1final-wallet-mdl-session-transcript`
- `oid4vp-1final-wallet-mdl-invalid-mso-signature`
- `oid4vp-1final-wallet-mdl-invalid-device-signature`
- `oid4vp-1final-wallet-mdl-replay-protection`

### Negative Security Tests (x509_hash)

**File:** `VpWalletNegativeTests.kt`

Tests that wallet correctly **rejects** non-HAIP-compliant requests:
- Unsigned requests
- Cleartext response requests
- Weak cryptographic parameters
- Invalid certificates
- Replay attacks
- Invalid x509_hash values

**Expected test modules (9):**
- `oid4vp-1final-wallet-reject-unsigned-request`
- `oid4vp-1final-wallet-reject-cleartext-response`
- `oid4vp-1final-wallet-reject-weak-curve`
- `oid4vp-1final-wallet-reject-weak-hash`
- `oid4vp-1final-wallet-reject-missing-holder-binding`
- `oid4vp-1final-wallet-reject-expired-certificate`
- `oid4vp-1final-wallet-reject-untrusted-ca`
- `oid4vp-1final-wallet-reject-wallet-nonce-mismatch`
- `oid4vp-1final-wallet-reject-insecure-origin`

---

## Troubleshooting

### "No credentials available to satisfy the request"

The wallet doesn't have credentials matching what the conformance suite requests:
1. Check wallet has SD-JWT VC with correct `vct` value
2. Check wallet has mDL with correct docType
3. Verify credential claims match DCQL query requirements

### "Failed to resolve request"

The wallet API couldn't parse the authorization request:
1. Check wallet API logs for specific error
2. Verify request_uri is accessible from wallet
3. Check JAR signature validation
4. **For x509_hash**: Verify client_id matches SHA-256(DER(leaf_cert))

### "Failed to send response to verifier"

The VP response was rejected by conformance suite:
1. Check response encryption (must use ECDH-ES + A256GCM)
2. Verify KB-JWT/DeviceAuth holder binding is present
3. Check nonce matches request nonce
4. **For x509_hash**: Verify certificate chain is valid

### Adapter Connection Issues

If the adapter can't reach the wallet API:
1. Verify wallet API2 is running on port 7001
2. Check no firewall blocking localhost connections
3. Verify `WALLET_API_URL` environment variable

---

## HAIP Requirements

| Req | Description | Status | Notes |
|-----|-------------|--------|-------|
| P-02 | **x509_hash** client identification | 🔄 Pending | MANDATORY for HAIP |
| W-25 | `vp_token` response type | 🔄 Pending | |
| W-27 | JAR with `request_uri` | 🔄 Pending | |
| W-28 | `direct_post.jwt` response mode | 🔄 Pending | |
| W-29 | Response encryption (ECDH-ES + P-256) | 🔄 Pending | |
| W-30 | DCQL query processing | 🔄 Pending | |
| W-35 | Same-device flow | 🔄 Pending | |
| W-36 | SD-JWT VC with KB-JWT (holder binding) | 🔄 Pending | |
| CF-02 | P-256 + SHA-256 | 🔄 Pending | |
| CF-04 | X.509 chain excludes trust anchor | 🔄 Pending | |
| CF-05 | X.509 certs not self-signed | 🔄 Pending | |

---

## x509_hash vs x509_san_dns

HAIP 1.0 §5 requirement **P-02** mandates the use of `x509_hash` for client identification:

| Aspect | x509_san_dns | x509_hash (HAIP) |
|--------|--------------|------------------|
| Client ID Format | `x509_san_dns:{dns_name}` | `x509_hash:{base64url(SHA-256(DER(cert)))}` |
| Binding | DNS name in SAN | Certificate hash |
| Security | Relies on DNS/PKI | Cryptographically bound to cert |
| HAIP Compliance | ❌ Baseline only | ✅ HAIP MANDATORY |

**Example x509_hash client_id:**
```
x509_hash:1P4N1ojALCdefwgW0rES-vAUOHmHKBebWpAbi_YbGR4
```

The wallet MUST:
1. Extract the base64url-encoded hash from the client_id
2. Compute SHA-256 of the DER-encoded leaf certificate from x5c
3. Verify the hashes match
4. Reject the request if they don't match

---

## Test Logs

Test results are stored in:
```
build/reports/tests/test/classes/id.walt.openid4vp.conformance.VpWalletConformanceTests.html
```

Conformance suite logs can be viewed at:
```
https://localhost.emobix.co.uk:8443/log-detail.html?log=<LOG_ID>
```

---

## Code Structure

```
waltid-openid4vp-conformance-runners/
├── src/main/kotlin/.../
│   ├── adapter/
│   │   └── VpWalletConformanceAdapter.kt    # Bridges conformance suite ↔ wallet API
│   └── testplans/plans/vp/wallet/
│       ├── WalletTestPlan.kt                # Base interface
│       ├── VpWalletSdJwtVcX509HashRequestUriSignedDirectPostHaip.kt  # HAIP SD-JWT
│       ├── VpWalletMdlX509HashRequestUriSignedDirectPostHaip.kt      # HAIP mDL
│       ├── VpWalletSdJwtVcX509SanDnsRequestUriSignedDirectPost.kt    # Baseline SD-JWT
│       ├── VpWalletMdlX509SanDnsRequestUriSignedDirectPost.kt        # Baseline mDL
│       └── VpWalletNegativeTests.kt                                  # Security tests
└── src/test/kotlin/.../
    └── VpWalletConformanceTests.kt          # JUnit test runner
```
