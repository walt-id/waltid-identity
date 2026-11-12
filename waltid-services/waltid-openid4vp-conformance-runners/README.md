# OpenID4VC Conformance Tests

Conformance runners for OpenID4VCI and OpenID4VP against the
[OpenID Foundation Conformance Suite](https://www.certification.openid.net/).

This module contains runners for several protocol roles. Each role has different
service dependencies, credentials, callback URLs, and test plans. Start with the
role-specific guide rather than assuming that one setup applies to every runner.

## Start Here

| Role | Use this guide |
|------|----------------|
| OpenID4VCI issuer / OSS issuer2 | [docs/VCI-ISSUER.md](docs/VCI-ISSUER.md) |
| OpenID4VP verifier | [docs/VP-VERIFIER.md](docs/VP-VERIFIER.md) |
| OpenID4VCI wallet | [docs/VCI-WALLET.md](docs/VCI-WALLET.md) |
| OpenID4VP wallet | [docs/VP-WALLET.md](docs/VP-WALLET.md) |
| WE BUILD wallet profile checks and live execution | [docs/ITB-WALLET.md](docs/ITB-WALLET.md) |

The OSS issuer2 workflow is intentionally documented only in
[docs/VCI-ISSUER.md](docs/VCI-ISSUER.md). It covers the issuer2 configuration,
Keycloak callback, local Docker Compose stack, wrapper script, test matrix,
HAIP certificates, browser automation, reports, and cleanup.

## Test Profiles

| Interface | Baseline | HAIP | Key Difference |
|-----------|----------|------|----------------|
| VCI Issuer | Base `vci` issuer matrix | `vci_haip` issuer matrix | HAIP uses authorization code, DPoP, client attestation, and x509-backed credentials |
| VP Verifier | `x509_san_dns` | `x509_hash` | Client ID scheme |

Baseline profiles provide functional protocol coverage. HAIP profiles target
[HAIP 1.0](https://openid.net/specs/openid4vc-high-assurance-interoperability-profile-1_0-final.html)
requirements.

## Project Structure

```text
waltid-openid4vp-conformance-runners/
├── src/main/kotlin/.../testplans/
│   ├── IssuerConformanceTestRunner.kt
│   ├── VerifierConformanceTestRunner.kt
│   └── plans/
│       ├── vci/issuer/          # VCI Issuer test plans
│       ├── vci/wallet/          # VCI Wallet test plans
│       └── vp/verifier/         # VP Verifier test plans
├── src/test/kotlin/.../
│   ├── IssuerConformanceTests.kt
│   ├── VerifierConformanceTests.kt
│   ├── VciWalletConformanceTests.kt
│   └── VpWalletConformanceTests.kt
├── docs/
│   ├── VCI-ISSUER.md
│   ├── VP-VERIFIER.md
│   ├── VCI-WALLET.md
│   └── VP-WALLET.md
└── run-issuer-conformance-local.sh
```

## Documentation

| Document | Description |
|----------|-------------|
| [docs/VCI-ISSUER.md](docs/VCI-ISSUER.md) | OSS issuer2 setup, execution, matrix, HAIP, and troubleshooting |
| [docs/VP-VERIFIER.md](docs/VP-VERIFIER.md) | VP verifier setup, status, and troubleshooting |
| [docs/VCI-WALLET.md](docs/VCI-WALLET.md) | VCI wallet conformance documentation |
| [docs/VP-WALLET.md](docs/VP-WALLET.md) | VP wallet conformance documentation |
| [TEST-PLANS-AND-PROFILES.md](TEST-PLANS-AND-PROFILES.md) | Test-plan inventory and profile reference |

`./gradlew test` only executes the test task. It does not universally provision
remote services or local conformance infrastructure. Follow the relevant
role-specific guide for a runnable environment and its intended command.

## CI summaries

The reusable OSS Gradle workflow
(`.github/workflows/gradle.yml`) publishes these role sections to GitHub Actions
job summaries, using the existing headings and table shapes. The verifier,
OpenID4VP wallet and OpenID4VCI wallet each publish their own section from a
parallel job. The issuer section is published by `issuer-conformance-results`
after merging the two parallel proof jobs:

1. OpenID4VP Verifier
2. OpenID4VP Wallet
3. OpenID4VCI Wallet
4. OpenID4VCI Issuer

GitHub-hosted CI points the wallet suites at `conformance.waltid.cloud`. Each
role job exposes only its own listener through a Cloudflare tunnel: the verifier
on port 7003, the OpenID4VP adapter on port 7006, and the OpenID4VCI adapter on
port 7007. Those suites therefore run live, and one role cannot cancel the
others. The OpenID4VCI issuer matrix still only runs when its dedicated workflow
inputs and issuer URL are configured.

When a role produced `summary.md`, that file is appended. Test names are
compacted to the module suffix and variant values so the table stays readable.
When a role did not write a report, the workflow still writes the same heading,
totals, and table, with a note that no results were produced.

Required wallet/verifier jobs validate their structured `results.json`: missing,
malformed, empty, unavailable-suite and all-skipped reports fail the job. At least
one accepted case with a suite test ID must execute, and every other case must
be accepted or explicitly skipped as inapplicable. Each role clears its previous
report before running. CI sets `CONFORMANCE_REQUIRED=true`, so suite unavailability
also produces a JUnit failure. Optional local suite-availability skips remain possible,
but their report is labelled `unavailable` and is not accepted as conformance
evidence.

Executed OpenID4VP verifier/wallet, OpenID4VCI wallet, and OpenID4VCI issuer
failures fail their respective jobs. Issuer batch and key-attestation coverage
remain required independently for both proof modes. The aggregate issuer artifact
retains the name `issuer-conformance-basic-haip-batch` and its existing JSON format.
Wallet and verifier aggregation retains `openid-conformance-summaries` and
`junit-conformance`. Failed role jobs, missing reports or requested JUnit artifacts
fail aggregation after the files that did arrive are uploaded.

Artifacts (when a role actually runs):

```text
build/reports/openid-conformance/
  vp-verifier/summary.md
  vp-verifier/results.json
  vci-issuer/summary.md
  vci-issuer/results.json
  vci-wallet/summary.md
  vp-wallet/summary.md
```

For the VCI issuer matrix, `OPENID4VCI_CONFORMANCE_STRICT` and
`OPENID4VCI_CONFORMANCE_CERTIFICATION_MODE` still apply for local exploration
of variant-level strictness. Unaccepted issuer modules still fail the run.
Certification mode always remains strict. Issuer reports default to
`build/reports/openid-conformance/vci-issuer` (override with
`OPENID4VCI_CONFORMANCE_REPORT_DIR`).

## OSS issuer deferred issuance

Deferred coverage uses OSS issuer-api2 from `feat/issuer2-deferred-issuance` and the
pinned suite `db1080a` (release-v5.2.3 / version 5.2.4). It runs separately from the
immediate suite in both `proof-jwt` and `proof-attestation` Actions jobs.

The added matrix has 12 issuer-initiated variants per proof mode: Basic VCI with
both grants, HAIP with authorization code, SD-JWT and mdoc, and plain/encrypted
requests and responses. Only `oid4vci-1_0-issuer-happy-flow-skip-notification` and
`oid4vci-1_0-issuer-batch-issuance` execute, for 24 modules per proof mode. Deferred
runs use the generic VCI issuer plan with the matching Basic/HAIP profile; this
makes both single-request and encrypted HAIP batch modules available. The immediate
run still uses the dedicated HAIP plan and reports its encrypted batch omission.

The ordinary encrypted happy-flow module also makes another credential request
after collection. On this branch that request is rejected as a consumed deferred
transaction. The added deferred phase uses the suite's single-request module
instead: repeated issuance after deferred collection is a separate finding, and
notifications/compression remain in the existing immediate coverage.
Enterprise issuer2, wallet-initiated deferred issuance and FAPI2 modules are not
part of this phase.

The suite makes only one deferred collection attempt. A test-only proxy forwards
the original initial credential request to the issuer, observes its real `202`,
approves the corresponding offer with the management `/deferred/complete` endpoint,
and then releases the original response unchanged. The suite subsequently collects
from the advertised deferred endpoint. The proxy never decrypts or manufactures
credentials; it preserves JWE bodies and DPoP headers and uses the verified access
token's session subject only to correlate test traffic with registered offer IDs.
Cloudflare requests compressed origin responses, so the proxy decodes a copy of
gzip/deflate management receipts to read the offer ID. The original encoded receipt
and its headers are forwarded unchanged. Proxy diagnostics contain only the method,
known route, and exception class; CI includes this log in each proof artifact.

For the existing local Docker/nginx setup, start issuer-api2 on host port 7005 with
the conformance profiles and public base URL described above, then run from the
unified build root:

```bash
OPENID4VCI_CONFORMANCE_ISSUANCE_MODE=deferred \
OPENID4VCI_CONFORMANCE_PROOF_MODES=jwt,attestation \
  bash waltid-identity/waltid-services/waltid-openid4vp-conformance-runners/run-issuer-conformance-local.sh
```

The launcher starts the proxy on port 7006 and mounts a generated nginx configuration
that routes issuer traffic through it. The local proxy binds all host interfaces so
Docker can reach it; CI binds loopback. Both upstream management and credential
requests go to the real local issuer. The launcher stops its proxy on exit.
Re-running in immediate mode restores the normal compose configuration.

For an already prepared runtime/tunnel through the proxy, run
`:waltid-services:waltid-openid4vp-conformance-runners:issuerDeferredConformanceTest`
with `-PskipLiveConformance=false`, the usual issuer/suite/profile environment,
`OPENID4VCI_CONFORMANCE_PROOF_MODES=jwt` or `attestation`, and
`OPENID4VCI_CONFORMANCE_DEFERRED_EVIDENCE_FILE` pointing at that proxy's receipt file.
`OPENID4VCI_CONFORMANCE_DEFERRED_REPORT_DIR` optionally overrides the default
`build/reports/issuer-ci/deferred` directory. The ordinary `test` task defaults to
immediate issuance; `OPENID4VCI_CONFORMANCE_ISSUANCE_MODE` can select either mode.

A required deferred module must finish `PASSED`, demonstrate its selected proof
generator, and have ordered initial-202 / approval-204 / collection-200 receipts
for every offer it used. Skips, missing variants/modules, missing receipts and failed
approval do not pass. Receipts contain offer hashes and event names only; CI exports
counts, plan/test IDs and sanitized failure summaries, with separate immediate and
deferred results and JUnit XML. No tokens, protocol bodies or raw receipt files are
uploaded by the result aggregation.

Repeated pending polling, denial, expiry, refresh, replay and restart recovery
remain separate integration-test concerns. A passing deferred phase establishes
issuance and collection for this matrix, not every deferred lifecycle behavior.
