# waltid-crypto2-signum

Mobile managed-key provider for Android KeyStore and iOS Keychain/Secure Enclave using published stable Signum and narrowly scoped native lifecycle extensions. Persisted records contain aliases, immutable policy, public keys, and optional attestation evidence, never private key material.

`generate` and `restore` return `ManagedKey`, so inferred kotlinx.serialization uses the provider-neutral managed-key
descriptor. Use `generateSignumKey` or `restoreSignumKey` when Signum-specific protection and attestation properties are
needed directly.

`SignumHardwarePolicy.REQUIRED` requires the backend to observe hardware backing. An
`attestationChallenge` additionally requires attestation evidence; hardware and
attestation are independent requirements.

On Android, Signum performs signing and key-use authorization, including for keys created or imported
by the native settings adapter. `AndroidSignumKeyBackend` takes an Android `Context` to check
StrongBox availability without retaining an activity. Explicit native configurations skip unavailable
StrongBox when preferred and reject it when required; native readback still verifies the actual
protection. Combined biometric/device-credential prompts omit the negative
button, as required by AndroidX. Reopening an authenticated signing key also starts and aborts a
native operation to detect permanent invalidation that a KeyStore lookup alone can miss. This check
does not prompt or sign; needing authentication is distinct from permanent invalidation. Native
invalidation during reopening or signing is reported as `SignumKeyInvalidatedException`.

On iOS, supported generated Secure Enclave keys use Signum. The Apple Keychain adapter handles
private-key import/export, explicit access groups, passcode-set-only accessibility and per-key timed
authorization reuse. It also generates unauthenticated Secure Enclave signing keys with an explicit
`privateKeyUsage` access control, which stable Signum does not construct for this policy. Native
key agreement and attestation are not offered for that combination. Ordinary P-256 Keychain keys retain export support for later backup or custody.
The selected engine is persisted with the key; reopening never selects a different implementation.
There is no exception-driven switch to another engine or weaker authorization policy.

Each iOS key has a versioned creation record in device-local Keychain storage, bound to its native
persistent reference, public key and observed attributes. Reopening rejects a changed policy, missing
record or replaced key. Prompt localization is separate from immutable security settings. Existing
keys without a creation record cannot be adopted by supplying a desired policy; recovery imports the
original material into a fresh owned entry. Incomplete creation leaves no active key; an entry left by
process termination is not silently adopted or replaced. For passcode-set-only keys, the creation
record uses the same passcode-bound accessibility. Its removal prevents reopening and use through
already opened handles, even if native token metadata survives. A missing record is reported as no owned key, even if native metadata survives. The SDK does not
adopt, overwrite or delete that unowned entry; explicit recovery imports into a fresh alias.

Secure Enclave access control protects the key separately from its outer Keychain item; the latter
can report a different accessibility class. Its accessibility therefore comes from the bound creation
policy, not the outer item attribute. Apple exposes no public getter for the complete access-control flags. Creation provenance is therefore
reported separately from native attribute inspection; it is not independent ACL verification or
attestation, and does not protect against an attacker who already controls the application's Keychain
access. SDK lifecycle operations are serialized with signing/export to prevent replacement by another
SDK operation while a key is in use.

Before creating, reopening or using a biometric-only key, the iOS adapter checks biometric availability
without prompting. It defers protected Keychain queries while biometrics are unavailable: on affected
iOS versions, querying an enrollment-tolerant ordinary Keychain entry in that state can make it
persistently unavailable. Credential-capable policies remain usable, and explicit deletion remains
possible. Native access control still authorizes each operation; the availability check grants no access.

A native token failure can indicate temporary unavailability rather than permanent invalidation.
`SignumKeyUnavailableException` preserves that distinction and the native cause; callers should retain
the key for retry. Cancellation and failed authorization remain separate failures.

## Native regression tests

`AndroidStrongBoxPreferenceTest` checks generated and imported P-256 keys with each StrongBox
preference and both preferred/required hardware backing. Run it on API 31+ targets with StrongBox,
TEE-only Keystore and software-backed Keystore. It checks native protection, reopening, signatures
and cleanup, including rejected requests, without authentication prompts or security-setting changes.
The existing Android wallet CI lane runs the emulator cases automatically.

The default iOS tests cover pure policy and error translation. Keychain lifecycle tests live in
`src/iosAppTest` and require an application host with Keychain access. On an Apple Silicon Mac with
Xcode, Java and an available iOS simulator, run:

```shell
scripts/test-ios-keychain.sh <simulator-udid>
```

Run from this module directory. The script builds with `-PenableIosKeychainTests=true`, installs an
isolated test application and saves its output under `build/keychain-tests.*`. It exercises owned-key
reopening, exact private-key recovery, policy mismatch and native-entry replacement. Simulator results
do not establish Secure Enclave, biometric, passcode, or cloud-backup behavior.

Android's `AndroidInteractiveAuthorizationTest` is opt-in. Pass the instrumentation argument
`wallet.authorization=biometric`, `credential`, or `cancel`, and use the named action for both generated
and imported keys. Without that argument, the interactive test is skipped. These tests require an
unlocked qualifying device and an operator; ordinary prompt-construction tests run without interaction.

For an entitled physical-device host, `IosSecureEnclaveKeyTest` runs with
`--test-secure-enclave`. It checks unauthenticated creation, reopening, signing, policy mismatch,
non-exportability and deletion for each Keychain accessibility choice. The device must have a passcode.
