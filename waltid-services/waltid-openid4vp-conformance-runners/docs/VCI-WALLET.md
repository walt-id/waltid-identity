# VCI Wallet Conformance Tests

## Overview

Test infrastructure for OpenID4VCI 1.0 conformance testing of the **wallet** (credential holder) side.
The OpenID conformance suite acts as the credential issuer, testing the wallet's ability to receive
and process verifiable credentials.

## Status

| Component | Status |
|-----------|--------|
| Test infrastructure | ✅ Complete |
| Certificate chain (HAIP 4.5.1) | ✅ Working |
| Metadata URL resolution | ✅ Fixed (trailing slash) |
| Pre-authorized code flow | ✅ Supported |
| Authorization code flow | ✅ **PASSING** |
| SSL trust for conformance suite | ✅ Configured |
| DPoP support (RFC 9449) | ✅ Complete with nonce retry |
| private_key_jwt authentication | ✅ Complete |
| Nonce endpoint support | ✅ Complete |
| SD-JWT VC format | ✅ Supported |

### Conformance Test Results

**Test Profile:** `oid4vci-1_0-wallet-test-credential-issuance-dpop-private_key_jwt-sd_jwt_vc-issuer_initiated-simple-immediate-unsigned-authorization_code-by_value-plain`

| Date | Passed | Failed | Notes |
|------|--------|--------|-------|
| 2026-07-01 | 140 | 0 | All tests passing ✅ |

## Architecture

```
┌─────────────────────────────┐         ┌─────────────────────────────┐
│   OpenID Conformance Suite  │         │      waltid-wallet-api2     │
│   (Acts as Issuer)          │  HTTP   │   (Credential Wallet)       │
│                             │ ◄────── │                             │
│ - Provides credential offer │         │ - Discovers issuer metadata │
│ - Handles authorization     │         │ - Performs authorization    │
│ - Issues credentials        │         │ - Requests credentials      │
│ - Validates wallet requests │         │ - Stores issued credentials │
└──────────────┬──────────────┘         └─────────────────────────────┘
        (Docker)                                      ▲
               │                                      │
               │     ┌────────────────────────┐       │
               └────►│  VCI Wallet Adapter    │───────┘
                     │  (Test Infrastructure) │
                     └────────────────────────┘
                          localhost:7007
```

## Test Profile

Based on `issuer-req.md` requirements (wallet perspective):

| Property | Value |
|----------|-------|
| Specification | OpenID4VCI 1.0 Final |
| Credential Format | sd_jwt_vc |
| Sender Constraint | dpop |
| Client Authentication | private_key_jwt |
| Grant Type | authorization_code |
| FAPI Profile | vci |
| Credential Encryption | plain |
| Issuance Mode | immediate |

## Implementation Details

### DPoP Support (RFC 9449)

The wallet supports DPoP (Demonstrating Proof of Possession) for both token and credential endpoints:

- **Token endpoint:** DPoP proof with `htm`, `htu`, `iat`, `jti`
- **Nonce retry:** Automatic retry with server-provided `DPoP-Nonce` on `use_dpop_nonce` error
- **Credential endpoint:** DPoP proof with `ath` (access token hash) for bound requests

### private_key_jwt Client Authentication (RFC 7523)

Token requests include `client_assertion` JWT:

- `iss` / `sub`: client_id
- `aud`: Authorization server issuer URL (not token endpoint)
- `jti`: Unique per-request (regenerated on DPoP nonce retry to avoid reuse)
- `exp`: 5 minutes from `iat`

**Important:** The `client_assertion` is regenerated for each token request attempt to comply with RFC 7523's requirement for unique `jti` values.

### Nonce Endpoint Support

Some issuers provide a `nonce_endpoint` in metadata instead of returning `c_nonce` in the token response. The wallet supports both:

1. **nonce_endpoint:** If issuer metadata includes `nonce_endpoint`, the wallet fetches the nonce from there
2. **c_nonce fallback:** If no nonce endpoint, uses `c_nonce` from token response
3. **Proof signing:** The fetched nonce is included in the key proof JWT for credential requests

### Key Proof JWT

Credential requests include a proof of possession:

- Header: `typ=openid4vci-proof+jwt`, `alg`, `kid` or `jwk`
- Payload: `iss`, `aud` (credential issuer), `iat`, `nonce`

## Quick Start

### Prerequisites

1. **Add hosts entry:**
   ```bash
   echo "127.0.0.1 localhost.emobix.co.uk" | sudo tee -a /etc/hosts
   ```

2. **Start conformance suite:**
   ```bash
   cd ~/dev/openid/conformance-suite
   docker compose -f docker-compose-walt.yml up -d
   # Wait ~30 seconds for startup
   ```

3. **Start wallet-api2:**
   ```bash
   cd ~/dev/walt-id/waltid-unified-build/waltid-identity
   ./gradlew :waltid-services:waltid-wallet-api2:run
   ```

### Run Tests

```bash
cd ~/dev/walt-id/waltid-unified-build/waltid-identity

# Run all VCI wallet tests
./gradlew :waltid-services:waltid-openid4vp-conformance-runners:test \
    --tests "VciWalletConformanceTests" -PrunIntegrationTests
```

## Components

### VciWalletConformanceAdapter

HTTP bridge between conformance suite and wallet-api2:

- **Port:** 7007
- **Endpoints:**
  - `GET/POST /credential-offer` - Receives offers from conformance suite
  - `GET /callback` - OAuth authorization callback
  - `GET /health` - Health check

### VciWalletSdJwtDpop

Test plan configuration for SD-JWT VC with DPoP:

- Credential format: `sd_jwt_vc`
- Sender constraint: `dpop`
- Client auth: `private_key_jwt`
- Grant type: `authorization_code`
- Flow variant: `issuer_initiated`

### VciWalletTestPlanRunner

Orchestrates test execution:

1. Creates test plan on conformance suite
2. Starts test modules
3. Coordinates credential flow via adapter
4. Polls for results

## Certificate Configuration

HAIP 4.5.1 requires credential signing certificates to be CA-signed (not self-signed).

The test configuration includes a proper certificate chain:
- **CA:** `CN=Test Credential CA/O=Walt.id Test` (10-year validity)
- **Leaf:** `CN=Test Credential Issuer/O=Walt.id Test` (1-year validity, signed by CA)

## SSL Trust Configuration

wallet-api2 is configured to trust the conformance suite's self-signed certificate
via a shared truststore:

```
waltid-openid4vp-conformance-runners/conformance-truststore.jks
```

## Test Modules

| Module | Description |
|--------|-------------|
| `oid4vci-1_0-wallet-test-credential-issuance` | Basic credential issuance |
| `oid4vci-1_0-wallet-test-credential-issuance-notification` | With notification endpoint |
| `oid4vci-1_0-wallet-happy-path-with-scopes-...` | Scopes-based authorization |

## Troubleshooting

### Conformance suite not available

```bash
# Check Docker containers
docker compose -f docker-compose-walt.yml ps

# Check logs
docker compose -f docker-compose-walt.yml logs server
```

### wallet-api2 SSL errors

Ensure truststore is configured in `build.gradle.kts`:

```kotlin
tasks.named<JavaExec>("run") {
    jvmArgs(
        "-Djavax.net.ssl.trustStore=.../conformance-truststore.jks",
        "-Djavax.net.ssl.trustStorePassword=changeit"
    )
}
```

### Test stuck in WAITING

The authorization_code flow requires manual OAuth login. Open the conformance
suite UI and complete the authorization.

## Related Documentation

- [VCI-ISSUER.md](VCI-ISSUER.md) - Issuer conformance tests
- [VCI-WALLET-IMPLEMENTATION.md](VCI-WALLET-IMPLEMENTATION.md) - Implementation details
- [OpenID4VCI 1.0 Spec](https://openid.net/specs/openid-4-verifiable-credential-issuance-1_0.html)
- [RFC 9449 - DPoP](https://datatracker.ietf.org/doc/html/rfc9449)
- [RFC 7523 - JWT Bearer Client Authentication](https://datatracker.ietf.org/doc/html/rfc7523)
