# VCI Issuer Conformance Tests

This document covers setup, execution, and status of OpenID4VCI Issuer conformance tests.

## Test Plan

| Profile | Test Plan | Variants |
|---------|-----------|----------|
| Base VCI issuer | `oid4vci-1_0-issuer-test-plan` | 288 generated combinations |
| HAIP VCI issuer | `oid4vci-1_0-issuer-haip-test-plan` | 8 generated combinations |

`IssuerVariantMatrix.all()` contains all 296 variants. Wrapper presets select either the base or HAIP
profile; they do not mix both profiles unless a direct Gradle invocation is explicitly left unfiltered.

---

## Matrix Behavior

The runner creates one conformance-suite plan for each selected matrix variant.
The complete matrix, presets, filters, and result states are documented in this
guide. The resulting config includes:

- `vci.credential_issuer_url`
- `vci.credential_configuration_id`
- `vci.client_attestation_issuer`
- `vci.client_attester_keys_jwks`
- `client_attestation.issuer`
- `client_attestation.attester_jwks`
- `client.jwks` and `client2.jwks` for DPoP

For issuer-initiated variants, the runner creates a fresh issuer2 credential offer
for each conformance module and delivers it when the suite exposes its credential
offer endpoint.

The conformance suite does not tell issuer2 that a credential request belongs to
HAIP. issuer2 selects HAIP behavior through the credential configuration/profile
addressed by `vci.credential_configuration_id`. For HAIP variants, the runner can
use separate credential configuration IDs:

- `OPENID4VCI_CONFORMANCE_HAIP_SD_JWT_CREDENTIAL_CONFIGURATION_ID`
- `OPENID4VCI_CONFORMANCE_HAIP_MDOC_CREDENTIAL_CONFIGURATION_ID`

If these variables are unset, HAIP variants fall back to the base SD-JWT VC and
mdoc credential configuration IDs.

---

## Prerequisites

1. **Local conformance hostname** available at `localhost.emobix.co.uk`, as used by the existing
   conformance-suite setup. The wrapper does not modify `/etc/hosts`.

   Verify that it resolves to a loopback address:

   ```bash
   getent hosts localhost.emobix.co.uk
   ```

2. **Host commands:** `docker` with Docker Compose, `openssl`, `keytool` from the JDK, and `curl`.
   The wrapper uses them to start the suite, generate the local TLS certificate, prepare the temporary
   truststore, and verify connectivity.

3. **issuer2 authentication service.** No conformance-specific Keycloak setup is needed when issuer2 is
   already configured to use its normal reachable Keycloak realm. The wrapper completes the existing
   authorization-code login with the issuer2 integration-test account. Pre-authorized-code variants do not
   use the authorization server.

4. **issuer2** running directly on the host at `0.0.0.0:7005`.

5. **Playwright** for authorization-code variants. The wrapper installs Chromium by default, but does not
   install operating-system packages because Gradle cannot answer an interactive `sudo` prompt. Provision
   missing browser libraries once in an interactive terminal before running the wrapper.

---

## Setup

### 1. Verify issuer2 Keycloak configuration

For authorization-code variants, verify that issuer2's already-configured Keycloak client allows this redirect URI:

```text
https://localhost.emobix.co.uk:9443/openid4vci/external/oauth/callback
```

Authorization-code automation uses the existing integration-test account:

```text
jane@walt.id / jane
```

### 2. Configure Issuer2

In `waltid-services/waltid-issuer-api2`, configure these local conformance
defaults in the following two files.

`config/web.conf`:

```hocon
webHost = "0.0.0.0"
webPort = "7005"
```

`config/issuer-service.conf`:

```hocon
baseUrl = "https://localhost.emobix.co.uk:9443"

ciTokenKey = """{"type":"jwk","jwk":{"kty":"EC","d":"KJ4k3Vcl5Sj9Mfq4rrNXBm2MoPoY3_Ak_PIR_EgsFhQ","crv":"P-256","x":"G0RINBiF-oQUD3d5DGnegQuXenI29JDaMGoMvioKRBM","y":"ed3eFGs2pEtrp7vAZ7BLcbrUtpKkYWAT2JPUQK4lN4E"}}"""

credentialEncryptionKey = """{"type":"jwk","jwk":{"kty":"EC","d":"ZSHgIcRvbwV9s224kHUaFqkEPShCAdwXocGl_w3M42Q","crv":"P-256","kid":"issuer2-credential-encryption-key","x":"GWKpdL3jPoPJ5wKgSA-jxS2jgp-ZUDE6sIQbeB86vF0","y":"F3xAwH96_xVciV7mFQslU_eRQgP-5pSZiNf8bjMoGfo"}}"""

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

These private keys and certificates are conformance fixtures and must not be
used in production.

### 3. Configure HAIP Issuer Profiles

HAIP credential checks validate the issued credential, not an extra request flag.
For SD-JWT VC, the issued JWS must contain an `x5c` certificate chain in the
header. Configure the issuer2 credential profile used for HAIP with:

- an `issuerKeyId` whose key matches the leaf certificate public key
- `issuerDid = null` so issuer2 uses the credential issuer URL as the issuer ID
- `x5Chain` containing the leaf certificate and any intermediate certificates

Use dedicated HAIP credential configuration IDs when the same issuer2 instance
also needs to run base VCI DID-signed profiles:

```bash
export OPENID4VCI_CONFORMANCE_HAIP_SD_JWT_CREDENTIAL_CONFIGURATION_ID="identity_credential_haip"
export OPENID4VCI_CONFORMANCE_HAIP_MDOC_CREDENTIAL_CONFIGURATION_ID="org.iso.18013.5.1.mDL.haip"
```

For issuer-initiated HAIP runs with these default IDs, create issuer2 profiles
with these IDs:

- `identityCredentialHaipSdJwt`
- `isoMdlHaip`

The HAIP trust anchors supplied to the conformance suite must validate the
certificate chain that issuer2 puts into the credential:

```bash
export OPENID4VCI_CONFORMANCE_CREDENTIAL_TRUST_ANCHOR_PEM_FILE=/path/to/credential-root-ca.pem
export OPENID4VCI_CONFORMANCE_STATUS_LIST_TRUST_ANCHOR_PEM_FILE=/path/to/status-list-root-ca.pem
```

### 4. Start Services

Start issuer2 separately on the host. The wrapper starts the conformance-suite
and Nginx Docker Compose stack itself; do not run `docker compose up` manually.

In Terminal 1, from the `waltid-identity` repository root, start issuer2:

```bash
./gradlew :waltid-services:waltid-issuer-api2:run
```

Leave issuer2 running. In Terminal 2, from
`waltid-services/waltid-openid4vp-conformance-runners`, run the wrapper:

```bash
./run-issuer-conformance-local.sh
```

The wrapper creates the local TLS truststore, starts the conformance-suite and
Nginx containers, then runs the issuer conformance test. It does not start
issuer2 or Keycloak.

It copies the committed `conformance-truststore.jks` to
`build/conformance/conformance-truststore.jks` before importing the generated
local certificate. The committed truststore remains unchanged. Do not include
local runtime artifacts such as `build/` or `mongo/` in a handover commit.

The wrapper verifies issuer metadata through Nginx itself. Once the wrapper has
started the local stack, you can verify the externally visible issuer metadata:

```bash
curl -ksf "https://localhost.emobix.co.uk:9443/.well-known/openid-credential-issuer/openid4vci"
```

---

## Run Locally

The wrapper resolves the standalone `waltid-identity` Gradle root from its own
location. The commands below intentionally use the runner directory so all
relative paths, including HAIP certificate paths, are unambiguous.

```bash
# From the waltid-identity repository root. If using the unified build,
# first run: cd waltid-identity
cd waltid-services/waltid-openid4vp-conformance-runners

# Default run: 12 variants, metadata and positive modules.
./run-issuer-conformance-local.sh
```

Do not use `./gradlew build` as the local conformance command. It neither starts
the conformance Docker stack nor issuer2, and the issuer conformance test is
skipped when no issuer target is configured.

### Default Selection

With no selection variables set, the wrapper uses
`vci-client-attestation-dpop-simple-unsigned` and runs the
`metadata,positive` module groups. This produces 12 valid variants:

- 2 credential formats: `sd_jwt_vc`, `mdoc`
- 3 grant/flow pairs: `authorization_code` with both flow variants, and
  `pre_authorization_code` with `issuer_initiated`
- 2 credential-response modes: `plain`, `encrypted`
- `client_attestation`, `dpop`, `simple`, and `unsigned` for the remaining axes

The invalid `pre_authorization_code` + `wallet_initiated` pair is not generated.
The wrapper also enables strict result checking, the static transaction code,
browser automation, Jane's test credentials, and Playwright installation.

On success, inspect the suite at `https://localhost.emobix.co.uk:8443` and the
matrix reports under `build/reports/openid-conformance/vci-issuer`.

### Change the Selection

Run every module returned by the conformance plan for the default 12 variants:

```bash
OPENID4VCI_CONFORMANCE_MODULE_GROUPS=all \
  ./run-issuer-conformance-local.sh
```

Run every generated base-plan variant and every returned module:

```bash
OPENID4VCI_CONFORMANCE_PRESET=all-basic-plan \
OPENID4VCI_CONFORMANCE_MODULE_GROUPS=all \
  ./run-issuer-conformance-local.sh
```

Use `OPENID4VCI_CONFORMANCE_PRESET=custom` with the filter variables in
[Useful Controls](#useful-controls) for a narrower selection.

### Other Execution Modes

```bash
# A remote issuer can be selected explicitly.
export OPENID4VCI_CONFORMANCE_CREDENTIAL_ISSUER_URL="https://issuer.example.com/openid4vci"
./run-issuer-conformance-local.sh

# Advanced diagnosis only. This does not prepare Docker, Nginx, or the truststore.
export OPENID4VCI_CONFORMANCE_CREDENTIAL_ISSUER_URL="https://localhost.emobix.co.uk:9443/openid4vci"
export OPENID4VCI_CONFORMANCE_CLIENT_ATTESTER_JWKS_FILE="$PWD/src/test/resources/keys/attester-key.json"
export OPENID4VCI_CONFORMANCE_BROWSER_AUTOMATION=true
export OPENID4VCI_CONFORMANCE_AUTH_USERNAME="jane@walt.id"
export OPENID4VCI_CONFORMANCE_AUTH_PASSWORD="jane"
../../gradlew :waltid-services:waltid-openid4vp-conformance-runners:installPlaywrightBrowsers
../../gradlew :waltid-services:waltid-openid4vp-conformance-runners:test \
  --tests "id.walt.openid4vp.conformance.IssuerConformanceTests.runIssuerConformanceTests"
```

---

### Execution Flow

1. The test fetches issuer metadata from `/.well-known/openid-credential-issuer/openid4vci`.
2. The runner generates and filters the base or HAIP VCI issuer matrix selected by the preset.
3. The runner creates the corresponding issuer plan for each selected variant.
4. Issuer-initiated modules receive a fresh issuer2 credential offer.
5. The runner executes the suite modules and writes matrix reports.

---

## Variant Matrix

The base `oid4vci-1_0-issuer-test-plan` contributes 288 valid
`fapi_profile=vci` variants. Its selectable axes are:

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

The base plan permits `pre_authorization_code` only with
`issuer_initiated`. `openid` and `fapi_response_mode` are not matrix axes because
the suite marks them inapplicable to `fapi_profile=vci`. The suite spelling is
`pre_authorization_code`.

The HAIP `oid4vci-1_0-issuer-haip-test-plan` contributes 8 variants:

- `fapi_profile=vci_haip`
- `credential_format=sd_jwt_vc,mdoc`
- `vci_grant_type=authorization_code`
- `vci_authorization_code_flow_variant=issuer_initiated,wallet_initiated`
- `client_auth_type=client_attestation`
- `sender_constrain=dpop`
- `authorization_request_type=simple`
- `fapi_request_method=unsigned`
- `vci_credential_encryption=plain,encrypted`

`IssuerVariantMatrix.all()` contains all 296 variants. Wrapper presets select a
single profile: base presets select `vci`; the HAIP preset selects `vci_haip`.
An unfiltered direct Gradle invocation selects the complete 296-variant matrix.

## Environment Variables

| Variable | Description | Example |
|----------|-------------|---------|
| `OPENID4VCI_CONFORMANCE_CREDENTIAL_ISSUER_URL` | Full issuer URL; defaults to the local proxy | `https://localhost.emobix.co.uk:9443/openid4vci` |
| `OPENID4VCI_CONFORMANCE_PRESET` | Matrix preset; defaults to the 12-variant client-attestation/DPoP selection | `all-basic-plan` |
| `OPENID4VCI_CONFORMANCE_MODULE_GROUPS` | Module groups; defaults to metadata and positive modules | `metadata,positive` or `all` |
| `OPENID4VCI_CONFORMANCE_SD_JWT_CREDENTIAL_CONFIGURATION_ID` | SD-JWT credential config | `identity_credential` |
| `OPENID4VCI_CONFORMANCE_MDOC_CREDENTIAL_CONFIGURATION_ID` | mDOC credential config | `org.iso.18013.5.1.mDL` |
| `OPENID4VCI_CONFORMANCE_HAIP_SD_JWT_CREDENTIAL_CONFIGURATION_ID` | HAIP SD-JWT credential config; defaults to the base SD-JWT ID | `identity_credential_haip` |
| `OPENID4VCI_CONFORMANCE_HAIP_MDOC_CREDENTIAL_CONFIGURATION_ID` | HAIP mdoc credential config; defaults to the base mdoc ID | `org.iso.18013.5.1.mDL.haip` |
| `OPENID4VCI_CONFORMANCE_CREDENTIAL_TRUST_ANCHOR_PEM_FILE` | Root/intermediate trust anchor PEM for HAIP credential x5c validation | `/path/to/credential-root-ca.pem` |
| `OPENID4VCI_CONFORMANCE_STATUS_LIST_TRUST_ANCHOR_PEM_FILE` | Root/intermediate trust anchor PEM for HAIP status-list validation | `/path/to/status-list-root-ca.pem` |
| `OPENID4VCI_CONFORMANCE_CLIENT_ATTESTER_JWKS_FILE` | Private client-attester JWK/JWKS used by the conformance suite to sign client attestation JWTs | `src/test/resources/keys/attester-key.json` |
| `OPENID4VCI_CONFORMANCE_AUTHORIZATION_SERVER` | External auth server | (optional) |

## Client Attestation Keys

The issuer runner uses `client_attestation` by default. The default private attester key includes an `x5c` chain so the conformance suite can create a valid `OAuth-Client-Attestation` JWT:

```text
src/test/resources/keys/attester-key.json
```

issuer2 can verify the same attestation in either of these modes:

| issuer2 verification method | Test resource to configure |
|-----------------------------|----------------------------|
| `static-jwk` | `src/test/resources/keys/attester-public-jwk.json` |
| `x509-chain` | `src/test/resources/certs/root-ca.pem` as `trustedRootCertificatesPem` |

The EUDI PID root certificate can only be used if the attester JWK also has a leaf certificate/private key chain issued under that root. A trusted root PEM by itself is not enough to generate a valid client attestation JWT.

---

## HAIP Certificate Material

HAIP validates the `x5c` certificate chain in the issued credential. The
credential trust anchor is separate from the client-attestation trust root.

The committed AT test certificates are:

- [HAIP credential root](../src/test/resources/certs/issuer2-haip-root-ca.pem), supplied to the conformance suite as a trust anchor
- [HAIP credential leaf](../src/test/resources/certs/issuer2-haip-leaf.pem), placed in issuer2's `defaultHaipIssuerX5chain`

Do not put the root certificate into issuer2's `x5Chain`. It must contain the
leaf and any intermediates only. The SD-JWT leaf key matches
`defaultHaipIssuerKey`; HAIP mdoc uses a separate Document Signer leaf for the
same key because the mdoc EKU must not be reused for SD-JWT.

From the runner directory, verify the committed chain before a HAIP run:

```bash
openssl verify \
  -CAfile src/test/resources/certs/issuer2-haip-root-ca.pem \
  src/test/resources/certs/issuer2-haip-leaf.pem
```

The command must print `issuer2-haip-leaf.pem: OK`.

The CA private key is intentionally not committed. When these test
certificates expire, generate a new test CA and purpose-specific leaves
offline, then update issuer2's inline chains and these public runner fixtures.

### Full HAIP Run Without Dedicated FAPI Modules

Run this command from the runner directory to execute metadata, positive, and
negative modules for all eight HAIP variants. It intentionally excludes the
dedicated FAPI module group.

```bash
export OPENID4VCI_CONFORMANCE_CREDENTIAL_ISSUER_URL="https://localhost.emobix.co.uk:9443/openid4vci" && \
export OPENID4VCI_CONFORMANCE_PRESET="vci-haip-client-attestation-dpop-simple-unsigned" && \
export OPENID4VCI_CONFORMANCE_MATRIX="all" && \
export OPENID4VCI_CONFORMANCE_MODULE_GROUPS="metadata,positive,negative" && \
export OPENID4VCI_CONFORMANCE_HAIP_SD_JWT_CREDENTIAL_CONFIGURATION_ID="identity_credential_haip" && \
export OPENID4VCI_CONFORMANCE_HAIP_MDOC_CREDENTIAL_CONFIGURATION_ID="org.iso.18013.5.1.mDL.haip" && \
export OPENID4VCI_CONFORMANCE_CREDENTIAL_TRUST_ANCHOR_PEM_FILE="$PWD/src/test/resources/certs/issuer2-haip-root-ca.pem" && \
export OPENID4VCI_CONFORMANCE_STATUS_LIST_TRUST_ANCHOR_PEM_FILE="$PWD/src/test/resources/certs/issuer2-haip-root-ca.pem" && \
export OPENID4VCI_CONFORMANCE_BROWSER_AUTOMATION="true" && \
export OPENID4VCI_CONFORMANCE_AUTH_USERNAME="jane@walt.id" && \
export OPENID4VCI_CONFORMANCE_AUTH_PASSWORD="jane" && \
export OPENID4VCI_CONFORMANCE_AUTH_TIMEOUT_SECONDS="90" && \
export OPENID4VCI_CONFORMANCE_INSTALL_PLAYWRIGHT="false" && \
unset OPENID4VCI_CONFORMANCE_VARIANT_ID \
      OPENID4VCI_CONFORMANCE_VARIANTS \
      OPENID4VCI_CONFORMANCE_MODULES && \
./run-issuer-conformance-local.sh
```

Reuse the same root for `OPENID4VCI_CONFORMANCE_STATUS_LIST_TRUST_ANCHOR_PEM_FILE`
only when issuer2 signs its status-list JWT under that CA; otherwise supply the
separate status-list root. Set `OPENID4VCI_CONFORMANCE_MODULE_GROUPS=all` to
also run the dedicated FAPI module group.

## Results and Selection Controls

The runner writes artifacts to `build/reports/openid-conformance/vci-issuer`
(override with `OPENID4VCI_CONFORMANCE_REPORT_DIR`):

```text
matrix.json
results.json
summary.md
```

CI publishes these summaries into the GitHub Actions job summary. Soft-fail is
controlled by `CONFORMANCE_ALLOW_FAILURE` (see the module
[README](../README.md#ci-summaries-and-soft-fail)); locally you can still use
`OPENID4VCI_CONFORMANCE_STRICT=false` for exploration.
Result states have these meanings:

- `generated`: variant was generated but not executed, usually discovery-only mode
- `suite_invalid`: the suite rejected the variant before creating a plan
- `not_applicable`: the suite created a plan with no modules
- `blocked`: required local setup is missing, such as an offer, login automation, mTLS material, or a reachable endpoint
- `failed`: suite modules ran but did not pass
- `passed`: suite modules ran and passed

### Useful Controls

```bash
# Wrapper presets
export OPENID4VCI_CONFORMANCE_PRESET=vci-client-attestation-dpop-simple-unsigned-preauth
export OPENID4VCI_CONFORMANCE_PRESET=all-basic-plan
export OPENID4VCI_CONFORMANCE_PRESET=vci-client-attestation-dpop-simple-unsigned
export OPENID4VCI_CONFORMANCE_PRESET=vci-haip-client-attestation-dpop-simple-unsigned
export OPENID4VCI_CONFORMANCE_PRESET=custom

# Generate and report the matrix without running suite modules
export OPENID4VCI_CONFORMANCE_MATRIX=discovery

# Run one generated variant
export OPENID4VCI_CONFORMANCE_VARIANT_ID="vci-sdjwt-preauth-issuer-clientatt-dpop-simple-unsigned-plain"

# Filter comma-separated dimension values when using the custom preset
export OPENID4VCI_CONFORMANCE_FILTER_FAPI_PROFILES="vci"
export OPENID4VCI_CONFORMANCE_FILTER_FORMATS="sd_jwt_vc"
export OPENID4VCI_CONFORMANCE_FILTER_GRANT_TYPES="pre_authorization_code"
export OPENID4VCI_CONFORMANCE_FILTER_FLOW_VARIANTS="issuer_initiated"
export OPENID4VCI_CONFORMANCE_FILTER_CLIENT_AUTH_TYPES="client_attestation"
export OPENID4VCI_CONFORMANCE_FILTER_SENDER_CONSTRAINTS="dpop"
export OPENID4VCI_CONFORMANCE_FILTER_AUTH_REQUEST_TYPES="simple"
export OPENID4VCI_CONFORMANCE_FILTER_REQUEST_METHODS="unsigned"
export OPENID4VCI_CONFORMANCE_FILTER_CREDENTIAL_ENCRYPTION="plain"

# Select module groups or exact suite modules
export OPENID4VCI_CONFORMANCE_MODULE_GROUPS="metadata,positive"
export OPENID4VCI_CONFORMANCE_MODULES="oid4vci-1_0-issuer-happy-flow,oid4vci-1_0-issuer-batch-issuance"

# Static transaction code for pre-authorized happy-flow modules
export OPENID4VCI_CONFORMANCE_STATIC_TX_CODE="493536"

# Exploration mode and output controls
export OPENID4VCI_CONFORMANCE_STRICT=false
export OPENID4VCI_CONFORMANCE_REPORT_DIR="$PWD/build/issuer-conformance"
export OPENID4VCI_CONFORMANCE_TIMEOUT_MINUTES=480
```

For progressive conformance work, run the full matrix in exploration mode,
review `summary.md`, then select one blocked or failed variant family with the
filter variables while adding issuer2 support.

## Browser Automation and Credential Offers

For `authorization_code` modules, the runner opens conformance-suite
front-channel authorization URLs with Playwright and completes the existing
Keycloak login. The browser must follow the redirect back to the conformance
suite callback because the suite, not issuer2, is the OAuth client/wallet.

The wrapper defaults for authorization-code runs are:

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

The wrapper installs Chromium without operating-system dependencies because
Playwright's dependency installer invokes interactive `sudo`. Install any
missing libraries in an interactive terminal. If Chromium is already installed,
skip the installation step:

```bash
export OPENID4VCI_CONFORMANCE_INSTALL_PLAYWRIGHT=false
```

Issuer-initiated modules expose a `credential_offer_endpoint`. The runner creates
a fresh issuer2 offer per module and forwards either its raw `credential_offer`
JSON or the inner HTTPS `credential_offer_uri` from the OpenID Credential Offer
deep link. This avoids reuse of a one-time pre-authorized code.

For pre-authorized-code variants, the wrapper sets
`OPENID4VCI_CONFORMANCE_STATIC_TX_CODE=493536` by default and passes it both to
issuer2 while creating the offer and to the conformance suite. This avoids a
manual `/tx_code` interaction.

The wrapper excludes `oid4vci-1_0-issuer-happy-flow-multiple-clients` only for
`pre_authorization_code` variants. The upstream module reuses client 1's consumed
pre-authorized code for client 2; issuer2 correctly rejects it with
`invalid_grant`. The same module remains enabled for `authorization_code`.

For local issuer tests, Docker Nginx exposes
`https://localhost.emobix.co.uk:9443` and proxies to issuer2 at
`http://host.docker.internal:7005`. The conformance-suite container resolves the
hostname through its Docker network alias; Gradle and Playwright use the
published host port.

---

## Troubleshooting

### "Invalid parameter: redirect_uri" in Keycloak
Add `https://localhost.emobix.co.uk:9443/openid4vci/external/oauth/callback` to the client redirect URIs.

### "Connect timed out" errors
Verify issuer2 listens on `0.0.0.0:7005`, then inspect the Nginx logs. Nginx reaches the host through `host.docker.internal`.

### "Unable to fetch credential issuer metadata"
- Check issuer2's `baseUrl` is `https://localhost.emobix.co.uk:9443`
- Run `getent hosts localhost.emobix.co.uk` and confirm the hostname resolves to a loopback address
- After Docker Compose starts, run `curl -ksf https://localhost.emobix.co.uk:8443/api/server`
- From this runner directory, run `docker compose -f docker-compose-walt.yml logs --tail=100 nginx server`

### Metadata path structure
```
✅ /.well-known/openid-credential-issuer/openid4vci
❌ /openid4vci/.well-known/openid-credential-issuer
```

---

## Test Logs

Test results are stored in:
```
build/reports/tests/test/
```

Conformance suite logs can be exported from:
```
https://localhost.emobix.co.uk:8443/log-detail.html?log=<LOG_ID>
```

Stop and remove the local conformance containers when their logs are no longer needed:

```bash
docker compose -f docker-compose-walt.yml down
```
