# Verifier Conformance Tests

## Overview

This document describes the OpenID4VP verifier conformance tests for the walt.id Verifier implementation.

These tests validate that the walt.id verifier correctly:
- Generates signed authorization requests (JAR - JWT-Secured Authorization Request)
- Processes verifiable presentation responses
- Validates credential signatures and holder binding
- Enforces cryptographic requirements per HAIP profile
- Handles X.509 certificate-based client authentication

## HAIP Compliance

The verifier conformance tests include HAIP (High Assurance Interoperability Profile) validation for eIDAS 2.0 compliance.

**HAIP Requirements for Verifier:**
- Signed authorization requests (MANDATORY)
- Encrypted VP responses supported (MANDATORY)
- P-256 key curve enforcement (MANDATORY)
- SHA-256 hash algorithm (MANDATORY)
- X.509 certificate-based client authentication

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

### Step 1: Start ngrok Tunnel

```bash
# In a separate terminal
ngrok http 7003

# Note the HTTPS URL, e.g.: https://abc123.ngrok-free.app
```

### Step 2: Run Tests

```bash
cd ~/dev/walt-id/waltid-unified-build/waltid-identity

# Set the ngrok URL
export VERIFIER_NGROK_URL="https://abc123.ngrok-free.app"

# Run all verifier tests
./gradlew :waltid-services:waltid-openid4vp-conformance-runners:test \
    --tests "VerifierConformanceTests"
```

### Step 3: View Results

Test results are saved to:
```
waltid-services/waltid-openid4vp-conformance-runners/build/reports/tests/test/index.html
```

## Test Plans

### Plan 1: mDL with X.509 SAN DNS (Plain VP)

**Class:** `MdlX509SanDnsRequestUriSignedDirectPost`

Tests ISO mDL (mobile Driving License) verification with X.509 certificate-based client authentication.

| Property | Value |
|----------|-------|
| **Credential Format** | `iso_mdl` (mso_mdoc) |
| **Client ID Scheme** | `x509_san_dns` |
| **Request Method** | `request_uri_signed` (JAR) |
| **VP Profile** | `plain_vp` |
| **Response Mode** | `direct_post` |

**Test Modules:**

| Module | Expected Outcome | Description |
|--------|------------------|-------------|
| `oid4vp-1final-verifier-happy-flow` | ACCEPT | Standard successful verification flow |
| `oid4vp-1final-verifier-request-uri-method-post` | ACCEPT (or skip) | Request URI fetched via POST method |
| `oid4vp-1final-verifier-invalid-session-transcript` | REJECT | Invalid mDOC session transcript |

**Cryptographic Configuration:**
- Verifier key: P-256 (secp256r1) EC key
- Certificate: CN=verifier.example.com, SAN DNS=verifier.example.com
- Issuer: walt.id Verifier CA
- Client ID: `x509_san_dns:verifier.example.com`

### Plan 2: SD-JWT VC with X.509 Hash (HAIP)

**Class:** `SdJwtVcX509SanDnsRequestUriSignedDirectPostJwt`

Tests SD-JWT VC (Selective Disclosure JWT Verifiable Credential) verification with HAIP (High Assurance Interoperability Profile) requirements.

| Property | Value |
|----------|-------|
| **Credential Format** | `sd_jwt_vc` (dc+sd-jwt) |
| **Client ID Scheme** | `x509_hash` |
| **Request Method** | `request_uri_signed` (JAR) |
| **VP Profile** | `haip` |
| **Response Mode** | `direct_post.jwt` (encrypted) |

**Test Modules:**

| Module | Expected Outcome | Description |
|--------|------------------|-------------|
| `oid4vp-1final-verifier-happy-flow` | ACCEPT | Standard successful verification |
| `oid4vp-1final-verifier-minimal-cnf-jwk` | ACCEPT | Minimal confirmation key in credential |
| `oid4vp-1final-verifier-request-uri-method-post` | ACCEPT (or skip) | Request URI via POST |
| `oid4vp-1final-verifier-invalid-kb-jwt-signature` | REJECT | Invalid key binding JWT signature |
| `oid4vp-1final-verifier-invalid-credential-signature` | REJECT | Invalid credential signature |
| `oid4vp-1final-verifier-invalid-sd-hash` | REJECT | Invalid selective disclosure hash |
| `oid4vp-1final-verifier-invalid-kb-jwt-nonce` | REJECT | Invalid nonce in KB-JWT |
| `oid4vp-1final-verifier-invalid-kb-jwt-aud` | REJECT | Invalid audience in KB-JWT |
| `oid4vp-1final-verifier-kb-jwt-iat-in-past` | REJECT | KB-JWT issued too far in past |
| `oid4vp-1final-verifier-kb-jwt-iat-in-future` | REJECT | KB-JWT issued in future |

**Cryptographic Configuration:**
- Verifier key: P-256 (secp256r1) EC key
- Response encryption: Required (HAIP mandate)
- Client ID: `x509_hash:<certificate-hash>`
- Trust anchor: walt.id Verifier CA

## Interpreting Test Results

### Conformance Result vs Verifier Result

Each test module reports two results:

1. **Conformance Result**: Whether the test execution completed successfully
2. **Verifier Result**: Whether the verifier accepted or rejected the presentation

### Expected Outcomes

| Outcome | Meaning |
|---------|---------|
| `ACCEPT` | Verifier must accept the presentation (positive test) |
| `REJECT` | Verifier must reject the presentation (negative test) |
| `ACCEPT_OR_SKIP` | Verifier may accept or skip (optional feature) |

### Example Output

```
================================================================================
Test Plan: oid4vp-1final-verifier-haip-test-plan
Variant: sd_jwt_vc + x509_hash + request_uri_signed + haip + direct_post.jwt
================================================================================
Plan created: abc123xyz
Modules: 10

[1/10] oid4vp-1final-verifier-happy-flow
       Conformance: PASSED
       Verifier:    SUCCESSFUL
       
[2/10] oid4vp-1final-verifier-invalid-kb-jwt-signature
       Conformance: PASSED  
       Verifier:    FAILED (correctly rejected invalid signature)

...

Summary:
  Total:  10
  Passed: 10 (verifier behavior matched expected outcome)
================================================================================
```

**Important:** For negative tests, `Verifier: FAILED` is the **correct** result.
The verifier correctly rejected an invalid presentation.

## HAIP Requirements Validated

The HAIP test plan validates these mandatory requirements:

| Requirement | Implementation |
|-------------|----------------|
| Signed Request | `request_uri_signed` with JAR |
| Encrypted Response | `direct_post.jwt` (JWE) |
| P-256 Keys | secp256r1 curve for all keys |
| SHA-256 | Hash algorithm for certificate hash |
| Holder Binding | KB-JWT for SD-JWT credentials |

## Troubleshooting

### Tests Skip Immediately

**Cause:** Conformance suite not available or ngrok URL not set.

**Solution:**
1. Verify conformance suite: `curl -k https://localhost.emobix.co.uk:8443/`
2. Verify ngrok is running: `curl https://your-ngrok-url.ngrok-free.app`
3. Set environment variable: `export VERIFIER_NGROK_URL="https://..."`

### Connection Refused in Conformance Suite Logs

**Cause:** Docker cannot reach the verifier.

**Solution:**
- Use ngrok (recommended)
- Or use host IP instead of localhost

### SSL Certificate Errors

**Cause:** Truststore not configured or certificate changed.

**Solution:**
```bash
# Re-extract and import certificate
openssl s_client -connect localhost.emobix.co.uk:8443 </dev/null 2>/dev/null | \
  openssl x509 -outform PEM > conformance-test.pem

keytool -delete -alias conformance-test-localhost \
  -keystore conformance-truststore.jks -storepass changeit 2>/dev/null || true
keytool -importcert -trustcacerts -alias conformance-test-localhost \
  -file conformance-test.pem -keystore conformance-truststore.jks \
  -storepass changeit -noprompt
```

### Address Already in Use

**Cause:** Another process is using port 7003.

**Solution:**
```bash
# Find and kill the process
sudo lsof -i :7003
kill <PID>
```

The conformance test starts its own embedded verifier - do not run a manual verifier instance.

## Test Plan Configuration

### Variant Parameters

Each test plan specifies variants that determine the test configuration:

| Parameter | Options | Description |
|-----------|---------|-------------|
| `credential_format` | `iso_mdl`, `sd_jwt_vc`, `iso_photoid` | Credential type |
| `client_id_prefix` | `x509_san_dns`, `x509_hash`, `did`, `verifier_attestation` | Client authentication |
| `request_method` | `request_uri_signed`, `request_uri`, `request` | How request is transmitted |
| `vp_profile` | `plain_vp`, `haip` | Security profile |
| `response_mode` | `direct_post`, `direct_post.jwt` | Response delivery |

### Configuration Structure

```kotlin
TestPlanConfiguration(
    testPlanCreationUrl = { /* URL parameters */ },
    testPlanCreationConfiguration = JsonObject { /* Configuration JSON */ },
    moduleVariant = "...",  // Module-specific variant
    moduleOutcomes = mapOf( /* Expected outcomes per module */ ),
    verificationSessionSetup = CrossDeviceFlowSetup(...)
)
```

## Adding New Test Plans

1. Create a new class implementing `TestPlan` interface
2. Define the test plan variant and configuration
3. Specify expected outcomes for each module
4. Add to `ConformanceTestRunner.testPlans` list
5. Document in this file

Example:
```kotlin
class NewTestPlan(verifierUrlPrefix: String, ...) : TestPlan {
    override val config = TestPlanConfiguration(
        testPlanCreationUrl = {
            append("planName", "oid4vp-1final-verifier-test-plan")
            append("variant", """{"credential_format":"..."}""")
        },
        ...
    )
}
```

## Related Documentation

- [VCI-WALLET.md](VCI-WALLET.md) - VCI wallet conformance tests
- [VCI-ISSUER.md](VCI-ISSUER.md) - VCI issuer conformance tests
- [VP-WALLET.md](VP-WALLET.md) - VP wallet conformance tests
- [OpenID4VP Specification](https://openid.net/specs/openid-4-verifiable-presentations-1_0.html)
- [HAIP Specification](https://openid.net/specs/openid4vc-high-assurance-interoperability-profile-1_0.html)
- [Conformance Suite GitLab](https://gitlab.com/openid/conformance-suite)

## Support

- [GitHub Issues](https://github.com/walt-id/waltid-identity/issues)
- [walt.id Discord](https://discord.gg/AW8AgqJthZ)
- [Documentation](https://docs.walt.id)
