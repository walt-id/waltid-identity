# VCI Issuer Conformance Tests

This document covers setup, execution, and status of OpenID4VCI Issuer conformance tests.

The local stack is pinned to this previously verified hosted-suite build
(not a claim about the current build at `https://conformance.waltid.cloud`):

```text
tag=release-v5.2.3 version=5.2.4 revision=db1080a
```

This is 73 commits after the `release-v5.2.3` tag, so pulling the release tag is
not equivalent. The wrapper builds the server image from the sibling
`conformance-suite` clone and rejects any other revision. A local rebuild has a
new `build_time`; the tag, application version, revision, and test code match.

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
for each new `VCIWaitForCredentialOffer` event and delivers it when the suite
exposes its credential offer endpoint. Wallet-initiated authorization-code
variants have no management offer provider.

### OSS management setup versus protocol batching

The management helper uses the **original single-profile contract** at
`POST /issuer2/credential-offers` for both grants:

```json
{"profileId":"identityCredentialSdJwt","authMethod":"AUTHORIZED"}
```

```json
{
  "profileId": "isoMdl",
  "authMethod": "PRE_AUTHORIZED",
  "txCode": {
    "input_mode": "numeric",
    "length": 6,
    "description": "OpenID4VCI conformance transaction code"
  },
  "txCodeValue": "493536"
}
```

The original 201 receipt includes `offerId`, `profileId`, `authMethod`,
epoch-millisecond `expiresAt`, the complete `credentialOffer` string, and applicable
`txCodeValue`/`issuerStateMode`. The runner leaves value mode, issuer-state mode,
and expiry at the product defaults; it sends tx-code fields only for pre-authorized
offers. HTTP failures and receipts missing required legacy fields fail explicitly;
there is no fallback POST using another contract.

One profile selects one dataset/format. It does **not** limit a Credential Request
to one copy: the suite can send multiple `proofs.jwt` values to `/credential` using
the same offer. `identityCredentialSdJwt` maps to `identity_credential`, and `isoMdl`
maps to `org.iso.18013.5.1.mDL`; the existing HAIP mappings remain separate.

The product's new `credentials[]` management request/flat receipt is covered by
separate issuer HTTP/integration tests, not this conformance script. Passing these
suite runs does not verify multi-selection management, stored-session migration,
or webhook/SSE compatibility. The runner does not change OSS/ES lifecycle policies.

This management adapter is **OSS-specific**: it takes the issuer URL's scheme and
authority and appends `/issuer2/credential-offers`. An ES protocol URL alone does
not configure ES management targets or authentication. An ES management adapter
is separate work, not part of these commands.

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

2. **Exact conformance-suite checkout.** Use a new, separate clone next to
   `waltid-identity`; do not switch/reset an existing user checkout:

   ```bash
   # From the conformance unified root; destination must not already exist.
   git clone --no-checkout https://gitlab.com/openid/conformance-suite.git conformance-suite-db1080a
   git -C conformance-suite-db1080a switch --detach db1080a4821eac6952beaed369904c862c98dd82
   export CONFORMANCE_SUITE_SOURCE_DIR="$PWD/conformance-suite-db1080a"
   git -C "$CONFORMANCE_SUITE_SOURCE_DIR" rev-parse HEAD
   git -C "$CONFORMANCE_SUITE_SOURCE_DIR" status --short
   ```

   The wrapper defaults to the sibling `conformance-suite` directory and checks
   both source revision and server build. In the `testreposforpr` workspace that
   default directory was absent; `openid/conformance-suite` was at `ad1c3a8…`,
   without the required commit object. Merely pointing at that checkout does not
   fix the mismatch. Keep it untouched and set the override to the separate pinned
   clone. A local clone containing the commit may be used as the clone source with
   `--no-hardlinks --no-checkout`, without changing the source checkout. Do not
   bypass revision checks or assume the release tag alone is sufficient.

3. **Host commands:** `docker` with Docker Compose, Maven, Git, `openssl`, `keytool` from the JDK, and `curl`.
   The wrapper uses them to start the suite, generate the local TLS certificate, prepare the temporary
   truststore, and verify connectivity.

4. **issuer2 authentication service.** No conformance-specific Keycloak setup is needed when issuer2 is
   already configured to use its normal reachable Keycloak realm. The wrapper completes the existing
   authorization-code login with the issuer2 integration-test account. Pre-authorized-code variants do not
   use the authorization server.

5. **issuer2** running directly on the host at `0.0.0.0:7005`, built from the
   completed offer-compatibility implementation. Record its commit/artifact and
   effective configuration; the runner branch's own issuer code is not necessarily
   that implementation. Coordinate the instance with its owner before live runs.

6. **Playwright** for authorization-code variants. The wrapper installs Chromium by default, but does not
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

# Capability-disabled baseline: omit this block. For batch verification, enable it.
# batchCredentialIssuance {
#   batchSize = 10
# }

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

Start the completed compatibility issuer separately on the host. The wrapper starts the conformance-suite
and Nginx Docker Compose stack itself; do not run `docker compose up` manually.

In Terminal 1, from the completed issuer build's `waltid-identity` repository root
(not automatically the runner checkout), start issuer2:

```bash
./gradlew :waltid-services:waltid-issuer-api2:run
```

Leave issuer2 running. In Terminal 2, from
`waltid-services/waltid-openid4vp-conformance-runners`, run the wrapper:

```bash
./run-issuer-conformance-local.sh
```

The wrapper creates the local TLS truststore, starts the conformance-suite and
Nginx containers, then runs the issuer conformance test. On the first run it
also builds the suite JAR and image from revision `db1080a`; later runs reuse the
matching build. It does not start issuer2 or Keycloak.

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

### Existing GitHub Actions CI

The reusable `.github/workflows/gradle.yml` runs the combined basic VCI / HAIP /
batch matrix alongside wallet/verifier tests in the existing `conformance` job's
`Run conformance tests` step, using one live Gradle `test` invocation.
The regular build enables issuer testing through conformance eligibility; the
existing `run-issuer-conformance` and `run-haip-conformance` inputs both select it.
Release workflows already set those inputs. Checkout, Gradle setup and browser
installation are shared; there is no separate issuer job.

Issuer2 is built, configured and started before the shared test step. Its port is
7005; verifier2 uses 7003 and the in-process VP/VCI wallets use 7015/7016 (their
public adapters use 7006/7007). There is no wallet/issuer port collision. Startup
rejects an occupied issuer port rather than terminating an unknown process.

Wallet/verifier tests retain the repository's `CONFORMANCE_ALLOW_FAILURE` setting.
`OPENID4VCI_CONFORMANCE_STRICT=true` overrides that policy only for issuer results,
and `OPENID4VCI_CONFORMANCE_REQUIRE_BATCH_PASS=true` still requires executed batch
coverage. One JUnit report covers all roles; issuer result artifacts remain
separate. The live test task uses `--rerun` and disables configuration caching so
it cannot reuse old test results or stale environment settings, while dependency
compilation can stay up to date. Cacheless runs retain `--rerun-tasks`.

If optional issuer setup fails, wallet/verifier tests can still execute and the
job retains the setup failure. No issuer target is exported unless its setup
succeeds. Issuer tests have a 90-minute timeout; the shared step has no new
90-minute limit on wallet/verifier testing. Services and tunnels are stopped by
always-run cleanup. Maven publication waits for the shared job, including issuer
results.

CI invokes the existing Gradle `test` task without an issuer-only `--tests` filter,
so it also discovers `IssuerConformanceTests` when the prepared issuer URL is
exported. It does not use the local wrapper or a new Gradle task. It uses
`conformance.waltid.cloud:443` and checks `/api/server`
for revision `db1080a`, tag `release-v5.2.3`, version `5.2.4` before starting the
issuer. A hosted-suite upgrade requires reviewing the pin and batch exceptions;
never bypass that check to get a green build.

The job builds Issuer2 from the same checked-out Identity revision as the
wallet/verifier tests and records that revision in the issuer artifact. It must
include the completed batch/legacy-offer compatibility implementation. The runner
branch alone does not supply it. CI-only files under `src/test/resources/issuer2/`
enable batch size 10 and define exactly four basic/HAIP SD-JWT VC/mdoc profiles.
The keys and certificates are public test fixtures; do not use them in production.
Certificate validity and key/root matching are checked by `IssuerCiConfigurationTest`.

A Cloudflare Quick Tunnel supplies the public issuer base URL. Authentication
still uses the issuer's configured demo Keycloak and the existing Jane test user.
The Keycloak client must allow the tunnel's
`https://<assigned-host>.trycloudflare.com/openid4vci/external/oauth/callback`.
Client registration/redirect permissions are an operator prerequisite, not changed
by this workflow. A callback rejection or demo outage is a real CI failure to
diagnose, not a reason to skip authorization-code coverage.

CI selects metadata, positive and negative modules, including batch, for both
formats, supported grants/initiation flows, client attestation, DPoP, simple
unsigned authorization requests, and plain/encrypted responses. Dedicated FAPI
modules remain outside this run. Existing runner exclusions are preserved.
The Cloudflare-backed issuer CI step additionally excludes
`oid4vci-1_0-issuer-happy-flow-additional-requests`: its TLS probes reach the public
Quick Tunnel edge, which accepted TLS 1.0/1.1 and a disallowed TLS 1.2 cipher in
the CI run. The exclusion covers the entire module, not just individual TLS
assertions, and is reported as a coverage gap in the job summary. This CI run
does not establish TLS conformance. Local runs keep the module enabled; remove
the CI-only exclusion when a compliant public TLS endpoint is available.

Issuer strict mode and `REQUIRE_BATCH_PASS=true` are explicit, independent of the
shared wallet/verifier soft-fail setting. The Kotlin runner enforces successful selected variants and executed
batch coverage; CI also rejects missing/empty results. The configured matrix
selects 20 variants, with 16 applicable batch variants and four encrypted-HAIP
variants where batch is not offered at this suite pin. Skipped batch execution
does not establish coverage. Suite and metadata preflight checks use `curl`/`jq`
directly in the workflow; there is no separate Python validation layer.

`Publish test report` includes `IssuerConformanceTests` in the same JUnit check as
wallet/verifier tests. It reports the aggregate issuer matrix test, not a separate
JUnit test for each suite module; the existing passed/skipped detail filters are
unchanged. `Publish OpenID conformance summary` includes all four roles, with issuer
variant statuses, clickable plan links and batch coverage counts taken from the
runner's `matrix.json`. Missing results are reported explicitly, and issuer testing
that was not requested is labelled as such. Missing batch classifications are
`UNKNOWN`, never inferred as passed. The existing strict/batch failure policies
are unchanged; this step only publishes their results.

The
`issuer-conformance-basic-haip-batch` artifact contains suite/Identity revision
information, batch classifications and result identifiers/statuses (including plan and test IDs for
looking up suite logs). Error bodies, raw issuer/tunnel/test logs, JUnit output,
rendered configuration and test keys are not uploaded in this artifact. The shared
JUnit check includes issuer results. The issuer and tunnel are stopped on
completion or failure.

Focused checks (no live conformance):

```bash
# From the unified-build root:
./gradlew :waltid-services:waltid-openid4vp-conformance-runners:test \
  -PskipLiveConformance=true \
  --tests id.walt.openid4vp.conformance.IssuerCiConfigurationTest
```

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

`all-basic-plan` is the suite's complete theoretical 288-variant base matrix.
It includes `private_key_jwt` and mTLS client authentication as well as mTLS
sender constraints. The current wrapper does not provision issuer2 client
registrations or client certificates for those combinations. Use the default
client-attestation/DPoP preset for the supported issuer2 setup, or add that
material before treating `all-basic-plan` as executable end to end.

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

CI publishes result summaries into the GitHub Actions job summary. The issuer CI
phase explicitly disables soft-fail; wallet/verifier settings are unchanged.
Outside that phase, soft-fail is controlled by `CONFORMANCE_ALLOW_FAILURE` (see the
module [README](../README.md#ci-summaries-and-soft-fail)); locally you can still use
`OPENID4VCI_CONFORMANCE_STRICT=false` for exploration.
Result states have these meanings:

- `generated`: variant was generated but not executed, usually discovery-only mode
- `suite_invalid`: the suite rejected the variant before creating a plan
- `not_applicable`: the suite created a plan with no modules
- `blocked`: required local setup is missing, such as an offer, login automation, mTLS material, or a reachable endpoint
- `failed`: suite modules ran but did not pass
- `passed`: all executed modules were accepted; ordinary runs can include legitimate
  capability-based `SKIPPED` results, so this alone does not establish batch coverage

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
export OPENID4VCI_CONFORMANCE_EXCLUDED_MODULES=""

# Opt-in batch acceptance: each applicable selected variant must execute batch and pass.
# Use only for batch-enabled verification, not ordinary baseline/discovery runs.
export OPENID4VCI_CONFORMANCE_REQUIRE_BATCH_PASS=true

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

Browser failures include the last browser URL and up to 12 recent main-frame
navigation events (destinations, HTTP statuses, and transport error codes).
Chrome/Firefox error pages are detected without waiting for the full login timeout.
URL user information, query parameters, and fragments are removed from these
diagnostics; page HTML and request bodies are not captured. Subresource failures
do not fail the login, and HTTP error responses are recorded without overriding
the suite's expected negative-test behavior. There is no automatic OAuth retry.
The matrix `summary.md` includes unaccepted module names, test IDs, and their
errors; `results.json` retains the per-module details. A browser failure can leave
the suite `WAITING` and the variant `BLOCKED`, even without a failed suite assertion.

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

Revision `db1080a` fixes the old pre-authorized multiple-client offer reuse, so
`oid4vci-1_0-issuer-happy-flow-multiple-clients` now runs for both grant types.
For pre-authorized multiple-client tests, the runner delivers a fresh offer for
each `VCIWaitForCredentialOffer` log event, even when the suite reuses the same
endpoint URL for client 2. Repeated polls of the same event do not resend offers.
The runner still excludes six client-attestation negative modules only for
`pre_authorization_code` with `client_attestation`: at this revision those modules
either continue after finishing the test, validate the wrong positive response,
or only apply their mutation to PAR. Their
`authorization_code` variants remain enabled and provide the intended coverage.

Specifically, `oid4vci-1_0-issuer-fail-invalid-client-attestation-signature` correctly
validates issuer2's `401 invalid_client` response, but its parent flow continues
into `requestProtectedResource()` after `fireTestFinished()`. This produces
`CreateEmptyResourceEndpointRequestHeaders` called in `WAITING` state, a suite
lifecycle failure rather than an issuer rejection failure.

## Batch Issuance

Revision `db1080a` adds `oid4vci-1_0-issuer-batch-issuance` to the positive group
of the base issuer plan (plain and encrypted) and the HAIP plan (plain only).
Encrypted HAIP does not offer a batch module at this pin.
The local wrapper excludes it by default; the capability-disabled
configuration above is a baseline example, not a statement about every issuer's
shipped default. Check the actual running issuer's metadata.

The module reads `batch_credential_issuance.batch_size`, caps the request at 20,
sends one JWT proof per requested credential, and checks that issuer2 returns
the same credential dataset bound to distinct proof keys. It also checks
format-specific unlinkability properties, including SD-JWT disclosures/time
claims and status references. At this pin, missing batch metadata, no cryptographic
binding, or fewer than two returned credentials leads to `SKIPPED`. Returning fewer
credentials than requested (but at least two) raises a warning rather than necessarily
failing. Record requested and returned counts, not just the aggregate status.

### Baseline, then explicit batch acceptance

First confirm the running issuer's compatibility artifact and use the verified
`CONFORMANCE_SUITE_SOURCE_DIR` from prerequisites. These commands run from this
runner directory, preserve the existing TLS/client-attestation/login setup, and
use different report directories. The wrapper starts infrastructure: do not run it
against another agent's shared stack without coordination.

For a capability-disabled baseline, omit `batchCredentialIssuance` from the test
issuer's effective configuration and verify the metadata omits it. Do not change a
shared issuer's configuration without its owner's agreement.

```bash
unset OPENID4VCI_CONFORMANCE_VARIANT_ID OPENID4VCI_CONFORMANCE_VARIANTS SKIP_LIVE_CONFORMANCE
OPENID4VCI_CONFORMANCE_PRESET=vci-client-attestation-dpop-simple-unsigned \
OPENID4VCI_CONFORMANCE_MATRIX=all \
OPENID4VCI_CONFORMANCE_MODULE_GROUPS=metadata,positive,negative \
OPENID4VCI_CONFORMANCE_MODULES='' \
OPENID4VCI_CONFORMANCE_EXCLUDED_MODULES=oid4vci-1_0-issuer-batch-issuance \
OPENID4VCI_CONFORMANCE_REQUIRE_BATCH_PASS=false \
OPENID4VCI_CONFORMANCE_STRICT=true \
OPENID4VCI_CONFORMANCE_STATIC_TX_CODE=493536 \
OPENID4VCI_CONFORMANCE_REPORT_DIR="$PWD/build/reports/issuer-baseline" \
./run-issuer-conformance-local.sh
```

For batch verification, enable `batchCredentialIssuance { batchSize = 10 }` on the
test issuer and verify served metadata contains `batch_credential_issuance.batch_size`
at least 2. Use a proof-bound profile without OSS's preconfigured-status batch
restriction. This is still a single-profile management offer, not `credentials[]`.

```bash
unset OPENID4VCI_CONFORMANCE_VARIANT_ID OPENID4VCI_CONFORMANCE_VARIANTS SKIP_LIVE_CONFORMANCE
OPENID4VCI_CONFORMANCE_PRESET=vci-client-attestation-dpop-simple-unsigned \
OPENID4VCI_CONFORMANCE_MATRIX=all \
OPENID4VCI_CONFORMANCE_MODULE_GROUPS=positive \
OPENID4VCI_CONFORMANCE_MODULES=oid4vci-1_0-issuer-batch-issuance \
OPENID4VCI_CONFORMANCE_EXCLUDED_MODULES='' \
OPENID4VCI_CONFORMANCE_REQUIRE_BATCH_PASS=true \
OPENID4VCI_CONFORMANCE_STRICT=true \
OPENID4VCI_CONFORMANCE_STATIC_TX_CODE=493536 \
OPENID4VCI_CONFORMANCE_REPORT_DIR="$PWD/build/reports/issuer-batch" \
./run-issuer-conformance-local.sh
```

This preset selects 12 base variants: SD-JWT VC/mdoc, pre-authorized issuer-initiated,
authorization-code issuer-initiated, and authorization-code wallet-initiated, each
with plain/encrypted credential responses. It is not the entire 296-variant matrix.
For a narrow first run, set
`OPENID4VCI_CONFORMANCE_VARIANT_ID=vci-sdjwt-preauth-issuer-clientatt-dpop-simple-unsigned-plain`
alongside the batch command; then clear it for the expanded run. HAIP remains a
separate preset with the certificate/profile configuration described above.

`OPENID4VCI_CONFORMANCE_REQUIRE_BATCH_PASS=true` checks raw module results after
writing the reports. Every selected variant where the pinned suite offers batch must contain an executed batch module
with a test ID and `FINISHED` / `PASSED`, accepted without a runner error. Missing,
excluded, unselected, skipped, blocked, or failed modules fail this acceptance
check even if ordinary aggregation would accept a skip. The only absent-module
exception is HAIP + authorization code + encrypted responses in suite `db1080a`.
It is reported as `NOT_OFFERED_BY_PINNED_SUITE`, never as passed batch coverage.
If a batch result is present even in that combination, it must pass normally.
At least one executed batch pass is required, and all selected variants must have
successful results; the exception cannot hide a failed or blocked encrypted HAIP run.
An encrypted-HAIP-only run therefore cannot satisfy batch acceptance by itself.
Discovery-only mode is
not compatible with this flag. Ordinary capability-based skips and the six scoped
pre-authorized client-attestation suite exclusions remain unchanged.

The console and `summary.md` show batch coverage counts, the summary has a
per-variant **Batch coverage** column, and `matrix.json` includes `batchCoverage`.
`results.json` keeps the raw module outcomes. No external `jq` acceptance check is needed.
The local wrapper disables Gradle configuration caching so each invocation configures
the test process with the current run's environment.

### Combined basic VCI and HAIP in one invocation

With the issuer URL, dedicated HAIP profile IDs, trust anchors, and browser login
configured as above, run from the runner directory:

```bash
unset OPENID4VCI_CONFORMANCE_VARIANT_ID OPENID4VCI_CONFORMANCE_VARIANTS SKIP_LIVE_CONFORMANCE
OPENID4VCI_CONFORMANCE_PRESET=custom \
OPENID4VCI_CONFORMANCE_MATRIX=all \
OPENID4VCI_CONFORMANCE_DISCOVERY_ONLY=false \
OPENID4VCI_CONFORMANCE_FILTER_FAPI_PROFILES=vci,vci_haip \
OPENID4VCI_CONFORMANCE_FILTER_FORMATS=sd_jwt_vc,mdoc \
OPENID4VCI_CONFORMANCE_FILTER_GRANT_TYPES=authorization_code,pre_authorization_code \
OPENID4VCI_CONFORMANCE_FILTER_FLOW_VARIANTS=wallet_initiated,issuer_initiated \
OPENID4VCI_CONFORMANCE_FILTER_CLIENT_AUTH_TYPES=client_attestation \
OPENID4VCI_CONFORMANCE_FILTER_SENDER_CONSTRAINTS=dpop \
OPENID4VCI_CONFORMANCE_FILTER_AUTH_REQUEST_TYPES=simple \
OPENID4VCI_CONFORMANCE_FILTER_REQUEST_METHODS=unsigned \
OPENID4VCI_CONFORMANCE_FILTER_CREDENTIAL_ENCRYPTION=plain,encrypted \
OPENID4VCI_CONFORMANCE_MODULE_GROUPS=metadata,positive,negative \
OPENID4VCI_CONFORMANCE_MODULES='' \
OPENID4VCI_CONFORMANCE_EXCLUDED_MODULES='' \
OPENID4VCI_CONFORMANCE_REQUIRE_BATCH_PASS=true \
OPENID4VCI_CONFORMANCE_STRICT=true \
OPENID4VCI_CONFORMANCE_STATIC_TX_CODE=493536 \
OPENID4VCI_CONFORMANCE_REPORT_DIR="$PWD/build/reports/issuer-combined" \
./run-issuer-conformance-local.sh
```

This selects 20 variants (12 basic VCI and 8 HAIP), excluding dedicated FAPI modules.
A successful run reports 16 batch passes and 4 encrypted HAIP variants where batch
is not offered. The runner derives applicability from the selected variants, not
hardcoded matrix counts, so narrowed runs work too. Update the applicability rule
when upgrading the pinned suite if its HAIP encrypted plan gains batch support.

Retain the raw per-module status/result and suite log URLs from `results.json`.
Also record the runner revision/diff, issuer commit/artifact and profile/configuration
versions, suite source revision and `/api/server` build, served batch metadata,
selected variants/modules, and exclusions with reasons. Preserve requested-proof
and returned-credential counts from the batch module's suite log. Redact tokens,
private keys, credentials, and authentication secrets before sharing evidence.

Earlier suspicions about generated IDs, precise timestamps, and status references
are not established defects of the completed compatibility build. Reproduce a
failure against that build before coordinating a fix with its owning agent. Do not
alter issuer lifecycle, authorization, status allocation, or notification policies
in the runner. The suite's use of distinct proof keys does not test or prohibit
the product's separate duplicate-holder-key support.

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
