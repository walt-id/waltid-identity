# VCI Issuer Conformance Tests

This document covers setup, execution, and status of OpenID4VCI Issuer conformance tests.

## Test Plan

| Profile | Test Plan | Variants |
|---------|-----------|----------|
| Base VCI issuer | `oid4vci-1_0-issuer-test-plan` | 288 generated combinations |

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

---

## Prerequisites

1. **Conformance Suite** running at `https://localhost.emobix.co.uk:8443`
   ```bash
   cd openid/conformance-suite
   docker compose -f docker-compose-walt.yml up -d
   ```

2. **Keycloak** running at `http://keycloak.localhost:8080` with `issuer` realm for authorization-code variants. Pre-authorized-code variants do not need Keycloak.

3. **ngrok** for exposing issuer to conformance suite
   ```bash
   ngrok http 7002
   ```

4. **issuer-api2** service

---

## Setup

### 1. Configure Keycloak

The `issuer-api` client in the `issuer` realm needs redirect URIs matching your ngrok URL:

```bash
# Get admin token
TOKEN=*** -s -X POST "http://keycloak.localhost:8080/realms/master/protocol/openid-connect/token" \
  -H "Content-Type: application/x-www-form-urlencoded" \
  -d "username=admin" -d "password=admin" \
  -d "grant_type=password" -d "client_id=admin-cli" | jq -r '.access_token')

# Get client UUID
CLIENT_UUID=$(curl -s "http://keycloak.localhost:8080/admin/realms/issuer/clients" \
  -H "Authorization: Bearer $TOKEN" | jq -r '.[] | select(.clientId == "issuer-api") | .id')

# Update redirect URIs (use wildcard for any ngrok URL)
curl -s -X PUT "http://keycloak.localhost:8080/admin/realms/issuer/clients/$CLIENT_UUID" \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d '{
    "clientId": "issuer-api",
    "redirectUris": ["http://localhost:7002/*", "https://*.ngrok-free.app/*"],
    "webOrigins": ["*"]
  }'
```

### 2. Create Test User

```bash
# Get user ID
USER_ID=$(curl -s "http://keycloak.localhost:8080/admin/realms/issuer/users" \
  -H "Authorization: Bearer $TOKEN" | jq -r '.[0].id')

# Reset password
curl -s -X PUT "http://keycloak.localhost:8080/admin/realms/issuer/users/$USER_ID/reset-password" \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d '{"type": "password", "value": "testuser", "temporary": false}'
```

**Test credentials:** `testuser` / `testuser`

### 3. Configure Issuer Base URL

Edit `waltid-services/waltid-issuer-api2/config/issuer-service.conf`:

```hocon
baseUrl = "https://YOUR-NGROK-SUBDOMAIN.ngrok-free.app"
```

### 4. Start Services

```bash
# Terminal 1: Start ngrok
ngrok http 7002

# Terminal 2: Start issuer-api2
cd waltid-unified-build/waltid-identity
./gradlew :waltid-services:waltid-issuer-api2:run
```

Verify metadata endpoint:
```bash
curl -s "https://YOUR-NGROK.ngrok-free.app/.well-known/openid-credential-issuer/openid4vci" | jq .
```

---

## Running Tests

```bash
cd waltid-unified-build

# Set environment variables
export OPENID4VCI_CONFORMANCE_CREDENTIAL_ISSUER_URL="https://YOUR-NGROK.ngrok-free.app/openid4vci"
export OPENID4VCI_CONFORMANCE_SD_JWT_CREDENTIAL_CONFIGURATION_ID="identity_credential"
export OPENID4VCI_CONFORMANCE_CLIENT_ATTESTER_JWKS_FILE="$PWD/waltid-identity/waltid-services/waltid-openid4vp-conformance-runners/src/test/resources/keys/attester-key.json"

# Run tests
./gradlew :waltid-services:waltid-openid4vp-conformance-runners:test --tests "id.walt.openid4vp.conformance.IssuerConformanceTests"
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
| `OPENID4VCI_CONFORMANCE_CREDENTIAL_ISSUER_URL` | Full issuer URL | `https://xxx.ngrok-free.app/openid4vci` |
| `OPENID4VCI_CONFORMANCE_SD_JWT_CREDENTIAL_CONFIGURATION_ID` | SD-JWT credential config | `identity_credential` |
| `OPENID4VCI_CONFORMANCE_MDOC_CREDENTIAL_CONFIGURATION_ID` | mDOC credential config | `org.iso.18013.5.1.mDL` |
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
Update `issuer-api` client redirect URIs to include your ngrok URL (see Setup §1).

### "Connect timed out" errors
The conformance suite runs in Docker and can't reach `localhost`. Use ngrok URL.

### "Unable to fetch credential issuer metadata"
- Verify ngrok is running and URL hasn't changed
- Check issuer's `baseUrl` config matches ngrok URL

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
