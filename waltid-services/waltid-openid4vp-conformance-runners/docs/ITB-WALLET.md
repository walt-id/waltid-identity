# WeBuild ITB wallet runs

[WAL-1423](https://linear.app/walt-new/issue/WAL-1423/itb-initial-tests) covers the
21 deployed CS-01, CS-02, CS-07 and TS12 cases. `itbWallet` drives their actual
ITB interactions through the production JVM wallet, using a fresh holder and
an in-memory credential store. `itbTest` provides eleven separate local protocol
checks with synthetic fixtures and mock issuance responses.

Neither entry point proves native platform delivery, consent UX or full EUDI
conformance. The [live baseline](ITB-BASELINE.md) records the exercised cases,
failures, evidence boundary and remaining qualification work.

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

`ITB_REPORT_DIR` selects the report directory; the default is this module's
`build/reports/itb-wallet`. Every selected case appears in `results.json`,
`junit.xml` and `summary.md`, including cases not reached after report initialization. Invalid build identity or
case selection fails before report initialization.
The process returns zero only if every selected case completes successfully in
both the wallet adapter and its own terminal ITB report. Failures, timeouts,
incomplete results and unexecuted cases remain failures in JUnit; none become
expected failures or skips. `adapterInvoked` records entry into the adapter,
not proof that every wallet protocol stage ran.

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

The **WeBuild ITB live wallet cases** workflow runs all 21 deployed cases on
pushes to the two WAL-1423 investigation branches. It remains strict on the
wallet-runner branch; the separate diagnostic branch labels its conditional
results. Runs share one concurrency group so their tenant sessions do not
overlap. The offline profile checks remain a separate PR check.

Configure repository Actions secrets `ITB_ORGANISATION_KEY`, `ITB_USERNAME` and
`ITB_PASSWORD`
and repository variable `ITB_ORGANISATION_ID=20`, following the existing conformance
workflows. A dedicated GitHub environment is not required. Tenant credentials are
injected only into the live test step, not the offline profile checks. As with
other repository secrets, maintainers must review workflow changes that could
access them; the branch trigger does not create a branch-specific secret boundary.

The JVM version and JSON/JUnit/Markdown reporting follow the existing conformance
setup. ITB uses an outbound portal bridge, so it does not need the other suites'
inbound tunnels or coordinated enterprise checkout. Its strict results follow
WAL-1423's explicit requirement; the older suites' `CONFORMANCE_ALLOW_FAILURE`
switch does not change ITB outcomes.

The live workflow uses a read-only GitHub token, does not persist checkout
credentials or write Gradle caches, and retains only sanitized reports for 14 days.
Manual dispatch is also available once the workflow exists on the default
branch. Its `cases` input can select a subset, such as `tc_vp_002` with its
issuance prerequisites; an empty input runs all 21. The checked-in workflow
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

## Coverage and external dependencies

Initial baseline: main at `78e6929fc360729feb45af81697405ca3cf01fa1`, the merge of
[Identity #2168](https://github.com/walt-id/waltid-identity/pull/2168) on 2026-09-21.
Its tree matches the assessed PR head `cd5520ba44c4418fcc25a3b4d29244922ca1f805`.
The four failing local checks below are observed results at that baseline, not an
allowlist: they must pass normally when their product behavior is corrected.

| Area | Local checks | Baseline | Boundary / dependency |
| --- | --- | --- | --- |
| CS-01 | Real authorization-code credential receipt; verify proof signature, audience, nonce and bound client `iss` | 1 fail: absent `iss` | Reported in WAL-495; external [#2246](https://github.com/walt-id/waltid-identity/pull/2246). Starts after authorization; does not qualify PAR, DPoP, browser login, pre-authorized grants or WIA/KA. |
| CS-02 | Accept trusted X.509 signed request; reject absent trust and altered payload | 3 pass | Fresh CA/leaf certificates and actual signature authentication. Does not qualify credential presentation, selective disclosure or native consent. |
| CS-02 | Strict profile rejects unsigned `redirect_uri` request objects and JSON returned by GET `request_uri` | 2 fail: unsigned input accepted | [WAL-896](https://linear.app/walt-new/issue/WAL-896) request-authentication scope; keep failures visible until the owning implementation enforces the profile. |
| CS-07 | Resolve an authentic signed DC API request and bind its platform-origin audience | 1 fail: signed protocol unsupported | External WAL-896 / [#2141](https://github.com/walt-id/waltid-identity/pull/2141). A DID fixture isolates shared protocol dispatch; native and X.509 DC API qualification remain separate. |
| CS-07 encryption | Independently decrypt a real wallet response with Nimbus; reject an encryption JWK missing `alg` | 2 pass | Exercises encryption independently of signed dispatch. Independent regression coverage; the fresh deployed VP002 flow also passes (see live baseline). |
| TS-12 transaction binding | Present a stored SD-JWT with nested payment data; verify holder signature, audience, nonce and independently calculated SHA-256 hash | 1 pass | Narrows [WAL-1295](https://linear.app/walt-new/issue/WAL-1295) to remaining UI, verifier and native E2E work. Does not establish normative SCA support. |

OpenID4VCI makes the proof `iss` claim optional for this bound-grant case. The
issuance assertion preserves the historical stricter interoperability regression;
all five deployed SD-JWT issuance cases passed without this fix, so it is not a
confirmed blocker for the current 21-case matrix. It does not require every
conforming issuer to reject an absent claim. See
[OpenID4VCI JWT proofs](https://openid.net/specs/openid-4-verifiable-credential-issuance-1_0.html#name-jwt-proof-type).

Other open PRs remain external dependencies. Do not copy their production fixes
into this baseline or disable affected cases. In particular,
[#2222](https://github.com/walt-id/waltid-identity/pull/2222) remains separate
identity/recovery work; these local checks do not require it. Refresh from main
and rerun against the actual merged dependency revisions as they become available.
Merging an owning PR alone does not prove that all profile assertions pass.

## Pinned requirements and outstanding acceptance work

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
Do not equate these ten local checks with the deployed cases.

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
issuer offer or verifier request and attributes that to an ITB case. Full results
and the remaining external dependencies are in [ITB-BASELINE.md](ITB-BASELINE.md).

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

The reference issuer's [credential metadata](https://dss.aegean.gr/rfc-issuer/.well-known/openid-credential-issuer)
and [authorization-server metadata](https://dss.aegean.gr/rfc-issuer/.well-known/oauth-authorization-server)
observed on 2026-09-21 advertise JWT/ES256 proofs, mandatory PAR, token endpoint
authentication `none` and anonymous pre-authorized access; they do not advertise
WIA/KA requirements. [WAL-1060](https://linear.app/walt-new/issue/WAL-1060) is thus
related to the current CS-01 profile's WUA requirement, but is not established as
a blocker for those deployed reference flows. This metadata is not proof of the
tenant's selected cases or of current CS-01 compliance. Likewise, broader
[WAL-1326](https://linear.app/walt-new/issue/WAL-1326) trust-list work is conditional
on the selected trust model, beyond the explicit request anchors checked here.
