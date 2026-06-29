# Issuer Conformance Tests

## Overview

This document describes the OpenID4VCI issuer conformance tests for the walt.id Issuer implementation.

These tests validate that the walt.id issuer correctly:
- Exposes credential issuer metadata (`.well-known/openid-credential-issuer`)
- Handles authorization code and pre-authorized code flows
- Implements DPoP (Demonstrating Proof-of-Possession) sender constraint
- Supports client attestation authentication
- Issues SD-JWT VC and mDOC credentials
- Validates credential request proofs

## Architecture

```
[OpenID Conformance Suite]    <->    [walt.id Issuer]
       (Wallet)                         (Issuer)
       
  Simulates wallet behavior         Exposes metadata
  Requests credentials              Handles authorization
  Validates responses               Issues credentials
```

The conformance suite acts as a **wallet** and calls the issuer's endpoints:
1. Fetches credential issuer metadata
2. Initiates authorization (auth code) or uses pre-authorized code
3. Exchanges tokens
4. Requests credential issuance
5. Validates issued credentials

## Prerequisites

### 1. Setup /etc/hosts

```bash
echo "127.0.0.1 localhost.emobix.co.uk" | sudo tee -a /etc/hosts
```

### 2. Clone and Setup Conformance Suite

```bash
git clone https://gitlab.com/openid/conformance-suite.git ~/dev/openid/conformance-suite

# Copy walt.id configuration
cp ~/dev/walt-id/waltid-unified-build/waltid-identity/waltid-services/waltid-openid4vp-conformance-runners/docker-compose-walt.yml ~/dev/openid/conformance-suite/

cp -r ~/dev/walt-id/waltid-unified-build/waltid-identity/waltid-services/waltid-openid4vp-conformance-runners/nginx ~/dev/openid/conformance-suite/
```

### 3. Start Conformance Suite

```bash
cd ~/dev/openid/conformance-suite
docker compose -f docker-compose-walt.yml up -d

# Wait ~30 seconds, then verify:
curl -k https://localhost.emobix.co.uk:8443/
```

### 4. Issuer Service Running

The issuer service must be running and accessible. Options:

**Option A: OSS Issuer (local)**
```bash
# Start issuer on port 7002
cd ~/dev/walt-id/waltid-unified-build/waltid-identity
./gradlew :waltid-services:waltid-issuer-api:run
```

**Option B: Enterprise Issuer**
```bash
# Start enterprise stack
cd ~/dev/walt-id/waltid-enterprise-quickstart
docker compose up -d
```

## Running Tests

### Configure Issuer URL

Set the issuer URL via environment variable:

```bash
# Option 1: Direct issuer URL
export OPENID4VCI_CONFORMANCE_CREDENTIAL_ISSUER_URL="http://localhost:7002"

# Option 2: Enterprise issuer (constructs URL from base + target)
export OPENID4VCI_CONFORMANCE_ENTERPRISE_TARGET="my-org"
# Results in: http://waltid.enterprise.localhost:3000/v2/my-org/issuer-service-api/openid4vci
```

### Run Tests

```bash
cd ~/dev/walt-id/waltid-unified-build/waltid-identity

./gradlew :waltid-services:waltid-openid4vp-conformance-runners:test \
    --tests "IssuerConformanceTests"
```

### View Results

Test results are saved to:
```
waltid-services/waltid-openid4vp-conformance-runners/build/reports/tests/test/index.html
```

## Test Plans

### Plan 1: Authorization Code Flow with DPoP

**Class:** `Oid4vciIssuerClientAttestationDpop`

Tests the standard wallet-initiated authorization code flow.

| Property | Value |
|----------|-------|
| **FAPI Profile** | `plain_fapi` |
| **Sender Constraint** | `dpop` |
| **Client Authentication** | `client_attestation` |
| **Flow Variant** | `wallet_initiated` |
| **Grant Type** | `authorization_code` |
| **Metadata Discovery** | `discovery` |

**Test Flow:**
1. Wallet fetches issuer metadata
2. Wallet initiates authorization request
3. User authenticates (manual step in conformance UI)
4. Wallet exchanges authorization code for tokens
5. Wallet requests credential with DPoP proof
6. Issuer issues credential

### Plan 2: Pre-Authorized Code Flow with DPoP

**Class:** `Oid4vciIssuerClientAttestationDpopPreAuth`

Tests the issuer-initiated pre-authorized code flow (no user interaction needed).

| Property | Value |
|----------|-------|
| **FAPI Profile** | `plain_fapi` |
| **Sender Constraint** | `dpop` |
| **Client Authentication** | `client_attestation` |
| **Flow Variant** | `issuer_initiated` |
| **Grant Type** | `pre_authorization_code` |
| **Metadata Discovery** | `discovery` |

**Test Flow:**
1. Issuer provides pre-authorized code (via credential offer)
2. Wallet fetches issuer metadata
3. Wallet exchanges pre-authorized code for tokens
4. Wallet requests credential with DPoP proof
5. Issuer issues credential

## Configuration

### Environment Variables

| Variable | Description | Example |
|----------|-------------|---------|
| `OPENID4VCI_CONFORMANCE_CREDENTIAL_ISSUER_URL` | Direct issuer URL | `http://localhost:7002` |
| `OPENID4VCI_CONFORMANCE_ENTERPRISE_BASE_URL` | Enterprise base URL | `http://waltid.enterprise.localhost:3000` |
| `OPENID4VCI_CONFORMANCE_ENTERPRISE_TARGET` | Enterprise target | `my-org` |
| `OPENID4VCI_CONFORMANCE_SD_JWT_CREDENTIAL_CONFIGURATION_ID` | SD-JWT credential ID | `VerifiableCredential` |
| `OPENID4VCI_CONFORMANCE_MDOC_CREDENTIAL_CONFIGURATION_ID` | mDOC credential ID | `org.iso.18013.5.1.mDL` |
| `OPENID4VCI_CONFORMANCE_CLIENT_ATTESTATION_ISSUER` | Client attestation issuer | `https://attestation.example.com` |
| `OPENID4VCI_CONFORMANCE_CLIENT_ATTESTER_JWKS_FILE` | Path to attester JWKS | `/path/to/attester-key.json` |
| `OPENID4VCI_CONFORMANCE_AUTHORIZATION_SERVER` | External auth server | `https://auth.example.com` |
| `OPENID4VCI_CONFORMANCE_CREDENTIAL_PROOF_TYPE_HINT` | Proof type hint | `jwt` |

### Credential Configuration IDs

The test runner automatically discovers credential configurations from issuer metadata.
Override with environment variables if needed:

```bash
export OPENID4VCI_CONFORMANCE_SD_JWT_CREDENTIAL_CONFIGURATION_ID="MySDJwtCredential"
export OPENID4VCI_CONFORMANCE_MDOC_CREDENTIAL_CONFIGURATION_ID="org.iso.18013.5.1.mDL"
```

## Expected Test Modules

### Authorization Code Flow Modules

| Module | Description |
|--------|-------------|
| `oid4vci-1_0-issuer-metadata-test` | Validates issuer metadata endpoint |
| `oid4vci-1_0-issuer-metadata-test-signed` | Validates signed metadata (optional) |
| `oid4vci-1_0-issuer-authorization-endpoint-test` | Tests authorization endpoint |
| `oid4vci-1_0-issuer-token-endpoint-test` | Tests token endpoint with DPoP |
| `oid4vci-1_0-issuer-credential-endpoint-test` | Tests credential issuance |
| `oid4vci-1_0-issuer-happy-flow` | Complete successful flow |

### Pre-Authorized Code Flow Modules

| Module | Description |
|--------|-------------|
| `oid4vci-1_0-issuer-preauth-happy-flow` | Complete pre-auth flow |
| `oid4vci-1_0-issuer-preauth-token-test` | Token exchange with pre-auth code |
| `oid4vci-1_0-issuer-preauth-credential-test` | Credential request after pre-auth |

## Expected Output

When tests execute successfully:

```
================================================================================
OpenID4VCI Issuer Conformance Tests
================================================================================

Conformance suite: localhost.emobix.co.uk:8443
Conformance available: true
Issuer URL: http://localhost:7002
Issuer configured: true

================================================================================

Conformance server version 5.2.0 available!
Fetching issuer metadata from: http://localhost:7002/.well-known/openid-credential-issuer
Issuer metadata endpoint responding
Discovered credential configuration ids:
  SD-JWT VC: VerifiableCredential
  mDOC:      <not found>

================================================================================
Running issuer plan: Oid4vciIssuerClientAttestationDpop
================================================================================
Created test plan: abc123xyz
The conformance suite will call issuer: http://localhost:7002
Modules to run: 6

[1/6] Running module: oid4vci-1_0-issuer-metadata-test
  Test ID: test123
  View at: https://localhost.emobix.co.uk:8443/log-detail.html?log=test123
  Waiting for conformance suite to complete...
  Status: FINISHED, Result: PASSED

[2/6] Running module: oid4vci-1_0-issuer-happy-flow
  ...

================================================================================
Issuer Conformance Test Results
================================================================================
  [0] test123: status=FINISHED, result=PASSED
  [1] test456: status=FINISHED, result=PASSED
  ...

Total: 6 modules
Passed: 6
================================================================================
```

## Authorization Code Flow: Manual Steps

For authorization code flow tests, user interaction is required:

1. Test starts and enters WAITING status
2. Open the conformance suite UI: https://localhost.emobix.co.uk:8443
3. Find the running test and click to view
4. Complete the OAuth login flow in the popup
5. Test continues automatically after authorization

For fully automated testing, use pre-authorized code flow.

## Troubleshooting

### Tests Skip

- Conformance suite not running
- Issuer URL not configured

Verify:
```bash
curl -k https://localhost.emobix.co.uk:8443/
echo $OPENID4VCI_CONFORMANCE_CREDENTIAL_ISSUER_URL
```

### Issuer Metadata Not Found

Check issuer is running and metadata endpoint works:
```bash
curl http://localhost:7002/.well-known/openid-credential-issuer
```

### Test Stuck in WAITING

Authorization code flow requires user interaction:
1. Open conformance suite UI
2. Complete OAuth login in the test

Or use pre-authorized code flow for automated tests.

### No Credential Configurations Found

Issuer must expose `credential_configurations_supported` in metadata:
```json
{
  "credential_configurations_supported": {
    "VerifiableCredential": {
      "format": "dc+sd-jwt",
      ...
    }
  }
}
```

### DPoP Errors

Ensure issuer supports DPoP:
- Token endpoint must accept `DPoP` header
- Must validate DPoP proof JWT
- Must bind access token to DPoP key

## Related Documentation

- [README.md](README.md) - General setup and configuration
- [QUICKSTART.md](QUICKSTART.md) - Quick setup guide
- [VERIFIER2-TESTS.md](VERIFIER2-TESTS.md) - Verifier conformance tests
- [WALLET-HAIP-TESTS.md](WALLET-HAIP-TESTS.md) - Wallet HAIP conformance tests
- [OpenID4VCI Specification](https://openid.net/specs/openid-4-verifiable-credential-issuance-1_0.html)
- [DPoP Specification](https://datatracker.ietf.org/doc/html/rfc9449)
- [Conformance Suite](https://gitlab.com/openid/conformance-suite)

## Support

- [GitHub Issues](https://github.com/walt-id/waltid-identity/issues)
- [walt.id Discord](https://discord.gg/AW8AgqJthZ)
- [Documentation](https://docs.walt.id)
