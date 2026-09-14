# iOS Keychain recovery

Optional integration for the mobile wallet identity lifecycle. The base SDK does not depend on or register this provider.

Register `KeychainIdentityRecovery(namespace)` in `IdentityConfiguration.recoveryProviders`. Signing keys and synchronizable recovery records use separate accessibility settings. Native Swift consumers can opt into the `WalletSDKKeychainRecovery` product instead.

Local OS acceptance does not prove cloud delivery or availability on another device.
See the [identity recovery guide](../waltid-openid4vc-wallet-mobile/docs/identity-recovery.md) for configuration, security boundaries and the replaceable provider contract.
