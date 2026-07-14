# VP Wallet Conformance Test - Gradle Runner Guide

## Quick Reference

**Current Test Status: 11/12 passing (92%)**

| Category | Passed | Failed | Notes |
|----------|--------|--------|-------|
| Happy Path | 5/6 | 1 | Test 2 alias conflict (framework limitation) |
| Negative Tests | 6/6 | 0 | All validations working ✅ |

---

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

```bash
cd ~/dev/walt-id/waltid-unified-build/waltid-identity
./waltid-services/waltid-openid4vp-conformance-runners/scripts/setup-test-wallet.sh
```

This creates:
- Credential store
- Wallet with key
- Holder DID
- SD-JWT VC credential with x5c header
- Writes wallet ID to `/tmp/conformance-wallet-id.txt`

---

## Running Tests

### Run All Wallet Tests

```bash
cd ~/dev/walt-id/waltid-unified-build

./gradlew :waltid-services:waltid-openid4vp-conformance-runners:test \
    --tests "VpWalletConformanceTests" \
    --rerun-tasks
```

### Run with More Memory (for long test runs)

```bash
./gradlew :waltid-services:waltid-openid4vp-conformance-runners:test \
    --tests "VpWalletConformanceTests" \
    --rerun-tasks \
    -Dorg.gradle.jvmargs="-Xmx4g"
```

### View Results

```bash
# HTML report
open waltid-identity/waltid-services/waltid-openid4vp-conformance-runners/build/reports/tests/test/index.html

# Or grep for summary
grep "Result:" build/reports/tests/test/*/*.html
```

---

## Expected Results

```
[1/12] Running module: oid4vp-1final-wallet-happy-flow
   Result: PASSED
[2/12] Running module: oid4vp-1final-wallet-alternate-happy-flow
   Result: FAILED          ← Alias conflict (known framework issue)
[3/12] Running module: oid4vp-1final-wallet-request-uri-method-post
   Result: PASSED
[4/12] Running module: oid4vp-1final-wallet-fewer-claims-than-available
   Result: PASSED
[5/12] Running module: oid4vp-1final-wallet-optional-credential-set
   Result: PASSED
[6/12] Running module: oid4vp-1final-wallet-no-claims-in-dcql-query
   Result: PASSED
[7/12] Running module: oid4vp-1final-wallet-negative-test-invalid-request-object-signature
   Result: REJECTED        ← Wallet correctly rejected
[8/12] Running module: oid4vp-1final-wallet-negative-test-mismatched-client-id
   Result: REJECTED        ← Wallet correctly rejected
[9/12] Running module: oid4vp-1final-wallet-negative-test-redirect-uri-with-direct-post
   Result: REJECTED        ← Wallet correctly rejected (redirect_uri + direct_post)
[10/12] Running module: oid4vp-1final-wallet-negative-test-missing-nonce
   Result: REJECTED        ← Wallet correctly rejected
[11/12] Running module: oid4vp-1final-wallet-negative-test-invalid-client-id-prefix
   Result: REJECTED        ← Wallet correctly rejected
[12/12] Running module: oid4vp-1final-wallet-negative-test-unknown-transaction-data-type
   Result: REJECTED        ← Wallet correctly rejected (unknown transaction_data type)
```

---

## Architecture

```
┌─────────────────────────┐
│   Conformance Suite     │  (Docker, port 8443)
│   Acts as Verifier      │
└───────────┬─────────────┘
            │ HTTPS (signed JAR via request_uri)
            ▼
┌─────────────────────────┐
│   Wallet Adapter        │  (port 7006, started by test runner)
│   /openid4vp/authorize  │
└───────────┬─────────────┘
            │ HTTP (openid4vp:// URL)
            ▼
┌─────────────────────────┐
│     Wallet API2         │  (port 7005)
│   /wallet/{id}/present  │
└─────────────────────────┘
```

**Key insight:** The adapter converts HTTP requests to `openid4vp://` URLs and calls the wallet's `/present` endpoint, which handles the entire flow (fetch JAR → match credentials → sign VP → submit to verifier).

---

## Troubleshooting

### Tests fail with 404 on request_uri

**Symptom:** Wallet can't fetch authorization request, gets 404.

**Fixed:** URL rewriting now preserves query parameters. If you still see this, ensure you're running the latest code.

### Negative tests show ERROR instead of REJECTED

**Symptom:** Tests that should pass (wallet correctly rejected) show as ERROR or timeout.

**Fixed:** Test runner now detects negative tests by module name (`contains("negative-test")`) and checks for error response patterns.

### "Wallet has no credential stores" or wrong wallet

**Cause:** Adapter picked the wrong wallet.

**Fix:** 
1. Re-run setup script: `./scripts/setup-test-wallet.sh`
2. Adapter now reads wallet ID from `/tmp/conformance-wallet-id.txt`
3. Or set `export CONFORMANCE_WALLET_ID=<your-wallet-id>`

### SSL certificate errors

**Symptom:** `PKIX path building failed`

**Fix:** Add conformance suite certificate to JDK truststore:
```bash
echo | openssl s_client -connect localhost.emobix.co.uk:8443 2>/dev/null | \
    openssl x509 -out /tmp/conformance-suite.crt

JAVA_HOME=/home/pp/.gradle/jdks/eclipse_adoptium-21-amd64-linux.2
$JAVA_HOME/bin/keytool -import -trustcacerts -alias conformance-suite \
    -file /tmp/conformance-suite.crt \
    -keystore $JAVA_HOME/lib/security/cacerts \
    -storepass changeit -noprompt
```

### Tests interrupted (alias conflict)

**Symptom:** `Alias has now been claimed by another test`

**Cause:** All modules in the same test plan share one alias. This is a known conformance suite limitation.

**Impact:** Only affects test 2 (alternate-happy-flow). Other tests pass.

### Gradle daemon crashes

**Symptom:** Build fails mid-test with "daemon was shut down"

**Fix:** Add more memory:
```bash
./gradlew ... -Dorg.gradle.jvmargs="-Xmx4g"
```

---

## Environment Variables

| Variable | Default | Description |
|----------|---------|-------------|
| `CONFORMANCE_WALLET_ID` | (from /tmp file) | Wallet ID for testing |
| `WALLET_API_URL` | `http://127.0.0.1:7005` | Wallet API base URL |
| `CONFORMANCE_HOST` | `localhost.emobix.co.uk` | Suite hostname |
| `CONFORMANCE_PORT` | `8443` | Suite HTTPS port |

---

## Known Issues & TODOs

### Test Framework

- [ ] **Alias conflict (Test 2)**: Each module needs unique alias; currently all share one
- [ ] Add per-module alias generation

See [VP-WALLET.md](VP-WALLET.md) for detailed status and architecture documentation.
