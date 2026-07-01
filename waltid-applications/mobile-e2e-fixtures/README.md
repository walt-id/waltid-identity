# Mobile E2E Fixtures

Shared test fixture code for mobile wallet end-to-end tests.

- `ios/TestHelpers/` contains Swift backend helpers used by iOS XCTest targets.
- `local-enterprise-fixtures.sh` contains shell setup shared by local Enterprise E2E scripts.

Keep these helpers app-neutral. App-specific UI automation belongs in the app test
target that drives the UI.
