# OpenID4VC Conformance Test Runners

Automated test runners for [OpenID Foundation Conformance Suite](https://gitlab.com/openid/conformance-suite).

## Test Coverage

| Protocol | Role | Test Class | Status | Docs |
|----------|------|------------|--------|------|
| OpenID4VCI 1.0 | **Wallet** | `VciWalletConformanceTests` | ✅ **140/140** | [VCI-WALLET.md](docs/VCI-WALLET.md) |
| OpenID4VCI 1.0 | **Issuer** | `IssuerConformanceTests` | ⚠️ 53/55 | [VCI-ISSUER.md](docs/VCI-ISSUER.md) |
| OpenID4VP 1.0 | **Verifier** | `VerifierConformanceTests` | ⚠️ 1/2 (mDL ✅) | [VP-VERIFIER.md](docs/VP-VERIFIER.md) |
| OpenID4VP 1.0 | **Wallet** | `VpWalletConformanceTests` | 🚫 Blocked | [VP-WALLET.md](docs/VP-WALLET.md) |

### Status Legend
- ✅ **Complete** — All tests passing
- ⚠️ **Partial** — Most tests pass, some known issues
- 🚫 **Blocked** — Waiting on upstream features (WAL-896 HAIP)

## Quick Start

### Prerequisites

1. **Add hosts entry** (one-time setup):
   ```bash
   echo "127.0.0.1 localhost.emobix.co.uk" | sudo tee -a /etc/hosts
   ```

2. **Clone and configure conformance suite**:
   ```bash
   git clone https://gitlab.com/openid/conformance-suite.git ~/dev/openid/conformance-suite
   
   # Copy walt.id config
   cp docker-compose-walt.yml ~/dev/openid/conformance-suite/
   cp -r nginx ~/dev/openid/conformance-suite/
   ```

3. **Start conformance suite**:
   ```bash
   cd ~/dev/openid/conformance-suite
   docker compose -f docker-compose-walt.yml up -d
   
   # Wait ~30s, then verify
   curl -k https://localhost.emobix.co.uk:8443/
   ```

4. **Install ngrok** (for issuer/verifier tests):
   ```bash
   # macOS
   brew install ngrok
   
   # Linux (snap)
   sudo snap install ngrok
   ```

### Running Tests

All commands run from: `~/dev/walt-id/waltid-unified-build/waltid-identity`

#### VCI Wallet (✅ Complete)
No external dependencies — runs standalone against conformance suite.

```bash
./gradlew :waltid-services:waltid-openid4vp-conformance-runners:test \
    --tests "VciWalletConformanceTests"
```

#### VCI Issuer (⚠️ 53/55)
Requires: `issuer-api2` running + ngrok tunnel

```bash
# Terminal 1: Start issuer
./gradlew :waltid-services:waltid-issuer-api2:run

# Terminal 2: Start ngrok
ngrok http 7002

# Terminal 3: Run tests (use your ngrok URL)
export OPENID4VCI_CONFORMANCE_CREDENTIAL_ISSUER_URL="https://YOUR-NGROK.ngrok-free.app/openid4vc"
./gradlew :waltid-services:waltid-openid4vp-conformance-runners:test \
    --tests "IssuerConformanceTests"
```

#### VP Verifier (⚠️ 1/2 — mDL passing)
Requires: `verifier-api2` running + ngrok tunnel

```bash
# Terminal 1: Start verifier
./gradlew :waltid-services:waltid-verifier-api2:run

# Terminal 2: Start ngrok
ngrok http 7003

# Terminal 3: Run tests (use your ngrok URL)
export VERIFIER_NGROK_URL="https://YOUR-NGROK.ngrok-free.app"
./gradlew :waltid-services:waltid-openid4vp-conformance-runners:test \
    --tests "VerifierConformanceTests"
```

#### VP Wallet (🚫 Blocked)
Blocked on WAL-896 HAIP features (JAR, JWE, HAIP policy support).

```bash
# Will skip until HAIP features are implemented
./gradlew :waltid-services:waltid-openid4vp-conformance-runners:test \
    --tests "VpWalletConformanceTests"
```

## Known Issues

### VCI Issuer (2 failures)
1. **"No 'iss' value in authorization response"** — Missing RFC 9207 issuer identification
2. **"Invalid http status"** — Unexpected HTTP status on credential endpoint

### VP Verifier (SD-JWT HAIP)
- **Trust anchor configuration** — HAIP test plan requires `Request Object Trust Anchor` in client config
- mDL (plain VP) tests work; SD-JWT HAIP tests need additional configuration

## Architecture

```
┌─────────────────────────────────────────────────────────────────────────┐
│                    OIDF Conformance Suite (Docker)                      │
│                  https://localhost.emobix.co.uk:8443                    │
│                                                                         │
│   Can act as: Issuer, Verifier, Wallet (depending on test plan)         │
└──────────────────────────────────┬──────────────────────────────────────┘
                                   │
                                   │ HTTPS (via ngrok for external access)
                                   ▼
┌─────────────────────────────────────────────────────────────────────────┐
│                         Test Adapters                                   │
│                                                                         │
│   VciWalletConformanceAdapter (7007) — bridges VCI wallet flow          │
│   VpWalletConformanceAdapter (7006)  — bridges VP wallet flow           │
│                                                                         │
│   (Adapters simulate "robot users" driving wallet APIs step-by-step)    │
└──────────────────────────────────┬──────────────────────────────────────┘
                                   │
                                   │ HTTP
                                   ▼
┌─────────────────────────────────────────────────────────────────────────┐
│                         walt.id Services                                │
│                                                                         │
│   wallet-api2   (7005) — credential wallet                              │
│   issuer-api2   (7002) — credential issuer                              │
│   verifier-api2 (7003) — presentation verifier                          │
└─────────────────────────────────────────────────────────────────────────┘
```

## Code Structure

```
src/
├── main/kotlin/id/walt/openid4vp/conformance/
│   ├── adapter/                    # Test adapters (conformance ↔ wallet APIs)
│   ├── testplans/
│   │   ├── http/                   # HTTP interfaces to conformance suite
│   │   ├── httpdata/               # Response DTOs
│   │   ├── plans/                  # Test plan definitions
│   │   │   ├── vci/{issuer,wallet}/
│   │   │   └── vp/{verifier,wallet}/
│   │   └── runner/                 # Test plan runners
│   └── utils/
└── test/kotlin/id/walt/openid4vp/conformance/
    ├── VciWalletConformanceTests.kt   # ✅ Complete
    ├── IssuerConformanceTests.kt      # ⚠️ Partial
    ├── VerifierConformanceTests.kt    # ⚠️ Partial
    └── VpWalletConformanceTests.kt    # 🚫 Blocked

docs/
├── VCI-WALLET.md    # VCI wallet documentation
├── VCI-ISSUER.md    # VCI issuer documentation
├── VP-VERIFIER.md   # VP verifier documentation
└── VP-WALLET.md     # VP wallet documentation
```

## Troubleshooting

### Tests are SKIPPED
- Conformance suite not running → `docker compose -f docker-compose-walt.yml up -d`
- Environment variable not set → Check `VERIFIER_NGROK_URL` or `OPENID4VCI_CONFORMANCE_CREDENTIAL_ISSUER_URL`
- ngrok tunnel down → Restart ngrok and update env var

### "Connection refused" errors
- Service not running → Start the required `*-api2` service
- Wrong port → Verify ngrok is tunneling to correct port (7002 for issuer, 7003 for verifier)

### Stale test results
- Use `--rerun-tasks` flag to force fresh test execution
- Restart conformance suite if tests get stuck: `docker compose -f docker-compose-walt.yml restart`

## External Links

- [OpenID4VCI 1.0 Spec](https://openid.net/specs/openid-4-verifiable-credential-issuance-1_0.html)
- [OpenID4VP 1.0 Spec](https://openid.net/specs/openid-4-verifiable-presentations-1_0.html)
- [HAIP Profile](https://openid.net/specs/openid4vc-high-assurance-interoperability-profile-1_0.html)
- [Conformance Suite GitLab](https://gitlab.com/openid/conformance-suite)
