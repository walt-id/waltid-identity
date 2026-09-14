# Signing identity and recovery

Create, reopen and recover the wallet's signing identity through ``Wallet/identities``.
The service preserves the exact key identifier, public key and DID across recovery.
It never replaces a missing key or rebinds existing credentials automatically.

## Overview

### Defaults

`try await wallet.identities.initialize()` reopens the selected identity or creates a
P-256 / `did:jwk` identity without recovery with the configured authorization. Handle
``WalletIdentityOperationResult/pending(identityID:)`` and failure before continuing.
Recovery integrations are absent by default. Alternatives require explicit selection.

### Explicit choices

```swift
let identities = await wallet.identities
switch try await identities.creationOptions(intent: .recoverable) {
case .available(let recommended, let alternatives):
    // Present the public metadata for these SDK-issued choices to the user.
    // Pass the selected, unmodified option to identities.create(_:).
    print(recommended.storage, alternatives.count)
case .unavailable(let reasons):
    print(reasons)
}
```

Executable options have no public initializer. The shared lifecycle checks their owner,
current prerequisites and native result again at execution. A hardware-plus-recovery
option is never offered on iOS: Secure Enclave keys cannot be imported on a replacement
device. Recoverable iOS identities use ordinary Keychain or encrypted database signing.
Ordinary Keychain access controls remain distinct from hardware execution.

### Optional providers

Add the `WalletSDKKeychainRecovery` product and register its provider explicitly:

```swift
import WalletSDK
import WalletSDKKeychainRecovery

let configuration = WalletConfiguration(identity: .init(
    recoveryProviders: [KeychainIdentityRecovery(namespace: "my-wallet")]
))
```

Apps can replace this integration with ``WalletIdentityRecoveryProvider``. Providers
receive secret bytes and must protect them, scope access, and reject conflicting records.
The SDK verifies local readback before accepting submission. OS acknowledgment does not
prove cloud delivery, restoration on another device, or deletion of other device copies.
Deleting wallet data and deleting provider recovery records are separate actions.

### Scope and assurance

``WalletIdentityPolicy`` expresses application constraints, not EUDI, HAIP or eIDAS
certification. Imported keys retain imported origin even when a platform supports
hardware execution. Native key evidence is not an OpenID4VCI key-attestation JWT.
Credential synchronization and reissuance remain separate from signing-key recovery.

See the Kotlin SDK's [identity lifecycle guide](https://github.com/walt-id/waltid-identity/blob/main/waltid-libraries/protocols/waltid-openid4vc-wallet-mobile/docs/identity-recovery.md)
for the platform matrix and the versioned recovery record specification.
