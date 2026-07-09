# VP Wallet Conformance Tests

Complete guide for running OpenID4VP Wallet conformance tests against the OpenID Foundation conformance suite.

## Current Status

| Component | Status | Notes |
|-----------|--------|-------|
| Test plan creation | ✅ Working | Plans created with 12 modules |
| Module extraction | ✅ Working | Modules from create response |
| Full test execution | 🔄 Ready | Needs wallet API + adapter running |

**Last tested:** 2026-07-09

---

## Quick Start — Full Test Run

### Step 1: Start Conformance Suite

```bash
cd ~/dev/openid/conformance-suite
docker compose -f docker-compose-walt.yml up -d

# Wait ~30s for startup, then verify:
curl -k https://localhost.emobix.co.uk:8443/api/runner/available
```

### Step 2: Start Wallet API2

In a **new terminal**:

```bash
cd ~/dev/walt-id/waltid-unified-build

# Start wallet-api2 on port 7001
./gradlew :waltid-services:waltid-wallet-api2:run
```

Wait until you see: `Application started` or similar.

### Step 3: Run Full Conformance Tests

In a **new terminal**:

```bash
cd ~/dev/walt-id/waltid-unified-build

# Run ALL wallet conformance tests (starts adapter automatically)
./gradlew :waltid-services:waltid-openid4vp-conformance-runners:test \
    --tests "VpWalletConformanceTests" --rerun-tasks --info
```

### Step 4: View Logs

Open: <https://localhost.emobix.co.uk:8443/logs.html>

Note: Tests use `"publish":"no"` by default. To see them in logs, either:
- Change `publish` to `"summary"` in test plan configuration, OR
- Query directly: `curl -sk https://localhost.emobix.co.uk:8443/api/plan/<PLAN_ID>`

---

## Individual Test Commands

### Validate Setup Only (No Wallet Required)

```bash
# Just creates test plan, doesn't run modules
./gradlew :waltid-services:waltid-openid4vp-conformance-runners:test \
    --tests "IsolatedWalletConformanceTest" --rerun-tasks
```

### Run SD-JWT VC Tests Only

```bash
./gradlew :waltid-services:waltid-openid4vp-conformance-runners:test \
    --tests "VpWalletConformanceTests.VP Wallet - SD-JWT VC" --rerun-tasks
```

### Run mDL Tests Only

```bash
./gradlew :waltid-services:waltid-openid4vp-conformance-runners:test \
    --tests "VpWalletConformanceTests.VP Wallet - mDL" --rerun-tasks
```

### Run Negative/Security Tests Only

```bash
./gradlew :waltid-services:waltid-openid4vp-conformance-runners:test \
    --tests "VpWalletConformanceTests.VP Wallet - Negative Tests" --rerun-tasks
```

---

## Prerequisites Checklist

- [ ] **Hosts entry**: `/etc/hosts` contains `127.0.0.1 localhost.emobix.co.uk`
- [ ] **Conformance suite running**: `docker ps | grep conformance`
- [ ] **Wallet API2 running**: `curl http://127.0.0.1:7001/wallet-api/health` (or check terminal)
- [ ] **Truststore configured**: Handled by `build.gradle.kts` automatically

---

## Test Profiles

### SD-JWT VC (HAIP-Compliant)

| Module | Description |
|--------|-------------|
| `wallet-happy-flow` | Basic presentation flow |
| `wallet-alternate-happy-flow` | With optional state, longer nonce |
| `wallet-request-uri-method-post` | POST for request_uri |
| `wallet-fewer-claims-than-available` | Data minimization |
| `wallet-optional-credential-set` | Credential sets handling |
| `wallet-no-claims-in-dcql-query` | No selective disclosure |
| `wallet-negative-test-*` | Security validation (6 tests) |

**Total: 12 test modules**

---

## Architecture

```
┌─────────────────────┐
│ Conformance Suite   │
│ (Verifier Role)     │
│ port 8443           │
└─────────┬───────────┘
          │ 1. Sends authorization request
          ▼
┌─────────────────────┐
│ Wallet Adapter      │
│ port 7006           │
│ (bridges protocol)  │
└─────────┬───────────┘
          │ 2. Forwards to wallet API
          ▼
┌─────────────────────┐
│ Wallet API2         │
│ port 7001           │
│ (processes request) │
└─────────────────────┘
          │ 3. Returns VP response
          ▼
    Back to Conformance Suite
```

The test runner:
1. Creates a test plan on conformance suite
2. Starts the wallet adapter (Ktor server on port 7006)
3. Runs each test module
4. Adapter proxies conformance ↔ wallet communication
5. Reports results

---

## Environment Variables

| Variable | Default | Description |
|----------|---------|-------------|
| `WALLET_API_URL` | `http://127.0.0.1:7001` | Wallet API2 base URL |
| `WALLET_ADAPTER_PORT` | `7006` | Adapter listens here |
| `CONFORMANCE_HOST` | `localhost.emobix.co.uk` | Conformance suite hostname |
| `CONFORMANCE_PORT` | `8443` | Conformance suite HTTPS port |
| `CONFORMANCE_WALLET_ID` | (auto) | Wallet ID for testing |

---

## Troubleshooting

### Tests timeout immediately

**Cause**: Wallet API2 not running or wrong port.

**Fix**:
```bash
# Check wallet is running
curl http://127.0.0.1:7001/wallet-api/health

# If not, start it:
./gradlew :waltid-services:waltid-wallet-api2:run
```

### "Connection refused" to conformance suite

**Cause**: Conformance suite not running or hosts entry missing.

**Fix**:
```bash
# Check hosts
grep emobix /etc/hosts
# Should show: 127.0.0.1 localhost.emobix.co.uk

# Check Docker
docker ps | grep conformance

# Restart if needed
cd ~/dev/openid/conformance-suite
docker compose -f docker-compose-walt.yml restart
```

### SSL certificate errors

**Cause**: JVM truststore not configured.

**Fix**: The `build.gradle.kts` sets this automatically. If issues persist:
```bash
# Verify truststore exists
ls -la ~/dev/walt-id/waltid-unified-build/waltid-identity/waltid-services/waltid-openid4vp-conformance-runners/conformance-truststore.jks
```

### Tests pass but no logs visible

**Cause**: `publish` is set to `"no"`.

**Fix**: Query the plan directly:
```bash
# Get plan ID from test output, then:
curl -sk "https://localhost.emobix.co.uk:8443/api/plan/<PLAN_ID>" | jq .
```

Or change the test configuration to `"publish": "summary"`.

---

## HAIP Compliance Requirements

| Req ID | Requirement | Implementation |
|--------|-------------|----------------|
| P-02 | x509_hash client identification | ✅ Test plans use `x509_hash` variant |
| W-27 | JAR with request_uri | ✅ `request_uri_signed` mode |
| W-28 | direct_post.jwt response mode | ✅ Configured in variant |
| W-29 | Response encryption (ECDH-ES) | ✅ `authorization_encrypted_response_alg` |
| W-36 | KB-JWT holder binding | 🔄 Wallet implementation |
| CF-02 | P-256 + SHA-256 | ✅ Default curve |

---

## Code Structure

```
waltid-openid4vp-conformance-runners/
├── docs/
│   └── VP-WALLET.md              # This file
├── src/main/kotlin/.../
│   ├── adapter/
│   │   └── VpWalletConformanceAdapter.kt  # HTTP bridge
│   ├── config/
│   │   └── ConformanceConfig.kt           # Environment config
│   └── testplans/
│       ├── http/ConformanceInterface.kt   # Conformance API client
│       ├── runner/WalletTestPlanRunner.kt # Test orchestration
│       └── plans/vp/wallet/
│           ├── WalletTestPlan.kt                              # Base class
│           ├── VpWalletSdJwtVcX509SanDnsRequestUriSignedDirectPost.kt
│           ├── VpWalletMdlX509SanDnsRequestUriSignedDirectPost.kt
│           └── VpWalletNegativeTests.kt
└── src/test/kotlin/.../
    ├── IsolatedWalletConformanceTest.kt  # Setup validation only
    └── VpWalletConformanceTests.kt       # Full test suite
```
