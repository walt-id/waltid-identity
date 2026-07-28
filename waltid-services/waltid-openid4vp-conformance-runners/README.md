# OpenID4VC Conformance Tests

Conformance test runners for OpenID4VCI and OpenID4VP against the [OpenID Foundation Conformance Suite](https://www.certification.openid.net/).

## Quick Start

### Prerequisites

1. **Local conformance hostname** available at `localhost.emobix.co.uk`, as used by the existing
   conformance-suite setup. No `/etc/hosts` change is performed by the runner.

   Verify that it resolves to a loopback address without changing the host configuration:

   ```bash
   getent hosts localhost.emobix.co.uk
   ```

   After the wrapper starts Docker Compose, verify the suite through the same hostname:

   ```bash
   curl -ksf https://localhost.emobix.co.uk:8443/api/server
   ```

2. **issuer2** running directly on the host with this test-only runtime configuration:
   ```hocon
   webHost = "0.0.0.0"
   webPort = 7005
   baseUrl = "https://localhost.emobix.co.uk:9443"

   ciTokenKey = """{"type":"jwk","jwk":{"kty":"EC","d":"KJ4k3Vcl5Sj9Mfq4rrNXBm2MoPoY3_Ak_PIR_EgsFhQ","crv":"P-256","x":"G0RINBiF-oQUD3d5DGnegQuXenI29JDaMGoMvioKRBM","y":"ed3eFGs2pEtrp7vAZ7BLcbrUtpKkYWAT2JPUQK4lN4E"}}"""
   ```

   Configure `clientAuthenticationConfig` with `preauth-anonymous` and the matching
   `client-attestation` trust root from [VCI Issuer Client Attestation](#vci-issuer-client-attestation).
   The key and certificate committed to this module are test fixtures and must not be used in production.

   The issuer service exposed at `/openid4vci` must advertise both credential configuration IDs used by
   the canonical run:

   - `identity_credential` for SD-JWT VC
   - `org.iso.18013.5.1.mDL` for mdoc

   HAIP issuer runs can use separate credential configuration IDs. Those profiles must be x509-backed:
   configure `x5Chain`, use an `issuerKeyId` matching the leaf certificate, and leave `issuerDid` unset
   so issuer2 signs with the credential issuer URL and emits `x5c` in SD-JWT VC headers.

   It must also support DPoP, client attestation, and plain and encrypted credential responses. Confirm the
   two IDs in `credential_configurations_supported` at:

   ```text
   https://localhost.emobix.co.uk:9443/.well-known/openid-credential-issuer/openid4vci
   ```

3. **Host commands:** `docker` with Docker Compose, `openssl`, `keytool` from the JDK, and `curl`.
   The wrapper uses them to start the suite, generate the local TLS certificate, prepare the temporary
   truststore, and verify connectivity.

4. **issuer2 authentication service.** No conformance-specific Keycloak setup is needed when issuer2 is
   already configured to use its normal reachable Keycloak realm. Authorization-code browser automation uses
   the same public integration-test account as issuer2: `jane@walt.id` / `jane`. The issuer2 Keycloak client
   must already allow this callback URI:

   ```text
   https://localhost.emobix.co.uk:9443/openid4vci/external/oauth/callback
   ```

5. **Playwright** for `authorization_code` flows. The wrapper installs Chromium by default, but does not
   install operating-system packages because Gradle cannot answer an interactive `sudo` prompt. Install
   missing system dependencies once in an interactive terminal, then set
   `OPENID4VCI_CONFORMANCE_INSTALL_PLAYWRIGHT=false` when Chromium is already installed.

### Running Tests

```bash
cd waltid-unified-build

# Default VCI issuer run: 12 variants, metadata and positive modules only.
./waltid-identity/waltid-services/waltid-openid4vp-conformance-runners/run-issuer-conformance-local.sh
```

### Default selection

With no selection variables set, the wrapper uses the `vci-client-attestation-dpop-simple-unsigned` preset
and runs only the `metadata,positive` module groups. This produces 12 valid variants:

- 2 credential formats: `sd_jwt_vc`, `mdoc`
- 3 grant/flow pairs: `authorization_code` with both flow variants, and
  `pre_authorization_code` with `issuer_initiated`
- 2 credential-response modes: `plain`, `encrypted`
- `client_attestation`, `dpop`, `simple`, and `unsigned` for the remaining axes

The invalid `pre_authorization_code` + `wallet_initiated` pair is not generated. Strict result checking,
the static transaction code, browser automation, Jane's test credentials, and Playwright installation are
also enabled by the wrapper.

The first authorization-code run installs Chromium. If Playwright reports missing operating-system libraries,
install them once in an interactive terminal before retrying. On success, inspect the suite at
`https://localhost.emobix.co.uk:8443` and the matrix reports under
`build/reports/openid4vci-issuer-matrix` in this module.

### Changing the selection

Set environment variables on the command to override the defaults. For example, keep the 12 variants but
run every module returned by the conformance plan:

```bash
OPENID4VCI_CONFORMANCE_MODULE_GROUPS=all \
  ./waltid-identity/waltid-services/waltid-openid4vp-conformance-runners/run-issuer-conformance-local.sh
```

Run all 288 generated base-plan variants and every returned module:

```bash
OPENID4VCI_CONFORMANCE_PRESET=all-basic-plan \
OPENID4VCI_CONFORMANCE_MODULE_GROUPS=all \
  ./waltid-identity/waltid-services/waltid-openid4vp-conformance-runners/run-issuer-conformance-local.sh
```

Use `OPENID4VCI_CONFORMANCE_PRESET=custom` with the matrix filter variables documented under
[VCI Issuer Variant Matrix](#vci-issuer-variant-matrix) for any narrower selection.

### Other execution modes

```bash

# A remote issuer can still be selected explicitly.
export OPENID4VCI_CONFORMANCE_CREDENTIAL_ISSUER_URL="https://issuer.example.com/openid4vci"
./waltid-identity/waltid-services/waltid-openid4vp-conformance-runners/run-issuer-conformance-local.sh

# Direct Gradle execution, if the suite, proxy, and truststore are already configured
export OPENID4VCI_CONFORMANCE_CREDENTIAL_ISSUER_URL="https://localhost.emobix.co.uk:9443/openid4vci"
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

### VCI Issuer

The issuer2 runner targets the base `oid4vci-1_0-issuer-test-plan` and the HAIP
`oid4vci-1_0-issuer-haip-test-plan` through generated VCI matrices. It supports
selecting individual variants or filtering matrix axes, and records module
results under `build/reports/openid4vci-issuer-matrix`.

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
| **VCI Issuer** | Base `vci` issuer matrix | `vci_haip` issuer matrix | HAIP uses auth-code, DPoP, client attestation, and x509-backed credentials |
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
| issuer2 is unreachable | Start issuer2 on `0.0.0.0:7005`; Docker Nginx reaches it through `host.docker.internal` |
| Local hostname is unresolved | Run `getent hosts localhost.emobix.co.uk` and confirm it returns a loopback address; the runner does not modify `/etc/hosts` |
| "Invalid redirect_uri" | Add `https://localhost.emobix.co.uk:9443/openid4vci/external/oauth/callback` to the Keycloak client redirect URIs |
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

For the local handover run, issuer2 needs the local Nginx base URL, matching CI token key, and client-attestation trust configuration.

```hocon
baseUrl = "https://localhost.emobix.co.uk:9443"

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

The wrapper copies the committed truststore to `build/conformance/conformance-truststore.jks` before
importing its generated certificate. The committed `conformance-truststore.jks` remains unchanged.
Do not include local runtime artifacts such as `mongo/` or `build/` in a clean handover commit.

## VCI Issuer Variant Matrix

By default, `IssuerConformanceTests` targets the base `oid4vci-1_0-issuer-test-plan` and attempts every generated variant that is defined by that plan's selectable VCI issuer axes:

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

The matrix uses the conformance-suite enum spelling `pre_authorization_code`.

The HAIP issuer matrix targets `oid4vci-1_0-issuer-haip-test-plan` and contains
8 variants:

- `fapi_profile=vci_haip`
- `credential_format=sd_jwt_vc,mdoc`
- `vci_grant_type=authorization_code`
- `vci_authorization_code_flow_variant=issuer_initiated,wallet_initiated`
- `client_auth_type=client_attestation`
- `sender_constrain=dpop`
- `authorization_request_type=simple`
- `fapi_request_method=unsigned`
- `vci_credential_encryption=plain,encrypted`

For HAIP, the runner uses `OPENID4VCI_CONFORMANCE_HAIP_SD_JWT_CREDENTIAL_CONFIGURATION_ID`
and `OPENID4VCI_CONFORMANCE_HAIP_MDOC_CREDENTIAL_CONFIGURATION_ID` when set. If
they are unset, it falls back to the base SD-JWT VC and mdoc configuration IDs.
Point the HAIP IDs at issuer2 profiles that include `x5Chain`; otherwise the suite
will fail SD-JWT VC with `Credential MUST contain an x5c in the header`.
For the default HAIP IDs above, issuer-initiated runs expect issuer2 profile IDs
`identityCredentialHaipSdJwt` and `isoMdlHaip`.

HAIP requires a credential trust anchor. It must be the root CA that issued the
leaf certificate placed in the issued credential's `x5c` header. Do not use the
leaf certificate itself and do not use the client-attestation CA. For the
default HAIP issuer2 leaf certificate, the required root has subject
`CN=walt.id HAIP Conformance Root CA`.
Keep that root out of issuer2's `x5Chain`; `x5Chain` contains the leaf and any
intermediates only.

Store the HAIP root as `src/test/resources/certs/issuer2-haip-root-ca.pem` and
verify the chain before running HAIP:

```bash
openssl verify \
  -CAfile waltid-identity/waltid-services/waltid-openid4vp-conformance-runners/src/test/resources/certs/issuer2-haip-root-ca.pem \
  waltid-identity/waltid-services/waltid-openid4vp-conformance-runners/src/test/resources/certs/issuer2-haip-leaf.pem
```

The command must print `issuer2-haip-leaf.pem: OK`.

The runner test resources contain the generated local HAIP root and leaf. The
root is supplied to the conformance suite; replace the PEM inside issuer2's
`defaultHaipIssuerX5chain` with the leaf. Do not place the root in issuer2's
`x5Chain`.

`issuer2-haip-root-ca.pem`:

```pem
-----BEGIN CERTIFICATE-----
MIIB+TCCAZ+gAwIBAgIUPP3bTiVIoMWqEYExsKjIfhywdlkwCgYIKoZIzj0EAwIw
SjELMAkGA1UEBhMCVVQxEDAOBgNVBAoMB3dhbHQuaWQxKTAnBgNVBAMMIHdhbHQu
aWQgSEFJUCBDb25mb3JtYW5jZSBSb290IENBMB4XDTI2MDcyNDEyMDkxMVoXDTM2
MDcyMTEyMDkxMVowSjELMAkGA1UEBhMCVVQxEDAOBgNVBAoMB3dhbHQuaWQxKTAn
BgNVBAMMIHdhbHQuaWQgSEFJUCBDb25mb3JtYW5jZSBSb290IENBMFkwEwYHKoZI
zj0CAQYIKoZIzj0DAQcDQgAEkgBYLh/iP7MwVAUG8ktgsRrNiEQBeEWJz13az1YZ
M56YrdM9+CNHH88m3JSG5Rj8PDxawabTYzvQG4xG2G3KWqNjMGEwHQYDVR0OBBYE
FIee+SutjFf5dBflQUE5CLl6nztbMB8GA1UdIwQYMBaAFIee+SutjFf5dBflQUE5
CLl6nztbMA8GA1UdEwEB/wQFMAMBAf8wDgYDVR0PAQH/BAQDAgEGMAoGCCqGSM49
BAMCA0gAMEUCIQCErYUpzrjGkK2kU5RXyHULwOdCCSBZY/57ibiqd9MEngIgLbvF
IHgFHl0OkJMBoJwk5MiXG4XDbWMiawsEPCz+4R8=
-----END CERTIFICATE-----
```

`issuer2-haip-leaf.pem`:

```pem
-----BEGIN CERTIFICATE-----
MIICAjCCAamgAwIBAgIUFQHbJ5+yyg0r1YgHoQJbUsLadPMwCgYIKoZIzj0EAwIw
SjELMAkGA1UEBhMCVVQxEDAOBgNVBAoMB3dhbHQuaWQxKTAnBgNVBAMMIHdhbHQu
aWQgSEFJUCBDb25mb3JtYW5jZSBSb290IENBMB4XDTI2MDcyNDEyMDkxMVoXDTM2
MDcyMTEyMDkxMVowQjELMAkGA1UEBhMCVVQxEDAOBgNVBAoMB3dhbHQuaWQxITAf
BgNVBAMMGGlzc3VlcjItaGFpcC1jb25mb3JtYW5jZTBZMBMGByqGSM49AgEGCCqG
SM49AwEHA0IABOBm2DPhn6y/LvSeGz1DHdtQolx9rejw1lXHqcYT7aKVa2Z7QWgv
Lz0nl4KOjstBcowX47VmhUQgaJi/8cMCqaujdTBzMAwGA1UdEwEB/wQCMAAwDgYD
VR0PAQH/BAQDAgeAMBMGA1UdJQQMMAoGCCsGAQUFBwMCMB0GA1UdDgQWBBRbR1/M
shX3RRfHC/W1Ob39VuBKczAfBgNVHSMEGDAWgBSHnvkrrYxX+XQX5UFBOQi5ep87
WzAKBggqhkjOPQQDAgNHADBEAiAjFIeG4s8AAKBhrJUNVyuuKFmkfFy1/RUovRoP
y5p7RQIgFBJqz39KOWx+Exr8cVsvPpO/gpprj5cqF6bjlZv8WMg=
-----END CERTIFICATE-----
```

If the original HAIP root certificate is unavailable in a local test setup,
create a new test-only root and a leaf certificate for the existing
`defaultHaipIssuerKey` JWK:

```bash
CERT_DIR="$PWD/waltid-identity/waltid-services/waltid-openid4vp-conformance-runners/src/test/resources/certs" && \
WORK_DIR="$PWD/build/conformance/issuer2-haip-ca" && \
mkdir -p "$CERT_DIR" "$WORK_DIR" && \
node -e 'const { createPublicKey } = require("crypto"); console.log(createPublicKey({ key: { kty: "EC", crv: "P-256", x: "4GbYM-GfrL8u9J4bPUMd21CiXH2t6PDWVcepxhPtopU", y: "a2Z7QWgvLz0nl4KOjstBcowX47VmhUQgaJi_8cMCqas" }, format: "jwk" }).export({ type: "spki", format: "pem" }));' > "$WORK_DIR/issuer2-haip-public-key.pem" && \
openssl ecparam -name prime256v1 -genkey -noout -out "$WORK_DIR/issuer2-haip-root-ca.key" && \
openssl req -x509 -new -sha256 -days 3650 \
  -key "$WORK_DIR/issuer2-haip-root-ca.key" \
  -out "$CERT_DIR/issuer2-haip-root-ca.pem" \
  -subj '/C=UT/O=walt.id/CN=walt.id HAIP Conformance Root CA' \
  -addext 'basicConstraints=critical,CA:true' \
  -addext 'keyUsage=critical,keyCertSign,cRLSign' && \
openssl x509 -new -sha256 -days 3650 \
  -force_pubkey "$WORK_DIR/issuer2-haip-public-key.pem" \
  -CA "$CERT_DIR/issuer2-haip-root-ca.pem" \
  -CAkey "$WORK_DIR/issuer2-haip-root-ca.key" \
  -CAcreateserial \
  -out "$CERT_DIR/issuer2-haip-leaf.pem" \
  -subj '/C=UT/O=walt.id/CN=issuer2-haip-conformance' \
  -extfile <(printf '%s\n' \
    'basicConstraints=critical,CA:false' \
    'keyUsage=critical,digitalSignature' \
    'extendedKeyUsage=clientAuth') && \
openssl verify -CAfile "$CERT_DIR/issuer2-haip-root-ca.pem" "$CERT_DIR/issuer2-haip-leaf.pem"
```

This preserves `defaultHaipIssuerKey`: the generated leaf uses its existing
public key. Replace the PEM in issuer2's `defaultHaipIssuerX5chain` with the
contents of `issuer2-haip-leaf.pem`. Do not add `issuer2-haip-root-ca.pem` to
`x5Chain`; pass it only to the conformance runner as a trust anchor. The
generated root private key stays under `build/conformance` and must not be
committed.

Run the full HAIP matrix without the dedicated FAPI module group as follows:

```bash
export OPENID4VCI_CONFORMANCE_CREDENTIAL_ISSUER_URL="https://localhost.emobix.co.uk:9443/openid4vci" && \
export OPENID4VCI_CONFORMANCE_PRESET="vci-haip-client-attestation-dpop-simple-unsigned" && \
export OPENID4VCI_CONFORMANCE_MATRIX="all" && \
export OPENID4VCI_CONFORMANCE_MODULE_GROUPS="metadata,positive,negative" && \
export OPENID4VCI_CONFORMANCE_HAIP_SD_JWT_CREDENTIAL_CONFIGURATION_ID="identity_credential_haip" && \
export OPENID4VCI_CONFORMANCE_HAIP_MDOC_CREDENTIAL_CONFIGURATION_ID="org.iso.18013.5.1.mDL.haip" && \
export OPENID4VCI_CONFORMANCE_CREDENTIAL_TRUST_ANCHOR_PEM_FILE="$PWD/waltid-identity/waltid-services/waltid-openid4vp-conformance-runners/src/test/resources/certs/issuer2-haip-root-ca.pem" && \
export OPENID4VCI_CONFORMANCE_STATUS_LIST_TRUST_ANCHOR_PEM_FILE="$PWD/waltid-identity/waltid-services/waltid-openid4vp-conformance-runners/src/test/resources/certs/issuer2-haip-root-ca.pem" && \
export OPENID4VCI_CONFORMANCE_BROWSER_AUTOMATION="true" && \
export OPENID4VCI_CONFORMANCE_AUTH_USERNAME="jane@walt.id" && \
export OPENID4VCI_CONFORMANCE_AUTH_PASSWORD="jane" && \
export OPENID4VCI_CONFORMANCE_AUTH_TIMEOUT_SECONDS="90" && \
export OPENID4VCI_CONFORMANCE_INSTALL_PLAYWRIGHT="false" && \
unset OPENID4VCI_CONFORMANCE_VARIANT_ID \
      OPENID4VCI_CONFORMANCE_VARIANTS \
      OPENID4VCI_CONFORMANCE_MODULES && \
./waltid-identity/waltid-services/waltid-openid4vp-conformance-runners/run-issuer-conformance-local.sh
```

Reuse the same root for `OPENID4VCI_CONFORMANCE_STATUS_LIST_TRUST_ANCHOR_PEM_FILE`
only when issuer2 signs its status-list JWT under that CA; otherwise provide the
separate status-list root certificate.

To run the same eight HAIP variants with every module returned by the plan, including
the dedicated FAPI module group, change the command's module selection to:

```bash
export OPENID4VCI_CONFORMANCE_MODULE_GROUPS="all"
```

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
export OPENID4VCI_CONFORMANCE_PRESET=vci-haip-client-attestation-dpop-simple-unsigned
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

# HAIP-specific credential configuration IDs and required x5c trust anchors
export OPENID4VCI_CONFORMANCE_HAIP_SD_JWT_CREDENTIAL_CONFIGURATION_ID="identity_credential_haip"
export OPENID4VCI_CONFORMANCE_HAIP_MDOC_CREDENTIAL_CONFIGURATION_ID="org.iso.18013.5.1.mDL.haip"
export OPENID4VCI_CONFORMANCE_CREDENTIAL_TRUST_ANCHOR_PEM_FILE="$PWD/waltid-identity/waltid-services/waltid-openid4vp-conformance-runners/src/test/resources/certs/issuer2-haip-root-ca.pem"
export OPENID4VCI_CONFORMANCE_STATUS_LIST_TRUST_ANCHOR_PEM_FILE="$PWD/waltid-identity/waltid-services/waltid-openid4vp-conformance-runners/src/test/resources/certs/issuer2-haip-root-ca.pem"

# Filter conformance-suite modules returned by the selected plan
export OPENID4VCI_CONFORMANCE_MODULE_GROUPS="metadata,positive"
export OPENID4VCI_CONFORMANCE_MODULE_GROUPS="metadata"
export OPENID4VCI_CONFORMANCE_MODULE_GROUPS="positive"
export OPENID4VCI_CONFORMANCE_MODULES="oid4vci-1_0-issuer-happy-flow,oid4vci-1_0-issuer-batch-issuance"

# Static transaction code for pre-authorized happy-flow modules
export OPENID4VCI_CONFORMANCE_STATIC_TX_CODE="493536"

# Executed matrices fail by default unless every selected variant passes.
# Explicitly disable strict mode only for exploratory runs.
export OPENID4VCI_CONFORMANCE_STRICT=false

# Override report location and test timeout
export OPENID4VCI_CONFORMANCE_REPORT_DIR="$PWD/build/issuer-conformance"
export OPENID4VCI_CONFORMANCE_TIMEOUT_MINUTES=480

```

For progressive conformance work, run the full matrix in exploration mode first, review `summary.md`, then cherry-pick one blocked or failed variant family with the filter variables while adding issuer2 support.

### Browser automation for authorization_code issuer tests

The OpenID conformance suite exposes front-channel authorization URLs through its browser interaction API. For
issuer `authorization_code` modules, the OSS runner can now open those URLs with Playwright and complete the
public Keycloak login flow automatically.

The important difference from issuer2's internal integration tests is that the conformance runner does not
intercept the authorization code. The browser must follow the redirect back to the conformance suite callback,
because the suite is the OAuth client/wallet in these tests.

The default wrapper preset is `vci-client-attestation-dpop-simple-unsigned`, with module groups
`metadata,positive`. It selects the 12 variants described in [Default selection](#default-selection).
`run-issuer-conformance-local.sh` always excludes
`oid4vci-1_0-issuer-happy-flow-multiple-clients` from `pre_authorization_code` variants: the upstream
module currently reuses client 1's consumed pre-authorized code for client 2 and receives the correct
`invalid_grant` response. The module remains enabled for `authorization_code` variants. Remove the
targeted wrapper exclusion only after the upstream module requests a fresh credential offer for its
second pre-authorized client or marks that grant variant as inapplicable.

For local issuer tests, Docker Nginx exposes `https://localhost.emobix.co.uk:9443` and proxies requests to
the bare-metal issuer2 process at `http://host.docker.internal:7005`. The conformance-suite container
resolves `localhost.emobix.co.uk` to Nginx through a Docker network alias, while host-side Gradle and
Playwright use the published port. This removes the public tunnel and its connection limits from the
local issuer workflow.

Defaults used by the wrapper for `all-basic-plan` and the mixed
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

The wrapper installs Chromium without operating-system dependencies by default. Gradle cannot safely
provide a password when Playwright invokes `sudo`; run Playwright `install-deps` in an interactive terminal
if the browser reports missing libraries. Set `PLAYWRIGHT_INSTALL_WITH_DEPS=true` only where noninteractive
privileged package installation is explicitly supported.

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
