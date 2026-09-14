# Signing identities and recovery

The mobile SDK owns P-256 identity creation, persistence and recovery. New identities use `did:jwk`.
An existing `did:key` or `did:jwk` is retained exactly after matching its public key. The SDK never
repairs missing material by generating a different key under the old identity.

**iOS cannot restore the original signing key into another device's Secure Enclave.** Choose
Secure Enclave generation with no secret backup, or recoverable ordinary-Keychain/software signing.
On Android, a recovered key can be imported into hardware-backed Keystore when the device supports
it. Its origin is still **imported**: the recovery secret existed outside that hardware.

## API

Use `wallet.identities` for the whole lifecycle. `initialize()` reopens the selected identity or
creates the recommended identity without recovery using configured defaults. Pending, ambiguous or
unavailable identities are returned explicitly; they never trigger replacement generation.

```kotlin
when (val result = wallet.identities.initialize()) {
    is IdentityOperationResult.Active -> openWallet(result.identity)
    is IdentityOperationResult.Pending -> showPendingSetup(result.identityId)
    is IdentityOperationResult.Failed -> showFailure(result.reason)
}
```

Apps offering choices request complete options and pass back the selected object:

```kotlin
when (val options = wallet.identities.creationOptions(IdentityIntent.Recoverable)) {
    is IdentityOptions.Available -> showChoices(listOf(options.recommended) + options.alternatives)
    is IdentityOptions.Unavailable -> showUnavailable(options.reasons)
}
// After the user selects one of those objects:
val result = wallet.identities.create(selectedOption)
```

Options have no public constructor, copy method or serializer. They belong to the wallet instance
that issued them. The SDK rechecks native support, authorization and provider availability before
execution. Refresh options after a configuration/device change. A failed option never permits a
silent change in authorization, hardware requirements or recovery intent.

| Operation | Purpose |
| --- | --- |
| `state()` | Read absent, active, pending or unavailable identity state. |
| `initialize()` | Convenient device-bound initialization through the same lifecycle. |
| `creationOptions()` / `create()` | Select and execute a supported new identity configuration. |
| `resumePending()` / `cancelPending()` | Retry or cancel pending local setup. Already submitted provider records are deleted only explicitly. |
| `backupOptions()` / `backup()` | Back up a retained recovery secret or an existing exportable software key. |
| `recoveryCandidates()` / `restorationOptions()` / `restore()` | Discover, validate and recover the original identity. |
| `deleteRecovery()` | Request deletion of one selected provider record. |

`WalletIdentity` contains only public information. Storage destination, origin, observed security
level, authorization, native attestation and recovery status are separate facts. Unknown security
levels are never treated as proof of a particular hardware tier.

The unreleased mobile SDK's old `bootstrap` API is removed. It could select independent first
key/DID rows and create keys outside this lifecycle. Use `identities.initialize()` for convenience,
or explicit options for onboarding. Other algorithms remain available in the crypto SDK; this
identity lifecycle creates P-256 keys.

## Configuration and defaults

```kotlin
val configuration = MobileWalletConfig(
    identity = IdentityConfiguration(
        policy = IdentityKeyPolicy.GeneralPurpose,
        recoveryProviders = listOf(myRecoveryProvider),
    ),
)
```

By default there is no recovery provider and no backup. Authorization inherits the wallet's default
current-biometric-set policy. `IdentityAuthorization.Explicit(...)` overrides that policy for new
identities. `alternativeAuthorizations` is an explicit allowlist for user choices, not an automatic fallback order.
`initialize()` never selects one of these alternatives in place of the primary authorization.
Software signing is offered only for an explicitly permitted `None` authorization; database
protection and app authentication remain separate from native signing authorization.

`GeneralPurpose` permits recovery. `DeviceBound` prohibits secret backup/export through the identity
service. `HardwareGenerated` additionally requires observed hardware and generated origin. These are
host constraints, not certification labels. Stored constraints also apply when backup is enabled
later or a missing key is repaired. An active key's immutable native policy is not changed in place.

`SignumPlatformPolicy.AndroidKeystore` and `.IosKeychain` configure the native destination. They do
not turn an explicitly chosen encrypted-database option into a native key. Hosts requiring hardware
must use `HardwareGenerated` or select an offered hardware option; a software option is always labeled.

`localRecoveryMaterial` defaults to retaining the additional recovery record in the encrypted local
journal. `DiscardAfterSubmission` removes that additional record after provider acceptance. It does
not erase an operational software private key and does not make an imported key hardware-generated.
Discarding the last local recovery record of a non-exportable native key prevents later backup to
another provider unless a recovery record can still be retrieved elsewhere.

## Platform capabilities

Availability is evaluated at runtime. “Supported” below describes implemented API behavior, not a
claim that every vendor, OS release or hardware combination has been physically verified.

| Configuration | Android | iOS |
| --- | --- | --- |
| Hardware-generated P-256 | Keystore TEE/StrongBox, observed after creation | Secure Enclave |
| Recoverable key in hardware | Native import where supported; origin remains imported | **Unavailable** |
| Recoverable native key | Keystore import with observed protection | Ordinary Keychain import, software signing |
| Encrypted-database P-256 | Supported; private/public consistency checked on reopen | Supported |
| Current enrollment / any biometric | Native authorization; enrollment invalidation is a separate policy | Keychain access control |
| Device credential / biometric-or-credential | API 30+ native authorization and AndroidX interaction | Device passcode / Keychain user presence |
| Timed authorization | Keystore timeout, independently read back | Process-local LAContext reuse, fixed non-sliding interval; no independent timeout readback |
| StrongBox required/preferred/discouraged | Configurable; required backing must be observed | Inapplicable |
| Unlocked device, validity dates, usage limit | Configurable within native API support; activation consumes one signature | Accessibility controls instead of Android flags |
| Keychain accessibility / access group | Inapplicable | Configurable; local signing item and recovery item are independent |
| Native generation attestation | Fresh challenge input; evidence returned for external verification | Unavailable for arbitrary signing keys in this backend |
| Recovery adapter | Optional Block Store artifact | Optional synchronizable Keychain artifact / Swift product |

Android usage dates and available presence/confirmation/usage-count metadata are read back.
Unlocked-device enforcement is passed to the OS; older public `KeyInfo` APIs cannot independently
read it back. Usage counts are best-effort native observations, not synchronized counters owned by
the SDK. A one-use identity key is unavailable because activation must first prove possession.
Future-dated/expired keys are also unavailable for immediate identity activation.

Protected Confirmation signs its own confirmation structure, so it is not offered for arbitrary
JOSE/COSE identity signatures. Trusted physical-presence controls without a supported interaction
path are likewise unavailable. Application-password and composite-AND Keychain ACL workflows are
not exposed by this identity service; it does not substitute the wallet PIN for a native credential.
Android on-body authentication is not offered as a stronger authorization guarantee. These limits
are explicit; arbitrary native flags are not accepted and then ignored.

## Optional providers

The base mobile SDK registers no recovery integration. Add the artifact and register a provider:

- Android: `waltid-openid4vc-wallet-recovery-blockstore`, `BlockStoreIdentityRecovery(context, namespace)`.
- Kotlin/iOS: `waltid-openid4vc-wallet-recovery-keychain`, `KeychainIdentityRecovery(namespace, accessGroup)`.
- Swift: the `WalletSDKKeychainRecovery` product, `KeychainIdentityRecovery(namespace:accessGroup:)`.

Block Store defaults to cloud mode requiring its end-to-end-encryption availability check.
`DeviceTransfer` is a separate mode with cloud backup disabled. Records are bounded to 4096 bytes;
Block Store permits 16 records per app. Stable namespace, app/signing identity, OS backup settings
and account prerequisites matter. A provider failure leaves creation pending rather than silently
activating a device-bound identity.

Synchronizable Keychain recovery exposes only compatible `whenUnlocked` / `afterFirstUnlock`
accessibility classes. Device-only classes cannot be selected for synchronized records. Keychain
entitlements and shared access groups are host responsibilities. A local query cannot establish
whether iCloud propagation completed.

`AcceptedLocally` is not “cloud backup verified.” `ConfirmedByProvider` records a provider assertion,
not independent certification. `Recovered` means this installation actually reconstructed and proved
the original key. `RemovalRequested` never proves that all cloud, offline or previously restored
copies disappeared. Deleting a wallet locally does not silently delete its remote recovery record.

A custom `IdentityRecoveryProvider` is trusted code that receives secret record bytes. It must:

- Protect confidentiality and integrity in transit and at rest and scope access to the intended user/app.
- Keep the same record ID idempotent; reject an attempt to overwrite it with different bytes.
- Bound input size, avoid logging records, and preserve cancellation and failure semantics.
- Report actual protection and delivery evidence rather than infer it from a successful local write.
- Make any envelope encryption key independently recoverable; a key trapped on the lost device cannot decrypt recovery data.

The format is specified in [identity-recovery-format.md](identity-recovery-format.md). An adapter
may wrap it in a reviewed authenticated encryption envelope. The SDK does not ship a password-based
cryptosystem or claim that an arbitrary byte store supplies these protections.

## Persistence and credential binding

The encrypted database journals preparation and backup submission before publishing the active
identity association. Native aliases are fresh per installation/attempt; the logical key ID, exact
DID and public key remain unchanged during recovery. Deleting one installation cannot delete the
other installation's native alias. Cleanup only targets the operation's owned key material.

This unreleased SDK has no compatibility migration or legacy key/DID adoption. Existing development
installations require a fresh wallet database. The SDK never clears an old database automatically.
New-format identities retain their exact key/DID association across restart and recovery.

Credential synchronization and reissuance are separate. Restoring an identity does not change a
credential's signed binding or authorize migration forbidden by its issuer. Use separate wallet
identities/explicit key selection where issuer or privacy policy requires separation.

## Standards and assurance boundaries

- Preserve exact `did:jwk` serialization; compare public keys without substituting a thumbprint for
  the DID. The [did:jwk specification](https://github.com/quartzjer/did-jwk/blob/main/spec.md) is a DID
  method specification, not a W3C Recommendation.
- P-256/JWK and ES256 use [RFC 7518 §§3.4, 6.2](https://www.rfc-editor.org/rfc/rfc7518.html).
  The private/public pair and possession of the original key are checked before activation.
- The recovery derivation uses [RFC 5869](https://www.rfc-editor.org/rfc/rfc5869.html) as a primitive;
  the record/derivation scheme is private and versioned. This is not a FIPS validation or an
  EUDI-standard backup format. A focused cryptographic review is required before release.
- [OpenID4VCI 1.0 §12.2.4 and Appendix D](https://openid.net/specs/openid-4-verifiable-credential-issuance-1_0.html)
  distinguish issuer-required key-attestation JWTs from native evidence. The existing JWT-proof path
  now rejects `key_attestations_required` instead of silently omitting it. A separate attester and
  protocol integration are required before such issuance is supported.
- EUDI/HAIP suitability cannot be inferred from hardware presence. Generated origin, migration/export
  restrictions, user authorization, attestation verification and ecosystem policy remain distinct.
  See [ARF 3.0.0, WUA and migration requirements](https://github.com/eu-digital-identity-wallet/eudi-doc-architecture-and-reference-framework/blob/6373eee10b6e80225c7ce706a5ff1775fb799b22/docs/annexes/annex-2/annex-2.02-high-level-requirements-by-topic.md).
  Restricted profiles should use hardware-generated, nonrecoverable keys and issuer reissuance.

Native behavior follows [Apple's Enclave lifecycle](https://developer.apple.com/documentation/security/protecting-keys-with-the-secure-enclave),
[Android Keystore](https://developer.android.com/privacy-and-security/keystore),
[Android KeyInfo](https://developer.android.com/reference/android/security/keystore/KeyInfo),
[Block Store](https://developer.android.com/identity/block-store) and
[Keychain synchronization](https://developer.apple.com/documentation/security/ksecattrsynchronizable).

The design follows the separation of common operations and native settings used by
[Multipaz](https://github.com/openwallet-foundation/multipaz/blob/44184f39a4cccbd9644f50253387703c23194772/multipaz/src/commonMain/kotlin/org/multipaz/securearea/SecureArea.kt),
[EUDI Android](https://github.com/eu-digital-identity-wallet/eudi-lib-android-wallet-core/blob/6533dd10ae838df35037c02f1fde0679647e5839/CustomizeSecureArea.md) and
[EUDI iOS](https://github.com/eu-digital-identity-wallet/eudi-lib-ios-wallet-kit/blob/d45cc689ca5734dc39c161c17ef3e52ed9d46345/Sources/EudiWalletKit/EudiWalletKit.docc/SecureAreas.md).
These source comparisons do not establish tested interoperability or certification. Native operations
extend the existing A-SIT Signum integration (Indispensable 3.24.0, Supreme 0.15.0).

Enterprise KMS import transfers custody. The inspected `import/jwk` and permission-gated `view`
endpoints return the stored key projection: **local JWK-backed keys can include private material**.
Remote KMS handles do not thereby become exportable. This is a key-custody API, not the versioned
opaque-record store/retrieve contract used here; it does not preserve recovery identity metadata by
itself. No Enterprise recovery adapter is registered. A host integration would need to define the
protected record schema, permissions, metadata retention and retrieval behavior explicitly. Remote
signing and credential synchronization retain their existing ownership.

## Evidence limits

Physical-device coverage and local provider round trips must be distinguished from OS device-loss
recovery. StrongBox-specific devices, the customer's Redmi and actual cross-device cloud restore
remain separate qualification cases. Do not claim those paths verified from a TEE import, a simulator
run or a synchronizable local put/get. No formal EUDI, HAIP, eIDAS or FIPS qualification is claimed.
