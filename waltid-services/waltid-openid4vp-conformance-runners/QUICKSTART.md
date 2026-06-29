# Quick Setup Guide

This guide walks you through running OpenID4VP conformance tests locally.

## Prerequisites

- Docker and Docker Compose
- Java 21+
- ngrok (for Verifier2 tests)
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

### 4. Install ngrok

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

## Run Verifier2 Conformance Tests

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
    --tests "Verifier2ConformanceTests"
```

### 3. View Results

Open in browser:
```
waltid-services/waltid-openid4vp-conformance-runners/build/reports/tests/test/index.html
```

## Run Wallet HAIP Conformance Tests

```bash
cd ~/dev/walt-id/waltid-unified-build/waltid-identity

# Run all wallet tests
./gradlew :waltid-services:waltid-openid4vp-conformance-runners:test \
    --tests "WalletHAIPConformanceTests"

# Or run specific plan
./gradlew :waltid-services:waltid-openid4vp-conformance-runners:test \
    --tests "WalletHAIPConformanceTests.HAIP Plan 1*"
```

Note: Wallet tests require WAL-896 HAIP features to be implemented.

## Stop Conformance Suite

```bash
cd ~/dev/openid/conformance-suite
docker compose -f docker-compose-walt.yml down

# Stop ngrok: Ctrl+C in ngrok terminal
```

## Expected Results

### Verifier2 Tests

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

Note: For negative tests, `verifier=FAILED` is the expected correct behavior (verifier correctly rejected invalid input).

### Wallet Tests

Currently skip because WAL-896 HAIP features are in development. Once implemented, tests will execute and validate wallet compliance.

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
- ngrok URL not set (Verifier2 tests)

Verify:
```bash
curl -k https://localhost.emobix.co.uk:8443/
echo $VERIFIER_NGROK_URL
```

### Connection Refused

For Verifier2 tests, the conformance suite (Docker) cannot reach host `localhost`.
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
- [VERIFIER2-TESTS.md](VERIFIER2-TESTS.md) - Verifier2 test details
- [WALLET-HAIP-TESTS.md](WALLET-HAIP-TESTS.md) - Wallet HAIP test details
