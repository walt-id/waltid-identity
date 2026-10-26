# iOS CI

The macOS workflow selects Kotlin simulator tests, native consumers, Compose
consumers, Enterprise integration tests and SDK docs independently. A docs-only or
Enterprise-only selection also runs the framework producer; simulator-only runs
do not need it. Linux docs and Kotlin simulator tests can start independently.

The iOS test jobs, shared framework producer and macOS SDK docs use Xcode 27.0
on GitHub's [`xcode-27` arm64 image](https://github.com/actions/runner-images/blob/main/images/macos/xcode-27-arm64-Readme.md).
The image currently uses macOS 27 and remains in public preview. Demo, bridge and
Enterprise tests select the image's iPhone 17 simulator on iOS 27.0 explicitly;
there is no fallback to an older runtime. `DEVELOPER_DIR` pins Xcode for the whole
workflow, including framework verification. Composite actions inherit that pin
and the supplied simulator configuration. Keep the producer and SDK-docs toolchain pins
aligned because framework reuse requires an exact Xcode match.

## Shared release framework

One producer assembles the full release `WalletCore.xcframework`, including device
and simulator arm64 slices. Its worker limit avoids concurrent native links on
the small hosted runner. It records the four resolved repository revisions.
Downstream consumers check out those exact revisions and restore the same-run
artifact. The manifest verifies the Identity commit, Xcode version, release
configuration, platform coverage and file hashes before the framework is used.
The archive is retained for one day; rerun the producer if it has expired.

Proximity bridge tests verify and reuse that release artifact, then build their
isolated simulator fixture with one Gradle worker. They verify the release again
afterwards to ensure the fixture build did not replace it. Consumers run in three
independent jobs: native provider plus native demo, Compose provider plus Compose
demo, and Swift bridge tests. Each provider stays with its demo to reuse the build.
Bridge tests run whenever either native or Compose is selected. Matrix failures
do not cancel siblings; every selected job must succeed for the reusable workflow
and `ci-gate` to succeed. Reports and logs have distinct names for each consumer.
Each job's selected functional phases share a 160-minute deadline. Report collection
and native/Compose execution checks run outside that deadline, with 10 minutes of their own.
Only the native job runs the 30-minute SwiftUI comparison. A visual-only selection
runs that native job without functional tests or functional-report requirements.
The 210-minute job budget leaves 10 further minutes for setup and artifact uploads.
These limits bound failures; successful runs finish as soon as their suites complete.

The Enterprise task accepts `-Penterprise.ios.walletCoreArtifact=<directory>` to
verify an already restored artifact. Without that property, it builds the release
framework itself. In both paths, its fixture starts only after framework
preparation succeeds and is stopped after XCTest.
The task's `enterprise.ios.destination` property (or `IOS_SIMULATOR_DESTINATION`)
must use `platform=iOS Simulator,name=<device type>,OS=<version>`, for example
`platform=iOS Simulator,name=iPhone 17,OS=27.0`. It validates this format before
running task dependencies. An existing simulator ID or `OS=latest` cannot select
the fresh, runtime-pinned destinations that keep the two demos isolated.

## Unattended coverage

Native app and Swift package tests discover their targets by default. Explicit
exclusions reserve physical signing prompts for attended checks, batch issuance
for the Enterprise fixture phase, and Swift Keychain/persistence snippets for the
entitled native app host. Result checks require nonempty unit and UI targets
separately, require nonempty sharedLogic and Swift SDK results, and retain class
guards for conditional bridge fixtures, platform adapters and app-hosted
Keychain/persistence sources. They reject failures and unexpected skips across
reported cases. The native app permits only its two recovery checks that require
physical hardware or an operator to skip. sharedLogic links
and executes its complete Kotlin iOS test target using
the toolchain's existing SwiftPM dependency linkage.

The recovery job also runs Signum's existing entitled Keychain host on its simulator,
requiring the ownership suite and retaining per-test JUnit results. It discovers
all tests except the physical Secure Enclave class and rejects skipped or incomplete runs.
The platform result guard also requires X.509 reports on JVM, JS Node, Android host
and iOS Simulator. Only the deferred Ed25519 cases may skip, with exceptions scoped
to the platforms that declare them.

There are no method inventories or fixed test counts to update when adding or
renaming ordinary tests. Missing Android PIN or browser opt-in makes the affected
test skip, which fails the result check even when the rest of its suite passes.
DC API and offline ITB profile checks also require their suites without fixing the
number of methods. Cloud, fixture, physical-device and isolated-process selectors
continue to route tests to the environment they need.

Android wallet device tests exclude the separately orchestrated recovery lifecycle
and the three suites that need an operator for native authorization or live ITB.
Wallet, crypto backend and both Compose instrumentation modules reject every
unexpected skip and require nonempty reports. Suite guards
catch lost targets or critical classes; individual test deletion remains visible
in source review rather than a duplicate method inventory.

The Enterprise phase runs SDK contracts plus native and Compose batch journeys
with its existing issuer/verifier fixture. The fixture URL reaches XCTest through
Xcode's `TEST_RUNNER_` environment forwarding. Native and Compose batch reports
are checked separately and retained on successful runs. App, shared iOS and shared
attestation fixture changes select the Enterprise lane as well as their ordinary
consumer lanes; new test files in these targets need no lane configuration entry.

## Compose URL handoffs

Compose tests create a temporary simulator from the workflow's named device type
and explicit iOS version, wait for boot completion, and delete it after execution.
Only the Compose wallet handles the demos' shared URL schemes there. XCTest's
system URL opener delivers links to the running wallet. Test and cleanup failures
propagate through the existing phase runner.

## Failure handling

Consumer XCTest commands disable verbose system diagnostic collection by default
with `-collect-test-diagnostics never`. This avoids long simulator diagnostic waits
after tests complete; ordinary result bundles, assertions, reports and test
attachments remain enabled. Select `collect-test-diagnostics: on-failure` when
dispatching or calling the workflow to collect verbose diagnostics such as
sysdiagnose. Direct script invocations accept `XCODE_TEST_DIAGNOSTICS=on-failure`.
This setting does not change test selection, timeouts or failure propagation.

Build/test steps and jobs have separate time limits, leaving time to collect
reports after a step fails. A small phase runner retains command output and
start/end/exit metadata; it propagates command failures and terminates the command's
process group on cancellation. Failed or cancelled consumer executions retain raw
result bundles and phase logs for seven days, including when a bundle cannot be
converted to JUnit. Converted consumer reports are also retained on success.
There is no periodic resource sampling or unconditional Gradle profiling.
Missing required test reports fail the affected lane and the aggregate CI gate.

Run the local helper checks with:

```sh
python3 -m unittest discover -s .github/scripts/mobile-ci -p 'test_*.py'
.github/scripts/ci/test-macos-lane-predicates.sh
```
