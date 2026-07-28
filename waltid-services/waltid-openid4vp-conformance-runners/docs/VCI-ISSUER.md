# VCI Issuer Conformance Tests

This document covers setup, execution, and status of OpenID4VCI Issuer conformance tests.

## Test Plan

| Profile | Test Plan | Variants |
|---------|-----------|----------|
| Base VCI issuer | `oid4vci-1_0-issuer-test-plan` | 288 generated combinations |
| HAIP VCI issuer | `oid4vci-1_0-issuer-haip-test-plan` | 8 generated combinations |

---

## Matrix Behavior

The runner creates one conformance-suite plan for each selected matrix variant.
The available axes and filters are documented in the main README. The resulting
config includes:

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

### 2. Configure Issuer Base URL

Set the base URL in the issuer2 service configuration used for the conformance run:

```hocon
baseUrl = "https://localhost.emobix.co.uk:9443"
```

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

```bash
# Terminal 1: start enterprise issuer2 on host port 7005
# Terminal 2: run the wrapper from the unified-build root
./waltid-identity/waltid-services/waltid-openid4vp-conformance-runners/run-issuer-conformance-local.sh
```

With no selection overrides, this runs the metadata and positive modules for 12 variants: SD-JWT VC and
mdoc, the three valid authorization/grant-flow pairs, and plain and encrypted credential responses. To run
all 288 generated variants and all returned modules instead:

```bash
OPENID4VCI_CONFORMANCE_PRESET=all-basic-plan \
OPENID4VCI_CONFORMANCE_MODULE_GROUPS=all \
  ./waltid-identity/waltid-services/waltid-openid4vp-conformance-runners/run-issuer-conformance-local.sh
```

Verify metadata endpoint:
```bash
curl -ks "https://localhost.emobix.co.uk:9443/.well-known/openid-credential-issuer/openid4vci" | jq .
```

---

## Running Tests

```bash
cd waltid-unified-build

./waltid-identity/waltid-services/waltid-openid4vp-conformance-runners/run-issuer-conformance-local.sh
```

### Test Execution Flow

1. The test fetches issuer metadata from `/.well-known/openid-credential-issuer/openid4vci`.
2. The runner generates and filters the base VCI issuer matrix.
3. The runner creates `oid4vci-1_0-issuer-test-plan` for each selected variant.
4. Issuer-initiated modules receive a fresh issuer2 credential offer.
5. The runner executes the suite modules and writes matrix reports.

---

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

## Troubleshooting

### "Invalid parameter: redirect_uri" in Keycloak
Add `https://localhost.emobix.co.uk:9443/openid4vci/external/oauth/callback` to the client redirect URIs.

### "Connect timed out" errors
Verify issuer2 listens on `0.0.0.0:7005`, then inspect the Nginx logs. Nginx reaches the host through `host.docker.internal`.

### "Unable to fetch credential issuer metadata"
- Check issuer2's `baseUrl` is `https://localhost.emobix.co.uk:9443`
- Run `getent hosts localhost.emobix.co.uk` and confirm the hostname resolves to a loopback address
- After Docker Compose starts, run `curl -ksf https://localhost.emobix.co.uk:8443/api/server`
- Run `docker compose -f waltid-identity/waltid-services/waltid-openid4vp-conformance-runners/docker-compose-walt.yml logs --tail=100 nginx server`

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
docker compose -f waltid-identity/waltid-services/waltid-openid4vp-conformance-runners/docker-compose-walt.yml down
```
