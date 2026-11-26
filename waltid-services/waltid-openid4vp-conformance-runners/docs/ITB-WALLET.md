# WeBuild ITB wallet runs

The runner selects 27 core Wallet SUT cases across CS-01, CS-02, CS-07, CS-10 and TS12.
OneSpan and Reconi reference issuance cases are available by explicit selection.
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

The live app accepts one copy per offer with one holder. The Android driver checks the issuance receipt and one newly stored credential. The SDK diagnostic also checks that no deferred transaction remains. Batch qualification requires distinct proofs and holder bindings and a presentation of each copy.

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
The pinned inventory was checked on 2026-11-26 and retains the deployed
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

In unattended mode, the default selection runs 21 core cases. `ITB_CASES` accepts
comma-separated `suite/case` selectors such as `cs02v1/tc_vp_002`; bare IDs work
when unique. The catalogue schedules each presentation's required issuance first.
A presentation uses that exact newly stored credential and has no ITB session if
its prerequisite fails. Select the six payments using the
[attended guide](ITB-NATIVE.md).

Select a reference issuance case with its qualified ID in `ITB_CASES` locally
or `cases` in the live workflow. These cases use the Android Preview app and
the same raw offer field as core issuance. Reconi's authorization flow opens
Chrome on the device. For `recweb_issue_02` the runner also reads the separate
`Transaction Code` field from the same dialog.

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
records each prepared session ID before starting its test steps. After the wallet
and portal steps, it retries a transient status or report read up to twice
within the case timeout. Cleanup stops
only that owned session. Before preparing a case, the runner gives the Android
bridge five seconds to acknowledge a ping. When the channel closes or the probe
fails, the runner records the remaining cases as `NOT_RUN` with
`device_connection_closed`. A stopped ITB session can
have `UNDEFINED` rather than `FAILURE`; the separate wallet outcome is preserved.
Reports contain identities, timestamps and error types/codes. A stalled test
definition read appears as `test_definition`. A failed HTTP response for that
read appears as `test_definition_http_<status>`. The report excludes response
bodies, credentials, browser state and ITB service logs.

The public login form gets 30 seconds to appear, then one reload and up to 60
seconds more before credentials are entered. The runner waits up to 60 seconds
for each selected-system control. Ordinary DOM steps retain their 15-second
limit. Login is submitted once. Setup failures expose the preparation-step
code and leave initialized case reports as `NOT_RUN`.

The bridge authenticates to the portal and selects the exact statement, suite
and case in **Interactive execution** mode. Each case starts from a fresh portal
document, so prior dialogs and asynchronously updated session lists cannot
select another interaction. The bridge reads the unique `VCI request` or
`VP request` field from the owned dialog. It reads the complete inline text
when the portal shows Copy and downloads longer values once. The URI retains
its original authority and query encoding. Each invocation case checks its
expected scheme before opening the wallet. DC API cases download the instruction
script and read its declarative inputs. Downloads are limited to 1 MiB and
removed after reading.

For DC API cases, the app
driver uses Chrome at the counterpart's origin and the Android credential picker.
Both demo modes confirm Chrome's disclosure prompt and Android's sharing
sheet when the displayed origin matches the configured ITB host. The driver checks
the payee, currency and amount in the wallet and submits the review if allowed.
The operator supplies the native biometric approval.
The unattended driver declines Chrome's separate notification onboarding if it
appears during the credential flow.
The hosted emulator enables Chrome's Digital Credentials issuance feature. This
is required on the image's Chrome 145 and enabled by default from Chrome 155.
[Chrome issuance documentation](https://developer.chrome.com/blog/digital-credentials-api-issuance)
The full matrix starts with VCI-006 and `tc15`, so Android's system picker has
only the prerequisite PID when it selects credentials for `tc15`.
VP-007 runs after VCI-008 and before VCI-005 so the wallet's single-match query
selects the mdoc issued by its prerequisite. Each TS12 issuance immediately
precedes its two payments so they use the freshly issued credential.
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
relevant PRs. The Compose Android device job also runs the Preview driver’s
blocked-payment regression without ITB credentials.

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
`cases` input can select core and reference cases, such as `tc_vp_002` with its
issuance prerequisite. An empty input runs the 21 core unattended app cases. An operator-required
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

On 2026-11-26 the authenticated **WE BUILD LSP** catalogue listed 87 cases in
16 suites. The four walt.id conformance statements contain 84 of them. The
[case manifest](../src/main/resources/itb/wallet-catalogue.json) selects 31
wallet cases by suite and case ID. These comprise 21 core unattended cases,
four opt-in reference issuer cases and six attended payments. The [ledger](#wallet-case-ledger)
accounts for the other 42 wallet cases and the 14 cases outside wallet coverage.
The suite metadata versions below are independent of the per-case version in each
execution report and of the normative specification version.

| Target | Deployed suite ID | Cases | Suite metadata version |
| --- | --- | --- | --- |
| CS-01 | `cs01v1` | VCI-001/002/003/005/006/007/008 (7) | 1.5 |
| CS-02 | `cs02v1` | VP-001/002/003/007/011/012/013 (7) | 1.4 |
| CS-07 | `cts07` | `tc15` presentation and `tc16` issuance (2) | 1.0 |
| CS-10 | `cs10-test-suite` | RV-ISS-01/02 (2) | 1.2 |
| CS-12 / TS12 | `ts12` | 3 issuance, 3 payment, 3 DC API payment (9) | 1.0 |
| OneSpan, opt-in | `onespan_com` | `onespan_vci_01`, `onespan_vci_03` | 1.0 |
| Reconi, opt-in | `rec` | `recweb_issue_01`, `recweb_issue_02` | 1.0 |

### Follow-up coverage

| Target | Evidence and remaining work |
| --- | --- |
| CS-01 | Seven authorization-code and pre-authorized-code issuance cases cover SD-JWT and mdoc. Each offer uses one holder. |
| CS-02 | Seven presentation cases cover signed requests and the deployed invocation variants. The base-suite VP-004 transaction-data case is disabled. See the [profile assessment](ITB-CS02-COVERAGE.md) for registration and consent gaps. |
| CS-04 | No dedicated wallet case is deployed. The runner uses synthetic key attestation. Qualifying provider-backed wallet instance and key attestations and the unit lifecycle requires an interoperable provider and issuer. [WAL-1060](https://linear.app/walt-new/issue/WAL-1060) |
| CS-06 | [CS-06 v1.1](https://github.com/webuild-consortium/wp4-architecture/pull/287) assigns certificate issuance to the relying party and certificate provider. The catalogue has no dedicated CS-06 wallet case. The separate Trust Framework suite offers issuance and presentation variants. [WAL-1485](https://linear.app/walt-new/issue/WAL-1485) covers signed presentation-request trust. [WAL-1851](https://linear.app/walt-new/issue/WAL-1851) covers issuer trust on receipt. |
| CS-07 | The app path drives `tc15` through Chrome presentation and `tc16` through Chrome issuance. It verifies the browser response, the new stored PID and the issuer status for that offer. In a control, closing the instructions alone yielded ITB `SUCCESS`. |
| CS-10 | RV-ISS-01 redeems both offers in one session and checks that the two stored SD-JWTs have one status-list URI, different indices and fresh clear status. RV-ISS-02 requires a fresh `INVALID` result, the SDK's projection of Token Status List value 1. The app run records each card's visible label before and after Check status and requires a new displayed check time before confirming the portal prompt. |
| CS-12 | The runner supports all three issuance variants and their six attended payments. See the [device results](#wallet-case-ledger) for the outstanding payment checks. The [native guide](ITB-NATIVE.md#sca-user-audience-compatibility) describes the separate SCA-User relying-party permission question. |
| CS-13 | The inspected statements expose no dedicated [intermediary wallet suite](https://github.com/webuild-consortium/wp4-architecture/blob/d6603effe714411d5dd7e9542d4d7b7d9fe11426/conformance-specs/cs-13-intermediary-services-preflight.md). Coverage needs an intermediary counterpart and the registration material for the wallet to authenticate the intermediary and identify the represented relying party. |

### Wallet case ledger

The runner supports the four reference issuer cases in the [deployed catalogue](#deployed-catalogue)
by explicit selection. The default unattended run omits them.

[WAL-1868](https://linear.app/walt-new/issue/WAL-1868) tracks the 42 wallet cases
outside the runner manifest. ITB listed 41 as enabled on 2026-11-26. Base CS-02
VP-004 was disabled. The table records the evidence from the 2026-11-23 trials
and the next check for each group. The 13 Trust Framework trials used v1.4.
ITB now publishes v1.5. Five of those trials stopped before session startup,
so wallet compatibility for those cases is unknown.

| Suite | Cases | Status | Evidence and next check |
| --- | --- | --- | --- |
| Base CS-02 (`cs02v1`) | `tc_vp_004` | Disabled in ITB | The tenant disables the case. Recheck when ITB enables it with a runnable transaction-data fixture. |
| OneSpan (`onespan_com`) | `onespan_vci_02` | Missing offer in last trial | The authorization-code dialog exposed neither an offer QR nor a usable offer value. The issuer fixture needs to supply an offer. |
| OneSpan (`onespan_com`) | `onespan_vci_04` | Missing code in last trial | The offer requires a transaction code and ITB supplied no code. The dialog needs to provide it. |
| OneSpan (`onespan_com`) | `onespan_vp_01` through `onespan_vp_07` | Trust configuration needed | GET retrieval returned HTTP 200. Wallet validation found no matching trust anchor. The POST-labelled cases (`02`, `05`) also omitted `request_uri_method`, and the DID-labelled cases (`05`, `06`) sent `x509_hash`. Recheck with verified test trust material and fixtures that exercise the stated methods and identifiers. |
| Namirial (`namiral_suite`) | `nam_issue_01`, `nam_issue_02`, `nam_issue_03`, `vci_006`, `vci_007`, `vci_008`, `nam_vp_01`, `nam_vp_02`, `nam_vp_03`, `vp_001`, `vp_002`, `vp_003`, `vp_004`, `vp_007` | Missing input in last trials | All 14 scan dialogs lacked a usable attachment. Sample issuance and presentation dialogs had empty QR and raw values. Recheck once the fixtures supply wallet input. |
| Procivis (`ts01`) | `tc001`, `tc002` | Client authentication failed | The issuer returned HTTP 400 `invalid_client` at token exchange. Recheck the test client's issuer registration and authentication requirements. |
| Procivis (`ts01`) | `tc003`, `tc004` | Certificate binding failed | Both signed requests failed leaf certificate hash binding. Recheck that `x509_hash` matches the DER-encoded leaf in `x5c`. The deployed cases share a title, so the bridge also needs an unambiguous case selector. |
| Trust Framework (`tf-test-suite`) | `tc_vci_001`, `tc_vci_005`, `tc_vci_006`, `tc_vci_007`, `tc_vci_008` | Retest token exchange on v1.5 | The issuer returned HTTP 401 `invalid_client` at token exchange. Confirm the reference issuer's client registration and authentication requirements. Issuer trust on receipt can only be exercised after issuance reaches the wallet. |
| Trust Framework (`tf-test-suite`) | `tc_vp_001`, `tc_vp_002` | Retest request delivery on v1.5 | The sessions exposed no usable wallet input. Recheck with a deployed request attachment. |
| Trust Framework (`tf-test-suite`) | `tc_vp_003`, `tc_vp_004`, `tc_vp_007`, `tc_vp_011`, `tc_vp_012` | Ready to retry on v1.5 | Two attempts timed out before ITB assigned a session ID. Start a fresh session and observe the first wallet interaction. |
| Trust Framework (`tf-test-suite`) | `tc_vp_013` | Verify the anchor and retest on v1.5 | POST returned HTTP 200. The demo wallet found no matching trust anchor. Retry this case first with an independently verified test anchor configured in the wallet. [WAL-1485](https://linear.app/walt-new/issue/WAL-1485) adds a registry option for signed requests. The demo can already use static anchors. |
| QES (`cs03_suite`) | `signatrure_01` | Wallet signing capability needed | The case requests holder consent and a document signature in AdES format. Qualification needs the wallet signing flow and a qualified remote signing provider. [WAL-1847](https://linear.app/walt-new/issue/WAL-1847) |

**Selected payments awaiting a passing result.** On 2026-11-24 all three issuance variants succeeded on a physical Android device.
The wallet reached payment review for `ts12_pay_01`, `ts12_pay_02`, `ts12_pay_03` and
their `ts12_pay_dc_api_01`, `ts12_pay_dc_api_02`, `ts12_pay_dc_api_03`
counterparts. All six recorded “Payment instructions could not be retrieved”
and ITB `UNDEFINED`. Recheck the reference issuer's type metadata, then rerun
both paths for SCA-IBAN, SCA-User and SCA-Card with native signing. The portal
report and device results are on [WAL-1534](https://linear.app/walt-new/issue/WAL-1534).
[Architecture #357](https://github.com/webuild-consortium/wp4-architecture/issues/357)
discusses the credential type identifiers and metadata.

**Other cases in the current domain**

| Suite | Cases | What the case exercises |
| --- | --- | --- |
| Relying Parties (`rfcv3-rp`, `rps-trust`) | `tc01`, `tc02`, `tc03` in each suite | Issuer or verifier with the reference wallet. |
| TS12 (`ts12`) | `w01`, `w02`, `w03` | Issuer or verifier with the WE BUILD headless wallet. |
| Intesi (`generated_suite`) | `webuild_issuer_request_001`, `webuild_issuer_request_002` | Both assert only the issuer's `POST /issuer/request` response. It returned HTTP 404 on 2026-11-24, before any wallet handoff. A wallet case would need an offer and a wallet outcome. |
| BUC6 (`buc6-rp-v1`) | `tc01`, `tc02` | Verifier under test, with the ITB+ wallet and trust disabled or enabled. |
| ITB+ harness (`itb-native-wallet-harness`) | `native-wallet-verifier-observations` | Collects observations from the ITB+ Android wallet during an external verifier flow. |

BUC6 and the ITB+ harness are visible under the verifier role outside the four
walt.id statements. The portal also offers nine statements in a separate
`WE Build L-OLD` domain. The walt.id account can see their names in the
statement picker. Its request for the legacy case catalogue returned HTTP 403.

### Exporting reports

Export the portal's **Conformance Statement Report** after the target suites have
passing sessions. Check the statement's coverage and keep the source SHA and
session IDs from the runner reports with it. The portal statement aggregates
tenant history. See the [WE BUILD submission guide](https://github.com/webuild-consortium/wp4-interop-test-bed/blob/d7f5df119c3bf9860e28d892d3fb2eaf0be03c93/docs/conformance-overview.md).

For one PDF covering all wallet statements, select the `walt.id` system in
**My conformance statements** and use **Download report** above the statement
list. This [conformance overview](https://www.itb.ec.europa.eu/docs/itb-oa/latest/manageConformanceStatements/index.html#conformance-overview-report)
includes the latest portal result for each case, including cases outside the
runner's selection. Hosted and attended executions can contribute on different days.

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
