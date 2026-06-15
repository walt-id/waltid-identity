# Android Wallet Demo E2E Tests

Scripts to run end-to-end tests for the Android wallet demo app.

## Prerequisites

- Android emulator/device connected (check with `adb devices`)
- For local enterprise tests: Enterprise stack + ngrok tunnel + `adb reverse tcp:7500 tcp:7500`

## Public EUDI Backend (No Infrastructure Required)

Tests against the public EUDI backend at `issuer.eudiw.dev`:

```bash
./e2e-public-eudi.sh
```

**Runs in CI**: ✅ Yes (via `.github/workflows/android-device-tests.yml`)

## Local Enterprise Backend (Requires Infrastructure)

Tests against local enterprise stack (requires docker-compose + ngrok):

```bash
# Non-attested
./e2e-local-enterprise.sh

# Attested (with client attestation)
./e2e-local-enterprise.sh --attested
# or
./e2e-local-enterprise-attested.sh
```

**Runs in CI**: ❌ No (requires local infrastructure)

### Setup for Local Tests

1. Start enterprise stack (see `scripts/demo/`)
2. Set up port forwarding:
   ```bash
   adb reverse tcp:7500 tcp:7500
   ```
3. Create `e2e.env`:
   ```bash
   cp e2e.env.example e2e.env
   # Set HOST_ALIAS_DOMAIN=<your ngrok domain>
   ```
4. Run script

## What Gets Tested

- **Device tests** (`waltid-openid4vc-wallet-client`): Wallet client library tests with hardware keys
- **UI tests** (`waltid-wallet-demo-android`): Full app E2E flows with UIAutomator
  - `EudiPublicBackendE2ETest`: Receive + present against EUDI backend
  - `LocalEnterpriseBackendE2ETest`: Receive + present against local enterprise

## CI Configuration

The CI runs:
- ✅ Wallet client device tests (`:waltid-openid4vc-wallet-client:connectedAndroidDeviceTest`)
- ✅ EUDI UI tests (`EudiPublicBackendE2ETest`)
- ❌ Local enterprise UI tests (excluded - needs infrastructure)

See `.github/workflows/android-device-tests.yml` for CI configuration.
