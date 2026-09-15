# Getting Started

Create a ``Wallet`` actor, initialize a signing identity, and keep the actor as the
native iOS entry point for wallet operations.

## Overview

### Configure a Wallet

Start with a stable ``WalletConfiguration/walletID``. The identifier is used by
the wallet core for persisted local state, so apps should treat it as an
application-level wallet identity rather than as a screen-local value.

```swift
import WalletSDK

let wallet = try await Wallet(
    configuration: WalletConfiguration(
        walletID: "consumer-wallet",
        defaultKeyUseAuthorizationPolicy: .biometricCurrentSet,
        keyUseAuthorizationPrompt: WalletKeyUseAuthorizationPrompt(
            message: "Authorize wallet signing",
            cancelText: "Cancel"
        )
    )
)
```

``WalletConfiguration`` uses managed encrypted local persistence by default.
Apps that own database-key recovery can pass ``WalletPersistence`` with
``WalletDatabaseKeyConfiguration/provided(_:)`` and a
``WalletDatabaseKeyProvider`` implementation.

Apps can provide ``WalletPersistence/credentialStore`` or ``WalletPersistence/didStore`` when they own credential or DID durability.
Omitted stores use the encrypted local database. Signing keys are always
platform-managed and remain in the iOS Keychain. Credential and DID stores can
be supplied independently. This example assumes app-defined store types that
implement the corresponding protocols.

<!-- doc-snippet:start swift-full-store-overrides -->
```swift
let wallet = try await Wallet(
    configuration: WalletConfiguration(
        walletID: "consumer-wallet",
        persistence: WalletPersistence(
            credentialStore: AppCredentialStore(),
            didStore: AppDidStore()
        )
    )
)
```
<!-- doc-snippet:end swift-full-store-overrides -->

> Important: Keep Kotlin Multiplatform and generated bridge symbols behind
> `WalletSDK`. Native iOS consumers should import this Swift package and work
> with Swift-owned types.

### Bootstrap DID State

Call ``WalletIdentityService/initialize()`` before issuance or presentation
flows that need wallet key material.

```swift
guard case .active(let identity) = try await wallet.identities.initialize() else {
    // Show pending setup or an unavailable identity before continuing.
    return
}
print(identity.did)
```

Use ``Wallet/keyUseAuthorizationPreflight(keyType:policy:)`` to check a
protected request before creating a key. `biometricCurrentSet` is immutable per
key, P-256 only, requires strong biometrics without passcode fallback, and is
supported only on a physical Secure Enclave device. Changing the default does
not change an existing key. The host app must also declare
`NSFaceIDUsageDescription` in `Info.plist` before using this policy; the
simulator cannot validate Secure Enclave or Face ID behavior.

Use `.biometricTimedReuse(timeoutSeconds:)` for a fixed 1–30 second interval
after successful strong-biometric authorization. The interval does not slide on
signing and the policy accepts newly enrolled biometrics. The SDK defers protected-key access while
biometrics are unavailable to avoid query failures during enrollment reset. This policy is not a
recovery guarantee. Check the
preflight result's `reuseEnforcement` and `timeoutValidation`: iOS reports
provider-process enforcement with provider-configuration-only validation.
The Apple adapter retains a per-key LocalAuthentication context for that interval; native
Keychain metadata does not expose the interval for independent readback. Timed reuse is recent
provider authentication, not consent for issuance, presentation, or another wallet action.

Use the returned ``WalletIdentity/did`` when a verifier flow needs an
explicit wallet DID.
