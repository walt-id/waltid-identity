# Signing identity and recovery

Create, reopen and recover the wallet's signing identity through ``Wallet/signingIdentity``.
The service preserves the exact key identifier, public key and DID across recovery.
It never replaces a missing key or rebinds existing credentials automatically.

## Overview

### Defaults

`try await wallet.signingIdentity.initialize()` reopens the selected identity or creates a
P-256 / `did:jwk` identity without recovery with the configured authorization. Handle
``SigningIdentityOperationResult/pending(identityID:reason:)`` and failure before continuing.
Recovery integrations are absent by default. Alternatives require explicit selection.

### Explicit choices

```swift
let identities = await wallet.signingIdentity
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

let configuration = WalletConfiguration(signingIdentity: .init(
    recoveryProviders: [KeychainIdentityRecovery(namespace: "my-wallet")]
))
```

Apps can replace this integration with ``IdentityRecoveryProvider``. Providers
receive secret bytes and must protect them, scope access, and reject conflicting records.
The SDK verifies local readback before accepting submission. OS acknowledgment does not
prove cloud delivery, restoration on another device, or deletion of other device copies.
Deleting wallet data and deleting provider recovery records are separate actions.

### Optional Enterprise custody

Add the `WalletSDKEnterpriseCustody` product and register its `EnterpriseIdentityKeyCustodian`
in ``SigningIdentityConfiguration/keyCustodians``. Hosts supply the HTTPS KMS resource and an
authorizer for a body-free request. Select an option from ``SigningIdentityManager/custodyOptions(identityID:)``
and pass it to ``SigningIdentityManager/copyToCustody(_:)`` to import the original signing key.
The SDK verifies the destination public key and records a public reference. The local key remains
available and recovery status stays unchanged. This integration does not store an identity recovery
record or configure remote signing. The ``SigningIdentityKeyPolicy/backupAndCustodyDisabled`` and
``SigningIdentityKeyPolicy/hardwareGenerated`` policies prohibit custody through the manager.
Disabling backup and custody does not establish physical non-exportability.

### Authorization evidence

``SigningIdentity/authorizationEvidence`` distinguishes native attribute inspection from an SDK
creation record bound to the native entry. iOS access-control flags and ACL-protected accessibility
cannot be independently read back through the public Security API. The SDK rejects unowned keys,
policy changes and replaced native entries; creation records are not attestation.

Supported generated Secure Enclave configurations use published stable Signum. Import/export,
explicit access groups, passcode-set-only accessibility and per-key timed reuse use the Apple
Keychain adapter. The choice stays internal and is fixed for each existing key. The same public
configuration and failure types apply to both implementations.

### Scope and assurance

``SigningIdentityKeyPolicy`` expresses application constraints, not EUDI, HAIP or eIDAS
certification. ``IssuanceRequest/keyPolicy`` requires the selected identity to already retain
the requested restriction before issuance starts; apps interpret issuer/profile requirements. Imported keys retain imported origin even when a platform supports
hardware execution. Native key evidence is not an OpenID4VCI key-attestation JWT.
Credential synchronization and reissuance remain separate from signing-key recovery.

See the Kotlin SDK's [identity lifecycle guide](https://github.com/walt-id/waltid-identity/blob/main/waltid-libraries/protocols/waltid-openid4vc-wallet-mobile/docs/identity-recovery.md)
for the platform matrix and the versioned recovery record specification.

Missing device credentials produce `deviceCredentialNotSet`, separately from missing biometric
enrollment or cancelled approval. A key configured with passcode-set-only accessibility also has a
passcode-bound ownership record: removing the passcode prevents reopening and further use, even
if native token metadata remains. Enabling a passcode again does not restore the key. Recoverable
keys require explicit recovery from their independent backup; Secure Enclave keys cannot be restored.
