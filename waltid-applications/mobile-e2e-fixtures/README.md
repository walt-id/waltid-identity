# Mobile E2E Fixtures

Shared test fixture code for mobile wallet end-to-end tests.

- `ios/TestHelpers/` contains Swift backend helpers used by iOS XCTest targets.
- `ios/IdentityDocumentTests/` contains assertions over the app/document-provider cross-process
  contract. It is kept out of `TestHelpers/` because that directory is built as a plain framework
  that links neither XCTest nor WalletSDK; these files are compiled directly into each demo's test
  target instead.

Keep these helpers app-neutral. App-specific UI automation belongs in the app test
target that drives the UI.
