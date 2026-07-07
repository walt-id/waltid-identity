# iOS Wallet Demo E2E Tests

Scripts to run end-to-end tests for the iOS wallet demo app.

## Prerequisites

- iOS Simulator running (or specify `IOS_SIMULATOR_ID`)

## Public EUDI Backend (No Infrastructure Required)

Tests against the public EUDI backend at `issuer.eudiw.dev`:

```bash
./e2e-public-eudi.sh
```

**Runs in CI**: ✅ Yes (via `.github/actions/gradle-ios/action.yml`)

The native iOS workflow runs the `iosAppTests` integration target in CI. Full UI
E2E coverage for the public backend is mirrored in the Compose iOS demo app and
runs there in CI.

## What Gets Tested

- **Unit tests** (`iosAppTests`): Wallet client integration tests
- **UI tests** (`iosAppUITests`): Full app E2E flows with UI automation
  - `EudiPublicBackendE2ETests`: Receive + present against EUDI backend

## CI Configuration

The CI runs:
- ✅ Unit tests (`iosAppTests`)
- ✅ Public EUDI integration tests through `iosAppTests`
- ❌ Compose iOS EUDI UI tests (excluded - flaky UI automation)

See `.github/actions/gradle-ios/action.yml` for CI configuration.
