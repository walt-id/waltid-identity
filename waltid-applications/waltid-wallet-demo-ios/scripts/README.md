# iOS Wallet Demo E2E Tests

Scripts to run end-to-end tests for the iOS wallet demo app.

## Prerequisites

- iOS Simulator running (or specify `IOS_SIMULATOR_ID`)
- For local enterprise tests: a provisioned Enterprise quickstart stack + ngrok tunnel

## Public EUDI Backend (No Infrastructure Required)

Tests against the public EUDI backend at `issuer.eudiw.dev`:

```bash
./e2e-public-eudi.sh
```

**Runs in CI**: ✅ Yes (via `.github/actions/gradle-ios/action.yml`)

The native iOS workflow runs the `iosAppTests` integration target in CI. Full UI
E2E coverage for the public backend is mirrored in the Compose iOS demo app and
runs there in CI.

## Local Enterprise Backend (Requires Infrastructure)

Tests against a local Enterprise stack. These tests are local-only for now and are not self-contained: the quickstart stack must provision the baseline organization, tenant, issuer2, verifier2, KMS, X509 store/certificates, VICAL, client attester, and mDL issuer profile before the mobile scripts run. The normal test command validates the environment and fails before Xcode if required resources are missing; it does not create resources unless an explicit preparation flag is used.

```bash
# Non-attested
./e2e-local-enterprise.sh

# Attested (with client attestation)
./e2e-local-enterprise.sh --attested
# or
./e2e-local-enterprise-attested.sh

# One-time local helper resource preparation, without running iOS tests
./e2e-local-enterprise.sh --prepare-only
```

**Runs in CI**: ❌ No (requires local infrastructure)

Non-attested issuer/profile setup is shared with the Compose app runners through
`../../mobile-e2e-fixtures/local-enterprise-fixtures.sh`.

### Setup for Local Tests

1. Start from a clean `waltid-enterprise-quickstart` checkout.
2. Configure the quickstart service URLs for public mobile redirects in `config/enterprise.conf`:
   ```hocon
   baseDomain = "enterprise.localhost"
   baseSsl = true
   # basePort = 7500
   ```
   `basePort` must stay omitted for the ngrok-backed run. If it is left as `7500`, the issuer embeds `:7500` into credential-offer URLs and the mobile app cannot fetch them through ngrok.
3. Start Docker Desktop or another Docker daemon, then bring up the Enterprise stack:
   ```bash
   docker compose up
   ```
4. Start an ngrok tunnel to the local stack and note the domain:
   ```bash
   ngrok http 7500
   ```
5. Provision the baseline resources and host alias without running the quickstart's built-in primary use case:
   ```bash
   cd cli
   npm install
   HOST_ALIAS_DOMAIN=<your-ngrok-domain> npx tsx walt.ts --init-system
   HOST_ALIAS_DOMAIN=<your-ngrok-domain> npx tsx walt.ts --setup-all
   ```
   For an existing database, `HOST_ALIAS_DOMAIN=<your-ngrok-domain> npx tsx walt.ts --setup-all` is enough if the system resources already exist with the right public URL settings.
6. Create `e2e.env` in this `scripts` directory:
   ```bash
   cp e2e.env.example e2e.env
   # Set HOST_ALIAS_DOMAIN=<your ngrok domain, without https://>
   ```
7. Create the mobile-only helper resources once:
   ```bash
   ./e2e-local-enterprise.sh --prepare-only
   ```
   This explicit preparation creates `issuer2-noattest.mdl-profile` for non-attested issuance and `verifier2-mobile` for public verifier URLs. The quickstart-created `verifier2` keeps a local base URL and is not suitable for mobile cross-device presentation URLs.
8. Boot an iOS simulator, or set `IOS_SIMULATOR_ID=<uuid>`.
9. Run the normal test script:
   ```bash
   ./e2e-local-enterprise.sh
   ```

The normal script validates the quickstart-owned resources and the mobile-only helper resources before launching the UI test, then runs only the local Enterprise UI tests. It does not create resources; rerun `--prepare-only` when you intentionally want to create or refresh the mobile helper resources.

## What Gets Tested

- **Unit tests** (`iosAppTests`): Wallet client integration tests
- **UI tests** (`iosAppUITests`): Full app E2E flows with UI automation
  - `EudiPublicBackendE2ETests`: Receive + present against EUDI backend
  - `LocalEnterpriseBackendE2ETests`: Receive + present against local enterprise

## CI Configuration

The CI runs:
- ✅ Unit tests (`iosAppTests`)
- ✅ Public EUDI integration tests through `iosAppTests`
- ❌ Compose iOS EUDI UI tests (excluded - flaky UI automation)
- ❌ Local enterprise UI tests (excluded - needs infrastructure)

See `.github/actions/gradle-ios/action.yml` for CI configuration.
