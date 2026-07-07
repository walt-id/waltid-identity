# Compose iOS Wallet Demo E2E Tests

Scripts to run end-to-end tests for the Compose iOS wallet demo app.

## Prerequisites

- iOS Simulator running (or specify `IOS_SIMULATOR_ID`)
- Xcode command line tools available on `PATH`

The scripts refresh the Compose iOS SwiftPM linkage package before invoking the
Xcode UI tests. Set `SKIP_IOS_APP_SETUP=true` when the project is already fresh
and you only want to rerun Xcode tests.

## Public EUDI Backend (No Infrastructure Required)

Tests against the public EUDI backend at `issuer.eudiw.dev`:

```bash
./e2e-public-eudi.sh
```

**Runs in CI**: Yes (via `.github/actions/gradle-ios/action.yml`)

## What Gets Tested

- **Public EUDI UI test**: receive + present against EUDI public backend

## CI Configuration

The CI runs:
- Mobile wallet iOS simulator tests
- Native iOS demo app tests
- Compose iOS app build/setup
- Compose iOS public EUDI UI tests are excluded from CI

See `.github/actions/gradle-ios/action.yml` for CI configuration.
