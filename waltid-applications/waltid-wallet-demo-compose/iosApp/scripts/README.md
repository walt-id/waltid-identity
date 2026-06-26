# Compose iOS Wallet Demo E2E Tests

Scripts to run end-to-end tests for the Compose iOS wallet demo app.

## Prerequisites

- iOS Simulator running (or specify `IOS_SIMULATOR_ID`)
- CocoaPods available on `PATH`
- For local enterprise tests: Enterprise stack + ngrok tunnel

The scripts sync the Compose iOS framework and run `pod install` before invoking
the Xcode UI tests. Set `SKIP_IOS_APP_SETUP=true` when the workspace is already
fresh and you only want to rerun Xcode tests.

## Public EUDI Backend (No Infrastructure Required)

Tests against the public EUDI backend at `issuer.eudiw.dev`:

```bash
./e2e-public-eudi.sh
```

**Runs in CI**: Yes (via `.github/actions/gradle-ios/action.yml`)

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

**Runs in CI**: No (requires local infrastructure)

### Setup for Local Tests

1. Start enterprise stack.
2. Create `e2e.env`:
   ```bash
   cp e2e.env.example e2e.env
   # Set HOST_ALIAS_DOMAIN=<your ngrok domain>
   ```
3. Boot an iOS simulator, or set `IOS_SIMULATOR_ID=<uuid>`.
4. Run the script.

## What Gets Tested

- **Public EUDI UI test**: receive + present against EUDI public backend
- **Local Enterprise UI tests**: receive, persist, and present against local enterprise
- **Attested local Enterprise mode**: same local enterprise flow with client attestation enabled

## CI Configuration

The CI runs:
- Mobile wallet iOS simulator tests
- Native iOS demo app tests
- Compose iOS public EUDI UI tests
- Local enterprise UI tests are excluded because they require local infrastructure

See `.github/actions/gradle-ios/action.yml` for CI configuration.
