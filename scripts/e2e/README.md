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

## Cleartext Workaround Patch (Local Stack)

Local enterprise metadata still resolves to `http://...enterprise.localhost...` in this setup.

Apply once in `waltid-identity` root:

```bash
git apply scripts/e2e/patches/local-cleartext-workarounds.patch
```

Revert:

```bash
git apply -R scripts/e2e/patches/local-cleartext-workarounds.patch
```

Patch touches:
- Android app manifest (`android:usesCleartextTraffic="true"`)
- iOS `Info.plist` (ATS exception for `enterprise.localhost`)

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
| 6 | `./e2e-ios-public-eudi-instrumented.sh` | FAIL |

iOS public EUDI failure: `Failed to parse inline credential offer` in wallet receive path.

## Current Gaps

1. iOS public EUDI path is not reliable.
- Primary blocker is inline credential offer parsing failure in wallet receive path.
- Next debugging target: parity between Android and iOS offer encoding/parsing assumptions for generated inline `credential_offer` URLs.

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
