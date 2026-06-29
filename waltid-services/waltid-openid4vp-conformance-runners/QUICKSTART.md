# Quick Setup Guide

This guide walks you through running OpenID4VP/VCI conformance tests locally.

## Prerequisites

- Docker and Docker Compose
- Java 21+
- ngrok (for Verifier tests)
- Ubuntu/Linux environment

## Initial Setup (One-Time)

### 1. Add Hosts Entry

```bash
echo "127.0.0.1 localhost.emobix.co.uk" | sudo tee -a /etc/hosts
```

### 2. Clone Conformance Suite

```bash
git clone https://gitlab.com/openid/conformance-suite.git ~/dev/openid/conformance-suite
```

### 3. Copy Configuration Files

```bash
# Copy Docker Compose
cp ~/dev/walt-id/waltid-unified-build/waltid-identity/waltid-services/waltid-openid4vp-conformance-runners/docker-compose-walt.yml ~/dev/openid/conformance-suite/

# Copy nginx configuration
cp -r ~/dev/walt-id/waltid-unified-build/waltid-identity/waltid-services/waltid-openid4vp-conformance-runners/nginx ~/dev/openid/conformance-suite/
```

### 4. Install ngrok (for Verifier tests)

Download from https://ngrok.com/download or:

```bash
# Snap (Linux)
sudo snap install ngrok

# Homebrew (macOS)
brew install ngrok
```

## Start Conformance Suite

```bash
cd ~/dev/openid/conformance-suite
docker compose -f docker-compose-walt.yml up -d

# Wait ~30 seconds, then verify:
curl -k https://localhost.emobix.co.uk:8443/
```

You should see HTML output. The web interface is available at https://localhost.emobix.co.uk:8443/

## Run Verifier Conformance Tests

### 1. Start ngrok Tunnel

In a separate terminal:

```bash
ngrok http 7003
```

Note the HTTPS URL (e.g., `https://abc123.ngrok-free.app`).

### 2. Run Tests

```bash
cd ~/dev/walt-id/waltid-unified-build/waltid-identity

# Set ngrok URL
export VERIFIER_NGROK_URL="https://abc123.ngrok-free.app"

# Run tests
./gradlew :waltid-services:waltid-openid4vp-conformance-runners:test \
    --tests "VerifierConformanceTests"
```

### 3. View Results

Open in browser:
```
waltid-services/waltid-openid4vp-conformance-runners/build/reports/tests/test/index.html
```

## Run Issuer Conformance Tests

### 1. Start Issuer Service

**Option A: OSS Issuer**
```bash
cd ~/dev/walt-id/waltid-unified-build/waltid-identity
./gradlew :waltid-services:waltid-issuer-api:run
# Issuer runs on port 7002
```

**Option B: Enterprise Issuer**
```bash
cd ~/dev/walt-id/waltid-enterprise-quickstart
docker compose up -d
```

### 2. Configure Issuer URL

```bash
# Option 1: Direct URL
export OPENID4VCI_CONFORMANCE_CREDENTIAL_ISSUER_URL="http://localhost:7002"

# Option 2: Enterprise target
export OPENID4VCI_CONFORMANCE_ENTERPRISE_TARGET="my-org"
```

### 3. Run Tests

```bash
cd ~/dev/walt-id/waltid-unified-build/waltid-identity

./gradlew :waltid-services:waltid-openid4vp-conformance-runners:test \
    --tests "IssuerConformanceTests"
```

**Note:** Authorization code flow tests require manual interaction in the conformance suite UI.
For fully automated tests, ensure your issuer supports pre-authorized code flow.

## Run Wallet Conformance Tests

```bash
cd ~/dev/walt-id/waltid-unified-build/waltid-identity

# Run all wallet tests
./gradlew :waltid-services:waltid-openid4vp-conformance-runners:test \
    --tests "WalletConformanceTests"

# Or run specific plan
./gradlew :waltid-services:waltid-openid4vp-conformance-runners:test \
    --tests "WalletConformanceTests.Plan 1*"
```

Note: Wallet tests require WAL-896 HAIP features to be implemented.

## Stop Conformance Suite

```bash
cd ~/dev/openid/conformance-suite
docker compose -f docker-compose-walt.yml down

# Stop ngrok: Ctrl+C in ngrok terminal
```

## Expected Results

### Verifier Tests

When working correctly, you should see:

```
Plan 1 (MdlX509SanDnsRequestUriSignedDirectPost):
  [0] oid4vp-1final-verifier-happy-flow: conformance=PASSED, verifier=SUCCESSFUL
  [1] oid4vp-1final-verifier-request-uri-method-post: conformance=PASSED, verifier=SUCCESSFUL
  [2] oid4vp-1final-verifier-invalid-session-transcript: conformance=PASSED, verifier=FAILED

Plan 2 (SdJwtVcX509SanDnsRequestUriSignedDirectPostJwt):
  [0] oid4vp-1final-verifier-happy-flow: conformance=PASSED, verifier=SUCCESSFUL
  ...
  [9] oid4vp-1final-verifier-kb-jwt-iat-in-future: conformance=PASSED, verifier=FAILED
```

Note: For negative tests, `verifier=FAILED` is the expected correct behavior.

### Issuer Tests

```
================================================================================
Running issuer plan: Oid4vciIssuerClientAttestationDpop
================================================================================
[1/6] Running module: oid4vci-1_0-issuer-metadata-test
  Status: FINISHED, Result: PASSED

[2/6] Running module: oid4vci-1_0-issuer-happy-flow
  Status: FINISHED, Result: PASSED
...

Total: 6 modules
Passed: 6
```

### Wallet Tests

Currently skip because WAL-896 HAIP features are in development.

## Troubleshooting

### Conformance Suite Not Starting

```bash
# Check containers
docker ps | grep conformance

# Check logs
docker logs conformance-suite-server-1
docker logs conformance-suite-nginx-1
```

Common issues:
- Port 8443 in use: `sudo lsof -i :8443`
- MongoDB slow to start: wait 60 seconds

### Tests Skip

- Conformance suite not running
- ngrok URL not set (Verifier tests)
- Issuer URL not set (Issuer tests)

Verify:
```bash
curl -k https://localhost.emobix.co.uk:8443/
echo $VERIFIER_NGROK_URL
echo $OPENID4VCI_CONFORMANCE_CREDENTIAL_ISSUER_URL
```

### Issuer Test Stuck in WAITING

Authorization code flow requires user interaction:
1. Open https://localhost.emobix.co.uk:8443
2. Find the running test
3. Complete OAuth login in the popup

Use pre-authorized code flow for automated tests.

### Connection Refused

For Verifier tests, the conformance suite (Docker) cannot reach host `localhost`.
Use ngrok to expose the local verifier.

### SSL Certificate Errors

Re-extract certificate:
```bash
cd ~/dev/walt-id/waltid-unified-build/waltid-identity/waltid-services/waltid-openid4vp-conformance-runners

openssl s_client -connect localhost.emobix.co.uk:8443 </dev/null 2>/dev/null | \
  openssl x509 -outform PEM > conformance-test.pem

keytool -delete -alias conformance-test-localhost -keystore conformance-truststore.jks \
  -storepass changeit 2>/dev/null || true
keytool -importcert -trustcacerts -alias conformance-test-localhost \
  -file conformance-test.pem -keystore conformance-truststore.jks \
  -storepass changeit -noprompt
```

### Address Already in Use (Port 7003)

The test starts its own embedded verifier. Kill any existing process:

```bash
sudo lsof -i :7003
kill <PID>
```

## Additional Resources

- [README.md](README.md) - Full documentation
- [VERIFIER-TESTS.md](VERIFIER-TESTS.md) - Verifier test details
- [ISSUER-TESTS.md](ISSUER-TESTS.md) - Issuer test details
- [WALLET-TESTS.md](WALLET-TESTS.md) - Wallet test details
