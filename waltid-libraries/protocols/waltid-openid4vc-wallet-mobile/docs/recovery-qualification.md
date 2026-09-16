# Device qualification — 2026-09-16

These are recorded observations for WAL-749, not a claim that every supported
policy, device or OS version has been qualified. The initial checks used an
Android 12 device with TEE-backed Keystore but no StrongBox support, and a
physical iOS 27.0 device with Secure Enclave and Face ID. Runs started
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

The Android 12 device also reported encrypted-cloud backup available and accepted a
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

## Additional security transitions — September 16

On the Android 12 device, removing all fingerprints preserved PIN use for both combined policies.
After re-enrollment, enrollment-tolerant and combined keys retained their public keys; the current-set
key recovered explicitly. A missing-enrollment error that stable Signum classified as cancellation
was corrected at the mobile boundary and verified in the no-fingerprint checks.

Removing the PIN on the Android 12 device invalidated all six authenticated controls;
the unauthenticated control still signed. Restoring a PIN and fingerprint did not revive the protected keys. All five recoverable
wallets recovered from their actual Block Store records and passed fresh-process signing checks
with the original public keys, DIDs and logical IDs. The generated protected control remained
unavailable. The real Block Store service reported E2EE unavailable, and encrypted-cloud writes
were refused without leaving a record. E2EE was still unavailable after device protection was
restored; this does not establish cloud delivery or restoration of backup availability.

Two iOS passcode-removal cycles reproduced missing-passcode error misclassification and
passcode-set-only keys whose retained native metadata incorrectly appeared active. The first
cycle included an unrecorded Keychain/password prompt choice; the repeat used fresh keys and
had no such prompt. Account state was not reset between runs. Default-accessibility controls
still signed after protection was restored. The unavailable ordinary passcode-set key recovered
from its retained backup and passed a fresh-process check. The generated passcode-set Enclave
control remained unusable and exposed an unmapped token error. Neither cycle produced an
unauthorized signature.

The fixes distinguish missing credentials, map unknown token failures to typed unavailability,
and bind passcode-set-only ownership records to passcode lifetime. With fresh keys, all eight
passcode-off and all eight post-restoration checks passed: both passcode-bound controls were
unavailable before any signing probe, and the four credential-capable controls reported exactly
`DeviceCredentialNotSet` while the passcode was absent. Restoring protection did not revive the
passcode-bound keys; the other six keys retained their original public keys.

Explicit recovery then exposed a missing-ownership classification that blocked repair while native
metadata survived. Returning no owned key when its creation record is absent fixes that boundary
without adopting, overwriting or deleting the unowned entry. After this correction, the two default
controls and two unavailable controls passed again; the backed-up ordinary Keychain key recovered
into a fresh alias and passed a fresh-process signature check with its original public key, DID and
logical IDs. The retained older Enclave fixture returned typed `ProtectedKeyUnavailable`, preserving
its native `CryptoTokenKit -10` cause without declaring permanent invalidation. Generated Enclave
keys remain unrecoverable. No further passcode change was needed for this final repair retry.

## iCloud synchronization and authenticated recovery — September 16

At `718f0c200c9a31a581af851ed517592603a4730a`, the physical iOS 27.0 device
and a macOS 26.6.2 endpoint used the same Apple Account, enabled iCloud Keychain
synchronization and the same entitled test-app access group. Fresh test namespaces
were checked for absence before writes. The macOS helper was local test tooling;
this does not add macOS SDK support.

Production SDK recovery records created on iOS arrived on macOS with matching
SHA-256 digests. The macOS helper then generated independent P-256 keys and wrote new
recovery records. The iOS device retrieved matching bytes through the production
provider and restored into empty test wallets using ordinary Keychain storage.
The records sent from iOS contained derived-key material; the new macOS records
restored on iOS contained exported-key material. Private records traveled only
through Keychain synchronization; the runner exchanged public metadata and digests.

Restoration passed without signing authorization, with passcode approval on every
use, and with a ten-second passcode reuse window. Each case preserved the original
public key, DID and logical IDs, verified a fresh signature against the original
public key, rejected an altered challenge and passed signing in a fresh app process.
The per-use key returned `AuthorizationNotCompleted` on cancellation, produced no
signature, retained its identity and signed on retry after reopening. The timed
key prompted on first use and after expiry, with no prompt for the immediate repeat;
the operator confirmed exactly two prompts.

The timed record was initially absent, so the first restoration check failed before
import or authentication. The local harness was corrected to allow a bounded
180-second delivery wait; on retry the record was already present and restoration
passed. Both results were retained. This bound is a test limit, not an iCloud
delivery guarantee or evidence of controlled offline/service-outage handling.

All disposable test wallets and synchronized records were removed on iOS;
subsequent macOS reads confirmed deletion propagation. These checks qualify
`WhenUnlocked` synchronized records for the tested account and OS combination.
They do not qualify migration between physical iOS devices, recovery after losing
all trusted Apple devices, or every signing policy and synchronization failure mode.

## StrongBox recovery — September 16

At `461cb546ac64e77d8110729bba8e72997d2e4821`, a StrongBox-capable
Android 15 device passed SDK recovery checks with StrongBox explicitly required.
Native readback reported `STRONGBOX`, the expected generated/imported origin and native
authorization attributes. The generated control signed after reopening. Recoverable
identities were restored from actual Block Store records after deleting their local
test wallet and signing key. Signing in a fresh app process retained the original
public key, DID and logical IDs. Restoration also rejected a weaker
encrypted-database destination.

The recovered configurations were no signing authorization, biometric or device
credential approval, and the combined policy with ten-second reuse. Fingerprint
approval passed throughout the authenticated recovery flow. Cancellation returned
`AuthorizationNotCompleted` without a signature; the recovered key retained its
identity and signed on retry using the existing PIN. The timed check verified
first use, immediate reuse and use after expiry; the operator confirmed exactly
two fingerprint prompts after the previous authorization window had expired.

On the Android 12 device without StrongBox support, the same required configuration
did not offer native or hardware creation options and left the test wallet without
a signing identity. The StrongBox-capable Android 15 device reported encrypted-cloud
backup available. The Android 12 device reported it unavailable and refused a
synthetic write without leaving a record.
These were availability checks, not cloud upload or delivery tests.

All checks used an isolated application and temporary keys/records. Test wallets
and Block Store records were deleted, and the newly installed test app was removed
from the StrongBox-capable device. Its accounts, screen lock and biometric
enrollment were unchanged.
This qualifies the tested StrongBox local-loss recovery and authorization cases;
it does not qualify cross-device transport, cloud restore, or additional hardware,
authorization and lifecycle combinations.

## StrongBox preference matrix — September 16

The durable `AndroidStrongBoxPreferenceTest` passed all 36 cases across a StrongBox-capable
Android 15 device, a TEE-only Android 12 device and an Android 15 emulator with software-backed
Keystore. Each target ran all three StrongBox preferences for generated and imported P-256 keys,
with hardware backing both preferred and required. No cases were skipped.

| StrongBox preference | StrongBox-capable device | TEE-only device | Software-backed emulator |
| --- | --- | --- | --- |
| Required | StrongBox | Rejected | Rejected |
| Preferred | StrongBox | TEE | Software only when hardware was not required |
| Discouraged | TEE | TEE | Software only when hardware was not required |

Successful cases verified native security level, key origin, original public-key preservation and
signatures before and after reopening through a new backend instance. Rejected requests left no key;
all temporary aliases were deleted. These checks did not involve recovery-provider transport,
authentication prompts or device-security changes.

The first runs exposed a preferred-StrongBox import failure on both targets without StrongBox:
Android wrapped hardware unavailability in `KeyStoreException`, bypassing the generation-style
exception handler. The adapter now checks the public StrongBox feature declaration before requesting
it. Preferred requests use the available Keystore; required requests fail. Native readback still
enforces the requested protection. The complete matrix passed after this fix; earlier failures are
retained in local evidence. Signum Supreme remained at 0.15.0.

## Qualification scope for review

The current review covers the SDK, recovery adapters and demo flows exercised above.
Play-distributed Android cloud restore and migration between two physical iPhones are
outside this qualification scope. Provider service outages are external dependencies;
they must remain visible failures rather than trigger weaker protection or a replacement key.
The unqualified cases below are limits on deployment claims, not claims of failed implementation.

## Remaining qualification

- Actual Android device-to-device transfer and encrypted-cloud delivery/restore.
- Migration between physical iOS devices and Apple Account recovery after loss of trusted devices.
- Controlled offline/delayed synchronization, service outages and conflicts; additional
  synchronization accessibility and signing-policy combinations.
- Additional authentication-factor and generated/imported policy combinations, and
  OS/device coverage beyond these cases. StrongBox coverage is limited to the cases above.

Simulator/emulator contracts and host tests remain useful automated regressions,
but cannot replace these transport, hardware and operator-controlled checks.
This record does not establish EUDI/HAIP compliance or eIDAS/FIPS certification.
