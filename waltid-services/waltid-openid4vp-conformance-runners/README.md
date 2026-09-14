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

## CI summaries and soft-fail

The reusable OSS Gradle workflow
(`.github/workflows/gradle.yml`) always publishes these role sections to the
GitHub Actions job summary, using the same heading and table shape as the
OpenID4VP verifier report:

1. OpenID4VP Verifier
2. OpenID4VP Wallet
3. OpenID4VCI Wallet
4. OpenID4VCI Issuer

GitHub-hosted CI points the wallet suites at `conformance.waltid.cloud` and
exposes the in-process adapters through Cloudflare tunnels (OpenID4VP adapter
on port 7006, OpenID4VCI adapter on port 7007), alongside the existing verifier
tunnel on 7003. Those wallet suites therefore run live. The OpenID4VCI issuer
matrix still only runs when its dedicated workflow inputs and issuer URL are
configured.

When a role produced `summary.md`, that file is appended. Test names are
compacted to the module suffix and variant values so the table stays readable.
When a role did not write a report, the workflow still writes the same heading,
totals, and table, with a note that no results were produced.

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

### `CONFORMANCE_ALLOW_FAILURE`

Repo/org Actions variable controlling soft-fail for all conformance roles:

| Value | Behavior |
|-------|----------|
| unset / empty / `true` | Soft-fail: failed tests still appear in the job summary, but JUnit does not fail the job for those failures |
| `false` | Hard-fail: any executed non-passing conformance result fails the job |

While actively working on conformance, keep the variable unset or set to
`true`. Set it to `false` when conformance must be green to merge.

Local equivalent:

```bash
export CONFORMANCE_ALLOW_FAILURE=true   # soft-fail
export CONFORMANCE_ALLOW_FAILURE=false  # hard-fail
```

For the VCI issuer matrix, `OPENID4VCI_CONFORMANCE_STRICT` and
`OPENID4VCI_CONFORMANCE_CERTIFICATION_MODE` still apply for local runs.
When `CONFORMANCE_ALLOW_FAILURE` is present (including empty CI injection), it
participates in strictness resolution; certification mode always remains strict.
Issuer reports default to `build/reports/openid-conformance/vci-issuer`
(override with `OPENID4VCI_CONFORMANCE_REPORT_DIR`).
