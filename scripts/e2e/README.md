# E2E Testing: Mobile Wallet Demos

This folder contains the native E2E paths for wallet demo apps.

Primary goal: keep orchestration thin and move receive/present logic into native tests.

## Kept Test Entrypoints

Local enterprise backend (requires local stack + ngrok):
- `./e2e-android-local-instrumented.sh`
- `./e2e-android-local-instrumented-attested.sh`
- `./e2e-ios-local-instrumented.sh`
- `./e2e-ios-local-instrumented-attested.sh`

Public EUDI backend (no local enterprise required):
- `./e2e-android-public-eudi-instrumented.sh`
- `./e2e-ios-public-eudi-instrumented.sh`

Legacy non-instrumented scripts (`e2e-android.sh`, `e2e-ios.sh`) are still present but are no longer the recommended path for this WAL-1033 effort.

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

1. Android non-attested
- Command: `./e2e-android-local-instrumented.sh`
- Result: FAIL
- Reason: issuer profile enforces attestation; receive fails with `401 invalid_client` and `Client attestation headers are required by this issuer configuration`.

2. Android attested
- Command: `./e2e-android-local-instrumented-attested.sh`
- Result: PASS

3. iOS non-attested
- Command: `./e2e-ios-local-instrumented.sh`
- Result: PASS (test-level pass)
- Important: in non-attested mode the test allows expected attestation rejection and can return early. This is not equivalent to a full receive+present success unless issuer profile does not enforce attestation.

4. iOS attested
- Command: `./e2e-ios-local-instrumented-attested.sh`
- Result: PASS

### Public EUDI

5. Android public EUDI instrumented
- Command: `./e2e-android-public-eudi-instrumented.sh`
- Result: PASS

6. iOS public EUDI instrumented (run twice)
- Command: `./e2e-ios-public-eudi-instrumented.sh`
- Result: FAIL (2/2)
- Failure evidence from xcresult:
  - `XCTAssertTrue failed - Receive failed, status: Receive failed: Failed to parse inline credential offer`
  - Follow-on: `XCTAssertNotNil failed - Verifier did not confirm wallet response`

xcresult paths from failing runs:
- `/Users/szipe/Library/Developer/Xcode/DerivedData/iosApp-ezcrcwlijmetamgtukwvrcoaesho/Logs/Test/Test-iosApp-2026.05.29_10-29-39-+0200.xcresult`
- `/Users/szipe/Library/Developer/Xcode/DerivedData/iosApp-ezcrcwlijmetamgtukwvrcoaesho/Logs/Test/Test-iosApp-2026.05.29_10-34-31-+0200.xcresult`

## Current Gaps / Handover Focus

1. Android non-attested local script currently fails hard under attestation-enforcing profile.
- To test full non-attested success, point to an issuer profile that does not require attestation.

2. iOS public EUDI path is currently not reliable.
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
