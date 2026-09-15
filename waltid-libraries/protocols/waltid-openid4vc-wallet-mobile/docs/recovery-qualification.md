# Device qualification — 2026-09-15

These are recorded observations for WAL-749, not a claim that every supported
policy, device or OS version has been qualified. The test devices were a
OnePlus GM1913 running Android 12 and an iPhone running iOS 27.0. Runs started
at `e14f7a01404988e51d88454d84b6b8ab8d77726c`; failures were retained and
retested with the Android invalidation and iOS key-handling fixes accompanying
this report. The later registration-publication fix is covered by automated
consumer tests, not by replaying these security-setting changes.

## Completed physical checks

| Check | Android | iOS |
| --- | --- | --- |
| Real recovery-provider contracts | Block Store namespace, conflict and capacity checks passed | Kotlin and Swift Keychain contracts passed for both supported synchronizable accessibility classes |
| Recovery after local state loss | Actual isolated-app uninstall/reinstall passed for database → database, native → native, database → native and TEE → TEE | Local key/database deletion and recovery passed for database → database, Keychain → Keychain and database → Keychain |
| Authenticated recovered-key use | Combined biometric/device-credential policy: fingerprint signing, cancellation, then PIN retry passed | Device-credential policy: passcode signing, cancellation, then passcode retry passed |
| Timed authorization after recovery | Combined policy: first approval, immediate reuse and approval after expiry passed | Device-credential policy: first approval, immediate reuse and approval after expiry passed |
| Ordinary device restart | Six retained generated/imported key cases passed | Two recovered passcode-protected Keychain keys and one generated passcode-protected Secure Enclave key passed |
| Normal PIN/passcode change, protection retained | Six retained cases passed; combined policies also signed using the new PIN | The same three passcode-protected cases passed using the new passcode |
| Biometric enrollment change | Adding a fingerprint invalidated the current-set key; enrollment-tolerant and combined keys survived. Recovery of the invalidated key into the existing wallet passed | Face ID reset/re-enrollment invalidated current-set keys; recovery of the imported current-set key into its existing wallet passed. Passcode-capable keys worked while Face ID was absent |
| Guarded enrollment-tolerant keys | Not applicable to the iOS guard | Both imported `BiometricAny` and `BiometricTimedReuse(10)` keys survived reset, unavailable-use checks and re-enrollment without recovery or replacement |
| Unauthenticated hardware generation | Generated TEE control signed and reopened | Secure Enclave creation, reopening and signing passed for all five accessibility configurations; policy mismatch was rejected and keys remained non-exportable |

Successful recovery/reopening checks retained the original public key, DID and
logical key ID, verified a fresh challenge signature against the original
public key, rejected an altered challenge, and checked the selected protection.
Recovery into an existing wallet also retained its identity ID. Native-source
recovery rejected a weaker encrypted-database destination. Recovery phases ran
in separate application processes. Operators entered credentials only on the
devices and confirmed approvals, cancellations and the two-prompt reuse checks.

The OnePlus also reported encrypted-cloud backup available and accepted a
synthetic record for local retrieval/deletion. **This did not establish cloud
upload, delivery or restoration.**

## Defects found and retested

- **Android invalidation:** Keystore could return a handle for an invalidated
  key. A non-interactive operation probe now detects that condition on reopening,
  and native invalidation is mapped consistently. The original invalidated
  wallet reported unavailable without replacement, then recovered explicitly.
  Repeated probes also preserved a single-use test key before its one signature.
- **iOS Secure Enclave creation:** unauthenticated P-256 generation now uses the
  existing Apple adapter's explicit `privateKeyUsage` access control. The outer
  Keychain item's accessibility is no longer presented as independent readback
  of the enclave key's access-control policy.
- **iOS error mapping:** native token unavailability is distinct from permanent
  invalidation, failed authorization and cancellation. The retained invalidated
  Secure Enclave key passed the stable-failure regression.
- **iOS enrollment reset:** in Apple-only controls, untouched ordinary Keychain
  entries survived reset/re-enrollment; matching entries queried while Face ID
  was absent remained missing. Both Secure Enclave controls survived. The SDK
  now checks biometric availability before biometric-only key lookup/use.
  Fresh SDK keys passed the complete guarded cycle. This supports the mitigation
  on the tested device; it does not prove Apple's internal deletion mechanism,
  behavior on every OS release, or atomic protection against concurrent settings
  changes. Native access control still authorizes every key operation.

Earlier failed attempts, operator cancellations and host timeouts remain in the
local qualification evidence; successful retries do not erase them. The
physical deployment helpers and device/account details remain outside the
repository. The portable runner and regression tests are described in
[Recovery qualification](recovery-testing.md).

## Remaining qualification

- Actual Android device-to-device transfer and encrypted-cloud delivery/restore.
- Real-service refusal when Block Store end-to-end encryption is unavailable.
- Actual iCloud Keychain synchronization and recovery on a second Apple device,
  including account-recovery scenarios. Same-device reads are not sync evidence.
- Removing the device PIN/passcode, then restoring protection; ordinary changes
  with protection retained do not cover removal.
- Removing all Android fingerprints, additional authentication-factor and
  generated/imported policy combinations, and OS/device coverage beyond these
  cases. The TEE recovery result does not qualify StrongBox recovery.

Simulator/emulator contracts and host tests remain useful automated regressions,
but cannot replace these transport, hardware and operator-controlled checks.
This record does not establish EUDI/HAIP compliance or eIDAS/FIPS certification.
