# WeBuild ITB wallet runs

The runner selects 27 Wallet SUT cases across CS-01, CS-02, CS-07, CS-10 and TS12.
The live GitHub workflow installs the Compose Wallet Preview Debug app and drives
its consent, credential storage and Android provider entry points. Its clean
emulator uses the app PIN and signing protection disabled. The six TS12 payment
cases use the same app in [attended mode](ITB-NATIVE.md).

The app and the SDK diagnostics use synthetic key attestation to reach the
reference issuer. The fixture's storage, authentication, certification and
status claims do not establish Wallet Provider assurance. `itbTest` runs eleven
independent offline protocol checks. See [ITB-BASELINE.md](ITB-BASELINE.md)
for the initial results.

## Batch issuance evidence

The live app test accepts one copy per offer with one holder. Its success check requires stored credentials and no pending
transaction; it does not assert multiple proofs, distinct holder bindings or
presentation of every returned copy. A green live ITB run is therefore not batch
issuance evidence.

Use the explicit two-holder wallet module described in [VCI-WALLET.md](VCI-WALLET.md)
for batch conformance. A future live ITB batch case must expose a documented batch
profile and assert at least two distinct proofs, the returned holder associations,
and successful use of every copy, alongside the exact session's terminal ITB
success. Do not infer that coverage from issuer metadata or a generic issuance
success alone.

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
The pinned inventory was checked on 2026-11-19 and retains the deployed
`[Wallet SUT]` qualifiers. CS-07 explicitly selects its visible `User` actor
within the configured statement. Newly deployed cases are not selected automatically.

On a fresh Android emulator with Google Play services and initialized Chrome
that supports Digital Credentials get and create, build the Preview APKs from the
same checkout as the coordinator. Set the variables above and select the explicit
ADB serial.

```bash
./gradlew :waltid-services:waltid-openid4vp-conformance-runners:installPlaywrightBrowsers -Pplaywright.browser=chromium
./gradlew :waltid-applications:waltid-wallet-demo-compose:androidApp:assemblePreviewDebug \
  :waltid-applications:waltid-wallet-demo-compose:androidApp:assemblePreviewDebugAndroidTest \
  -PenableAndroidBuild=true -PwalletSigningProtectionMode=disabled
scripts/itb/android-demo.sh unattended <emulator-serial>
```

The script installs Preview and instrumentation, creates temporary ADB forwards,
and removes the forwards when the run ends. Reserve the test device for this run.
The installation retains its test data for inspection. Use a fresh emulator or
uninstall that run's disposable Preview package before reuse.

With no selector the app runs 21 unattended cases. `ITB_CASES` accepts
comma-separated `suite/case` selectors such as `cs02v1/tc_vp_002`; bare IDs work
when unique. The catalogue schedules each presentation's required issuance first.
A presentation uses that exact newly stored credential and has no ITB session if
its prerequisite fails. Select the six payments using the
[attended guide](ITB-NATIVE.md).

Running `./gradlew :waltid-services:waltid-openid4vp-conformance-runners:itbWallet`
without a device bridge selects the JVM protocol diagnostics. This driver uses a
fresh in-memory wallet and excludes the browser-only issuance case and the two revocation cases that require app status observations. It can
diagnose a protocol failure separately from app or browser behavior.

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

The public login form gets 30 seconds to appear, then one reload and up to 60
seconds more before credentials are entered. The runner waits up to 60 seconds
for each selected-system control. Ordinary DOM steps retain their 15-second
limit. Login is submitted once. Setup failures expose the preparation-step
code and leave initialized case reports as `NOT_RUN`.

The bridge authenticates to the portal and selects the exact statement, suite
and case in **Interactive execution** mode. Each case starts from a fresh portal
document, so prior dialogs and asynchronously updated session lists cannot
select another interaction. For URL-based presentations it downloads the owned
session's **Credential QR code** and decodes only QR format. This uses the actual
wallet invocation even when ITB's separately generated request text contains a
false EAN/UPC decode. It never falls back to that text after a QR error. The
existing case-specific invocation-scheme check and wallet verification still apply.
QR downloads are limited to 1 MiB and image dimensions to 2048 by 2048 pixels.
Offers and scripts use short inline values or downloads from the exact labelled
row. The portal exposes Copy instead of Download for short inline values; the
bridge never accesses the clipboard. It reads declarative
DC API inputs without executing supplied JavaScript. Downloads are removed immediately after reading. For DC API cases, the app
driver uses Chrome at the counterpart's origin and the Android credential picker.
The unattended emulator confirms Chrome's disclosure prompt and Android's
sharing sheet for the configured ITB host. In attended mode the operator
confirms those prompts.
The full matrix starts with VCI-006 and `tc15`, so Android's system picker has
only the prerequisite PID when it selects credentials for `tc15`.
For CS-07 issuance, the wallet uses a Custom Tab for issuer authorization so the
original provider request can finish in Chrome. The driver requires the app's new
PID, the browser's create response and an `issued` result from the same offer's
authenticated status endpoint. The SDK diagnostics use their own in-memory
wallet and interpret authorization redirects locally.

The demo app uses its configured verifier trust anchors, which already include
the EUDI PID Issuer CA 02 recorded in [the fixture provenance](../src/main/resources/itb/README.md).
`ITB_X509_TRUST_ANCHORS` optionally overrides the pinned CA on the SDK diagnostic
drivers.

The **WeBuild ITB live wallet cases** workflow is explicitly dispatched by an
operator because it uses a shared external tenant. Runs share one concurrency
group so their tenant sessions do not overlap. The six payment cases require
local operator-assisted execution. The offline profile checks run separately on
relevant PRs.

The portal bridge gives a started, owned session up to 45 seconds to display its
wallet interaction and reports the portal's generic execution error separately.
It never clicks Start again or creates a replacement session after an uncertain
start. A JVM diagnostic payment that reaches the SCA callback is reported as
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
inbound tunnels or coordinated enterprise checkout. ITB results are always strict;
`CONFORMANCE_ALLOW_FAILURE` does not change their outcomes.

The live workflow uses a read-only GitHub token, does not persist checkout
credentials or write Gradle caches, and retains only sanitized reports for 14 days.
Dispatch is available from the default branch. Its
`cases` input can select an unattended subset, such as `tc_vp_002` with its
issuance prerequisite. An empty input runs all 21 unattended app cases. An operator-required
case is rejected before any tenant session starts. The checked-in workflow
alone is not a successful hosted run.

Live runs remain manual after assessing the repository's `ci:conformance` label
convention. That label controls eligibility; it does not authorize ITB secrets for
a particular PR revision. A future live label lane must reject forks, authorize an
immutable head, recheck the label and current head before secrets, require renewed
authorization after pushes, and serialize with this lane. Local operators must also
check tenant idleness and reserve it while running; GitHub concurrency cannot lock
local sessions. Do not run PR code with secrets under an unguarded
`pull_request_target` workflow.

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
These local requirements remain pinned independently of the live catalogue.
The live CS-02 suite labels VP-007 as an mdoc interoperability extension outside
the normative profile. The
[CS-02 v1.2 assessment](ITB-CS02-COVERAGE.md) maps the full profile and newly
deployed cases to supported coverage and explicit gaps, including missing registrar
data, registration trust and physical invocation/consent. The selected cases do not
establish full coverage of the updated profile.

The [public ITB base-protocol inventory](https://github.com/webuild-consortium/wp4-interop-test-bed/tree/9cd45c78483766e7bc60d9028def9103214a4529/tests/base-protocols)
at `9cd45c78483766e7bc60d9028def9103214a4529` contains eight issuance and seven
presentation holder cases. Its `test-suite12.xml` is an issuance suite, not TS12
payment testing. The authenticated tenant catalogue differs from that snapshot.
Do not equate the eleven local profile checks with the deployed cases.

### Deployed catalogue

The authenticated Walt.id catalogue was inspected on 2026-11-19.
The [case manifest](../src/main/resources/itb/wallet-catalogue.json) records the
actual suite/case identifiers exposed by the portal's REST API configuration.
The suite metadata versions below are independent of the per-case version in each
execution report and of the normative specification version.

| Target | Deployed suite ID | Cases | Suite metadata version |
| --- | --- | --- | --- |
| CS-01 | `cs01v1` | VCI-001/002/003/005/006/007/008 (7) | 1.1 |
| CS-02 | `cs02v1` | VP-001/002/003/007/011/012/013 (7) | 1.3 |
| CS-07 | `cts07` | `tc15` presentation and `tc16` issuance (2) | 1.0 |
| CS-10 | `cs10-test-suite` | RV-ISS-01/02 (2) | 1.0 |
| CS-12 / TS12 | `ts12` | 3 issuance, 3 payment, 3 DC API payment (9) | 1.0 |

### Follow-up coverage

| Target | Evidence and remaining work |
| --- | --- |
| CS-01 | Seven authorization-code and pre-authorized-code issuance cases cover SD-JWT and mdoc. Each offer uses one holder. |
| CS-02 | Seven presentation cases cover signed requests and the deployed invocation variants. The base-suite VP-004 transaction-data case is disabled. See the [profile assessment](ITB-CS02-COVERAGE.md) for registration and consent gaps. |
| CS-04 | The runner supplies synthetic key attestation for issuance. Provider-backed WIA and KA, verification, and the unit lifecycle belong to [WAL-1060](https://linear.app/walt-new/issue/WAL-1060). |
| CS-06 | [CS-06 v1.1](https://github.com/webuild-consortium/wp4-architecture/pull/287) assigns certificate issuance to the RP and certificate provider. Wallet-side access-certificate validation needs [WAL-1485](https://linear.app/walt-new/issue/WAL-1485) and ITB trust configuration. The 13 Trust Framework wallet cases need separate qualification, including VP-004 transaction-data binding. |
| CS-07 | The app path drives `tc15` through Chrome presentation and `tc16` through Chrome issuance. It verifies the browser response, the new stored PID and the issuer status for that offer. In a control, closing the instructions alone yielded ITB `SUCCESS`. |
| CS-10 | RV-ISS-01 redeems both offers in one session and checks that the two stored SD-JWTs have one status-list URI, different indices and fresh clear status. RV-ISS-02 requires a fresh `INVALID` result, the SDK's projection of Token Status List value 1. The app run records each card's visible label before and after Check status and requires a new displayed check time before confirming the portal prompt. |
| CS-12 | The local SCA matrix covers the updated SCA-User credential shape. The [native guide](ITB-NATIVE.md) records the earlier attended runs and the outstanding RP-permission and deployment checks. |
| CS-13 | The inspected statements expose no dedicated [intermediary wallet suite](https://github.com/webuild-consortium/wp4-architecture/blob/d6603effe714411d5dd7e9542d4d7b7d9fe11426/conformance-specs/cs-13-intermediary-services-preflight.md). Coverage needs an intermediary counterpart and the registration material for the wallet to authenticate the intermediary and identify the represented relying party. |

Export the portal's **Conformance Statement Report** after the target suites have
passing sessions. Check the statement's coverage and keep the source SHA and
session IDs from the runner reports with it. The portal statement aggregates
tenant history. See the [WE BUILD submission guide](https://github.com/webuild-consortium/wp4-interop-test-bed/blob/d7f5df119c3bf9860e28d892d3fb2eaf0be03c93/docs/conformance-overview.md).

### Verified portal and REST boundary

Interactive portal startup is required by the deployed DC API cases: REST
background startup completes their instruction steps before the wallet can act.
The runner therefore uses the interactive execution path for all selected cases and
uses REST only for authenticated status, terminal XML reports and cleanup. The
hyphenated `ITB-API-KEY` header is required by the deployment. XML report requests
send only `Accept: application/xml`; a combined JSON/XML header is rejected.

The REST reports omit the offers and request context. The bridge reads these
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
