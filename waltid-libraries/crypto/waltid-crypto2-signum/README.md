# waltid-crypto2-signum

Mobile managed-key provider for Android KeyStore and iOS Keychain/Secure Enclave using published stable Signum and narrowly scoped native lifecycle extensions. Persisted records contain aliases, immutable policy, public keys, and optional attestation evidence, never private key material.

`generate` and `restore` return `ManagedKey`, so inferred kotlinx.serialization uses the provider-neutral managed-key
descriptor. Use `generateSignumKey` or `restoreSignumKey` when Signum-specific protection and attestation properties are
needed directly.

`SignumHardwarePolicy.REQUIRED` requires the backend to observe hardware backing. An
`attestationChallenge` additionally requires attestation evidence; hardware and
attestation are independent requirements.

On Android, Signum performs signing and key-use authorization, including for keys created or imported
by the native settings adapter. Combined biometric/device-credential prompts omit the negative
button, as required by AndroidX.

On iOS, supported generated Secure Enclave keys use Signum. The Apple Keychain adapter handles
private-key import/export, explicit access groups, passcode-set-only accessibility and per-key timed
authorization reuse. Ordinary P-256 Keychain keys retain export support for later backup or custody.
The selected engine is persisted with the key; reopening never selects a different implementation.
There is no exception-driven switch to another engine or weaker authorization policy.

Each iOS key has a versioned creation record in device-local Keychain storage, bound to its native
persistent reference, public key and observed attributes. Reopening rejects a changed policy, missing
record or replaced key. Prompt localization is separate from immutable security settings. Existing
keys without a creation record cannot be adopted by supplying a desired policy; recovery imports the
original material into a fresh owned entry. Incomplete creation leaves no active key; an entry left by
process termination is not silently adopted or replaced.

Apple exposes no public getter for the complete access-control flags. Creation provenance is therefore
reported separately from native attribute inspection; it is not independent ACL verification or
attestation, and does not protect against an attacker who already controls the application's Keychain
access. SDK lifecycle operations are serialized with signing/export to prevent replacement by another
SDK operation while a key is in use.

## Native regression tests

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
