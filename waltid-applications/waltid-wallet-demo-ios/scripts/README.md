# iOS Wallet Demo E2E Tests

Scripts to run end-to-end tests for the iOS wallet demo app.

## Prerequisites

- iOS Simulator running (or specify `IOS_SIMULATOR_ID`)
- For local enterprise tests: Enterprise stack + ngrok tunnel

## Public EUDI Backend (No Infrastructure Required)

Tests against the public EUDI backend at `issuer.eudiw.dev`:

```bash
./e2e-public-eudi.sh
```

**Runs in CI**: ✅ Yes (via `.github/actions/gradle-ios/action.yml`)

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

1. Start enterprise stack
2. Create `e2e.env`:
   ```bash
   cp e2e.env.example e2e.env
   # Set HOST_ALIAS_DOMAIN=<your ngrok domain>
   ```
3. Run script

## What Gets Tested

- **Unit tests** (`iosAppTests`): Wallet client integration tests
- **UI tests** (`iosAppUITests`): Full app E2E flows with UI automation
  - `EudiPublicBackendE2ETests`: Receive + present against EUDI backend
  - `LocalEnterpriseBackendE2ETests`: Receive + present against local enterprise

## CI Configuration

The CI runs:
- ✅ Unit tests (`iosAppTests`)
- ✅ EUDI UI tests (`EudiPublicBackendE2ETests`)
- ❌ Local enterprise UI tests (excluded - needs infrastructure)

See `.github/actions/gradle-ios/action.yml` for CI configuration.
