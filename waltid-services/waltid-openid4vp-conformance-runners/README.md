# OpenID4VC Conformance Tests

Conformance test runners for OpenID4VCI and OpenID4VP against the [OpenID Foundation Conformance Suite](https://www.certification.openid.net/).

## Quick Start

### Prerequisites

1. **Conformance Suite** running locally:
   ```bash
   cd ~/dev/openid/conformance-suite
   docker compose -f docker-compose-walt.yml up -d
   ```
   Verify: https://localhost.emobix.co.uk:8443/

2. **ngrok** for exposing local services

3. **Keycloak** at `http://keycloak.localhost:8080` (for authorization_code flows)

### Running Tests

```bash
cd ~/dev/walt-id/waltid-unified-build

# VCI Issuer tests
export OPENID4VCI_CONFORMANCE_CREDENTIAL_ISSUER_URL="https://YOUR-NGROK.ngrok-free.app/openid4vci"
./gradlew :waltid-services:waltid-openid4vp-conformance-runners:test --tests "IssuerConformanceTests"

# VP Verifier tests
export VERIFIER_NGROK_URL="https://YOUR-NGROK.ngrok-free.app"
./gradlew :waltid-services:waltid-openid4vp-conformance-runners:test --tests "VerifierConformanceTests"
```

---

## Test Status Summary

### VCI Issuer (2026-07-08)

| Test | Result | Notes |
|------|--------|-------|
| `oid4vci-1_0-issuer-metadata-test` | ✅ PASSED | Metadata endpoint compliant |
| `oid4vci-1_0-issuer-happy-flow` | ❌ FAILED | Missing RFC 9207 `iss` parameter |
| `oid4vci-1_0-issuer-metadata-test-signed` | ❌ FAILED | Test environment issue |

**Pass rate: 1/3 (33%)**

📄 **Details:** [docs/VCI-ISSUER.md](docs/VCI-ISSUER.md)

### VP Verifier (2026-07-13) ✅ ALL PASSING

| Test | Credential | Client ID | Response Mode | Result |
|------|------------|-----------|---------------|--------|
| `MdlX509SanDnsRequestUriSignedDirectPost` | mDL (mdoc) | x509_san_dns | direct_post | ✅ PASSED |
| `SdJwtVcX509SanDnsRequestUriSignedDirectPost` | SD-JWT VC | x509_san_dns | direct_post.jwt | ✅ PASSED |
| `SdJwtVcX509HashRequestUriSignedDirectPostHaip` | SD-JWT VC | x509_hash | direct_post.jwt | ✅ PASSED |
| `MdlX509HashRequestUriSignedDirectPostHaip` | mDL (mdoc) | x509_hash | direct_post.jwt | ✅ PASSED |

**Pass rate: 4/4 (100%)**

📄 **Details:** [docs/VP-VERIFIER.md](docs/VP-VERIFIER.md)

### VCI Wallet

📄 **Details:** [docs/VCI-WALLET.md](docs/VCI-WALLET.md)

### VP Wallet

📄 **Details:** [docs/VP-WALLET.md](docs/VP-WALLET.md)

---

## Test Profiles

| Interface | Baseline | HAIP | Key Difference |
|-----------|----------|------|----------------|
| **VCI Issuer** | `pre-authorized_code` | `authorization_code` | Grant type |
| **VP Verifier** | `x509_san_dns` | `x509_hash` | Client ID scheme |

**Baseline:** Automated functional testing  
**HAIP:** Strict [HAIP 1.0](https://openid.net/specs/openid4vc-high-assurance-interoperability-profile-1_0-final.html) compliance

---

## Project Structure

```
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
└── docs/
    ├── VCI-ISSUER.md            # ← Issuer setup & status
    ├── VP-VERIFIER.md           # ← Verifier setup & status
    ├── VCI-WALLET.md
    └── VP-WALLET.md
```

---

## Documentation

| Document | Description |
|----------|-------------|
| [docs/VCI-ISSUER.md](docs/VCI-ISSUER.md) | VCI Issuer test setup, status, troubleshooting |
| [docs/VP-VERIFIER.md](docs/VP-VERIFIER.md) | VP Verifier test setup, status, troubleshooting |
| [docs/VCI-WALLET.md](docs/VCI-WALLET.md) | VCI Wallet test documentation |
| [docs/VP-WALLET.md](docs/VP-WALLET.md) | VP Wallet test documentation |
| [TEST-PLANS-AND-PROFILES.md](TEST-PLANS-AND-PROFILES.md) | Detailed test plan specifications |

---

## Troubleshooting

| Issue | Solution |
|-------|----------|
| "Connect timed out" | Use ngrok URL, not localhost (Docker can't reach host) |
| "Invalid redirect_uri" | Add ngrok URL to Keycloak client redirect URIs |
| Tests stuck in WAITING | Manual OAuth login required for authorization_code flows |
| Metadata 404 | Check path: `/.well-known/openid-credential-issuer/<path>` |

See individual docs for detailed troubleshooting.
