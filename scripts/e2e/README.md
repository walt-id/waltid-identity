# E2E Testing: Mobile Wallet Demos

This folder contains the native E2E paths for wallet demo apps.

Primary goal: keep orchestration thin and move receive/present logic into native tests.

## Kept Test Entrypoints

Local enterprise backend (requires local stack + ngrok):
- `./e2e-android-local-instrumented.sh` — non-attested (default)
- `./e2e-android-local-instrumented.sh --attested` — with client attestation
- `./e2e-ios-local-instrumented.sh` — non-attested (default)
- `./e2e-ios-local-instrumented.sh --attested` — with client attestation

Convenience wrappers (call the above with `--attested`):
- `./e2e-android-local-instrumented-attested.sh`
- `./e2e-ios-local-instrumented-attested.sh`

Public EUDI backend (no local enterprise required):
- `./e2e-android-public-eudi-instrumented.sh`
- `./e2e-ios-public-eudi-instrumented.sh`

### Attested vs Non-Attested

The `--attested` flag controls which issuer profile is used:
- **Non-attested** (default): uses `issuer2-noattest.mdl-profile` — no client attestation required. The script auto-provisions `issuer2-noattest` via API if it doesn't exist.
- **Attested** (`--attested`): uses `issuer2.mdl-profile` — requires wallet to present client attestation headers.

Legacy non-instrumented scripts (`e2e-android.sh`, `e2e-ios.sh`) are still present but are no longer the recommended path.

## Environment Setup

From `scripts/e2e`:

```bash
cp e2e.env.example e2e.env
# set HOST_ALIAS_DOMAIN=<your ngrok domain>
```

### Local Enterprise Stack Constraints

1. Start enterprise quickstart (`docker compose up -d`, then recreate).
2. After recreate, comment out `basePort = 7500` in quickstart `enterprise.conf`, then restart enterprise API.
3. Keep ngrok forwarding `7500`.
4. For Android local flow, set emulator reverse port:

```bash
adb reverse tcp:7500 tcp:7500
```

This is required because verifier callback URLs still reference `localhost:7500` for emulator routing.

## Public EUDI Notes

- Android public EUDI flow is fully native in `EudiPublicBackendReceiveInstrumentedTest.kt` (offer generation + verifier polling inside test).
- iOS public EUDI flow is fully native in `EudiPublicBackendUITests.swift` (offer generation + verifier polling inside test).
- No helper Python scripts are required anymore for public EUDI instrumented paths.

## Actual Run Matrix (2026-05-29)

### Local Enterprise

All 4 scenarios pass:

| # | Command | Result |
|---|---------|--------|
| 1 | `./e2e-android-local-instrumented.sh` | PASS |
| 2 | `./e2e-android-local-instrumented.sh --attested` | PASS |
| 3 | `./e2e-ios-local-instrumented.sh` | PASS |
| 4 | `./e2e-ios-local-instrumented.sh --attested` | PASS |

### Public EUDI

| # | Command | Result |
|---|---------|--------|
| 5 | `./e2e-android-public-eudi-instrumented.sh` | PASS |
| 6 | `./e2e-ios-public-eudi-instrumented.sh` | PASS |

## Resolved Issues (2026-05-29)

The iOS public EUDI path previously failed with multiple issues, now all fixed:

1. **Stale offer URL injection**: `resolveOfferURL()` read from hardcoded `/tmp/waltid-e2e-offer-url.txt` which could contain stale data. Fixed: file-based injection now requires explicit `E2E_OFFER_URL_FILE` env var.
2. **Broken cross-domain cookies**: `URLSessionConfiguration.ephemeral` with an explicit `HTTPCookieStorage()` prevented cookie sharing between `issuer.eudiw.dev` and `backend.issuer.eudiw.dev`. Fixed: removed the explicit cookie storage (ephemeral config shares cookies by default).
3. **tx_code type mismatch**: EUDI backend returns `tx_code` as integer, not string. Fixed: added `NSNumber` fallback in offer generation.
4. **iOS `getCosePublicKey()` not implemented**: `JWKKeyCoseTransform.ios.kt` was a `TODO()` stub. Fixed: implemented using JWK JSON extraction (needed for mdoc presentations).
5. **iOS `encryptJwe()` not implemented**: EUDI verifier uses `direct_post.jwt` response mode requiring ECDH-ES JWE encryption. Fixed: implemented via JOSESwift library in Swift, bridged to Kotlin/Native.

## Quick Command Recap

```bash
cd scripts/e2e

# Local enterprise
./e2e-android-local-instrumented-attested.sh
./e2e-android-local-instrumented.sh
./e2e-ios-local-instrumented-attested.sh
./e2e-ios-local-instrumented.sh

# Public EUDI
./e2e-android-public-eudi-instrumented.sh
./e2e-ios-public-eudi-instrumented.sh
```
