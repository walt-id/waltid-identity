# Mobile E2E Fixtures

Shared test fixture code for mobile wallet end-to-end tests.

- `ios/TestHelpers/` contains Swift backend helpers used by iOS XCTest targets.
- `ios/IdentityDocumentTests/` contains assertions over the app side of the app/document-provider
  contract: that a demo's namespace resolves to its own App Group and Keychain group, and that a
  wallet opened the way the extension opens it finds the host app's state and signing key. Both
  wallets run in the test host process, so extension-process entitlements are covered by
  `.github/scripts/mobile-ci/verify-ios-identity-document-provider.sh` and by physical-device
  acceptance instead. It is kept out of `TestHelpers/` because that directory is built as a plain
  framework that links neither XCTest nor WalletSDK; these files are compiled directly into each
  demo's test target instead.

Keep these helpers app-neutral. App-specific UI automation belongs in the app test
target that drives the UI.
