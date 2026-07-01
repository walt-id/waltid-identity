# VP Verifier Conformance Tests

## Overview

This document describes the OpenID4VP verifier conformance tests for the walt.id Verifier implementation.

These tests validate that the walt.id verifier correctly:
- Generates signed authorization requests (JAR - JWT-Secured Authorization Request)
- Processes verifiable presentation responses
- Validates credential signatures and holder binding
- Enforces cryptographic requirements per HAIP profile
- Handles X.509 certificate-based client authentication

## Prerequisites

### 1. Install ngrok

Download from https://ngrok.com/download or:

```bash
# Snap (Linux)
sudo snap install ngrok

# Homebrew (macOS)
brew install ngrok
```

### 2. Setup /etc/hosts

```bash
echo "127.0.0.1 localhost.emobix.co.uk" | sudo tee -a /etc/hosts
```

### 3. Clone and Setup Conformance Suite

```bash
# Clone the conformance suite
git clone https://gitlab.com/openid/conformance-suite.git ~/dev/openid/conformance-suite

# Copy walt.id specific configuration
cp ~/dev/walt-id/waltid-unified-build/waltid-identity/waltid-services/waltid-openid4vp-conformance-runners/docker-compose-walt.yml ~/dev/openid/conformance-suite/

# Copy nginx configuration
cp -r ~/dev/walt-id/waltid-unified-build/waltid-identity/waltid-services/waltid-openid4vp-conformance-runners/nginx ~/dev/openid/conformance-suite/
```

### 4. Start Conformance Suite

```bash
cd ~/dev/openid/conformance-suite
docker compose -f docker-compose-walt.yml up -d

# Wait ~30 seconds for initialization
# Verify it's running:
curl -k https://localhost.emobix.co.uk:8443/
```

## Running Tests

### Step 1: Start Conformance Suite

```bash
cd ~/dev/openid/conformance-suite
docker compose -f docker-compose-walt.yml up -d

# Wait ~30 seconds for initialization
# Verify it's running:
curl -k https://localhost.emobix.co.uk:8443/
```

### Step 2: Start verifier-api2

```bash
cd ~/dev/walt-id/waltid-unified-build/waltid-identity
./gradlew :waltid-services:waltid-verifier-api2:run
```

### Step 3: Start ngrok Tunnel

```bash
# In a separate terminal - tunnel to verifier-api2 port
ngrok http 7003

# Note the HTTPS URL, e.g.: https://abc123.ngrok-free.app
```

### Step 4: Set Environment Variable and Run Tests

```bash
# Set the ngrok URL (replace with your actual ngrok URL)
export VERIFIER_NGROK_URL="https://abc123.ngrok-free.app"

cd ~/dev/walt-id/waltid-unified-build/waltid-identity

# Run all verifier conformance tests
./gradlew :waltid-services:waltid-openid4vp-conformance-runners:test \
    --tests "VerifierConformanceTests"
```

### Step 5: View Results

Test results are saved to:
```
waltid-services/waltid-openid4vp-conformance-runners/build/reports/tests/test/index.html
```

You can also view detailed logs in the conformance suite web UI at:
```
https://localhost.emobix.co.uk:8443/
```

## Test Plans

### Plan 1: mDL with X.509 SAN DNS (Plain VP)

**Class:** `MdlX509SanDnsRequestUriSignedDirectPost`

Tests ISO mDL (mobile Driving License) verification with X.509 certificate-based client authentication.
This is a **non-HAIP** test plan for basic OID4VP compliance.

| Property | Value |
|----------|-------|
| **Conformance Plan** | `oid4vp-1final-verifier-test-plan` |
| **Credential Format** | `iso_mdl` (mso_mdoc) |
| **Client ID Scheme** | `x509_san_dns` |
| **Request Method** | `request_uri_signed` (JAR) |
| **VP Profile** | `plain_vp` |
| **Response Mode** | `direct_post` |

**Expected Test Modules:**

| Module | Expected Outcome | Description |
|--------|------------------|-------------|
| `oid4vp-1final-verifier-happy-flow` | ✅ PASS | Standard successful verification flow |
| `oid4vp-1final-verifier-request-uri-method-post` | ✅ PASS (or skip) | Request URI fetched via POST method |
| `oid4vp-1final-verifier-invalid-session-transcript` | ✅ PASS (REJECT) | Verifier must reject invalid mDOC session transcript |

**Cryptographic Configuration:**
- Verifier key: P-256 (secp256r1) EC key
- Certificate: CN=verifier.example.com, SAN DNS=verifier.example.com
- Certificate chain: Leaf → Intermediate CA (NOT self-signed leaf)
- Client ID: `x509_san_dns:verifier.example.com`

### Plan 2: SD-JWT VC with HAIP (High Assurance)

**Class:** `SdJwtVcX509SanDnsRequestUriSignedDirectPost`

Tests SD-JWT VC (Selective Disclosure JWT Verifiable Credential) verification with **HAIP (High Assurance Interoperability Profile)** requirements for eIDAS 2.0 compliance.

| Property | Value |
|----------|-------|
| **Conformance Plan** | `oid4vp-1final-verifier-haip-test-plan` |
| **Credential Format** | `sd_jwt_vc` (dc+sd-jwt) |
| **Client ID Scheme** | `x509_san_dns` |
| **Request Method** | `request_uri_signed` (JAR) |
| **VP Profile** | `haip` |
| **Response Mode** | `direct_post.jwt` (encrypted JWE) |
| **Encrypted Response** | Required (HAIP mandate) |

**Expected Test Modules:**

| Module | Expected Outcome | Description |
|--------|------------------|-------------|
| `oid4vp-1final-verifier-happy-flow` | ✅ PASS | Standard successful verification |
| `oid4vp-1final-verifier-minimal-cnf-jwk` | ✅ PASS | Minimal confirmation key in credential |
| `oid4vp-1final-verifier-request-uri-method-post` | ✅ PASS (or skip) | Request URI via POST |
| `oid4vp-1final-verifier-invalid-kb-jwt-signature` | ✅ PASS (REJECT) | Invalid key binding JWT signature |
| `oid4vp-1final-verifier-invalid-credential-signature` | ✅ PASS (REJECT) | Invalid credential signature |
| `oid4vp-1final-verifier-invalid-sd-hash` | ✅ PASS (REJECT) | Invalid selective disclosure hash |
| `oid4vp-1final-verifier-invalid-kb-jwt-nonce` | ✅ PASS (REJECT) | Invalid nonce in KB-JWT |
| `oid4vp-1final-verifier-invalid-kb-jwt-aud` | ✅ PASS (REJECT) | Invalid audience in KB-JWT |
| `oid4vp-1final-verifier-kb-jwt-iat-in-past` | ✅ PASS (REJECT) | KB-JWT issued too far in past |
| `oid4vp-1final-verifier-kb-jwt-iat-in-future` | ✅ PASS (REJECT) | KB-JWT issued in future |

**Cryptographic Configuration:**
- Verifier key: P-256 (secp256r1) EC key
- Response encryption: Required (HAIP mandate)
- Certificate chain: Leaf → Intermediate CA (NOT self-signed leaf)
- Client ID: `x509_san_dns:verifier.example.com`

## Certificate Chain Requirements

⚠️ **IMPORTANT**: The OpenID conformance suite validates that the leaf certificate is NOT self-signed.

The error `"Leaf certificate in x5c chain must not be self-signed"` means your certificate chain needs:

1. **Root CA** - Self-signed root certificate (optional in x5c, used as trust anchor)
2. **Intermediate CA** - Signed by Root CA
3. **Leaf Certificate** - Signed by Intermediate CA (this is the one used for signing)

The x5c chain in the test plans includes `[leaf, intermediate]` - both signed by their respective parents.

## Troubleshooting

### Test is SKIPPED

**Cause:** Conformance suite not reachable or ngrok URL not set.

**Fix:**
1. Verify conformance suite is running: `curl -k https://localhost.emobix.co.uk:8443/`
2. Verify `VERIFIER_NGROK_URL` environment variable is set
3. Check ngrok is running and forwarding to port 7003

### "Leaf certificate in x5c chain must not be self-signed"

**Cause:** The test plan's certificate chain has a self-signed leaf certificate.

**Fix:** This should be fixed in the current version. If you still see this error, ensure you're using the latest test plan code with the CA-signed certificate chain.

### Conformance Suite Shows FAILED but Verifier Shows SUCCESS

**Cause:** The verifier internally verified the presentation, but the conformance suite found an issue with the request or response format.

**Fix:** Check the conformance suite log for specific validation failures. Common issues:
- Certificate chain validation
- Response encryption missing (for HAIP)
- Incorrect response_mode

### Connection Refused to ngrok URL

**Cause:** verifier-api2 is not running or ngrok tunnel is down.

**Fix:**
1. Start verifier-api2: `./gradlew :waltid-services:waltid-verifier-api2:run`
2. Restart ngrok: `ngrok http 7003`
3. Update `VERIFIER_NGROK_URL` with new ngrok URL

## Expected Results Summary

| Test Plan | Expected Pass | Expected Fail | Notes |
|-----------|--------------|---------------|-------|
| mDL Plain VP | 3/3 | 0 | All modules should pass |
| SD-JWT HAIP | 10/10 | 0 | All modules should pass |

## Architecture

```
[OpenID Conformance Suite]    <->    [walt.id Verifier]
       (Wallet)                           (Verifier)
       
  Simulates wallet behavior          Generates requests
  Sends VP responses                 Validates presentations
  Reports conformance result         Returns verification result
```

The conformance suite acts as a **wallet** and calls the verifier's authorization endpoint.
The verifier processes the request and validates the VP response.

### Network Topology

Since the conformance suite runs in Docker, it cannot directly reach `localhost` on the host machine.
The solution is to use ngrok to expose the local verifier:

```
[Conformance Suite (Docker)]  -->  [ngrok tunnel]  -->  [Verifier (localhost:7003)]
```
