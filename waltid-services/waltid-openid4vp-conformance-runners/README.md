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
├── run-issuer-conformance-local.sh
├── run-wallet-api2-conformance-local.sh
└── run-wallet-conformance-local.sh
```

## Documentation

| Document | Description |
|----------|-------------|
| [docs/VCI-ISSUER.md](docs/VCI-ISSUER.md) | OSS issuer2 setup, execution, matrix, HAIP, and troubleshooting |
| [docs/VP-VERIFIER.md](docs/VP-VERIFIER.md) | VP verifier setup, status, and troubleshooting |
| [docs/VCI-WALLET.md](docs/VCI-WALLET.md) | VCI wallet conformance documentation |
| [docs/VP-WALLET.md](docs/VP-WALLET.md) | VP wallet conformance documentation |
| [TEST-PLANS-AND-PROFILES.md](TEST-PLANS-AND-PROFILES.md) | Test-plan inventory and profile reference |
| [Wallet API2 basic-plan matrices](../../docs/openid4vci-wallet-1.0-basic-plan-matrices.md) | Module-level VCI wallet conformance gaps |
| [Wallet API2 compatibility report](../../docs/openid4vci-wallet-1.0-compatibility-report.md) | Consolidated VCI wallet gaps and implementation order |
| [Wallet API2 naming notes](../../docs/wallet2-oid4vci-naming-and-extensibility.md) | API-shape and extensibility analysis |

`./gradlew test` only executes the test task. It does not universally provision
remote services or local conformance infrastructure. Follow the relevant
role-specific guide for a runnable environment and its intended command.

VCI wallet conformance uses two local processes: start Wallet API2 with
`run-wallet-api2-conformance-local.sh`, then run
`run-wallet-conformance-local.sh` from a second terminal. The complete setup is
in [docs/VCI-WALLET.md](docs/VCI-WALLET.md).
