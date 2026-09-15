# Recovery qualification

The recovery harness uses disposable wallets, real encrypted persistence and the
configured native backup adapter. It verifies the original public key, DID and
key identifier, destination protection, fresh challenge signatures and reopening
after process termination. It never contacts an issuer or verifier. Key-use
authorization is explicitly `None`; biometric, passcode and security-setting
qualification remain separate operator-assisted tests.

Each phase runs in a new application process. The host checkpoint contains
public metadata, the application hash, selected device identifier and phase
results. Keep the output directory and original application artifact to resume a
run. It never contains recovery bytes or private keys.

## Build the isolated test applications

Run from the repository root with Python 3 (standard library only) and the
repository's JDK configured. For Android on Linux or macOS, set `ANDROID_HOME`
(or `ANDROID_SDK_ROOT`) to an SDK containing platform-tools and build-tools. For
iOS, use an Apple Silicon Mac with Xcode and an installed simulator runtime;
`DEVELOPER_DIR` selects Xcode when needed. No personal account, signing team or
local agent tooling is required for these local-loss checks.

```sh
./gradlew :waltid-libraries:protocols:waltid-openid4vc-wallet-mobile:assembleAndroidDeviceTest \
  -PenableAndroidBuild=true -PenableIosBuild=false

python3 scripts/build-recovery-simulator-host.py --output build/ios-recovery-host
```

Android uses `id.walt.wallet2.mobile.test`; iOS uses
`id.walt.wallet.recovery-tests`. The iOS builder embeds Keychain entitlements
and the SQLCipher runtime in a simulator-only app. The runner uses Android SDK
tools or Xcode’s `xcrun simctl` for installation and launch. These are test
hosts, not distributable demo apps.

## Unattended local-loss checks

```sh
python3 scripts/qualify-wallet-recovery.py local-loss \
  --device ANDROID_SERIAL \
  --apk waltid-libraries/protocols/waltid-openid4vc-wallet-mobile/build/outputs/apk/androidTest/waltid-openid4vc-wallet-mobile-androidTest.apk \
  --storage NativeStorage --output build/recovery/android-native

python3 scripts/qualify-wallet-recovery.py local-loss \
  --platform ios-simulator --device SIMULATOR_UDID \
  --app build/ios-recovery-host/RecoveryTests.app \
  --storage NativeStorage --output build/recovery/ios-native
```

Start the intended emulator/simulator first and supply its exact serial/UDID;
`booted` is not a stable identifier for resumable runs. Use a fresh output
directory per configuration. Android supports `EncryptedDatabase`,
`NativeStorage` and `Hardware` when offered by the device. iOS restoration
supports `EncryptedDatabase` and `NativeStorage`; Secure Enclave recovery is
unsupported. An unavailable provider or unsupported selection fails the run; a
skipped/empty test suite cannot count as recovery evidence.

Use `--restore-storage NativeStorage` with `--storage EncryptedDatabase` to test
recovery into native storage. Without `--restore-storage`, recovery uses the
source storage type. Native-storage runs also assert that database storage is
not offered as a recovery destination; recovery must preserve the recorded
minimum protection. Keep both storage arguments unchanged when resuming a run.

`local-loss` executes prepare → verify → delete test-local state → restore →
verify → cleanup. Deletion checks that the original native key is missing, while
preserving the backup record. The restoration phase requires an empty wallet and
no original local key entry. Cleanup deletes only this run's wallet and
namespaced backup records. Each output directory contains `results.xml` (JUnit),
`checkpoint.json` and per-phase logs. Failed phases remain failures even when
cleanup succeeds.

## Resuming and Android reinstall qualification

The same arguments also accept individual commands: `prepare`, `verify`,
`lose-local`, `restore`, `cleanup`. The checkpoint enforces phase order,
application/device continuity and the original source/destination storage
selection. `prepare` intentionally retains the test wallet and backup for a
later invocation; finish with `cleanup`.

For the separately scheduled Android reinstall test, execute `prepare`,
`verify`, **`reinstall`**, `restore`, `verify`, `cleanup` against the same
output directory and exact signed APK. `reinstall` explicitly uninstalls only
the isolated instrumentation application and installs it again; it does not
reset the device. Enable the required device backup services before attempting
provider transport. The script refuses to use a demo or arbitrary application
package. An interrupted installation can be retried with `reinstall` without
repeating the uninstall.

iOS uninstall does not establish Keychain loss, so the harness rejects that
operation. Physical iPhone testing needs an appropriately signed host and
remains outside this simulator runner.

## Evidence boundaries

- Android local-loss checks exercise the real Block Store adapter without
  uninstall or cloud delivery.
- Android reinstall checks establish retention/retrieval after app removal, not
  remote cloud delivery.
- Simulator Keychain checks establish local provider/persistence behavior, not
  iCloud synchronization   or physical hardware protection.
- Security-setting changes, authenticated restored-key use, cross-device
  transfer and actual cloud   restoration require the separate device/provider
  qualification plan.

The Block Store contract suite additionally covers namespace isolation,
conflicts, idempotence, combined identifier/data size limits, entry quota,
preservation after rejection and capacity reuse. It must run by itself in its
isolated test application because quota is application-wide. Google documents
the combined entry limit and application-wide quota in
[BlockstoreClient.storeBytes](https://developers.google.com/android/reference/com/google/android/gms/auth/blockstore/BlockstoreClient#storeBytes(com.google.android.gms.auth.blockstore.StoreBytesData)).

## Android emulator coverage

Block Store contracts and `local-loss` workflows can run on an emulator with
Google Play services. Use a Google APIs or Google Play image; the default AOSP
image used by the ordinary wallet CI lane does not provide that service. Select
the emulator explicitly when physical devices are also attached.

The `wallet-recovery` CI phase runs on an API 35 Google APIs x86-64 emulator,
independently of the DC API demos. It runs the three provider contracts followed
by same-storage `EncryptedDatabase` and `NativeStorage` workflows and
encrypted-database-to-native-storage recovery, with JUnit reports and logs
uploaded even on failure. Provider unavailability, skipped tests and incomplete
results fail the phase. The same entry point works locally:

```sh
ANDROID_SERIAL=EMULATOR_SERIAL .github/scripts/mobile-ci/run-android-wallet-recovery-tests.sh
```

That entry point requires `ANDROID_HOME`. Without `ANDROID_SERIAL`, it only
selects a device when exactly one connected device is an emulator. It never
automatically selects a physical device. The phased recovery class is excluded
from the ordinary wallet CI test invocation.

These checks use `DeviceTransfer` mode for local storage and do not need a
signed-in Google account. Emulator Keystore results do not establish physical
TEE or StrongBox protection, and local retrieval does not establish transport.
The iOS simulator CI job runs the Keychain contract and the same three recovery
configurations in an entitled test host. Its entry point creates and deletes
only its own simulator:

```sh
.github/scripts/mobile-ci/run-ios-wallet-recovery-tests.sh
```

The iOS script uses the selected Xcode and newest available iOS runtime. Set
`IOS_RECOVERY_DEVICE_TYPE` to an installed simulator device type when the
default iPhone 17 type is unavailable.

Host runner regressions: `python3 -m unittest discover -s scripts/tests -p
test_qualify_wallet_recovery.py`. SDK lifecycle regressions:
`:waltid-libraries:protocols:waltid-openid4vc-wallet-mobile:testAndroidHostTest`.
