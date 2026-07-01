# OpenID4VC Conformance Test Runners

Automated test runners for [OpenID Foundation Conformance Suite](https://gitlab.com/openid/conformance-suite).

## Test Coverage

| Protocol | Role | Test Class | Status | Docs |
|----------|------|------------|--------|------|
| OpenID4VCI 1.0 | **Wallet** (receives credentials) | `VciWalletConformanceTests` | ✅ 140/140 passing | [VCI-WALLET.md](docs/VCI-WALLET.md) |
| OpenID4VCI 1.0 | **Issuer** (issues credentials) | `IssuerConformanceTests` | 🟡 Ready | [VCI-ISSUER.md](docs/VCI-ISSUER.md) |
| OpenID4VP 1.0 | **Verifier** (requests presentations) | `VerifierConformanceTests` | 🟡 Ready | [VP-VERIFIER.md](docs/VP-VERIFIER.md) |
| OpenID4VP 1.0 | **Wallet** (presents credentials) | `WalletConformanceTests` | ⏳ Blocked (WAL-896) | [VP-WALLET.md](docs/VP-WALLET.md) |

## Quick Start

### Prerequisites

```bash
# 1. Add hosts entry
echo "127.0.0.1 localhost.emobix.co.uk" | sudo tee -a /etc/hosts

# 2. Clone conformance suite
git clone https://gitlab.com/openid/conformance-suite.git ~/dev/openid/conformance-suite

# 3. Copy walt.id config
cp docker-compose-walt.yml ~/dev/openid/conformance-suite/
cp -r nginx ~/dev/openid/conformance-suite/

# 4. Start conformance suite
cd ~/dev/openid/conformance-suite
docker compose -f docker-compose-walt.yml up -d

# 5. Verify (wait ~30s for startup)
curl -k https://localhost.emobix.co.uk:8443/
```

### Run Tests

```bash
cd ~/dev/walt-id/waltid-unified-build/waltid-identity

# VCI Wallet (no external dependencies)
./gradlew :waltid-services:waltid-openid4vp-conformance-runners:test \
    --tests "VciWalletConformanceTests" -PrunIntegrationTests

# VCI Issuer (requires Keycloak + ngrok)
export OPENID4VCI_CONFORMANCE_CREDENTIAL_ISSUER_URL="https://YOUR-NGROK.ngrok-free.app/openid4vci"
./gradlew :waltid-services:waltid-openid4vp-conformance-runners:test \
    --tests "IssuerConformanceTests" -PrunIntegrationTests

# VP Verifier (requires ngrok)
export VERIFIER_NGROK_URL="https://YOUR-NGROK.ngrok-free.app"
./gradlew :waltid-services:waltid-openid4vp-conformance-runners:test \
    --tests "VerifierConformanceTests" -PrunIntegrationTests

# VP Wallet (blocked on WAL-896)
./gradlew :waltid-services:waltid-openid4vp-conformance-runners:test \
    --tests "WalletConformanceTests" -PrunIntegrationTests
```

## Architecture

```
┌─────────────────────────────────────────────────────────────────────────┐
│                    OIDF Conformance Suite (Docker)                      │
│                  https://localhost.emobix.co.uk:8443                    │
│                                                                         │
│   Can act as: Issuer, Verifier, Wallet (depending on test plan)         │
└──────────────────────────────────┬──────────────────────────────────────┘
                                   │
                                   │ HTTPS
                                   ▼
┌─────────────────────────────────────────────────────────────────────────┐
│                         Test Adapters                                   │
│                                                                         │
│   VciWalletConformanceAdapter (7007) — bridges VCI wallet flow          │
│   WalletConformanceAdapter (7006)    — bridges VP wallet flow           │
│                                                                         │
│   (Adapters simulate "robot users" driving wallet APIs step-by-step)    │
└──────────────────────────────────┬──────────────────────────────────────┘
                                   │
                                   │ HTTP
                                   ▼
┌─────────────────────────────────────────────────────────────────────────┐
│                         walt.id Services                                │
│                                                                         │
│   wallet-api2  (7005) — credential wallet                               │
│   issuer-api2  (7002) — credential issuer                               │
│   verifier-api2 (7003) — presentation verifier                          │
└─────────────────────────────────────────────────────────────────────────┘
```

## Code Structure

```
src/
├── main/kotlin/id/walt/openid4vp/conformance/
│   ├── adapter/                    # Test adapters (bridge conformance ↔ wallet APIs)
│   │   ├── VciWalletConformanceAdapter.kt
│   │   └── WalletConformanceAdapter.kt
│   ├── config/
│   │   └── ConformanceConfig.kt
│   ├── plans/                      # Shared plan interfaces
│   │   └── ConformanceTestPlan.kt
│   ├── testplans/
│   │   ├── http/                   # HTTP interfaces to conformance suite
│   │   ├── httpdata/               # Response DTOs
│   │   ├── keys/                   # Test key material
│   │   ├── plans/                  # Test plan definitions
│   │   │   ├── vci/                # VCI-specific plans
│   │   │   │   └── issuer/         # Issuer plans
│   │   │   │   └── wallet/         # VCI wallet plans
│   │   │   └── vp/                 # VP-specific plans
│   │   │       └── verifier/       # Verifier plans
│   │   │       └── wallet/         # VP wallet plans
│   │   └── runner/                 # Test plan runners
│   └── utils/
└── test/kotlin/id/walt/openid4vp/conformance/
    ├── VciWalletConformanceTests.kt
    ├── IssuerConformanceTests.kt
    ├── VerifierConformanceTests.kt
    └── WalletConformanceTests.kt

docs/
├── VCI-WALLET.md                   # VCI wallet test docs
├── VCI-ISSUER.md                   # VCI issuer test docs
├── VP-VERIFIER.md                  # VP verifier test docs
└── VP-WALLET.md                    # VP wallet test docs
```

## Documentation

- [VCI-WALLET.md](docs/VCI-WALLET.md) — VCI wallet conformance (receive credentials)
- [VCI-ISSUER.md](docs/VCI-ISSUER.md) — VCI issuer conformance (issue credentials)
- [VP-VERIFIER.md](docs/VP-VERIFIER.md) — VP verifier conformance (request presentations)
- [VP-WALLET.md](docs/VP-WALLET.md) — VP wallet conformance (present credentials)

## External Links

- [OpenID4VCI 1.0 Spec](https://openid.net/specs/openid-4-verifiable-credential-issuance-1_0.html)
- [OpenID4VP 1.0 Spec](https://openid.net/specs/openid-4-verifiable-presentations-1_0.html)
- [HAIP Profile](https://openid.net/specs/openid4vc-high-assurance-interoperability-profile-1_0.html)
- [Conformance Suite GitLab](https://gitlab.com/openid/conformance-suite)
