# ``iosApp``

Use the walt.id mobile wallet SDK from a native iOS application.

## Overview

The iOS wallet demo shows the recommended Swift integration shape for the Kotlin
Multiplatform wallet SDK:

1. Keep platform UI and view-model state in Swift.
2. Use a small shared bridge to hide Kotlin coroutine and optional-type details.
3. Create the SDK wallet through `WalletDemoBridgeController`.
4. Run wallet operations from Swift concurrency tasks.

The bridge delegates to `MobileWalletFactory`, which wires the SDK to iOS
Keychain/Secure Enclave storage and a native SQLDelight database.

## Topics

### Wallet SDK Integration

- <doc:MobileWalletSDKIntegration>
