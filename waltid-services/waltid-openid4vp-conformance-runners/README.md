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

3. **Keycloak** at `http://keycloak.localhost:8080` (for authorization_code flows)

### Running Tests

```bash
cd waltid-unified-build

# VCI Issuer tests
export OPENID4VCI_CONFORMANCE_CREDENTIAL_ISSUER_URL="https://YOUR-NGROK.ngrok-free.app/openid4vci"
export OPENID4VCI_CONFORMANCE_CLIENT_ATTESTER_JWKS_FILE="$PWD/waltid-identity/waltid-services/waltid-openid4vp-conformance-runners/src/test/resources/keys/attester-key.json"
./gradlew :waltid-services:waltid-openid4vp-conformance-runners:test --tests "id.walt.openid4vp.conformance.IssuerConformanceTests"

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
- Config: issuer URL, credential configuration ID, client-attestation issuer, attester JWKS, DPoP client JWKS, and an issuer2-generated `credential_offer_uri`
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
| Tests stuck in WAITING | Manual OAuth login required for authorization_code flows |
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
