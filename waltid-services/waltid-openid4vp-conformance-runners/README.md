# OpenID4VC Conformance Tests

Conformance test runners for OpenID4VCI and OpenID4VP against the [OpenID Foundation Conformance Suite](https://www.certification.openid.net/).

## Quick Start

### Prerequisites

1. **Conformance Suite** running locally:
   ```bash
   cd openid/conformance-suite
   docker compose -f docker-compose-walt.yml up -d
   ```
   Verify: https://localhost.emobix.co.uk:8443/

2. **ngrok** for exposing local services

3. **Keycloak** for `authorization_code` flows. The pre-authorized-code wrapper preset does not need Keycloak.
   Authorization-code presets use the same public Keycloak test user as issuer2 integration tests:
   `jane@walt.id` / `jane`.

### Running Tests

```bash
cd waltid-unified-build

# VCI Issuer tests, pre-authorized-code-first wrapper preset by default
export OPENID4VCI_CONFORMANCE_CREDENTIAL_ISSUER_URL="https://YOUR-NGROK.ngrok-free.app/openid4vci"
./run-issuer-conformance-local.sh

# Direct Gradle execution, if the conformance suite is already running and trusted
export OPENID4VCI_CONFORMANCE_CREDENTIAL_ISSUER_URL="https://YOUR-NGROK.ngrok-free.app/openid4vci"
export OPENID4VCI_CONFORMANCE_CLIENT_ATTESTER_JWKS_FILE="$PWD/waltid-identity/waltid-services/waltid-openid4vp-conformance-runners/src/test/resources/keys/attester-key.json"
export OPENID4VCI_CONFORMANCE_BROWSER_AUTOMATION=true
export OPENID4VCI_CONFORMANCE_AUTH_USERNAME="jane@walt.id"
export OPENID4VCI_CONFORMANCE_AUTH_PASSWORD="jane"
./gradlew :waltid-services:waltid-openid4vp-conformance-runners:installPlaywrightBrowsers
./gradlew :waltid-services:waltid-openid4vp-conformance-runners:test \
  --tests "id.walt.openid4vp.conformance.IssuerConformanceTests.runIssuerConformanceTests"

# VP Verifier tests
export VERIFIER_NGROK_URL="https://YOUR-NGROK.ngrok-free.app"
./gradlew :waltid-services:waltid-openid4vp-conformance-runners:test --tests "VerifierConformanceTests"
```

---

## Test Status Summary

### VCI Issuer (handover snapshot, 2026-07-14)

The active issuer2 handover profile is the base issuer conformance plan:

- Test plan: `oid4vci-1_0-issuer-test-plan`
- Variant: `sender_constrain=dpop`, `client_auth_type=client_attestation`, `vci_authorization_code_flow_variant=wallet_initiated`, `vci_grant_type=pre-authorized_code`, `credential_format=sd_jwt_vc`, `authorization_request_type=simple`, `fapi_request_method=unsigned`, `fapi_profile=vci`, `fapi_response_mode=plain_response`
- Config: issuer URL, credential configuration ID, client-attestation issuer, attester JWKS, DPoP client JWKS, and issuer2-generated credential offers delivered per issuer-flow module
- Status: observed passing in the local handover environment. Re-run before treating this as a CI or release signal.

Details: [docs/VCI-ISSUER.md](docs/VCI-ISSUER.md)

### VP Verifier (2026-07-08)

| Test | Result | Notes |
|------|--------|-------|
| mDL Baseline (`plain_vp`) | ✅ PASSED | x509_san_dns, direct_post |
| SD-JWT HAIP | ❌ FAILED | Audience mismatch (x509_hash) |
| mDL HAIP | ❌ FAILED | Audience mismatch (x509_hash) |
| SD-JWT x509_hash HAIP | ❌ FAILED | Audience mismatch (x509_hash) |

**Pass rate: 1/4 (25%)**

**Blocking Issue:** HAIP tests fail because the verifier's `AudienceCheckSdJwtVPPolicy` doesn't support `x509_hash:<sha256>` audience format required by HAIP.

📄 **Details:** [docs/VP-VERIFIER.md](docs/VP-VERIFIER.md)

### VCI Wallet

📄 **Details:** [docs/VCI-WALLET.md](docs/VCI-WALLET.md)

### VP Wallet

📄 **Details:** [docs/VP-WALLET.md](docs/VP-WALLET.md)

---

## Test Profiles

| Interface | Baseline | HAIP | Key Difference |
|-----------|----------|------|----------------|
| **VCI Issuer** | `pre-authorized_code` + `client_attestation` | `authorization_code` + `private_key_jwt` | Grant/auth profile |
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
| Tests stuck in WAITING | Enable `OPENID4VCI_CONFORMANCE_BROWSER_AUTOMATION=true` for authorization_code flows |
| Missing Playwright browser | Run `./gradlew :waltid-services:waltid-openid4vp-conformance-runners:installPlaywrightBrowsers` |
| Keycloak login error | Check `OPENID4VCI_CONFORMANCE_AUTH_USERNAME` and `OPENID4VCI_CONFORMANCE_AUTH_PASSWORD` |
| Metadata 404 | Check path: `/.well-known/openid-credential-issuer/<path>` |

See individual docs for detailed troubleshooting.

## VCI Issuer Client Attestation

Issuer conformance plans use `client_attestation` by default. The default attester key is:

```text
src/test/resources/keys/attester-key.json
```

For issuer2 `static-jwk` verification, use the matching public key:

```text
src/test/resources/keys/attester-public-jwk.json
```

For issuer2 `x509-chain` verification, trust the matching local root CA:

```text
src/test/resources/certs/root-ca.pem
```

For the local handover run, issuer2 needs matching base URL, CI token key, and client-attestation trust configuration. Replace `baseUrl` when the ngrok tunnel changes.

```hocon
baseUrl = "https://9bc0-2a02-8109-8686-5d00-8333-124e-42b0-6481.ngrok-free.app"

ciTokenKey = """{"type":"jwk","jwk":{"kty":"EC","d":"KJ4k3Vcl5Sj9Mfq4rrNXBm2MoPoY3_Ak_PIR_EgsFhQ","crv":"P-256","x":"G0RINBiF-oQUD3d5DGnegQuXenI29JDaMGoMvioKRBM","y":"ed3eFGs2pEtrp7vAZ7BLcbrUtpKkYWAT2JPUQK4lN4E"}}"""

clientAuthenticationConfig {
  supportedMethods = [
    {
      type = "preauth-anonymous"
    },
    {
      type = "client-attestation"
      config {
        verificationMethod {
          type = "x509-chain"
          trustedRootCertificatesPem = [
"""-----BEGIN CERTIFICATE-----
MIICCTCCAa6gAwIBAgIUd2OgSqKSx5bt1dwVpxyOsdBrCwEwCgYIKoZIzj0EAwIw
UDEvMC0GA1UEAwwmd2FsdC5pZCBPcGVuSUQ0VkNJIENvbmZvcm1hbmNlIFRlc3Qg
Q0ExEDAOBgNVBAoMB3dhbHQuaWQxCzAJBgNVBAYTAlVUMB4XDTI2MDcxMzE2MTYz
M1oXDTM2MDcxMDE2MTYzM1owUDEvMC0GA1UEAwwmd2FsdC5pZCBPcGVuSUQ0VkNJ
IENvbmZvcm1hbmNlIFRlc3QgQ0ExEDAOBgNVBAoMB3dhbHQuaWQxCzAJBgNVBAYT
AlVUMFkwEwYHKoZIzj0CAQYIKoZIzj0DAQcDQgAEcKWoEYWPMA8sMHQt4Whhdnyb
eGY4uxNJ61K8qEkR7yjxpDPlTUwMLoFY4LwvDZbmrd1wuAQzC19vN3ZCKy0waqNm
MGQwHwYDVR0jBBgwFoAUUGfw1hxU8WtLHa5RnP+dVRINVTYwEgYDVR0TAQH/BAgw
BgEB/wIBADAOBgNVHQ8BAf8EBAMCAQYwHQYDVR0OBBYEFFBn8NYcVPFrSx2uUZz/
nVUSDVU2MAoGCCqGSM49BAMCA0kAMEYCIQC/45X54n1VyZuAN8vmin6cluuoNBD5
VACJ445Tx9FAuQIhAN6yqTj1u30N51FsULyrdbwXRgBRo7CgE1CZC9ejeD1E
-----END CERTIFICATE-----"""
          ]
        }
      }
    }
  ]
}
```

The current handover intentionally preserves the variant values that passed locally, including `wallet_initiated` and `pre-authorized_code`. Do not change them to alternate conformance-suite enum spellings without re-running the same issuer2 test plan.

Do not include local runtime artifacts such as `mongo/` or a locally mutated `conformance-truststore.jks` in a clean handover commit.

## VCI Issuer Variant Matrix

By default, `IssuerConformanceTests` now targets the base `oid4vci-1_0-issuer-test-plan` and attempts every generated variant that is defined by that plan's selectable VCI issuer axes:

| Dimension | Values |
|-----------|--------|
| FAPI profile | `vci` |
| Credential format | `sd_jwt_vc`, `mdoc` |
| Grant type | `authorization_code`, `pre_authorization_code` |
| Auth code flow variant | `wallet_initiated`, `issuer_initiated` |
| Client authentication | `client_attestation`, `private_key_jwt`, `mtls` |
| Sender constraint | `dpop`, `mtls` |
| Authorization request type | `simple`, `rar` |
| Request method | `unsigned`, `signed_non_repudiation` |
| Credential response encryption | `plain`, `encrypted` |

The generated matrix is constrained to the combinations accepted by the base issuer plan implementation:

- `pre_authorization_code` is generated only with `issuer_initiated`
- `openid` and `fapi_response_mode` are not generated as matrix axes because the conformance suite marks them as not applicable for `fapi_profile=vci`

The matrix uses the conformance-suite enum spelling `pre_authorization_code`. `OPENID4VCI_CONFORMANCE_MATRIX=legacy` keeps the previous handover variant string `pre-authorized_code`.

The runner records unsupported or not-yet-wired combinations instead of hiding them:

- `generated`: variant was generated but not executed, usually discovery-only mode
- `suite_invalid`: the suite rejected the variant before creating a plan
- `not_applicable`: the suite created a plan with no modules
- `blocked`: local setup is missing, for example credential offer, login automation, mTLS material, or timeout
- `failed`: suite modules ran but did not pass
- `passed`: suite modules ran and passed

Artifacts are written to `build/reports/openid4vci-issuer-matrix` by default:

```text
matrix.json
results.json
summary.md
```

Useful controls:

```bash
# Wrapper presets
export OPENID4VCI_CONFORMANCE_PRESET=vci-client-attestation-dpop-simple-unsigned-preauth
export OPENID4VCI_CONFORMANCE_PRESET=all-basic-plan
export OPENID4VCI_CONFORMANCE_PRESET=vci-client-attestation-dpop-simple-unsigned
export OPENID4VCI_CONFORMANCE_PRESET=custom

# Generate the matrix and write artifacts without running suite modules
export OPENID4VCI_CONFORMANCE_MATRIX=discovery

# Run exact variants by generated ID
export OPENID4VCI_CONFORMANCE_VARIANT_ID="vci-sdjwt-preauth-issuer-clientatt-dpop-simple-unsigned-plain"
export OPENID4VCI_CONFORMANCE_VARIANTS="vci-sdjwt-preauth-issuer-clientatt-dpop-simple-unsigned-plain"

# Filter dimensions, comma-separated
export OPENID4VCI_CONFORMANCE_FILTER_FAPI_PROFILES="vci"
export OPENID4VCI_CONFORMANCE_FILTER_FORMATS="sd_jwt_vc"
export OPENID4VCI_CONFORMANCE_FILTER_CLIENT_AUTH_TYPES="client_attestation,private_key_jwt"
export OPENID4VCI_CONFORMANCE_FILTER_SENDER_CONSTRAINTS="dpop"

# Filter conformance-suite modules returned by the selected plan
export OPENID4VCI_CONFORMANCE_MODULE_GROUPS="metadata,positive"
export OPENID4VCI_CONFORMANCE_MODULE_GROUPS="metadata"
export OPENID4VCI_CONFORMANCE_MODULE_GROUPS="positive"
export OPENID4VCI_CONFORMANCE_MODULES="oid4vci-1_0-issuer-happy-flow,oid4vci-1_0-issuer-batch-issuance"

# Static transaction code for pre-authorized happy-flow modules
export OPENID4VCI_CONFORMANCE_STATIC_TX_CODE="493536"

# Make Gradle fail when variants are blocked, suite-invalid, or failed
export OPENID4VCI_CONFORMANCE_STRICT=true

# Override report location and test timeout
export OPENID4VCI_CONFORMANCE_REPORT_DIR="$PWD/build/issuer-conformance"
export OPENID4VCI_CONFORMANCE_TIMEOUT_MINUTES=480

# Previous handover profile, including the old HAIP attempt
export OPENID4VCI_CONFORMANCE_MATRIX=legacy
```

For progressive conformance work, run the full matrix in exploration mode first, review `summary.md`, then cherry-pick one blocked or failed variant family with the filter variables while adding issuer2 support.

### Browser automation for authorization_code issuer tests

The OpenID conformance suite exposes front-channel authorization URLs through its browser interaction API. For
issuer `authorization_code` modules, the OSS runner can now open those URLs with Playwright and complete the
public Keycloak login flow automatically.

The important difference from issuer2's internal integration tests is that the conformance runner does not
intercept the authorization code. The browser must follow the redirect back to the conformance suite callback,
because the suite is the OAuth client/wallet in these tests.

The default wrapper preset is `vci-client-attestation-dpop-simple-unsigned-preauth`, which runs
pre-authorized-code variants only. The wrapper enables browser automation automatically when the
selected module set can include `oid4vci-1_0-issuer-happy-flow-multiple-clients`, because that
positive pre-authorized-code module starts a second-client front-channel authorization request after
the first credential issuance.

Defaults used by `run-issuer-conformance-local.sh` for `all-basic-plan` and the mixed
`vci-client-attestation-dpop-simple-unsigned` preset:

```bash
export OPENID4VCI_CONFORMANCE_BROWSER_AUTOMATION=true
export OPENID4VCI_CONFORMANCE_AUTH_USERNAME="jane@walt.id"
export OPENID4VCI_CONFORMANCE_AUTH_PASSWORD="jane"
export OPENID4VCI_CONFORMANCE_AUTH_TIMEOUT_SECONDS=90
export PLAYWRIGHT_BROWSER=chromium
export PLAYWRIGHT_HEADLESS=true
export PLAYWRIGHT_INSTALL_WITH_DEPS=false
export OPENID4VCI_CONFORMANCE_INSTALL_PLAYWRIGHT=true
```

`PLAYWRIGHT_INSTALL_WITH_DEPS=true` makes Playwright try to install OS packages through the system package
manager. Use it only when the current user can install apt dependencies; otherwise install missing packages
manually, for example `sudo apt-get install libavif13`.

If the Playwright install task hangs or the browser is already installed, skip that step:

```bash
export OPENID4VCI_CONFORMANCE_INSTALL_PLAYWRIGHT=false
```

Metadata-only and single happy-flow pre-authorized-code runs do not need browser automation. Keep it
disabled when running those narrower module selections if you want to avoid Playwright setup:

```bash
export OPENID4VCI_CONFORMANCE_BROWSER_AUTOMATION=false
```

For issuer-initiated variants, each conformance module exposes its own `credential_offer_endpoint` and waits
until an issuer credential offer is delivered there. The runner creates a fresh issuer2 credential offer for
that module, then forwards either the raw `credential_offer` JSON or the inner HTTPS `credential_offer_uri`
from an `openid-credential-offer://` deep link to that suite endpoint. This avoids reusing a single-use
pre-authorized code across multiple conformance modules.

For pre-authorized-code variants, the wrapper presets set `OPENID4VCI_CONFORMANCE_STATIC_TX_CODE=493536` by
default. The runner sends that same value to issuer2 as `txCodeValue` when creating the offer, and to the
conformance suite as `vci.static_tx_code`, so the suite can continue without waiting for manual `/tx_code`
input.
