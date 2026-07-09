# VP Wallet Conformance Tests

This document covers setup, execution, and status of OpenID4VP Wallet conformance tests.

## Current Status

| Component | Status | Notes |
|-----------|--------|-------|
| Test plan creation | ✅ Working | Verified via `IsolatedWalletConformanceTest` |
| Test module fetching | ❌ 404 Error | API path needs investigation |
| Full test execution | 🔄 Blocked | Waiting on module fetch fix |

**Last tested:** 2026-07-09

### Known Issues

1. **Test module 404**: After test plan creation succeeds, `getTestModules()` returns 404
2. **Timeout in main test class**: `VpWalletConformanceTests` has mysterious HTTP timeouts;
   use `IsolatedWalletConformanceTest` for now

---

## Quick Start

### Prerequisites

1. **Conformance Suite** running:
   ```bash
   cd ~/dev/openid/conformance-suite
   docker compose -f docker-compose-walt.yml up -d
   ```

2. **Hosts entry**: `/etc/hosts` must contain:
   ```
   127.0.0.1 localhost.emobix.co.uk
   ```

3. Verify conformance suite is up:
   ```bash
   curl -k https://localhost.emobix.co.uk:8443/api/runner/available
   ```

### Running Tests

```bash
cd ~/dev/walt-id/waltid-unified-build

# Run the working isolated test (RECOMMENDED)
./gradlew :waltid-services:waltid-openid4vp-conformance-runners:test \
    --tests "IsolatedWalletConformanceTest"

# Run full test suite (may timeout - use for debugging)
./gradlew :waltid-services:waltid-openid4vp-conformance-runners:test \
    --tests "VpWalletConformanceTests"
```

---

## Test Profiles

### HAIP-Compliant Profiles (Recommended)

| Profile | Format | Client ID | Response Mode | Status |
|---------|--------|-----------|---------------|--------|
| SD-JWT VC HAIP | SD-JWT VC | **x509_hash** | direct_post.jwt | 🔄 Pending |
| mDL HAIP | ISO mDL | **x509_hash** | direct_post.jwt | 🔄 Pending |
| Negative Security | SD-JWT VC | **x509_hash** | direct_post.jwt | 🔄 Pending |

### Baseline Profiles (Non-HAIP)

| Profile | Format | Client ID | Response Mode | Status |
|---------|--------|-----------|---------------|--------|
| SD-JWT VC Baseline | SD-JWT VC | x509_san_dns | direct_post.jwt | ✅ Plan created |
| mDL Baseline | ISO mDL | x509_san_dns | direct_post.jwt | 🔄 Pending |

> ⚠️ **HAIP Requirement P-02**: For full HAIP compliance, `x509_hash` MUST be used.

---

## Architecture

```
┌─────────────────┐    ┌─────────────────┐    ┌─────────────────┐
│ Conformance     │    │ Wallet Adapter  │    │ Wallet API2     │
│ Suite (Verifier)│───▶│ (port 7006)     │───▶│ (port 7001)     │
└─────────────────┘    └─────────────────┘    └─────────────────┘
```

The **VpWalletConformanceAdapter** bridges the conformance suite with the walt.id wallet API.

---

## Environment Variables

| Variable | Description | Default |
|----------|-------------|---------|
| `WALLET_API_URL` | Wallet API2 base URL | `http://127.0.0.1:7001` |
| `WALLET_ADAPTER_PORT` | Adapter port | `7006` |
| `CONFORMANCE_HOST` | Conformance suite host | `localhost.emobix.co.uk` |
| `CONFORMANCE_PORT` | Conformance suite port | `8443` |

---

## Code Structure

```
waltid-openid4vp-conformance-runners/
├── src/main/kotlin/.../
│   ├── adapter/
│   │   └── VpWalletConformanceAdapter.kt
│   ├── testplans/
│   │   ├── http/ConformanceInterface.kt
│   │   ├── runner/WalletTestPlanRunner.kt
│   │   └── plans/vp/wallet/
│   │       ├── WalletTestPlan.kt
│   │       ├── VpWalletSdJwtVcX509SanDnsRequestUriSignedDirectPost.kt
│   │       ├── VpWalletMdlX509SanDnsRequestUriSignedDirectPost.kt
│   │       └── VpWalletNegativeTests.kt
└── src/test/kotlin/.../
    ├── IsolatedWalletConformanceTest.kt  # ← Working test (use this)
    └── VpWalletConformanceTests.kt       # Full suite (timeout issues)
```

---

## Troubleshooting

### "Failed to get test modules: 404"

The test plan was created but the API path for modules is incorrect. Investigation needed.

### HTTP Timeout in VpWalletConformanceTests

The main test class has timeout issues due to class initialization. Use `IsolatedWalletConformanceTest` instead:

```bash
./gradlew :waltid-services:waltid-openid4vp-conformance-runners:test \
    --tests "IsolatedWalletConformanceTest"
```

### Conformance suite not available

1. Check Docker is running: `docker ps | grep conformance`
2. Verify hosts entry: `ping localhost.emobix.co.uk`
3. Test HTTPS: `curl -k https://localhost.emobix.co.uk:8443/`

---

## HAIP Requirements

| Req | Description | Status |
|-----|-------------|--------|
| P-02 | x509_hash client identification | 🔄 Pending |
| W-27 | JAR with request_uri | 🔄 Pending |
| W-28 | direct_post.jwt response mode | 🔄 Pending |
| W-29 | Response encryption (ECDH-ES + P-256) | 🔄 Pending |
| W-36 | KB-JWT holder binding | 🔄 Pending |
| CF-02 | P-256 + SHA-256 | 🔄 Pending |
