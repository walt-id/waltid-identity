# VP Verifier Conformance Tests

## Status: ⚠️ Partial (1/2 test plans passing)

| Test Plan | Status | Notes |
|-----------|--------|-------|
| **mDL Plain VP** | ✅ PASSING | ISO mDL with X.509 SAN DNS |
| **SD-JWT HAIP** | ❌ Config issue | Needs trust anchor configuration |

## Overview

Tests the walt.id Verifier implementation against the OpenID Foundation Conformance Suite.

The verifier tests validate:
- Signed authorization requests (JAR - JWT-Secured Authorization Request)
- Verifiable presentation response processing
- Credential signature and holder binding validation
- X.509 certificate-based client authentication
- HAIP (High Assurance Interoperability Profile) requirements

## Prerequisites

### 1. Conformance Suite Setup

```bash
# Add hosts entry (one-time)
echo "127.0.0.1 localhost.emobix.co.uk" | sudo tee -a /etc/hosts

# Clone conformance suite
git clone https://gitlab.com/openid/conformance-suite.git ~/dev/openid/conformance-suite

# Copy walt.id config
cd ~/dev/walt-id/waltid-unified-build/waltid-identity/waltid-services/waltid-openid4vp-conformance-runners
cp docker-compose-walt.yml ~/dev/openid/conformance-suite/
cp -r nginx ~/dev/openid/conformance-suite/

# Start conformance suite
cd ~/dev/openid/conformance-suite
docker compose -f docker-compose-walt.yml up -d

# Verify (wait ~30s for startup)
curl -k https://localhost.emobix.co.uk:8443/
```

### 2. ngrok Setup

```bash
# Install ngrok
brew install ngrok      # macOS
sudo snap install ngrok # Linux

# Start tunnel to verifier port
ngrok http 7003
# Note the HTTPS URL (e.g., https://abc123.ngrok-free.app)
```

## Running Tests

### Step-by-Step

```bash
# Terminal 1: Start verifier-api2
cd ~/dev/walt-id/waltid-unified-build/waltid-identity
./gradlew :waltid-services:waltid-verifier-api2:run

# Terminal 2: Start ngrok tunnel
ngrok http 7003

# Terminal 3: Run tests
export VERIFIER_NGROK_URL="https://YOUR-NGROK-URL.ngrok-free.app"
cd ~/dev/walt-id/waltid-unified-build/waltid-identity
./gradlew :waltid-services:waltid-openid4vp-conformance-runners:test \
    --tests "VerifierConformanceTests"
```

### Force Fresh Run

```bash
./gradlew :waltid-services:waltid-openid4vp-conformance-runners:test \
    --tests "VerifierConformanceTests" --rerun-tasks
```

### View Results

- **Gradle report**: `build/reports/tests/test/index.html`
- **Conformance UI**: https://localhost.emobix.co.uk:8443/

## Test Plans

### Plan 1: mDL with X.509 SAN DNS (Plain VP) ✅

**Status: PASSING**

Tests ISO mDL (mobile Driving License) verification with X.509 certificate-based client authentication.

| Property | Value |
|----------|-------|
| **Class** | `MdlX509SanDnsRequestUriSignedDirectPost` |
| **Conformance Plan** | `oid4vp-1final-verifier-test-plan` |
| **Credential Format** | `iso_mdl` (mso_mdoc) |
| **Client ID Scheme** | `x509_san_dns` |
| **Request Method** | `request_uri_signed` (JAR) |
| **VP Profile** | `plain_vp` |
| **Response Mode** | `direct_post` |

**Test Modules:**

| Module | Status | Description |
|--------|--------|-------------|
| `oid4vp-1final-verifier-happy-flow` | ✅ PASS | Standard successful verification |
| `oid4vp-1final-verifier-request-uri-method-post` | ✅ PASS | Request URI via POST |
| `oid4vp-1final-verifier-invalid-session-transcript` | ✅ PASS | Rejects invalid mDOC transcript |

### Plan 2: SD-JWT VC with HAIP ❌

**Status: Configuration issue (trust anchor required)**

Tests SD-JWT VC verification with HAIP (High Assurance Interoperability Profile) requirements.

| Property | Value |
|----------|-------|
| **Class** | `SdJwtVcX509SanDnsRequestUriSignedDirectPost` |
| **Conformance Plan** | `oid4vp-1final-verifier-haip-test-plan` |
| **Credential Format** | `sd_jwt_vc` (dc+sd-jwt) |
| **Client ID Scheme** | `x509_san_dns` |
| **Request Method** | `request_uri_signed` (JAR) |
| **VP Profile** | `haip` |
| **Response Mode** | `direct_post.jwt` (encrypted JWE) |
| **Encrypted Response** | Required (HAIP mandate) |

**Known Issue:**
```
EnsureClientRequestObjectTrustAnchorConfigured failure: 
'Request Object Trust Anchor' field is missing from the 'Client' section
```

The HAIP test plan requires a trust anchor configuration that is not yet implemented.

## Certificate Chain Requirements

⚠️ **Important**: The conformance suite validates that leaf certificates are NOT self-signed.

The test plans use a proper CA-signed certificate chain:
1. **Root CA** — `walt.id Verifier Root CA` (self-signed)
2. **Intermediate CA** — `walt.id Verifier Intermediate CA` (signed by Root)
3. **Leaf Certificate** — `CN=verifier.example.com` (signed by Intermediate)

The x5c chain includes `[leaf, intermediate]` — the leaf cert is NOT self-signed.

## Troubleshooting

### Tests are SKIPPED

**Cause**: Missing prerequisites

**Fix**:
1. Verify conformance suite: `curl -k https://localhost.emobix.co.uk:8443/`
2. Check `VERIFIER_NGROK_URL` is set
3. Verify ngrok is running: `curl $VERIFIER_NGROK_URL/health` (404 is OK)

### "Connection refused" to ngrok URL

**Cause**: verifier-api2 not running or ngrok down

**Fix**:
1. Start verifier: `./gradlew :waltid-services:waltid-verifier-api2:run`
2. Restart ngrok: `ngrok http 7003`
3. Update `VERIFIER_NGROK_URL` with new URL

### Test stuck at "CREATED" status

**Cause**: HTTP caching or conformance suite state issue

**Fix**:
1. Restart conformance suite: `docker compose -f docker-compose-walt.yml restart`
2. Use `--rerun-tasks` flag
3. The code now uses `Cache-Control: no-cache` to prevent stale responses

### "Leaf certificate in x5c chain must not be self-signed"

**Cause**: Using old test plan code with self-signed certificate

**Fix**: Ensure you have the latest code. The certificate chain was fixed in commit `dc1748781`.

## Architecture

```
                    ┌─────────────────────────────────────┐
                    │   OpenID Conformance Suite          │
                    │   (acts as WALLET)                  │
                    │   localhost.emobix.co.uk:8443       │
                    └──────────────┬──────────────────────┘
                                   │
                                   │ 1. GET /verification-session/{id}/request
                                   │ 2. POST /verification-session/{id}/response
                                   ▼
┌──────────────────────────────────────────────────────────────────────────┐
│                              ngrok                                        │
│                     (exposes verifier to internet)                        │
│                     https://xxxx.ngrok-free.app                           │
└──────────────────────────────────┬───────────────────────────────────────┘
                                   │
                                   │ Tunneled to localhost:7003
                                   ▼
                    ┌─────────────────────────────────────┐
                    │     walt.id Verifier-API2           │
                    │     (acts as VERIFIER)              │
                    │     localhost:7003                  │
                    └─────────────────────────────────────┘
```

The test creates a verification session, then the conformance suite (acting as a wallet) fetches the authorization request and submits a VP response.

## Environment Variables

| Variable | Required | Description |
|----------|----------|-------------|
| `VERIFIER_NGROK_URL` | Yes | ngrok HTTPS URL pointing to verifier-api2 (port 7003) |

## Related Documentation

- [README.md](../README.md) — Main conformance test overview
- [VCI-ISSUER.md](VCI-ISSUER.md) — VCI issuer conformance tests
- [VCI-WALLET.md](VCI-WALLET.md) — VCI wallet conformance tests
- [VP-WALLET.md](VP-WALLET.md) — VP wallet conformance tests (blocked)
