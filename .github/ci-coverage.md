# Identity CI coverage contract

This is the merge-gate and lane map for `waltid-identity`. GitHub branch protection
does not currently require individual job names. The stable check to require is
**`ci-gate`**.

`ci-gate` succeeds when every invoked lane succeeds, and accepts skipped lanes,
including explicitly deferred conformance. A failed or cancelled lane fails the
gate. The gate's summary records live conformance selection and its reason; a
successful gate with deferred coverage is not evidence of a conformance pass.

The Build workflow is not skipped for documentation or asset-only changes.
Those runs still emit `ci-gate` so a required check cannot stay pending;
`gradle-build` and `macos-predicate-tests` are skipped instead. Path
eligibility still runs so `docs/mobile-sdk-api` can start `sdk-docs`.

Release candidate Maven publish is a separate job that waits for the
Gradle job and, when requested, the live conformance job.

The following labels add coverage beyond automatic path selection. `ci:macos`
and `ci:mobile` force every macOS lane. `ci:sdk-docs`, `ci:cacheless`, `ci:crypto2`,
`ci:conformance` (or the older `ci:issuer-conformance` alias), and `ci:android`
force those specific lanes.

## Deferring live conformance

Maintainers can use `ci:conformance-deferred` to postpone repeated live runs
while a coordinated PR stack is being developed. Ordinary build and platform
selection remain unchanged. Either `ci:conformance` or `ci:issuer-conformance`
overrides deferral, including on draft PRs. Removing the deferral label triggers
eligibility again; ready PRs resume automatic path selection. Draft and fork
restrictions still apply. Main pushes and manual/workflow-call runs are unchanged.

The maintainer applying deferral owns completing the coverage and must identify
the cumulative PR in the deferred PR's description. Add `ci:conformance` to the
cumulative PR and record a successful live run for the exact integrated revision
before it enters `main`. Record the actual companion repository revisions when
the change spans repositories; branch names and fallback to `main` alone do not
establish which combination passed. Changes to those revisions require fresh
evidence.

If the stack is merged separately into `main`, require passing conformance for
each state that will land; later stack commits can repair earlier failures.
Alternatively, land the validated cumulative state together. Post-merge main
coverage does not fulfill this pre-merge policy.

Deferred coverage is skipped. Earlier failures remain in run history, but a new
run can produce a successful `ci-gate` with conformance skipped. Deferral does not
resolve those failures. The label does not expire or verify cumulative evidence;
maintainers enforce the completion policy during review.

## Lanes

| Lane | Automatic when | Covers | Not a substitute for |
|---|---|---|---|
| `gradle-build` | Code changes (skipped for docs/asset-only PRs and pushes) | Unified Gradle `build allTests`, including Android host compilation when `android-eligibility` is true, and coordinated Enterprise graph compilation | Live OpenID conformance, device/UI tests, iOS XCFramework consumers |
| `conformance` | Issuer/verifier/wallet OpenID4VP and OpenID4VCI paths unless deferred, or `ci:conformance` / `ci:issuer-conformance` | Cloudflare tunnel + live conformance runners | The default Gradle job (live suites are skipped there) |
| `android-device-tests` | Android-relevant paths, or `ci:android` / `ci:mobile` | Selected instrumented device phases | Host-side Android compilation in `gradle-build` |
| `ios-simulator` | Kotlin `common*` / `ios*` sources, library Gradle files, wallet-mobile, shared build-logic/CI | Kotlin iOS compile + `iosSimulatorArm64Test` | Swift package, XCFramework, native/Compose demos, DocC |
| `native` + `compose` (one WalletCore consumer job) | Native demo / Compose demo / `waltid-wallet-demo-shared-ios` / wallet-mobile / wallet-sdk-ios / `Package.swift` / shared build-logic | One WalletCore XCFramework assemble, then the selected demo tests and document-provider checks | Kotlin simulator tests, Enterprise iOS, ABI/DocC |
| `enterprise` | wallet-mobile / persistence-mobile / wallet-sdk-ios / shared build-logic | Enterprise iOS mobile integration against Identity | Community Identity-only compilation |
| `sdk-docs` | Mobile SDK modules, DocC/Dokka workflow, or `ci:sdk-docs` | Linux Dokka + snippets; macOS `checkKotlinAbi` with iOS targets, iOS-enabled Dokka, and Swift DocC | Demo Xcode tests |
| `crypto2-platform-tests` | crypto2/jose/cose paths, or `ci:crypto2` | Windows + macOS x64 + macOS arm64/iOS crypto2 tests | Unrelated library changes |
| `macos-predicate-tests` | Code changes (skipped for docs/asset-only PRs and pushes) | Fixture coverage of the A3 path predicates and conformance eligibility, summary, and gate behavior | Runtime job success |

Main and release keep a cacheless Gradle rebuild (`clean cleanAllTests --rerun-tasks --no-daemon`).
PR Gradle runs `build allTests` with the setup-gradle daemon and cached outputs.
The Linux Gradle job times out after 90 minutes. Library JVM tests default to a
10-minute per-test timeout; live conformance / e2e / integration suites are excluded.

## Measurements

Do not treat a 15–25 minute warm Linux build as a promise. Record, per representative
PR and main run:

- job queue time and wall clock
- Gradle task outcomes (`EXECUTED`, `UP-TO-DATE`, `FROM-CACHE`) from the job summary
- setup-gradle cache restore/save
- peak RSS, `free`, and swap delta from the Gradle job summary
- cancellation rate on `main` versus pull requests

Heap, swap, daemon, and remote-cache changes wait on that evidence.
