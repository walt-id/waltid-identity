# Mobile Test Utilities

Shared test infrastructure for iOS and Android mobile wallet tests.

## Overview

This module provides:
- **Shared test backend helpers** - EUDI test backend integration for credential offer generation
- **Test utilities** - Common mobile testing helpers
- **KMP test infrastructure** - Accessible from both Android (Kotlin) and iOS (via KMP bridge)

## Module Structure

```
waltid-mobile-test-utils/
├── src/
│   ├── commonTest/kotlin/
│   │   ├── backend/                     # Test backend helpers
│   │   │   └── EudiTestBackend.kt      # EUDI backend integration
│   │   └── helpers/                     # Test flow helpers
│   ├── androidDeviceTest/              # Android-specific test utilities
│   └── iosMain/                        # iOS bridge exports
```

## Usage

### Android (Kotlin)

Add dependency to your module's `build.gradle.kts`:

```kotlin
sourceSets {
    commonTest.dependencies {
        implementation(project(":waltid-libraries:protocols:waltid-mobile-test-utils"))
    }
    
    val androidDeviceTest by getting {
        dependencies {
            implementation(project(":waltid-libraries:protocols:waltid-mobile-test-utils"))
        }
    }
}
```

Use in tests:

```kotlin
import id.walt.mobile.test.backend.EudiTestBackend

class MyWalletTest {
    @Test
    fun testReceiveCredential() = runBlocking {
        // Generate offer from EUDI backend
        val offer = EudiTestBackend.generateOffer()
        
        // Use offer.offerUrl with your wallet client
        val result = walletClient.receiveCredential(offer.offerUrl)
        assertTrue(result.success)
    }
}
```

### iOS (Swift)

**Note:** iOS cannot directly import from `commonTest` in KMP. iOS tests should keep the existing Swift `EudiTestBackend` implementation in the app bundle. The Kotlin version in this module is used by Android tests only.

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
