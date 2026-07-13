# VP Wallet Conformance Test - Gradle Runner Guide

## Prerequisites

### 1. Start Services (3 terminals)

**Terminal 1 - Conformance Suite (Docker):**
```bash
cd ~/dev/openid/conformance-suite
docker compose -f docker-compose-walt.yml up -d
# Wait ~30s for startup
# Verify: curl -sk https://localhost.emobix.co.uk:8443/
```

**Terminal 2 - Issuer API:**
```bash
cd ~/dev/walt-id/waltid-unified-build
./gradlew :waltid-services:waltid-issuer-api2:run
# Runs on port 7002
```

**Terminal 3 - Wallet API:**
```bash
cd ~/dev/walt-id/waltid-unified-build
./gradlew :waltid-services:waltid-wallet-api2:run
# Runs on port 7005
```

### 2. Set Up Test Wallet

Run the setup script to create a wallet with credential:

```bash
cd ~/dev/walt-id/waltid-unified-build/waltid-identity
./waltid-services/waltid-openid4vp-conformance-runners/scripts/setup-test-wallet.sh
```

This creates:
- Credential store
- Wallet with key
- Holder DID
- SD-JWT VC credential with x5c header

### 3. Start ngrok Tunnel (Required for Docker)

The conformance suite runs in Docker and cannot reach `localhost:7006` directly.

**Terminal 4 - ngrok:**
```bash
ngrok http 7006
# Copy the https://xxx.ngrok-free.app URL
```

## Running Tests

### Option A: Run All Wallet Tests

```bash
cd ~/dev/walt-id/waltid-unified-build

# Set ngrok URL (replace with your actual ngrok URL)
export WALLET_ADAPTER_URL="https://YOUR-NGROK-URL.ngrok-free.app/openid4vp/authorize"

# Run all wallet conformance tests
./gradlew :waltid-services:waltid-openid4vp-conformance-runners:test \
    --tests "VpWalletConformanceTests" \
    --rerun-tasks
```

### Option B: Run Specific Test Plan

```bash
# SD-JWT VC tests only
./gradlew :waltid-services:waltid-openid4vp-conformance-runners:test \
    --tests "VpWalletConformanceTests.VP Wallet - SD-JWT VC" \
    --rerun-tasks

# mDL tests only
./gradlew :waltid-services:waltid-openid4vp-conformance-runners:test \
    --tests "VpWalletConformanceTests.VP Wallet - mDL" \
    --rerun-tasks

# Negative tests only
./gradlew :waltid-services:waltid-openid4vp-conformance-runners:test \
    --tests "VpWalletConformanceTests.VP Wallet - Negative Tests" \
    --rerun-tasks
```

### Option C: Run Isolated Test (Debugging)

```bash
./gradlew :waltid-services:waltid-openid4vp-conformance-runners:test \
    --tests "IsolatedWalletConformanceTest" \
    --rerun-tasks
```

## Test Plans Available

| Test Class | Plan | Description |
|------------|------|-------------|
| `VpWalletConformanceTests` | SD-JWT VC | SD-JWT VC + x509_san_dns + direct_post.jwt |
| `VpWalletConformanceTests` | mDL | ISO mDL + x509_san_dns + direct_post |
| `VpWalletConformanceTests` | Negative | Security validation tests |

## Architecture

```
┌─────────────────────────┐
│   Conformance Suite     │  (Docker, port 8443)
│   Acts as Verifier      │
└───────────┬─────────────┘
            │ HTTPS (signed JAR)
            ▼
┌─────────────────────────┐
│        ngrok            │  (Public URL → localhost:7006)
└───────────┬─────────────┘
            │
            ▼
┌─────────────────────────┐
│   Wallet Adapter        │  (port 7006, started by test runner)
│   /openid4vp/authorize  │
└───────────┬─────────────┘
            │ HTTP
            ▼
┌─────────────────────────┐
│     Wallet API          │  (port 7005)
│   /wallet-api/...       │
└─────────────────────────┘
```

## Troubleshooting

### "Conformance suite not available"
- Ensure Docker is running: `docker ps | grep conformance`
- Check /etc/hosts: `127.0.0.1 localhost.emobix.co.uk`
- Verify SSL: `curl -sk https://localhost.emobix.co.uk:8443/`

### "Wallet has no credential stores"
- Re-run setup script: `./scripts/setup-test-wallet.sh`
- Wallet-api2 restarts clear in-memory data

### "Connection refused" to adapter
- Check ngrok is running
- Verify `WALLET_ADAPTER_URL` environment variable is set
- Adapter starts automatically when tests run

### Tests interrupted (alias conflict)
- This happens when running via web UI, not Gradle
- Gradle runner handles sequential execution automatically

### SSL certificate errors
- JDK truststore should be configured by build.gradle.kts
- Manual fix: `sudo keytool -import -alias conformance-emobix -file /tmp/conformance-ca.crt -keystore $JAVA_HOME/lib/security/cacerts -storepass changeit`

## Environment Variables

| Variable | Default | Description |
|----------|---------|-------------|
| `WALLET_ADAPTER_URL` | `http://host.docker.internal:7006/openid4vp/authorize` | Adapter URL for conformance suite |
| `CONFORMANCE_WALLET_ID` | (from setup script) | Wallet ID for testing |

## Expected Results

When tests pass, you'll see output like:
```
================================================================================
Test Plan: VP Wallet: SD-JWT VC + x509_san_dns + request_uri_signed + direct_post.jwt
================================================================================
  Plan name: oid4vp-1final-wallet-haip-test-plan
  ...

Test modules: 12
   - oid4vp-1final-wallet-happy-flow
   - oid4vp-1final-wallet-alternate-happy-flow
   ...

[1/12] Running module: oid4vp-1final-wallet-happy-flow
   Result: PASSED
...

Test Results:
--------------------------------------------------------------------------------
  Total modules: 12
  Passed:  12
================================================================================
```
