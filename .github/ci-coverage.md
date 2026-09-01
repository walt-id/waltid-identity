# Identity CI coverage contract

This is the merge-gate and lane map for `waltid-identity`. GitHub branch protection
does not currently require individual job names. The stable check to require is
**`ci-gate`**.

`ci-gate` succeeds when every invoked lane succeeds, and treats path-filtered
(skipped) lanes as passing. A failed or cancelled lane fails the gate.

The Build workflow is not skipped for documentation or asset-only changes.
Those runs still emit `ci-gate` so a required check cannot stay pending;
`gradle-build` is skipped instead.

Release and fix-release Maven publish is a separate job that waits for the
Gradle job and, when requested, the live conformance job.

Labels add coverage; they are not the only way to obtain it. `ci:macos` and
`ci:mobile` force every macOS lane. `ci:sdk-docs`, `ci:cacheless`, `ci:crypto2`,
`ci:conformance` (or the older `ci:issuer-conformance` alias), and `ci:android`
force those specific lanes.

## Lanes

| Lane | Automatic when | Covers | Not a substitute for |
|---|---|---|---|
| `gradle-build` | Code changes (skipped for docs/asset-only PRs and pushes) | Unified Gradle `build allTests`, including Android host compilation when `android-eligibility` is true, and coordinated Enterprise graph compilation | Live OpenID conformance, device/UI tests, iOS XCFramework consumers |
| `conformance` | Issuer/verifier/wallet OpenID4VP and OpenID4VCI paths, or `ci:conformance` / `ci:issuer-conformance` | Cloudflare tunnel + live conformance runners | The default Gradle job (live suites are skipped there) |
| `android-device-tests` | Android-relevant paths, or `ci:android` / `ci:mobile` | Selected instrumented device phases | Host-side Android compilation in `gradle-build` |
| `ios-simulator` | Kotlin `common*` / `ios*` sources, library Gradle files, wallet-mobile, shared build-logic/CI | Kotlin iOS compile + `iosSimulatorArm64Test` | Swift package, XCFramework, native/Compose demos, DocC |
| `native` + `compose` (one WalletCore consumer job) | Native demo / Compose demo / `waltid-wallet-demo-shared-ios` / wallet-mobile / wallet-sdk-ios / `Package.swift` / shared build-logic | One WalletCore XCFramework assemble, then the selected demo tests and document-provider checks | Kotlin simulator tests, Enterprise iOS, ABI/DocC |
| `enterprise` | wallet-mobile / persistence-mobile / wallet-sdk-ios / shared build-logic | Enterprise iOS mobile integration against Identity | Community Identity-only compilation |
| `sdk-docs` | Mobile SDK modules, DocC/Dokka workflow, or `ci:sdk-docs` | Linux Dokka + snippets; macOS `checkKotlinAbi` with iOS targets + Swift DocC | Demo Xcode tests |
| `crypto2-platform-tests` | crypto2/jose/cose paths, or `ci:crypto2` | Windows + macOS x64 + macOS arm64/iOS crypto2 tests | Unrelated library changes |
| `macos-predicate-tests` | Every Build workflow run | Fixture coverage of the A3 path predicates | Runtime job success |

Main and release keep a cacheless Gradle rebuild (`clean cleanAllTests --rerun-tasks --no-daemon`).
PR Gradle keeps `clean cleanAllTests --no-daemon` and omits `--rerun-tasks` until that
experiment is compared on identical SHAs.

## Measurements

Do not treat a 15–25 minute warm Linux build as a promise. Record, per representative
PR and main run:

- job queue time and wall clock
- Gradle task outcomes (`EXECUTED`, `UP-TO-DATE`, `FROM-CACHE`) from the job summary
- setup-gradle cache restore/save
- peak RSS, `free`, and swap delta from the Gradle job summary
- cancellation rate on `main` versus pull requests

Heap, swap, daemon, and remote-cache changes wait on that evidence.
