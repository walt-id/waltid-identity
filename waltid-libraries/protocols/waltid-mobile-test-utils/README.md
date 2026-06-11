# Mobile Test Utilities

Shared test infrastructure for iOS and Android mobile wallet tests.

## Overview

This module provides:
- **EudiTestBackend** - EUDI public backend integration (offer generation, verifier transactions)
- **LocalEnterpriseTestBackend** - Walt.ID Enterprise backend integration (authentication, offers, verification)
- **Test utilities** - Common mobile testing helpers
- **KMP test infrastructure** - Accessible from both Android (Kotlin) and iOS (via Swift TestHelpers)

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
        
        // Use with wallet client
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

**Note:** iOS cannot directly import from Kotlin `commonMain` in KMP for test utilities. iOS uses the **TestHelpers framework** located in `waltid-applications/waltid-wallet-demo-ios/iosApp/TestHelpers/` which provides equivalent Swift implementations.

```swift
import TestHelpers

class EudiIntegrationTests: XCTestCase {
    func testReceiveCredential() async throws {
        // Generate offer via EudiOfferFlow
        let flow = EudiOfferFlow(client: WalletE2EClient())
        let offerURL = try await flow.generate(credentialID: "eu.europa.ec.eudi.pid_vc_sd_jwt")
        
        // Use with wallet controller
        let result = try await controller.receive(offerURL)
        XCTAssertTrue(result.success)
    }
}
```

See `TestHelpers/` framework documentation for full Swift API.

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
2. **iOS/Android parity** - Test utilities work on both platforms (via appropriate bridges)
3. **No production code** - Only test helpers and backend utilities
4. **CI-compatible** - Tests using public backends should run in CI
5. **Clear separation** - Local-only tests clearly marked and excluded from CI

## Verification

To ensure this module isn't leaking into production:

```bash
# Should return nothing - mobile-test-utils should NOT appear in runtime classpath
./gradlew :waltid-libraries:protocols:waltid-openid4vc-wallet-client:dependencies \
  --configuration runtimeClasspath | grep mobile-test-utils

# Should show mobile-test-utils - OK in test classpath
./gradlew :waltid-libraries:protocols:waltid-openid4vc-wallet-client:dependencies \
  --configuration testRuntimeClasspath | grep mobile-test-utils
```

## Contributing

When adding new test utilities:

1. Keep test helpers in `commonTest` when possible (shared across platforms)
2. Add platform-specific utilities only when necessary
3. Update this README with usage examples

## Related Modules

- `waltid-openid4vc-wallet-client` - Mobile wallet client (uses this for tests)
- `waltid-openid4vc-wallet-persistence-client` - Wallet persistence layer
- `waltid-wallet-demo-android` - Android demo app
- `waltid-wallet-demo-ios` - iOS demo app
