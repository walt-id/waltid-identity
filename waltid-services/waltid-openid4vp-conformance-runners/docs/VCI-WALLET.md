# VCI Wallet Conformance Tests

This document covers setup, execution, and status of OpenID4VCI Wallet conformance tests.

## Test Profiles

| Profile | Test Plan | Format | Grant Type | Client Auth | Status |
|---------|-----------|--------|------------|-------------|--------|
| SD-JWT VC + DPoP | `oid4vci-1_0-wallet-test-plan` | SD-JWT VC | authorization_code | private_key_jwt | 🔄 Not yet tested |
| ISO mdoc + DPoP | `oid4vci-1_0-wallet-test-plan` | mso_mdoc | authorization_code | private_key_jwt | 🔄 Not yet tested |
| SD-JWT VC HAIP | `oid4vci-1_0-wallet-haip-test-plan` | SD-JWT VC | authorization_code | client_attestation | 🔄 Not yet tested |

**Last tested:** Not yet run

---

## Current Status

### Test Results

_Tests have not been run yet. This section will be updated with actual results._

### Architecture

The VCI Wallet conformance tests use an **adapter pattern**:

```
┌─────────────────┐    ┌─────────────────┐    ┌─────────────────┐
│ Conformance     │    │ Wallet Adapter  │    │ Wallet API      │
│ Suite (Issuer)  │───▶│ (port 7007)     │───▶│ (port 7005)     │
└─────────────────┘    └─────────────────┘    └─────────────────┘
        │                      │
        │                      ▼
        │              1. Receive credential offer
        │              2. Discover issuer metadata
        │              3. Initiate authorization
        │              4. Exchange code for tokens
        │              5. Request credential with proof
        │                      │
        ◀──────────────────────┘
        6. Store issued credential
```

The **VciWalletConformanceAdapter** bridges the conformance suite with the walt.id wallet API.

---

## Prerequisites

1. **Conformance Suite** running at `https://localhost.emobix.co.uk:8443`
   ```bash
   cd ~/dev/openid/conformance-suite
   docker compose -f docker-compose-walt.yml up -d
   ```

2. **Wallet API** running (wallet-api2):
   ```bash
   cd ~/dev/walt-id/waltid-unified-build
   ./gradlew :waltid-services:waltid-wallet-api2:run
   ```

3. **/etc/hosts entry:**
   ```
   127.0.0.1 localhost.emobix.co.uk
   ```

---

## Setup

### 1. Start Wallet API

```bash
cd ~/dev/walt-id/waltid-unified-build
./gradlew :waltid-services:waltid-wallet-api2:run
```

Verify wallet is running:
```bash
curl -s http://127.0.0.1:7005/health | jq .
```

### 2. Configure Test Wallet

Ensure a test wallet exists that can receive credentials. The adapter uses a default wallet for testing.

---

## Running Tests

```bash
cd ~/dev/walt-id/waltid-unified-build

# Run all VCI Wallet tests
./gradlew :waltid-services:waltid-openid4vp-conformance-runners:test --tests "VciWalletConformanceTests"

# Run specific test
./gradlew :waltid-services:waltid-openid4vp-conformance-runners:test \
  --tests "VciWalletConformanceTests.vciWalletSdJwtVcDpopAuthorizationCode"

# Run HAIP full target test
./gradlew :waltid-services:waltid-openid4vp-conformance-runners:test \
  --tests "VciWalletConformanceTests.vciWalletSdJwtVcAuthorizationCodeHaipFullTarget"
```

### Test Execution Flow

1. **Adapter starts** on port 7007
2. **Test plan created** on conformance suite (issuer mode)
3. **Conformance suite** sends credential offer to adapter
4. **Adapter** calls wallet API to:
   - Discover issuer metadata
   - Initiate authorization flow
   - Exchange auth code for tokens (with DPoP)
   - Request credential with proof
5. **Wallet** stores issued credential
6. **Conformance suite** validates the credential request

---

## Environment Variables

| Variable | Description | Default |
|----------|-------------|---------|
| `WALLET_API_URL` | Wallet API base URL | `http://127.0.0.1:7005` |
| `CONFORMANCE_HOST` | Conformance suite host | `localhost.emobix.co.uk` |
| `CONFORMANCE_PORT` | Conformance suite port | `8443` |

---

## Test Plans

### SD-JWT VC + DPoP

**File:** `VciWalletSdJwtDpop.kt`

Tests wallet's complete credential issuance flow:
1. Receive credential offer from issuer
2. Discover issuer metadata
3. Initiate authorization code flow
4. Exchange auth code for tokens with DPoP
5. Request credential with proof
6. Validate and store issued SD-JWT VC

**Configuration:**

| Property | Value |
|----------|-------|
| Credential Format | sd_jwt_vc |
| Sender Constraint | dpop |
| Client Authentication | private_key_jwt |
| Grant Type | authorization_code |
| Flow Variant | issuer_initiated |
| FAPI Profile | vci |
| Credential Configuration ID | `eu.europa.ec.eudi.pid.1` |

### ISO mdoc + DPoP

**File:** `VciWalletMdocDpop.kt`

Tests wallet's ability to receive ISO 18013-5 mdoc credentials:
1. Receive credential offer from issuer
2. Discover issuer metadata
3. Initiate authorization code flow
4. Exchange auth code for tokens with DPoP
5. Request credential with proof
6. Validate and store issued ISO mdoc

**Configuration:**

| Property | Value |
|----------|-------|
| Credential Format | mdoc (mso_mdoc) |
| Sender Constraint | dpop |
| Client Authentication | private_key_jwt |
| Grant Type | authorization_code |
| Flow Variant | issuer_initiated |
| FAPI Profile | vci |
| Credential Configuration ID | `eu.europa.ec.eudi.pid.mdoc.1` |

### SD-JWT VC HAIP (Full Target)

**File:** `VciWalletSdJwtHaip.kt`

Full HAIP wallet profile targeting complete HAIP compliance. The test harness reaches the conformance suite and executes the full HAIP module set.

**Configuration:**

| Property | Value |
|----------|-------|
| Credential Format | sd_jwt_vc |
| Sender Constraint | dpop |
| Client Authentication | client_attestation |
| Grant Type | authorization_code |
| FAPI Profile | vci_haip |
| Credential Configuration ID | `eu.europa.ec.eudi.pid.1` |

**Additional HAIP Requirements:**
- Wallet Attestation (W-04)
- Key Attestation (W-05)
- Trust anchor validation

---

## Troubleshooting

### "Connect timed out" errors

The conformance suite runs in Docker and can't reach `localhost`:
1. Get host IP: `ip route get 1.1.1.1 | awk '{print $7}'`
2. Ensure adapter uses host IP, not 127.0.0.1

### "Failed to discover issuer metadata"

The wallet API couldn't fetch issuer metadata from conformance suite:
1. Check conformance suite is running
2. Verify `/etc/hosts` entry exists
3. Check SSL trust configuration

### OAuth Flow Issues

If authorization fails:
1. Check callback URL is reachable from browser
2. Verify redirect_uri matches conformance suite configuration
3. Check wallet API logs for PKCE/DPoP errors

### Adapter Connection Issues

If the adapter can't reach the wallet API:
1. Verify wallet API is running on port 7005
2. Check no firewall blocking localhost connections

---

## HAIP Requirements

| Req | Description | Status |
|-----|-------------|--------|
| W-01 | Authorization Code flow | 🔄 Pending |
| W-02 | FAPI2 Security Profile (PKCE S256, PAR, iss) | 🔄 Pending |
| W-03 | DPoP for sender-constrained tokens | 🔄 Pending |
| W-04 | Wallet Attestation for client authentication | 🔄 Pending |
| W-05 | Key Attestation (prove key possession) | 🔄 Pending |
| W-10 | Credential Offer (same-device + cross-device) | 🔄 Pending |
| W-22 | SD-JWT VC validation (cnf, status, x5c) | 🔄 Pending |
| CF-02 | P-256 + SHA-256 | 🔄 Pending |

---

## Certificate Chain

All test plans use a CA-signed certificate chain for HAIP compliance:

- **Leaf:** `CN=Test Credential Issuer` (P-256, signed by CA)
- **CA:** `CN=Test Credential CA` (self-signed root)

**Important:** HAIP §4.5.1 requires that credential signing certificates are NOT self-signed.

---

## Test Logs

Test results are stored in:
```
build/reports/tests/test/classes/id.walt.openid4vp.conformance.VciWalletConformanceTests.html
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
│   │   └── VciWalletConformanceAdapter.kt    # Bridges conformance suite ↔ wallet API
│   └── testplans/
│       ├── plans/vci/wallet/
│       │   ├── VciWalletTestPlan.kt          # Base interface
│       │   ├── VciWalletSdJwtDpop.kt         # SD-JWT VC baseline
│       │   ├── VciWalletMdocDpop.kt          # ISO mdoc baseline
│       │   └── VciWalletSdJwtHaip.kt         # SD-JWT VC HAIP
│       └── runner/
│           └── VciWalletTestPlanRunner.kt    # Test execution logic
└── src/test/kotlin/.../
    └── VciWalletConformanceTests.kt          # JUnit test runner
```
