# WeBuild ITB wallet runs

[WAL-1423](https://linear.app/walt-new/issue/WAL-1423/itb-initial-tests) covers the
21 deployed CS-01, CS-02, CS-07 and TS12 cases. `itbWallet` drives their actual
ITB interactions through the production JVM wallet, using a fresh holder and
an in-memory credential store. `itbTest` provides ten separate local protocol
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
| `ITB_SYSTEM_KEY` | System API key from the portal's REST configuration |
| `ITB_BASE_ACTOR_KEY` | Base Protocols actor API key |
| `ITB_DOMAIN_ACTOR_KEY` | Domain Specific actor API key |
| `ITB_PAYMENT_ACTOR_KEY` | Payment use cases actor API key |
| `ITB_USERNAME`, `ITB_PASSWORD` | Existing portal account |
| `ITB_ORGANISATION_ID` | Portal organisation ID (`20` for the inspected Walt.id tenant) |
| `ITB_BUILD_REVISION` | Tested 40-character commit SHA; append `+dirty` for an uncommitted tree |

Portal UI IDs are not API keys. Actor keys are required only for the selected
specifications. The deployment is pinned by the case manifest to
`https://dev-i4mlab.aegean.gr/itb`.

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
stops only sessions returned by its own start call. A stopped ITB session can
have `UNDEFINED` rather than `FAILURE`; the separate wallet outcome is preserved.
Reports contain identities, timestamps and error types/codes, not credentials,
raw protocol messages, browser state or ITB service logs.

The bridge authenticates to the portal, filters by the exact REST session ID,
and downloads that session's offer/request/script input. It reads declarative
DC API inputs without executing supplied JavaScript. Downloads are removed
immediately after reading. The deployed authorization-code issuer returns a
direct redirect; the adapter validates its destination, state and code before
continuing through production wallet issuance. Interactive issuer login is not
implemented by this reference-environment runner.

The ephemeral test wallet trusts the independently sourced EUDI PID Issuer CA 02
recorded in [the fixture provenance](../src/main/resources/itb/README.md).
`ITB_X509_TRUST_ANCHORS` optionally replaces it with an operator-supplied PEM
file. Request `x5c` chains are never automatically trusted.

The **WeBuild ITB live wallet cases** workflow is manually dispatched and strict.
Configure matching repository secrets for the seven key/account variables and
repository variable `ITB_ORGANISATION_ID`. It runs selected cases and uploads
only sanitized reports. It does not expose tenant credentials to pull-request
jobs. Hosted execution still needs CI credentials and workflow qualification;
the checked-in workflow alone is not a successful hosted run.

## Local profile checks

```bash
./gradlew :waltid-services:waltid-openid4vp-conformance-runners:itbTest
```

Any assertion failure fails the task. An explicit local diagnostic run can keep
collecting reports with `-PitbAllowFailures=true`; failures remain in JUnit.
Results are under `build/test-results/itbTest` and `build/reports/tests/itbTest`.

The **WeBuild ITB profile checks** workflow runs the runner's unit tests and these
profile assertions on relevant PR changes. PR runs are strict; a manual run may
explicitly disable enforcement. Compilation, setup and incomplete report
collection always fail. The report collector checks the exact ten-check
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

### Verified REST and interaction boundary

An authenticated check against the deployment on 2026-09-21 confirmed that
`ITB-API-KEY` works for starting sessions and retrieving their status and reports.
The deployment's Swagger UI instead sends `ITB_API_KEY`, which returned HTTP 401
with "Needs API key header." Use the documented hyphenated header.

For a REST-started VCI-006 session, `withReports` and `withLogs` exposed execution
steps and the supporting service's session ID, but omitted the credential offer
and interaction context. The authenticated portal's **My test sessions → View
pending interaction → VCI request** exposed the offer for that same session.
Consequently, the deployed workflow needs a browser interaction bridge or a
test-suite interaction handler in addition to REST orchestration. Do not create
a separate issuer offer and report it as the ITB session's wallet interaction.

The complete development baseline and remaining blockers are documented in
[ITB-BASELINE.md](ITB-BASELINE.md). Those runs exercised the production adapter
with a Cua-driven portal bridge; they do not qualify the standalone Playwright
CLI or hosted workflow. The combined CLI/CI entry points must still be exercised
on the final build before this draft is considered ready.

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
