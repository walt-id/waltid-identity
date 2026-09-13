# Proximity test automation

[WAL-1349](https://linear.app/walt-new/issue/WAL-1349/qualify-proximity-presentation-for-interoperability-and-release)
owns qualification evidence. Tests and fixes stay with the existing engine, mobile
transport, wallet SDK and demo modules. The
[suite manifest](../.github/scripts/mobile-ci/proximity-test-suites.json) records
exact required selectors, source paths, execution classes and assertion oracles.
Counts are evidence for a particular run, not a coverage percentage or qualification.

## Risk inventory

The starting inventory was reviewed at Identity `e61aabde1a9537f04027f09af9b1dd14c20198fc`.
The following maps the accepted AUT risks to preserved assertions and distinct additions.
Similar names and declared test counts alone do not establish that a test executes.

| Risk | Preserved production assertions | Added boundary / remaining limitation |
| --- | --- | --- |
| AUT-01: missing discovery, stale framework, physical leakage | Existing Gradle/JUnit/XCTest runners | Exact passing IDs, nonzero execution, owned-suite skip rejection and forbidden physical prefixes; fixture/release header checks and separate physical sources/target |
| AUT-02: field projection and receipt | `KMPProximityProjectionTests` submission/review collection regressions | Real native `StateFlow` through the Swift bridge, exact selected fields in forwarded approval and receipt, cancellation and error projection |
| AUT-02: rendered stale consent | Shared Compose review/terminal scenarios; native view-model cancellation, settings replacement and preparation tests | Android/iOS replacement review and cancellation remove stale controls; Android prepared receipt details exclude unselected fields; native combined selection preserves exact elements and proof |
| AUT-03: NFC fragmentation and lifecycle | `NfcRetrievalApduProcessorTest` APDU order, malformed DO53, request two and deactivation; native CardSession adapter tests | Android successful close retains routing through the final response fragment; bounded drain, send failure, disconnect and fresh-generation recovery |
| AUT-03: native BLE callback identity | Common framing, handshake, incoming packet and provider tests | Real Android/CoreBluetooth callback adapters reject wrong peers/attributes, dispose on disconnect/cancellation, preserve ordered bytes under backpressure and permit fresh recovery |
| AUT-03: Wi-Fi Aware ownership | Android publisher/native resource and raw-socket tests | Real attach/publish/network callback failures and cancellation clean late resources and close pending socket accept; no independent hardware peer claim |
| AUT-04: independent full exchange | SDK request processing and holder session tests | Pinned reader checks static NFC handover, fragmented response, issuer/device authentication, field digests, configured-root trust and a wrong-root control |
| AUT-04: retained connection and rejection | Holder wire-error and coordinator cancellation tests | Independent reader request two on one connection uses fresh selective consent; ciphertext mutation and cancellation suppress disclosure and allow fresh recovery |
| AUT-05: crypto known answers | `MdocSessionCipherTest` directional keys, IV layout, counter exhaustion, replay and RFC 5869 HKDF | Public external transcript/key/ciphertext vectors through production cipher and platform ECDH, on JVM, Android host, iOS Simulator and JS |
| AUT-05: malformed requests and authentication | `HolderWireErrorTest` encrypted status, empty response, consent suppression and CBOR budgets; SDK reader trust/request matrices | External-vector mutations change one condition and retain valid controls; existing holder/SDK tests remain the oracle for wire status and absence of consent |
| AUT-06: real radios and OS behavior | Deterministic counterparts above | Optional selected-device hosts/controller for BLE roles/bearers, Android direct NFC and eligible NFC-to-BLE; hardware passes require separate physical execution |

## CI and local deterministic execution

`verify_proximity_suites.py` checks the named suites assigned to `jvm`, `android-host`,
`ios-simulator` or `js-node`. The standard Gradle workflows invoke it after their
tests. The iOS Kotlin and consumer lanes invoke the action from their checked-out
revision, so editing that action changes the code actually executed by those lanes.
Path eligibility includes the guard scripts and native simulator test sources.

`run-ios-wallet-sdk-tests.sh` assembles the release and test-fixture XCFrameworks
from the selected checkout, checks their headers, records source and binary hashes,
and explicitly runs `WalletSDKTests/KMPProximityProjectionTests` on a simulator.
Testing only the demo's `iosAppTests` cannot replace this package suite. Its
`WALLET_SDK_BRIDGE_FIXTURES=1` framework has the same SDK sources and an additional
test-only session producer. The ordinary release framework has no test producer.
The native demo lane separately requires its exact consent-selection regression.

Focused local examples, run from Identity:

```sh
./gradlew :waltid-libraries:credentials:waltid-mdoc-proximity:jvmTest \
  --tests '*IndependentSessionVectorTest'
./gradlew :waltid-libraries:protocols:waltid-openid4vc-wallet-mobile:testAndroidHostTest \
  --tests '*IndependentNfcReaderTest' -PenableAndroidBuild=true
.github/scripts/mobile-ci/run-ios-wallet-sdk-tests.sh \
  'platform=iOS Simulator,id=SELECTED_SIMULATOR_UDID'
```

Apply each Gradle `--tests` selector to its own task. A selector following the last
task in a multi-task invocation does not select all preceding tasks. Preserve failed
attempts and zero-test/setup failures; do not retry until green and report only the
last result. Unit-test deadlines and coroutine deadlines remain finite.

The Enterprise iOS lane separately times prerequisite fixture compilation, simulator
startup, framework assembly, Xcode build, fixture health and test execution. Its
bounded step budget is 70 minutes; its five native tests and per-phase limits remain
unchanged. This is setup headroom and diagnostics, not evidence of runner reliability.
The coordinated Enterprise companion supplies the split build/test task and retains
per-run phase logs on failure or cancellation.

## Independent source authority

The [reader provenance](../waltid-libraries/protocols/waltid-openid4vc-wallet-mobile/src/androidHostTest/kotlin/id/walt/wallet2/mobile/peer/README.md)
pins Multipaz 0.100.0, its source hash and Apache-2.0 license. That revision sends
NFC retrieval SELECT P2=00, while the selected authorized DIS requires P2=0C.
The holder rejects the stock command. The test-only reader corrects that operation;
successful results establish interoperability with this corrected reader, not the
unmodified release. The holder is not changed to accept the peer's conflicting byte.

The [vector manifest](../waltid-libraries/credentials/waltid-mdoc-proximity/src/commonTest/kotlin/id/walt/mdoc/proximity/vectors/manifest.json)
records external plaintext, transcript, key and ciphertext provenance and extraction.
Expected ciphertext is never regenerated by the holder under test. A test-only key
adapter imports the public example scalars into the platform ECDH backend because
Android's production runtime intentionally excludes that private-key import profile.
This tests ECDH and the production session cipher, not Android runtime import support.
Restricted standard procedures are neither copied nor inferred from peer behavior.

## Physical and qualification boundary

The [local physical entry point](../scripts/proximity-physical/README.md) documents
the exact supported matrix, selected disposable hosts, signing/capability checks,
operator actions, deadlines and sanitized reports. Its Android sources and iOS target
are absent from ordinary discovery. Gradle opt-in and runtime guards reject CI;
Android emulator runners also exclude the physical marker. Precondition failure,
missing results and cleanup failure cannot be a passing physical run.

Compile checks, loopback APDUs, host doubles and simulator tests do not establish radio
interoperability, HCE entitlement/eligibility, OS permission behavior or qualification.
iOS direct NFC/prepared lifecycle procedures and browser-reader discovery remain
separate assisted work; Wi-Fi Aware hardware retrieval remains blocked without an
independent peer. Protected-key and accessibility procedures retain their own fixtures
and manual evidence. This implementation does not resume a paused physical run or
change the existing case ledger's dispositions.
