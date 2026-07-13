# VCI Issuer Conformance Tests

This document covers setup, execution, and status of OpenID4VCI Issuer conformance tests.

## Test Profiles

| Profile | Test Plan | Grant Type | Format | Status |
|---------|-----------|------------|--------|--------|
| SD-JWT Baseline | `oid4vci-1_0-issuer-metadata-test` | `pre-authorized_code` | SD-JWT VC | ✅ PASSED |
| SD-JWT Happy Flow | `oid4vci-1_0-issuer-happy-flow` | `pre-authorized_code` | SD-JWT VC | ❌ FAILED |
| SD-JWT Signed Metadata | `oid4vci-1_0-issuer-metadata-test-signed` | `pre-authorized_code` | SD-JWT VC | ❌ FAILED |

**Last tested:** 2026-07-08

---

## Current Status

### ✅ Passing Tests

**`oid4vci-1_0-issuer-metadata-test`** — Validates issuer metadata endpoint
- Credential issuer URL uses HTTPS ✓
- Metadata structure compliant ✓
- Credential configurations valid ✓

### ❌ Failing Tests

**`oid4vci-1_0-issuer-happy-flow`** — Full issuance flow
| Issue | Severity | Description |
|-------|----------|-------------|
| Missing `iss` parameter | FAILURE | Authorization response must include `iss` (RFC 9207) |
| Invalid HTTP status | FAILURE | Cascading failure from above |
| Unexpected auth parameters | WARNING | Review authorization endpoint response |

**`oid4vci-1_0-issuer-metadata-test-signed`** — Signed metadata validation
| Issue | Severity | Description |
|-------|----------|-------------|
| Metadata fetch failed | FAILURE | ngrok URL expired during test run |

### Required Fixes

1. **RFC 9207 Compliance** — Add `iss` parameter to authorization response
   - Specification: [RFC 9207 - OAuth 2.0 Authorization Server Issuer Identification](https://datatracker.ietf.org/doc/html/rfc9207)
   - The `iss` parameter prevents mix-up attacks

2. **Stable Test URL** — Use persistent ngrok URL or stable test environment

---

## Prerequisites

1. **Conformance Suite** running at `https://localhost.emobix.co.uk:8443`
   ```bash
   cd ~/dev/openid/conformance-suite
   docker compose -f docker-compose-walt.yml up -d
   ```

2. **Keycloak** running at `http://keycloak.localhost:8080` with `issuer` realm

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
cd ~/dev/walt-id/waltid-unified-build/waltid-identity
./gradlew :waltid-services:waltid-issuer-api2:run
```

Verify metadata endpoint:
```bash
curl -s "https://YOUR-NGROK.ngrok-free.app/.well-known/openid-credential-issuer/openid4vci" | jq .
```

---

## Running Tests

```bash
cd ~/dev/walt-id/waltid-unified-build

# Set environment variables
export OPENID4VCI_CONFORMANCE_CREDENTIAL_ISSUER_URL="https://YOUR-NGROK.ngrok-free.app/openid4vci"
export OPENID4VCI_CONFORMANCE_SD_JWT_CREDENTIAL_CONFIGURATION_ID="identity_credential"

# Run tests
./gradlew :waltid-services:waltid-openid4vp-conformance-runners:test --tests "IssuerConformanceTests"
```

### Test Execution Flow

1. **Baseline tests** run automatically (pre-authorized_code grant)
2. **HAIP tests** enter WAITING state for OAuth login:
   - Click authorization button in conformance suite UI
   - Login at Keycloak with `testuser` / `testuser`
   - After "Processing response" screen, return to test results

---

## Environment Variables

| Variable | Description | Example |
|----------|-------------|---------|
| `OPENID4VCI_CONFORMANCE_CREDENTIAL_ISSUER_URL` | Full issuer URL | `https://xxx.ngrok-free.app/openid4vci` |
| `OPENID4VCI_CONFORMANCE_SD_JWT_CREDENTIAL_CONFIGURATION_ID` | SD-JWT credential config | `identity_credential` |
| `OPENID4VCI_CONFORMANCE_MDOC_CREDENTIAL_CONFIGURATION_ID` | mDOC credential config | `org.iso.18013.5.1.mDL` |
| `OPENID4VCI_CONFORMANCE_AUTHORIZATION_SERVER` | External auth server | (optional) |

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

## HAIP Requirements

| Req | Description | Status |
|-----|-------------|--------|
| I-01 | Authorization Code flow | ⚠️ Missing `iss` |
| I-02 | FAPI2 Security Profile (PKCE, PAR) | ✅ |
| I-03 | DPoP for sender-constrained tokens | ✅ |
| I-22 | SD-JWT VC with holder binding | ✅ |
| CF-02 | P-256 + SHA-256 (ES256) | ✅ |

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
