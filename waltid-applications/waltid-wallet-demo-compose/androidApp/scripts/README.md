# Compose Android Wallet Demo E2E Tests

Scripts to run end-to-end tests for the Compose Android wallet demo app.

## Prerequisites

- Android emulator/device connected (check with `adb devices`)
- For local enterprise tests: a provisioned Enterprise quickstart stack and ngrok tunnel

## Public EUDI Backend (No Infrastructure Required)

Tests against the public EUDI backend at `issuer.eudiw.dev`:

```bash
./e2e-public-eudi.sh
```

**Runs in CI**: ✅ Yes (via `.github/workflows/android-device-tests.yml`)

## Local Enterprise Backend (Requires Infrastructure)

Tests against a local Enterprise stack. These tests are local-only for now and are not self-contained: the quickstart stack must provision the baseline organization, tenant, issuer2, verifier2, KMS, X509 store/certificates, VICAL, client attester, and mDL issuer profile before the mobile scripts run. The normal test command validates the environment and fails before Gradle if required resources are missing; it does not create resources unless an explicit preparation flag is used.

```bash
# Non-attested
./e2e-local-enterprise.sh

# Attested (with client attestation)
./e2e-local-enterprise.sh --attested
# or
./e2e-local-enterprise-attested.sh

# One-time local helper resource preparation, without running Android tests
./e2e-local-enterprise.sh --prepare-only
```

**Runs in CI**: ❌ No (requires local infrastructure)

Non-attested issuer/profile setup is shared with the iOS runner through
`../../../mobile-e2e-fixtures/local-enterprise-fixtures.sh`.

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
   Android still needs this public URL in the current setup: credential offers and verifier request URIs are created through `HOST_ALIAS_DOMAIN`. When running on an emulator, the script separately uses `10.0.2.2` for direct test-side API calls and adds the matching `Host` header automatically.
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
8. Run the normal test script:
   ```bash
   ./e2e-local-enterprise.sh
   ```

The normal script validates the quickstart-owned resources and the mobile-only helper resources before launching the UI test. It does not create resources; rerun `--prepare-only` when you intentionally want to create or refresh the mobile helper resources.

## What Gets Tested

- **Device tests** (`waltid-openid4vc-wallet-mobile`): Mobile wallet library tests with hardware keys
- **UI tests** (`waltid-wallet-demo-compose:androidApp`): Full app E2E flows with UIAutomator
  - `EudiPublicBackendE2ETest`: Receive + present against EUDI backend
  - `LocalEnterpriseBackendE2ETest`: Receive + present against local enterprise

## CI Configuration

The CI runs:
- ✅ Mobile wallet device tests (`:waltid-openid4vc-wallet-mobile:connectedAndroidDeviceTest`)
- ✅ EUDI UI tests (`EudiPublicBackendE2ETest`)
- ❌ Local enterprise UI tests (excluded - needs infrastructure)

See `.github/workflows/android-device-tests.yml` for CI configuration.
