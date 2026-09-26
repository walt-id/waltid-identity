# WeBuild ITB wallet runs

[WAL-1423](https://linear.app/walt-new/issue/WAL-1423/itb-initial-tests) covers the
21 deployed CS-01, CS-02, CS-07 and TS12 cases. `itbWallet` drives their actual
ITB interactions through the production wallet, using a fresh holder and
an in-memory credential store. `itbTest` provides eleven separate local protocol
checks with synthetic fixtures and mock issuance responses.

The live runner supplies a short-lived, self-signed **test key attestation**
bound to each proof key and issuer nonce. Its storage, authentication,
certification and status values are visibly synthetic `example.invalid` values.
The deployed issuer currently accepts them for these test sessions; their
acceptance does not establish a certified Wallet Provider, trusted attester,
real status service or WE BUILD assurance level. This fixture is confined to
the conformance runner and its device test, not the production wallet.

Neither entry point proves native platform delivery, consent UX or full EUDI
conformance. Use per-run reports and [WAL-1423](https://linear.app/walt-new/issue/WAL-1423/itb-initial-tests)
for current qualification; [ITB-BASELINE.md](ITB-BASELINE.md) is a dated historical result.

## Hosted cases

From the Identity repository root, with JDK 21, supply credentials through your
local environment or a secret manager. Never commit them or enable HTTP/body
logging for these runs.

| Environment variable | Value |
| --- | --- |
| `ITB_ORGANISATION_KEY` | Existing organisation API key |
| `ITB_USERNAME`, `ITB_PASSWORD` | Existing portal account |
| `ITB_ORGANISATION_ID` | Portal organisation ID (`20` for the inspected Walt.id tenant) |
| `ITB_BUILD_REVISION` | Tested 40-character commit SHA; append `+dirty` for an uncommitted tree |

Portal UI IDs are not API keys. The manifest pins the deployment to
`https://dev-i4mlab.aegean.gr/itb`, the system name to `walt.id`, and the exact
statement, suite and case labels. A different selected system or missing/ambiguous
label fails before wallet execution. System and actor API keys are not needed.

```bash
./gradlew :waltid-services:waltid-openid4vp-conformance-runners:installPlaywrightBrowsers -Pplaywright.browser=chromium
./gradlew :waltid-services:waltid-openid4vp-conformance-runners:itbWallet
```

By default all 21 cases run in catalogue order. `ITB_CASES` can select comma-separated
case IDs (for example `tc_vp_002`); required issuance cases are automatically
included. A failed prerequisite does not skip its presentation cases. Unknown
or empty selections are configuration errors.

When `CI=true`, selection is restricted to the 15 cases that do not need a
user-authentication prompt: seven CS-01, four CS-02, one CS-07 and three TS12
issuance cases. Explicitly requesting any of the six TS12 payment cases fails
before a session starts. The local default remains all 21; for the six payment
cases with their three issuance prerequisites, use the [operator-assisted native
setup](ITB-NATIVE.md) and select them explicitly:

```bash
ITB_CASES=ts12_pay_01,ts12_pay_02,ts12_pay_03,ts12_pay_dc_api_01,ts12_pay_dc_api_02,ts12_pay_dc_api_03 \
  ./gradlew :waltid-services:waltid-openid4vp-conformance-runners:itbWallet
```

`ITB_REPORT_DIR` selects the report directory; the default is this module's
`build/reports/itb-wallet`. Every selected case appears in `results.json`,
`junit.xml` and `summary.md`, including cases not reached after report initialization. Invalid build identity or
case selection fails before report initialization.
The process returns zero only if every selected case completes successfully in
both the wallet adapter and its own terminal ITB report. Failures, timeouts,
incomplete results and unexecuted cases remain failures in JUnit; none become
expected failures or skips. `adapterInvoked` records entry into the adapter,
not proof that every wallet protocol stage ran.
The summary and JSON report identify the synthetic attestation fixture.

The runner starts one session at a time, never retries an uncertain start, and
records each prepared session ID before starting its test steps. Cleanup stops
only that owned session. A stopped ITB session can
have `UNDEFINED` rather than `FAILURE`; the separate wallet outcome is preserved.
Reports contain identities, timestamps and error types/codes, not credentials,
raw protocol messages, browser state or ITB service logs.

The bridge authenticates to the portal and selects the exact statement, suite
and case in **Interactive execution** mode. Each case starts from a fresh portal
document, so prior dialogs and asynchronously updated session lists cannot
select another interaction. It downloads the owned session's offer/request/script
input directly from its execution page. It reads declarative
DC API inputs without executing supplied JavaScript. Downloads are removed
immediately after reading. The deployed authorization-code issuer returns a
direct redirect; the adapter validates its destination, state and code before
continuing through production wallet issuance. Interactive issuer login is not
implemented by this reference-environment runner.

The ephemeral test wallet trusts the independently sourced EUDI PID Issuer CA 02
recorded in [the fixture provenance](../src/main/resources/itb/README.md).
`ITB_X509_TRUST_ANCHORS` optionally replaces it with an operator-supplied PEM
file. Request `x5c` chains are never automatically trusted.

The **WeBuild ITB live wallet cases** workflow is explicitly dispatched by an
operator because it uses a shared external tenant. Runs share one concurrency
group so their tenant sessions do not overlap. The six payment cases remain
local, operator-assisted coverage; CI success means 15/15 selected cases, not
21/21. The offline profile checks remain a separate PR check.

The portal bridge gives a started, owned session up to 45 seconds to display its
wallet interaction and reports the portal's generic execution error separately.
It never clicks Start again or creates a replacement session after an uncertain
start. A hosted JVM payment that reaches the SCA callback is reported as
`AUTH_UNAVAILABLE`: that runner has no verified end-user factors and sends no
proof with invented `amr`. Other request and credential failures retain their
own non-passing results. Operator-assisted native execution is described in
[ITB-NATIVE.md](ITB-NATIVE.md); it is not an unattended hosted CI check.

Configure repository Actions secrets `ITB_ORGANISATION_KEY`, `ITB_USERNAME` and
`ITB_PASSWORD`
and repository variable `ITB_ORGANISATION_ID=20`, following the existing conformance
workflows. A dedicated GitHub environment is not required. Tenant credentials are
injected only into the live test step, not the offline profile checks. As with
other repository secrets, maintainers must review workflow changes that could
access them; manual dispatch does not create an additional secret boundary.

The JVM version and JSON/JUnit/Markdown reporting follow the existing conformance
setup. ITB uses an outbound portal bridge, so it does not need the other suites'
inbound tunnels or coordinated enterprise checkout. Its strict results follow
WAL-1423's explicit requirement; the older suites' `CONFORMANCE_ALLOW_FAILURE`
switch does not change ITB outcomes.

The live workflow uses a read-only GitHub token, does not persist checkout
credentials or write Gradle caches, and retains only sanitized reports for 14 days.
Dispatch becomes available once the workflow exists on the default branch. Its
`cases` input can select an unattended subset, such as `tc_vp_002` with its
issuance prerequisite; an empty input runs all 15. An operator-required
case is rejected before any tenant session starts. The checked-in workflow
alone is not a successful hosted run.

## Local profile checks

```bash
./gradlew :waltid-services:waltid-openid4vp-conformance-runners:itbTest
```

Any assertion failure fails the task. An explicit local diagnostic run can keep
collecting reports with `-PitbAllowFailures=true`; failures remain in JUnit.
Results are under `build/test-results/itbTest` and `build/reports/tests/itbTest`.

The **WeBuild ITB offline profile checks** workflow runs the runner's unit tests and these
profile assertions on relevant PR changes. PR runs are strict; a manual run may
explicitly disable enforcement. Compilation, setup and incomplete report
collection always fail. The report collector checks the exact eleven-check
inventory, duplicates and skipped results:

```bash
python3 -m unittest discover -s scripts/itb -p 'test_*.py'
```

## Requirements and coverage

The requirement references are WeBuild architecture commit
`da58a26a84ab3b5bd04c15cb63d5e9ab7479c292`:
[CS-01 v1.3](https://github.com/webuild-consortium/wp4-architecture/blob/da58a26a84ab3b5bd04c15cb63d5e9ab7479c292/conformance-specs/cs-01-credential-issuance.md),
[CS-02 v1.1](https://github.com/webuild-consortium/wp4-architecture/blob/da58a26a84ab3b5bd04c15cb63d5e9ab7479c292/conformance-specs/cs-02-credential-presentation.md),
[CS-07 v0.1 pre-flight](https://github.com/webuild-consortium/wp4-architecture/blob/da58a26a84ab3b5bd04c15cb63d5e9ab7479c292/conformance-specs/cs-07-credential-presentation-dc-api.md), and
[CS-12 v1.0 (TS12 profile)](https://github.com/webuild-consortium/wp4-architecture/blob/da58a26a84ab3b5bd04c15cb63d5e9ab7479c292/conformance-specs/cs-12-sca-payments.md).
The local check names identify requirement areas, not hosted ITB case IDs.

The [public ITB base-protocol inventory](https://github.com/webuild-consortium/wp4-interop-test-bed/tree/9cd45c78483766e7bc60d9028def9103214a4529/tests/base-protocols)
at `9cd45c78483766e7bc60d9028def9103214a4529` contains eight issuance and seven
presentation holder cases. Its `test-suite12.xml` is an issuance suite, not TS12
payment testing. The authenticated tenant catalogue differs from that snapshot.
Do not equate the eleven local profile checks with the deployed cases.

### Deployed catalogue

The authenticated Walt.id catalogue on ITB 1.29.5 was inspected on 2026-09-21.
The [case manifest](../src/main/resources/itb/wal-1423-catalogue.json) records the
actual suite/case identifiers exposed by the portal's REST API configuration.
It contains no API keys. Versions below are the suite's displayed descriptions;
the execution report must still record the version actually run.

| Target | Deployed suite ID | Cases | Displayed version |
| --- | --- | --- | --- |
| CS-01 | `cs01v1` | VCI-001/002/003/005/006/007/008 (7) | 1.0, December 2025 |
| CS-02 | `cs02v1` | VP-001/002/003/007 (4) | 1.0, December 2025 |
| CS-07 | `cts07` | `tc15` (1) | Not exposed on the inspected configuration screen |
| TS12 | `ts12` | 3 issuance, 3 payment, 3 DC API payment (9) | Not exposed on the inspected configuration screen |

The initial matrix therefore contains **21 cases**. The separate Trust Framework
Integration suite (1.1, August 2026, optional WUA), QES suite and relying-party
tests are not additional initial wallet cases. Historical tenant results do not
identify this branch's build and must not be reported as its results. Case titles
and linked documentation are insufficient to infer interaction contracts: the
VCI-006 documentation popup still describes an unrelated AcademicID flow.

### Verified portal and REST boundary

Interactive portal startup is required by the deployed DC API cases: REST
background startup completes their instruction steps before the wallet can act.
The runner therefore uses one interactive execution path for all 21 cases and
uses REST only for authenticated status, terminal XML reports and cleanup. The
hyphenated `ITB-API-KEY` header is required by the deployment. XML report requests
send only `Accept: application/xml`; a combined JSON/XML header is rejected.

The REST reports omit the offers and request context. The bridge downloads these
from the owned session's pending interaction. It never substitutes a separate
issuer offer or verifier request and attributes that to an ITB case. The initial
standalone outcome and regression rationale are in the
[historical baseline](ITB-BASELINE.md).

### Offline runner checks

```bash
./gradlew :waltid-services:waltid-openid4vp-conformance-runners:test --tests 'id.walt.itb.*' -PskipLiveConformance=true
./gradlew :waltid-services:waltid-openid4vp-conformance-runners:itbPortalTest -PskipLiveConformance=true
python3 -m unittest discover -s scripts/itb -p 'test_*.py'
```

The explicit `itbPortalTest` task requires the Chromium installation shown above;
ordinary JVM tests do not. It intercepts all browser traffic and verifies suite
selection, interactive mode, download labels, session identity and stale-dialog
cleanup against local DOM fixtures. PR checks run these without tenant secrets.
These tests protect the runner; they do not establish live wallet conformance.
