# iOS CI

The macOS workflow selects Kotlin simulator tests, native consumers, Compose
consumers, Enterprise integration tests and SDK docs independently. A docs-only or
Enterprise-only selection also runs the framework producer; simulator-only runs
do not need it. Linux docs and Kotlin simulator tests can start independently.

## Shared release framework

One producer assembles the full release `WalletCore.xcframework`, including device
and simulator arm64 slices. It records the four resolved repository revisions.
Downstream consumers check out those exact revisions and restore the same-run
artifact. The manifest verifies the Identity commit, Xcode version, release
configuration, platform coverage and file hashes before the framework is used.
The archive is retained for one day; rerun the producer if it has expired.

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
