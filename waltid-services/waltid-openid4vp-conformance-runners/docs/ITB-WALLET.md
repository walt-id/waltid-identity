# WeBuild wallet profile checks

[WAL-1423](https://linear.app/walt-new/issue/WAL-1423/itb-initial-tests) targets
CS-01, CS-02, CS-07 and TS-12. The `itbTest` task provides ten local checks of
selected wallet protocol requirements and reported interoperability failures.
It exercises production wallet code with synthetic credentials, mock issuance
HTTP responses and fresh holder/verifier keys. It requires no ITB account,
remote services, mobile device or enterprise checkout.

These checks are **not hosted ITB test executions or a conformance certificate**.
The full ticket remains open until the deployed suite and native requirements
have been qualified. In particular, the payment fixture is an ordinary SD-JWT
bound to nested payment transaction data, not a normative SCA attestation.

## Run and inspect

From the Identity repository root, with JDK 21:

```bash
./gradlew :waltid-services:waltid-openid4vp-conformance-runners:itbTest
```

The default is strict: any failing assertion fails the task. To collect the
baseline while product dependencies are outstanding:

```bash
./gradlew :waltid-services:waltid-openid4vp-conformance-runners:itbTest -PitbAllowFailures=true
python3 scripts/itb/report.py waltid-services/waltid-openid4vp-conformance-runners/build/test-results/itbTest
```

`itbAllowFailures` accepts only `true` or `false`. Report-only mode retains real
JUnit failures; there are no expected-failure annotations, dependency-based
skips or assertions that accept broken behavior. Reports are regenerated on
each invocation. The source set is separate from the ordinary `test` task.

Results under this module:

- `build/test-results/itbTest/TEST-*.xml`: individual JUnit outcomes.
- `build/reports/tests/itbTest/index.html`: browsable failures and stack traces.

The **WeBuild ITB profile checks** workflow runs on relevant pull-request changes
in report-only mode, adds every result to the job summary and uploads XML/HTML
artifacts. Its manual `enforce` input enables strict assertions once the workflow
is available on the default branch. Compilation, setup and incomplete report
collection still fail the job in report-only mode. A successful job with failed
profile assertions is not a passing profile result. This workflow is separate
from the existing OpenID Foundation conformance workflow and CI gate.

The report collector rejects incomplete class/count inventories, duplicate
results and skipped checks. Update `scripts/itb/report.py`'s inventory when
intentionally changing coverage. Verify collection with:

```bash
python3 -m unittest discover -s scripts/itb -p 'test_*.py'
```

## Coverage and external dependencies

Initial baseline: main at `78e6929fc360729feb45af81697405ca3cf01fa1`, the merge of
[Identity #2168](https://github.com/walt-id/waltid-identity/pull/2168) on 2026-09-21.
Its tree matches the assessed PR head `cd5520ba44c4418fcc25a3b4d29244922ca1f805`.
The four failing checks below are observed results at that baseline, not an
allowlist: they must pass normally when their product behavior is corrected.

| Area | Local checks | Baseline | Boundary / dependency |
| --- | --- | --- | --- |
| CS-01 | Real authorization-code credential receipt; verify proof signature, audience, nonce and bound client `iss` | 1 fail: absent `iss` | Reported in WAL-495; external [#2246](https://github.com/walt-id/waltid-identity/pull/2246). Starts after authorization; does not qualify PAR, DPoP, browser login, pre-authorized grants or WIA/KA. |
| CS-02 | Accept trusted X.509 signed request; reject absent trust and altered payload | 3 pass | Fresh CA/leaf certificates and actual signature authentication. Does not qualify credential presentation, selective disclosure or native consent. |
| CS-02 | Strict profile rejects unsigned `redirect_uri` request objects and JSON returned by GET `request_uri` | 2 fail: unsigned input accepted | [WAL-896](https://linear.app/walt-new/issue/WAL-896) request-authentication scope; keep failures visible until the owning implementation enforces the profile. |
| CS-07 | Resolve an authentic signed DC API request and bind its platform-origin audience | 1 fail: signed protocol unsupported | External WAL-896 / [#2141](https://github.com/walt-id/waltid-identity/pull/2141). A DID fixture isolates shared protocol dispatch; native and X.509 DC API qualification remain separate. |
| CS-07 encryption | Independently decrypt a real wallet response with Nimbus; reject an encryption JWK missing `alg` | 2 pass | Exercises encryption independently of signed dispatch. Diagnostic coverage for the reported VP002 error, not a reproduction of its unavailable original request. |
| TS-12 transaction binding | Present a stored SD-JWT with nested payment data; verify holder signature, audience, nonce and independently calculated SHA-256 hash | 1 pass | Narrows [WAL-1295](https://linear.app/walt-new/issue/WAL-1295) to remaining UI, verifier and native E2E work. Does not establish normative SCA support. |

OpenID4VCI makes the proof `iss` claim optional for this bound-grant case. The
issuance assertion pins the stricter observed ITB interoperability requirement;
it does not require every conforming issuer to reject an absent claim. See
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
payment testing. Complete CS-07/payment executable definitions and the current
tenant catalogue were not verified. Do not equate these ten local checks with
those fifteen public cases or with all four target profiles.

Remaining work to close WAL-1423:

1. Record the authenticated tenant/system/actor, deployed suite versions and case
   IDs, required fixtures and mandatory/optional applicability. Confirm that the
   ticket's TS-12 maps to current CS-12. Resolve public title/script discrepancies
   against executable definitions. Retain credentials in the approved secret
   store, never in source or reports.
2. Add a hosted runner that starts selected sessions, drives the wallet through
   required interactions, polls to terminal results with bounded timeouts, and
   exports case outcomes with suite/build/session identities. Missing, timed-out,
   cancelled and failed cases must remain distinct from passes. Starting sessions
   or checking REST health alone is insufficient.
3. Qualify the selected CS-01 grant/proof/format matrix and CS-02 request retrieval,
   signed trust, DCQL, holder binding, disclosure and encrypted response cases.
   Obtain the original or fresh sanitized VP002 encryption metadata before
   assigning its root cause.
4. Qualify CS-07 Android/iOS presentation and issuance on supported native paths:
   signed requests, expected-origin rejection, encrypted delivery, canonical
   `openid4vci-v1`, consent and cancellation. Record platform limitations and the
   optional cross-device capability decision. Proximity work in #2168 does not
   itself supply Digital Credentials API evidence.
5. Qualify normative SCA credential recognition/metadata, schema and UI labels,
   single-attestation constraints, KB-JWT `jti`/`amr`/`response_mode`, real factor
   evidence and denied/cancelled authentication. [WAL-1417](https://linear.app/walt-new/issue/WAL-1417)
   covers TS12 UI metadata; WAL-1295 explicitly excludes normative SCA attestation
   format. Do not synthesize authentication claims from a successful generic
   presentation. Assign remaining SCA implementation ownership after case mapping.
6. Rerun all required cases against merged dependencies and exact application
   builds. Enable enforcement only when the intended matrix is demonstrated;
   report-only CI and a Done/Approved dependency ticket are insufficient evidence.

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
