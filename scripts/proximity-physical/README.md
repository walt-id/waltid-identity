# Optional local proximity tests

This harness provides an isolated Android test APK, an iOS test host and a local
controller for a pinned Multipaz Android reader. **The harness has compile and
hardware-free guard evidence only. No physical configuration is qualified by its
addition.** Existing physical runs remain separately authorized; adding this
entry point does not resume a paused run.

The reader uses Multipaz Android `0.100.0`, revision
`7c0988bee3384d13a0732e0c33336ae0faf3b863`. Its NFC retrieval SELECT is corrected
to the selected standard, as documented in the
[peer provenance](../../waltid-libraries/protocols/waltid-openid4vc-wallet-mobile/src/androidHostTest/kotlin/id/walt/wallet2/mobile/peer/README.md).
This is a modified independent reader, not an unmodified Multipaz interoperability
claim. No peer dependency is added to published runtime artifacts.

## Supported entry points

| Configuration | Holder | Reader | Assertion and operator action |
| --- | --- | --- | --- |
| `ble-gatt-central`, `ble-gatt-peripheral` | Android API 30+ / iOS 26+ | Android API 30+ | Exact fields, issuer/device authentication, configured trust and actual GATT bearer; accept disposable-host OS prompts |
| `ble-l2cap-central`, `ble-l2cap-peripheral` | Android API 30+ / iOS 26+ | Android API 30+ | Same oracles, with actual L2CAP selection and PSM; fallback to GATT fails |
| `nfc-direct-disconnect` | Android API 30+ | Android API 30+ with NFC reader | Fragmented response, review-time field loss and stale-approval rejection; position the phones for each round |
| `nfc-ble-continuation` | Android API 30+ / eligible signed iOS 26+ host | Android API 30+ with NFC reader and BLE | BLE request/response after the reader disables its NFC field; position the phones and accept any supported NFC sheet |

The role suffix names the **holder's** BLE role. Each invocation performs one
successful exchange, a disconnect while review is pending with no received data,
then a fresh successful exchange. It checks exact two-field disclosure, signatures,
digests, configured issuer trust and rejection under a different root. Test credentials
are synthetic Ada/Lovelace documents with fresh in-memory keys. No wallet account or
ordinary application storage is used.

iOS direct NFC and prepared-sharing/lifecycle/permission procedures need a separate
named-host procedure. Browser reader discovery has no controllable reader in this
entry point. Wi-Fi Aware retrieval remains blocked without an independent peer.
Protected keys, camera quality and human accessibility checks remain separate.
These cases must not be reported as passed or skipped by selecting a nearby configuration.

## Run after selecting disposable hosts

Commit or isolate the source first; the controller requires a clean exact revision.
Android requires `adb`, the configured SDK and two explicitly selected physical
serials. The iOS variant requires Xcode, `xcodebuildmcp`, `xcresultparser`, a signing
team and a distinct bundle ID ending in `.proximityphysical`. The controller builds
the host, checks its actual signed identity and, for NFC, its signed HCE entitlement
and AID prefixes before installing/running the selected hosts.

```sh
python3 scripts/proximity-physical/run.py \
  --opt-in physical-local --holder-platform android \
  --holder-id SELECTED_HOLDER_SERIAL --reader-id SELECTED_READER_SERIAL \
  --configuration ble-gatt-peripheral

python3 scripts/proximity-physical/run.py \
  --opt-in physical-local --holder-platform ios \
  --holder-id SELECTED_IPHONE_UDID --reader-id SELECTED_READER_SERIAL \
  --configuration ble-gatt-peripheral \
  --ios-bundle YOUR_BUNDLE.proximityphysical --ios-team YOUR_TEAM \
  --controller-address YOUR_MAC_PRIVATE_LAN_IPV4
```

For iOS NFC continuation, supply `--ios-entitlements PATH_TO_APPROVED_HOST_ENTITLEMENTS`.
Signing identity alone does not establish runtime HCE eligibility. The real
CardSession and Bluetooth checks still run in the host; unsupported access fails.
The Mac and iPhone must share the selected private LAN. A random per-run token
protects the temporary HTTP controller; its endpoint is bound to the supplied
address, carries only synthetic fixture/control material, and closes on cleanup.

The controller installs only `id.walt.proximity.physical` on selected Android phones,
grants that disposable APK's declared permissions and stops that package afterward.
It does not uninstall apps or change ordinary wallet/reader configuration. It removes
only this run's UUID control directory. The iOS host is stopped using the PID reported
by its authenticated start event. Cleanup failure fails the run and identifies that
further cleanup is required; a missing PID cannot justify stopping an unrelated app.

## Discovery and evidence

Default Gradle sources contain no physical classes. Opt-in
`-PenableProximityPhysicalTests=true` adds the Android test-only sources and changes
the test application ID; normal CI/emulator scripts also exclude the runtime-retained
`id.walt.mobile.test.PhysicalDeviceTest` annotation. Physical framework compilation
uses `-PenableWalletSdkPhysicalFixtures=true` and writes to a separate output directory.
Both Gradle flags reject CI environment variables. The iOS project under
`waltid-wallet-sdk-ios/PhysicalTests` has its own scheme and test plan and is outside
package/default demo discovery. Its runtime preflight rejects simulators and missing
local selectors. A precondition failure is never a successful early return.

Hardware-free controller and policy tests:

```sh
PYTHONDONTWRITEBYTECODE=1 python3 -m unittest discover -s .github/scripts/mobile-ci -p 'test_*.py'
PYTHONDONTWRITEBYTECODE=1 python3 -m unittest discover -s scripts/proximity-physical -p 'test_*.py'
```

Per-run output is `build/proximity-physical/<UUID>/result.json` unless `--output` is
specified. The shared report retains exact source, peer revision, fixture, OS versions,
APK hash, signed-host identity when applicable, observed routes/bearers, test verdicts
and phase timings. The `private` subdirectory holds build/test diagnostics and raw
test results; do not publish it as a sanitized report. Shared evidence omits serials,
UDIDs, QR payloads, certificates, process IDs and the controller token. Timing is
controller observation time, not a comparison of device clocks or a measurement of
operator action latency. Unsupported/missing peers, failed builds, absent test IDs,
skips, timeouts and cleanup failures cannot produce a passing report.
