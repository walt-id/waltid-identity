# Compose Android Wallet Demo E2E Tests

Scripts to run end-to-end tests for the Compose Android wallet demo app.

## Prerequisites

- Android emulator/device connected (check with `adb devices`)

## Public EUDI Backend (No Infrastructure Required)

Tests against the public EUDI backend at `issuer.eudiw.dev`:

```bash
./e2e-public-eudi.sh
```

**Runs in CI**: ✅ Yes (via `.github/workflows/android-device-tests.yml`)

## What Gets Tested

- **Device tests** (`waltid-openid4vc-wallet-mobile`): Mobile wallet library tests with hardware keys
- **UI tests** (`waltid-wallet-demo-compose:androidApp`): Full app E2E flows with UIAutomator
  - `EudiPublicBackendE2ETest`: Receive + present against EUDI backend

## CI Configuration

The CI runs:
- ✅ Mobile wallet device tests (`:waltid-openid4vc-wallet-mobile:connectedAndroidDeviceTest`)
- ✅ EUDI UI tests (`EudiPublicBackendE2ETest`)

See `.github/workflows/android-device-tests.yml` for CI configuration.
