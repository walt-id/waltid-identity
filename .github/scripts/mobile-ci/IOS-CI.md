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
and use the supplied simulator destination directly, without selecting Xcode again
or resolving another simulator. Keep the producer and SDK-docs toolchain pins
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
afterwards to ensure the fixture build did not replace it. The consumer step has
time for all five phases; the job leaves additional time to retain failure evidence.
The workflow defines both time limits.

The Enterprise task accepts `-Penterprise.ios.walletCoreArtifact=<directory>` to
verify an already restored artifact. Without that property, it builds the release
framework itself. In both paths, its fixture starts only after framework
preparation succeeds and is stopped after XCTest.

## Failure handling

Build/test steps and jobs have separate time limits, leaving time to collect
reports after a step fails. A small phase runner retains command output and
start/end/exit metadata; it propagates command failures and terminates the command's
process group on cancellation. Failed or cancelled jobs upload these logs for seven days.
There is no periodic resource sampling or unconditional Gradle profiling.
Missing required test reports fail the affected lane and the aggregate CI gate.

Run the local helper checks with:

```sh
python3 -m unittest discover -s .github/scripts/mobile-ci -p 'test_*.py'
.github/scripts/ci/test-macos-lane-predicates.sh
```
