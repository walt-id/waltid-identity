# Mobile Test Utilities

Shared Kotlin test infrastructure for Android mobile wallet tests, paired with the
Swift `TestHelpers` backend fixtures used by iOS XCTest targets.

## Overview

This module provides:
- **EudiTestBackend** - EUDI public backend integration (offer generation, verifier transactions)
- **LocalEnterpriseTestBackend** - Walt.ID Enterprise backend integration (authentication, offers, verification)
- **Test utilities** - Common mobile testing helpers
- **KMP test infrastructure** - Used directly from Android tests; iOS mirrors the same fixture API through Swift `TestHelpers`

## Module Structure

```
waltid-mobile-test-utils/
├── src/
│   └── commonMain/kotlin/
│       └── backend/                          # Test backend helpers
│           ├── EudiTestBackend.kt            # EUDI public backend integration
│           └── LocalEnterpriseTestBackend.kt # Enterprise backend integration
```

**Note:** Utilities are in `commonMain` (not `commonTest`) to allow cross-module sharing while remaining test-only via module-level dependency configuration.

## Usage

### Android (Kotlin)

Add dependency to your module's `build.gradle.kts`:

```kotlin
// For library tests
sourceSets {
    val androidDeviceTest by getting {
        dependencies {
            implementation(project(":waltid-libraries:protocols:waltid-mobile-test-utils"))
        }
    }
}

// For demo app E2E tests
dependencies {
    androidTestImplementation(project(":waltid-libraries:protocols:waltid-mobile-test-utils"))
}
```

Use in tests:

```kotlin
import id.walt.mobile.test.backend.EudiTestBackend
import id.walt.mobile.test.backend.LocalEnterpriseTestBackend

// EUDI Public Backend
class EudiIntegrationTest {
    @Test
    fun testReceiveCredential() = runBlocking {
        // Generate offer from EUDI backend
        val offer = EudiTestBackend.generateOffer()
        
        // Use with the mobile wallet
        val result = walletClient.receive(offer.offerUrl)
        assertTrue(result.isNotEmpty())
        
        // Create verifier transaction
        val credentialId = EudiTestBackend.extractCredentialIdFromOfferUrl(offer.offerUrl)
        val verifier = EudiTestBackend.createVerifierTransaction(credentialId)
        
        // Present credential
        val presentResult = walletClient.present(verifier.authorizationRequestUri)
        assertTrue(presentResult.success)
        
        // Wait for verifier confirmation
        EudiTestBackend.waitForVerifierSuccess(verifier.transactionId)
    }
}

// Local Enterprise Backend
class EnterpriseIntegrationTest {
    private val httpClient = HttpClient(Android)
    
    @Test
    fun testEnterpriseFlow() = runBlocking {
        val config = LocalEnterpriseTestBackend.BackendConfig(
            apiBaseUrl = "https://your-ngrok-domain.ngrok.io",
            ngrokBaseUrl = "https://your-ngrok-domain.ngrok.io",
            adminEmail = "admin@walt.id",
            adminPassword = "admin123456",
            org = "waltid",
            tenant = "tenant01",
            issuerProfile = "issuer.mdl-profile",
            verifier = "verifier"
        )
        
        // Authenticate
        val token = LocalEnterpriseTestBackend.getAdminToken(config, httpClient)
        
        // Create offer
        val offerUrl = LocalEnterpriseTestBackend.createPreAuthorizedOffer(config, token, httpClient)
        
        // Create verifier session
        val session = LocalEnterpriseTestBackend.createVerifierSession(config, token, httpClient)
        
        // ... use with wallet ...
        
        // Wait for verification
        LocalEnterpriseTestBackend.waitForVerifierSuccess(config, session.sessionId, httpClient)
    }
}
```

### iOS (Swift)

**Note:** iOS XCTest targets do not import this Kotlin module directly. They use
the **TestHelpers** fixtures located in
`waltid-applications/waltid-wallet-demo-ios/iosApp/TestHelpers/`. Keep backend
payloads and method semantics aligned between this module and `TestHelpers`
when adding WAL-1097-style E2E flows.

```swift
import TestHelpers

class EudiIntegrationTests: XCTestCase {
    func testReceiveCredential() async throws {
        let backend = EudiPublicBackend()
        let offerURL = try await backend.generatePreAuthorizedOffer(
            credentialID: "eu.europa.ec.eudi.pid_vc_sd_jwt"
        )
        
        // Use with wallet controller
        let result = try await controller.receive(offerURL)
        XCTAssertTrue(result.success)
    }
}
```

See `TestHelpers/` for the full Swift API, including local Enterprise
authentication, offer creation, verifier sessions, and verifier polling.

## Test Backends

### EUDI Public Backend

- **URL**: https://issuer.eudiw.dev
- **Credentials**: PID, mDL (SD-JWT, mDoc formats)
- **CI Compatible**: Yes (no authentication required)
- **Local Testing**: Yes

**Functions:**
- `generateOffer(credentialId)` - Generate pre-authorized credential offer
- `createVerifierTransaction(credentialId)` - Create verification session
- `waitForVerifierSuccess(transactionId)` - Poll verifier for presentation confirmation

### Local Enterprise Backend

- **Requirements**: Walt.ID Enterprise + ngrok tunnel
- **CI Compatible**: No (infrastructure dependencies)
- **Local Testing**: Yes (with proper setup)

Tests using local enterprise backend should be marked as local-only and excluded from CI.

## Design Principles

1. **Test-only module** - This module should NEVER be added to `commonMain` or production dependencies
2. **iOS/Android parity** - Keep Kotlin `waltid-mobile-test-utils` and Swift `TestHelpers` fixture APIs semantically aligned
3. **No production code** - Only test helpers and backend utilities
4. **CI-compatible** - Tests using public backends should run in CI
5. **Clear separation** - Local-only tests clearly marked and excluded from CI

## Verification

To ensure this module isn't leaking into production:

```bash
# Should return nothing - mobile-test-utils should NOT appear in runtime classpath
./gradlew :waltid-libraries:protocols:waltid-openid4vc-wallet-mobile:dependencies \
  --configuration runtimeClasspath | grep mobile-test-utils

# Should show mobile-test-utils - OK in test classpath
./gradlew :waltid-libraries:protocols:waltid-openid4vc-wallet-mobile:dependencies \
  --configuration testRuntimeClasspath | grep mobile-test-utils
```

## Contributing

When adding new test utilities:

1. Keep test helpers in `commonTest` when possible (shared across platforms)
2. Add platform-specific utilities only when necessary
3. Update this README with usage examples

## Related Modules

- `waltid-openid4vc-wallet-mobile` - Mobile wallet library (uses this for tests)
- `waltid-openid4vc-wallet-persistence-mobile` - Wallet persistence layer
- `waltid-wallet-demo-compose` - Compose demo app
- `waltid-wallet-demo-ios` - iOS demo app
