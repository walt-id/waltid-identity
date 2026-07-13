# Mobile Test Utilities

Shared Kotlin test infrastructure for Android mobile wallet tests, paired with
the Swift `TestHelpers` backend fixtures used by iOS XCTest targets.

## Helpers

- `EudiTestBackend` targets the public EUDI test backend.
- `DemoTestBackend` targets the public walt.id issuer2/verifier2 demo endpoints.
- `EnterpriseMobileFixtureClient` targets the self-contained Enterprise fixture
  server started by the Enterprise integration-test tasks.

Utilities live in `commonMain` so Android device tests in other modules can
reuse them while still depending on this module only from test source sets.

## Android Usage

```kotlin
sourceSets {
    val androidDeviceTest by getting {
        dependencies {
            implementation(project(":waltid-libraries:protocols:waltid-mobile-test-utils"))
        }
    }
}
```

The Enterprise fixture client intentionally exposes only mobile-facing test
operations: list scenarios, create an offer, create a verifier session, and poll
verification status. Enterprise tenant/service provisioning stays inside the
Enterprise integration-test module.

## iOS Parity

iOS XCTest targets use the Swift helpers in
`waltid-applications/mobile-e2e-fixtures/ios/TestHelpers/`. Keep Swift and
Kotlin fixture payloads semantically aligned when adding mobile integration
coverage.

## Design Rules

1. Keep this module test-only; never add it to production source sets.
2. Keep backend helpers small and declarative.
3. Do not put Enterprise admin login, tunnel setup, or service provisioning in
   mobile tests.
4. Prefer public unauthenticated demo endpoints or self-contained fixture
   endpoints over local manual setup.
