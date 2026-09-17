# Signing identities and recovery

A signing identity binds a signing key to its DID and records how that key is protected and recovered.

The mobile SDK owns P-256 signing identity creation, persistence and recovery. New identities use `did:jwk`.
An existing `did:key` or `did:jwk` is retained exactly after matching its public key. The SDK never
repairs missing material by generating a different key under the old identity.

**iOS cannot restore the original signing key into another device's Secure Enclave.** Choose
Secure Enclave generation with no secret backup, or recoverable ordinary-Keychain/software signing.
On Android, a recovered key can be imported into hardware-backed Keystore when the device supports
it. Its origin is still **imported**: the recovery secret existed outside that hardware.

## API

Use `wallet.signingIdentity` for the whole lifecycle. `initialize()` reopens the selected identity or
creates the recommended identity without recovery using configured defaults. Pending, ambiguous or
unavailable identities are returned explicitly; they never trigger replacement generation.

```kotlin
when (val result = wallet.signingIdentity.initialize()) {
    is SigningIdentityOperationResult.Active -> openWallet(result.identity)
    is SigningIdentityOperationResult.Pending -> showPendingSetup(result.identityId)
    is SigningIdentityOperationResult.Failed -> showFailure(result.reason)
}
```

Apps offering choices request complete options and pass back the selected object:

```kotlin
when (val options = wallet.signingIdentity.creationOptions(SigningIdentityIntent.Recoverable)) {
    is SigningIdentityCreationOptions.Available -> showChoices(listOf(options.recommended) + options.alternatives)
    is SigningIdentityCreationOptions.Unavailable -> showUnavailable(options.reasons)
}
// After the user selects one of those objects:
val result = wallet.signingIdentity.create(selectedOption)
```

Options have no public constructor, copy method or serializer. They belong to the wallet instance
that issued them. The SDK rechecks native support, authorization and provider availability before
execution. Refresh options after a configuration/device change. A failed option never permits a
silent change in authorization, hardware requirements or recovery intent.

| Operation | Purpose |
| --- | --- |
| `state()` | Read absent, active, pending or unavailable identity state. |
| `initialize()` | Initialize without recovery using the configured key policy. |
| `recoveryProviderStatuses()` | Read each configured provider's availability and unmet prerequisites; does not confirm delivery. |
| `creationOptions()` / `create()` | Select and execute a supported new identity configuration. |
| `resumePending()` / `cancelPending()` | Retry or cancel pending local setup. Already submitted provider records are deleted only explicitly. |
| `backupOptions()` / `backup()` | Back up a retained recovery secret or an existing exportable software key. |
| `discoverRecovery()` / `restorationOptions()` / `restore()` | Discover candidates and per-provider failures in one snapshot, validate each candidate independently, and recover the original identity. |
| `deleteRecovery()` | Request deletion of one selected provider record. |
| `custodyOptions()` / `copyToCustody()` | Copy an exportable key to an explicitly selected custodian. |

`SigningIdentity` contains only public information. Storage destination, origin, observed security
level, authorization, native attestation and recovery status are separate facts. Unknown security
levels are never treated as proof of a particular hardware tier.

The unreleased mobile SDK's old `bootstrap` API is removed. It could select independent first
key/DID rows and create keys outside this lifecycle. Use `signingIdentity.initialize()` for convenience,
or explicit options for onboarding. Other algorithms remain available in the crypto SDK; this
identity lifecycle creates P-256 keys.

## Configuration and defaults

```kotlin
val configuration = MobileWalletConfig(
    signingIdentity = SigningIdentityConfiguration(
        policy = SigningIdentityKeyPolicy.GeneralPurpose,
        recoveryProviders = listOf(myRecoveryProvider),
    ),
)
```

By default there is no recovery provider and no backup. Authorization inherits the wallet's default
current-biometric-set policy. `SigningIdentityAuthorization.Explicit(...)` overrides that policy for new
identities. `alternativeAuthorizations` is an explicit allowlist for user choices, not an automatic fallback order.
`initialize()` never selects one of these alternatives in place of the primary authorization.
Software signing is offered only for an explicitly permitted `None` authorization; database
protection and app authentication remain separate from native signing authorization.

`GeneralPurpose` permits recovery. `BackupAndCustodyDisabled` prohibits secret backup and custody through the manager. It does not make an otherwise exportable key physically non-exportable. `HardwareGenerated` additionally requires observed hardware and generated origin. These are
host constraints, not certification labels. Stored constraints also apply when backup is enabled
later or a missing key is repaired. An active key's immutable native policy is not changed in place.
Portable records retain minimum storage and authorization requirements; a restore option must satisfy
both those minimums and current host configuration. Device-specific aliases, access groups and old
attestations are never replayed. This is not automatic interpretation of arbitrary ecosystem policy.

`PlatformKeyConfiguration.AndroidKeystore` and `.IosKeychain` configure the native destination. They do
not turn an explicitly chosen encrypted-database option into a native key. Hosts requiring hardware
must use `HardwareGenerated` or select an offered hardware option; a software option is always labeled.

`localRecoveryMaterial` defaults to retaining the additional recovery record in the encrypted local
journal. `DiscardAfterConfirmation` removes that additional record after the required confirmation. It does
not erase an operational software private key and does not make an imported key hardware-generated.
Discarding the last local recovery record of a non-exportable native key prevents later backup to
another provider unless a recovery record can still be retrieved elsewhere. The service retrieves
the record from its recorded source provider, validates it, and verifies the copy at the selected
destination. Removing the source copy remains a separate explicit operation. Ordinary-Keychain
keys also expose an explicit native private-key export capability; Enclave and Keystore handles do not.

`recoveryConfirmation` defaults to `LocalAcceptance`, which requires exact provider readback without
claiming remote delivery. Set `ProviderConfirmation` to require the provider's delivery assertion
before activation or disposal of the additional local record. A local-only receipt leaves creation
pending. The retained requirement cannot be weakened by reopening with a less strict default.
Options expose `recoveryAvailability` so hosts can display protection and delivery scope before selection.

## Platform capabilities

Availability is evaluated at runtime. Android hardware choices require a temporary native-key probe
and inspection of actual backing; the probe is removed and never becomes a wallet identity. “Supported” below describes implemented API behavior, not a
claim that every vendor, OS release or hardware combination has been physically verified.

| Configuration | Android | iOS |
| --- | --- | --- |
| Hardware-generated P-256 | Keystore TEE/StrongBox, observed after creation | Secure Enclave |
| Recoverable key in hardware | Native import where supported; origin remains imported | **Unavailable** |
| Recoverable native key | Keystore import with observed protection | Ordinary Keychain import, software signing |
| Encrypted-database P-256 | Supported; private/public consistency checked on reopen | Supported |
| Current enrollment / any biometric | Native authorization; enrollment invalidation is a separate policy | Keychain access control |
| Device credential / biometric-or-credential | API 30+ native authorization and AndroidX interaction | Device passcode / Keychain user presence |
| Timed authorization | Keystore timeout, independently read back | Process-local LAContext reuse across handle reloads, fixed non-sliding interval; no independent timeout readback |
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
On affected iOS versions, querying an ordinary biometric-only Keychain entry while Face ID is
unenrolled can leave it unavailable after re-enrollment, even with `biometryAny`. Apple DTS has
[identified a related reset symptom as a possible OS bug](https://developer.apple.com/forums/thread/774790).
The SDK checks biometric availability before protected-key lookup and use, returning temporary
unavailability without querying the entry when biometrics are unavailable. Passcode-capable policies
remain usable. This guard does not weaken native authorization or restore entries already lost;
retain an independent recovery record when key continuity is required. Direct native access outside
the SDK must also avoid this trigger. Temporary failures do not imply permanent invalidation, and
reopening never silently replaces the signing key.

Missing device credentials are reported as `DeviceCredentialNotSet`, separately from missing
biometric enrollment and cancelled authorization. After a native authorization failure, the SDK
checks current availability to distinguish these conditions; successful native timed reuse is unchanged.
On iOS, a key configured with `WHEN_PASSCODE_SET_DEVICE_ONLY` also has a passcode-bound ownership
record. Removing the passcode removes the record required to reopen or use the key, even if native
token metadata remains. Re-enabling the passcode does not restore that record. Recovery is explicit
and requires a retained backup; Secure Enclave keys cannot be recovered.

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

Providers can throw `IdentityProviderException` with `TemporarilyUnavailable`, `InteractionRequired`,
`Rejected`, `Conflict`, or `ConfirmationPending`. Swift integrations use `IdentityProviderError`.
Pending results and persisted pending state expose the reason; retry never changes the original key.
Unknown provider errors remain unavailable and their messages are not exposed by the lifecycle.

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

This unreleased SDK requires a fresh wallet database for existing development installations. The SDK never clears an old database automatically.
New-format identities retain their exact key/DID association across restart and recovery.

Set `MobileWalletIssuanceRequest.keyPolicy` (Swift: `IssuanceRequest.keyPolicy`) when a host or issuer
profile requires `BackupAndCustodyDisabled` or `HardwareGenerated`. Before starting issuance, the SDK checks that
the selected key is the active identity and already retains the required restriction. A later request
cannot relabel a general-purpose or recoverable identity as device-bound. The default is
`GeneralPurpose`; apps remain responsible for interpreting issuer/profile requirements.

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

## Optional Enterprise key custody

Add `waltid-openid4vc-wallet-custody-enterprise` or the Swift `WalletSDKEnterpriseCustody` product,
then register `EnterpriseIdentityKeyCustodian` in `keyCustodians`. Neither integration is a base-SDK
dependency or default registration. Hosts supply the authenticated HTTPS KMS resource and client settings.

The service offers only policy-compatible custody options. `copyToCustody()` imports the original
private JWK under its stable key ID, compares the returned public key, and records a public destination
reference. It retains the local signing key and does not change recovery status. Retries are idempotent;
a different key at the destination is a conflict and is never overwritten automatically.

The Enterprise `import/jwk` response can contain private material for local JWK-backed keys. The
adapters bound responses, refuse redirects, return only public receipt fields and expose categorized
errors. Host HTTP clients must not log bodies. The endpoint requires `ES_KMS_IMPORT_KEY_JWK` and
appropriate destination access. It does not retain the portable identity recovery record. Remote
signing, credential synchronization and later recovery from custody are separate integrations.

## Evidence limits

Physical-device coverage and local provider round trips must be distinguished from OS device-loss
recovery. TEE and StrongBox protection, local recovery, cross-device synchronization and cloud
restore require separate evidence. The [qualification record](recovery-qualification.md) describes
the tested capabilities and remaining gaps. Do not infer hardware or transport qualification from
a simulator run or a synchronizable local put/get. No formal EUDI, HAIP, eIDAS or FIPS qualification
is claimed.

### Native implementation and authorization evidence

`PlatformKeyConfiguration` and `PlatformKeyFacts` describe behavior and observations without exposing
the signing library. `keyFacts.authorizationEvidence` distinguishes native authorization attributes
(Android) from an SDK creation record bound to the native key (iOS). Neither is hardware attestation.
A requested configuration is not itself evidence of the key's protection.

The iOS adapter uses stable Signum for the supported generated Secure Enclave configurations and an
Apple Keychain implementation for import/export, access groups, passcode-set-only accessibility and
per-key timed reuse. Its recorded backend and namespace remain fixed for the key's lifetime. Keys
with absent or inconsistent creation records are rejected; recovery creates a fresh entry containing
the original key.

Cancellation and failed authentication return no signature. Known native failures map to the wallet's
authorization failure categories and retain native diagnostics. Unexpected faults remain distinguishable.
The WAL-749 implementation does not claim WAL-1207's separate Signum-only authorization architecture.
