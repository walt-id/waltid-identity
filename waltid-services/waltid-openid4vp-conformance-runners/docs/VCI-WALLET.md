# OpenID4VCI Wallet Conformance

This runner tests Wallet API2 as the OpenID4VCI wallet. The OpenID
Conformance Suite acts as the credential issuer and authorization server.

The runner is intentionally separate from issuer conformance testing:

- issuer tests call an issuer2 deployment;
- wallet tests configure the suite as the issuer, then drive Wallet API2 through
  its public OpenID4VCI receive APIs;
- the adapter only receives suite credential offers and OAuth callbacks. It does
  not construct PAR, token, nonce, proof, or credential requests itself.

## Prerequisites

For any `client_attestation` context, configure the Wallet API2 instance with
the local, test-only attestation endpoint below before starting it. The runner
starts this endpoint on port `7007`, signs attestations with its bundled test
key, and the suite trusts its corresponding test certificate chain.

```hocon
attestationConfig {
  attesterUrl = "http://127.0.0.1:7007/wallet-instance-attestation/jwk"
  requestBody {
    jwk = "{{public_jwk}}"
  }
}
```

This replaces the default external attester only for local conformance runs.
Do not use the test endpoint or its bundled key material in a deployment.

The local wrapper starts the suite, MongoDB, and Nginx through
`docker-compose-walt.yml`. It requires `docker`, `curl`, `openssl`, and
`keytool`. It creates a stable local TLS certificate and Java truststore under
the runner's `build/` directory; it does not modify `/etc/hosts`.

Prepare that TLS material before starting Wallet API2. This is required because
Wallet API2 is a separate Java process and must trust the self-signed Nginx
certificate served by the suite at `https://localhost.emobix.co.uk:8443`.
From this runner directory, start Wallet API2 with the conformance launcher:

```bash
# Terminal 1
./run-wallet-api2-conformance-local.sh
```

It prepares the local suite, reuses its certificate for repeat runs, and starts
Wallet API2 with the generated truststore. Leave that terminal running. In a
second terminal, return to this runner directory and execute the selected
conformance command. For the baseline run:

```bash
# Terminal 2
./run-wallet-conformance-local.sh
```

To prepare the TLS material and start Wallet API2 manually instead, use:

```bash
OPENID4VCI_WALLET_CONFORMANCE_PREPARE_TLS_ONLY=true \
./run-wallet-conformance-local.sh

CONFORMANCE_RUNNER_DIR="$(pwd)"
cd ../..
JAVA_TOOL_OPTIONS="-Djavax.net.ssl.trustStore=$CONFORMANCE_RUNNER_DIR/build/conformance/conformance-truststore.jks -Djavax.net.ssl.trustStorePassword=changeit -Djavax.net.ssl.trustStoreType=JKS" \
./gradlew :waltid-services:waltid-wallet-api2:run
```

For authorization-code modules, Playwright Chromium is installed by default.
Set `OPENID4VCI_WALLET_CONFORMANCE_INSTALL_PLAYWRIGHT=false` after the browser
has been installed. OS-level Playwright dependencies are deliberately not
installed by the wrapper because that requires interactive `sudo`.

## Run A Baseline Context

From this directory:

```bash
./run-wallet-conformance-local.sh
```

The default context is:

```text
fapi_profile=vci
credential_format=sd_jwt_vc
vci_grant_type=authorization_code
vci_authorization_code_flow_variant=issuer_initiated
vci_credential_offer_variant=by_value
client_auth_type=private_key_jwt
sender_constrain=dpop
authorization_request_type=simple
fapi_request_method=unsigned
vci_credential_issuance_mode=immediate
vci_credential_encryption=plain
```

It runs every module the suite adds for that plan. The browser flow is automated
through Playwright. No Keycloak setup is required: the suite itself is the
authorization server for wallet conformance tests.

## Matrix Presets

The suite accepts the following valid plan contexts:

| Preset | Plan contexts | Command |
|---|---:|---|
| Baseline default | 1 | `./run-wallet-conformance-local.sh` |
| All basic VCI | 1,728 | `OPENID4VCI_WALLET_CONFORMANCE_PRESET=all-basic-plan ./run-wallet-conformance-local.sh` |
| All HAIP contexts | 6 | `OPENID4VCI_WALLET_CONFORMANCE_PRESET=all-haip-plan ./run-wallet-conformance-local.sh` |
| Basic plus HAIP | 1,734 | `OPENID4VCI_WALLET_CONFORMANCE_PRESET=all ./run-wallet-conformance-local.sh` |

`all-basic-plan` and `all` are large, serial runs. They create a distinct
conformance plan for each context and can take many hours. The runner writes
`results.json` and `summary.md` to the reported matrix artifact directory.

Use `custom` to select an exact subset. For example, the basic SD-JWT VC,
issuer-initiated, authorization-code contexts with immediate or deferred
issuance are:

```bash
OPENID4VCI_WALLET_CONFORMANCE_PRESET=custom \
OPENID4VCI_WALLET_CONFORMANCE_FILTER_FAPI_PROFILES=vci \
OPENID4VCI_WALLET_CONFORMANCE_FILTER_FORMATS=sd_jwt_vc \
OPENID4VCI_WALLET_CONFORMANCE_FILTER_GRANT_TYPES=authorization_code \
OPENID4VCI_WALLET_CONFORMANCE_FILTER_FLOW_VARIANTS=issuer_initiated \
OPENID4VCI_WALLET_CONFORMANCE_FILTER_CLIENT_AUTH_TYPES=private_key_jwt \
OPENID4VCI_WALLET_CONFORMANCE_FILTER_SENDER_CONSTRAINTS=dpop \
OPENID4VCI_WALLET_CONFORMANCE_FILTER_AUTH_REQUEST_TYPES=simple \
OPENID4VCI_WALLET_CONFORMANCE_FILTER_REQUEST_METHODS=unsigned \
OPENID4VCI_WALLET_CONFORMANCE_FILTER_CREDENTIAL_ENCRYPTION=plain \
OPENID4VCI_WALLET_CONFORMANCE_FILTER_ISSUANCE_MODES=immediate,deferred \
OPENID4VCI_WALLET_CONFORMANCE_FILTER_OFFER_VARIANTS=by_value,by_reference \
./run-wallet-conformance-local.sh
```

## Module Selection

By default every module returned by the selected suite plan runs. Limit a run
with either `OPENID4VCI_WALLET_CONFORMANCE_MODULE_GROUPS` or
`OPENID4VCI_WALLET_CONFORMANCE_MODULES`:

| Group | Suite modules |
|---|---|
| `issuance` or `positive` | Credential issuance |
| `notification` | Credential notification |
| `scopes` | Scope-based issuance |
| `client-attestation` | Client-attestation challenge |
| `batch` | Batch credential issuance |
| `fapi` | FAPI 2 Security Profile modules in the HAIP plan |

For example:

```bash
OPENID4VCI_WALLET_CONFORMANCE_PRESET=all-haip-plan \
OPENID4VCI_WALLET_CONFORMANCE_MODULE_GROUPS=issuance,fapi \
./run-wallet-conformance-local.sh
```

## Current Execution Boundaries

The matrix enumerates all suite-supported basic and HAIP plan contexts. It does
not pretend Wallet API2 supports every context already:

- `wallet_initiated` is reported as `BLOCKED`: Wallet API2 has no API to start
  an authorization-code issuance without a received credential offer.
- `issuer_initiated_dc_api` is reported as `BLOCKED`: Wallet API2 has no Digital
  Credentials API request handler.
- Results for mTLS, client attestation, RAR, signed request objects, encrypted
  responses, deferred issuance, batch issuance, and HAIP reflect the actual
  Wallet API2 implementation. Unsupported behavior fails the relevant suite
  module and keeps the wrapper exit code non-zero.

`PASSED` and suite `SKIPPED` results are accepted. Any `FAILED`, `INTERRUPTED`,
`TIMEOUT`, or `BLOCKED` context fails the wrapper by default. Use
`OPENID4VCI_WALLET_CONFORMANCE_STRICT=false` only to collect a non-blocking
coverage report while implementing support.

The upstream wallet plan identifies itself as alpha/non-certification. Passing a
local matrix is therefore evidence for implementation progress, not a protocol
certification claim.

## Implementation Gap Reports

- [Basic-plan matrices](../../../docs/openid4vci-wallet-1.0-basic-plan-matrices.md)
  record the current result and missing work for each basic wallet module.
- [Compatibility report](../../../docs/openid4vci-wallet-1.0-compatibility-report.md)
  consolidates the Wallet API2 protocol gaps and proposed implementation order.
- [Naming and extensibility notes](../../../docs/wallet2-oid4vci-naming-and-extensibility.md)
  record the API-shape decisions to take before broadening support.

## Topology

```text
Browser / Playwright
       |
       v
Conformance Suite (Docker, :8443)
       |
       v
Nginx (Docker, :9444) ---> Wallet conformance adapter (host, :7007)
                                      |
                                      v
                             Wallet API2 (host, :7006)
```

The adapter creates one temporary Wallet API2 wallet with the test key, stores
credentials in it, and deletes it in `finally` after the run. For
`client_attestation` it also acts as the local test attester; Wallet API2 calls
that endpoint over the loopback interface.

## Useful Variables

| Variable | Default | Purpose |
|---|---|---|
| `OPENID4VCI_WALLET_CONFORMANCE_PRESET` | baseline | Select baseline, basic, HAIP, all, or custom matrix |
| `OPENID4VCI_WALLET_CONFORMANCE_WALLET_URL` | `http://127.0.0.1:7006` | Wallet API2 base URL |
| `OPENID4VCI_WALLET_CONFORMANCE_MODULE_GROUPS` | `all` | Comma-separated module groups |
| `OPENID4VCI_WALLET_CONFORMANCE_MODULES` | unset | Exact comma-separated suite module names |
| `OPENID4VCI_WALLET_CONFORMANCE_TX_CODE` | `123456` | Test transaction code for pre-authorized-code modules |
| `OPENID4VCI_WALLET_CONFORMANCE_BROWSER_AUTOMATION` | `true` | Enable Playwright front-channel automation |
| `OPENID4VCI_WALLET_CONFORMANCE_BROWSER_TIMEOUT_SECONDS` | `90` | Per-browser interaction timeout |
| `OPENID4VCI_WALLET_CONFORMANCE_INSTALL_PLAYWRIGHT` | `true` | Install the selected Playwright browser before the run |
| `PLAYWRIGHT_INSTALL_WITH_DEPS` | `false` | Request OS dependencies; requires interactive `sudo` and is disabled by default |
| `OPENID4VCI_WALLET_CONFORMANCE_MODULE_TIMEOUT_MINUTES` | `5` | Per-module timeout |
| `OPENID4VCI_WALLET_CONFORMANCE_STRICT` | `true` | Return non-zero for any non-passing context |
| `OPENID4VCI_WALLET_CONFORMANCE_REPORT_DIR` | runner report directory | Matrix JSON and Markdown artifacts |

The suite and Nginx containers remain running after the wrapper exits, matching
the issuer runner. Stop them explicitly when finished:

```bash
docker compose -f docker-compose-walt.yml down
```
