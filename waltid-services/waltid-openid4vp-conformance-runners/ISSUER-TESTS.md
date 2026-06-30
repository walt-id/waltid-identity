# Issuer Conformance Tests (issuer-api2)

## Overview

This document describes how to run OpenID4VCI 1.0 conformance tests against `waltid-issuer-api2`.

**Target Conformance Profile** (from `issuer-req.md`):

| Property | Value |
|----------|-------|
| Test Plan | OpenID4VCI 1.0 Final |
| FAPI | vci |
| Sender Constraint | dpop |
| Client Authentication | client_attestation |
| Auth code flow variant | Both |
| Credential Format | Both (SD-JWT VC, mDOC) |
| Auth Request Type | Simple |
| Request Method | Unsigned |
| Grant Type | Both (authorization_code, pre-authorized_code) |
| Credential Response Encryption | Plain |

## Architecture

```
┌─────────────────────────────┐         ┌─────────────────────────────┐
│   OpenID Conformance Suite  │         │      waltid-issuer-api2     │
│   (Acts as Wallet Client)   │  HTTP   │    (Credential Issuer)      │
│                             │ ──────► │                             │
│ - Fetches metadata          │         │ - Exposes metadata          │
│ - Initiates authorization   │         │ - Handles OAuth (Keycloak)  │
│ - Exchanges tokens          │         │ - Issues credentials        │
│ - Requests credentials      │         │ - Validates DPoP proofs     │
└─────────────────────────────┘         └─────────────────────────────┘
         (Docker)                              (Host + ngrok)
                                                    │
                                                    ▼
                                        ┌─────────────────────┐
                                        │      Keycloak       │
                                        │  (Authorization     │
                                        │   Server)           │
                                        └─────────────────────┘
```

The conformance suite runs in Docker and acts as a **wallet client** that calls the issuer's endpoints.

## Prerequisites

### 1. Add hosts entry

```bash
echo "127.0.0.1 localhost.emobix.co.uk" | sudo tee -a /etc/hosts
```

### 2. Clone and setup conformance suite

```bash
git clone https://gitlab.com/openid/conformance-suite.git ~/dev/openid/conformance-suite

# Copy walt.id configuration
cp ~/dev/walt-id/waltid-unified-build/waltid-identity/waltid-services/waltid-openid4vp-conformance-runners/docker-compose-walt.yml ~/dev/openid/conformance-suite/

cp -r ~/dev/walt-id/waltid-unified-build/waltid-identity/waltid-services/waltid-openid4vp-conformance-runners/nginx ~/dev/openid/conformance-suite/
```

### 3. Start conformance suite

```bash
cd ~/dev/openid/conformance-suite
docker compose -f docker-compose-walt.yml up -d

# Wait ~30 seconds, then verify:
curl -k https://localhost.emobix.co.uk:8443/
```

### 4. Start Keycloak (for authorization_code flow)

The authorization_code flow requires an external OAuth server. Start Keycloak:

```bash
docker run -d --name keycloak \
  -p 8080:8080 \
  -e KC_HOSTNAME=keycloak.localhost \
  -e KEYCLOAK_ADMIN=admin \
  -e KEYCLOAK_ADMIN_PASSWORD=admin \
  quay.io/keycloak/keycloak:latest start-dev
```

Or using docker-compose, add to your stack.

**Add hosts entry for Keycloak:**
```bash
echo "127.0.0.1 keycloak.localhost" | sudo tee -a /etc/hosts
```

Access Keycloak admin: http://keycloak.localhost:8080/admin (admin/admin)

### 5. Configure Keycloak

Create a realm and client for issuer-api2:

```bash
# Get admin token
TOKEN=$(curl -s -X POST "http://keycloak.localhost:8080/realms/master/protocol/openid-connect/token" \
  -H "Content-Type: application/x-www-form-urlencoded" \
  -d "username=admin&password=admin&grant_type=password&client_id=admin-cli" | jq -r '.access_token')

# Create realm
curl -X POST "http://keycloak.localhost:8080/admin/realms" \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d '{"realm": "issuer", "enabled": true}'

# Create client
curl -X POST "http://keycloak.localhost:8080/admin/realms/issuer/clients" \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d '{
    "clientId": "issuer-api",
    "enabled": true,
    "protocol": "openid-connect",
    "publicClient": false,
    "standardFlowEnabled": true,
    "directAccessGrantsEnabled": true,
    "redirectUris": ["https://*.ngrok-free.app/*", "http://localhost:7002/*"],
    "webOrigins": ["*"],
    "secret": "issuer-api-secret"
  }'

# Create test user
curl -X POST "http://keycloak.localhost:8080/admin/realms/issuer/users" \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d '{
    "username": "testuser",
    "enabled": true,
    "emailVerified": true,
    "credentials": [{"type": "password", "value": "testuser", "temporary": false}]
  }'
```

### 6. Configure issuer-api2 for Keycloak

Create/update `waltid-issuer-api2/config/authentication-service.conf`:

```hocon
name = "keycloak"
authorizeUrl = "http://keycloak.localhost:8080/realms/issuer/protocol/openid-connect/auth"
accessTokenUrl = "http://keycloak.localhost:8080/realms/issuer/protocol/openid-connect/token"
clientId = "issuer-api"
clientSecret = "issuer-api-secret"
defaultScopes = ["openid", "profile"]
forwardIssuerStateToAuthorizationServer = true
```

### 7. Start issuer-api2 with ngrok

The conformance suite runs in Docker and cannot reach localhost directly. Use ngrok:

**Terminal 1 - Start issuer-api2:**
```bash
cd ~/dev/walt-id/waltid-unified-build/waltid-identity
./gradlew :waltid-services:waltid-issuer-api2:run
```

**Terminal 2 - Start ngrok:**
```bash
ngrok http 7002
```

Note the ngrok URL (e.g., `https://xxxx.ngrok-free.app`).

**Update Keycloak redirect URIs** if ngrok URL changed:
```bash
# Get token and client UUID, then update redirectUris to include your ngrok URL
```

## Running Tests

### Set environment variable

```bash
export OPENID4VCI_CONFORMANCE_CREDENTIAL_ISSUER_URL="https://YOUR-NGROK-URL.ngrok-free.app/openid4vci"
```

**Important:** The path must be `/openid4vci` (not `/draft13`). issuer-api2 uses OpenID4VCI 1.0 paths.

### Run the tests

```bash
cd ~/dev/walt-id/waltid-unified-build/waltid-identity

./gradlew :waltid-services:waltid-openid4vp-conformance-runners:test --tests "IssuerConformanceTests"
```

### Complete OAuth login (authorization_code flow)

When the test reaches **WAITING** status, you'll see:
```
Test <testId> is stuck in WAITING status after 60 seconds.
Please complete the test manually at https://localhost.emobix.co.uk:8443/test-info/<testId>
```

**You have ~60 seconds to:**

1. Open the URL in your browser
2. Click to continue the OAuth flow
3. Login to Keycloak with: `testuser` / `testuser`
4. The test continues automatically after login

## Test Plans

### Oid4vciIssuerClientAttestationDpop

The main test plan configured in `Oid4vciIssuerClientAttestationDpop.kt`:

| Property | Value |
|----------|-------|
| Plan Name | `oid4vci-1_0-issuer-test-plan` |
| FAPI Profile | `vci` |
| Sender Constraint | `dpop` |
| Client Authentication | `attest_jwt_client_auth` |
| Flow Variant | `wallet_initiated` |
| Grant Type | `authorization_code` |
| Credential Format | `sd_jwt_vc` |

**Test Modules:**

| Module | Description | User Action Required |
|--------|-------------|---------------------|
| `oid4vci-1_0-issuer-metadata-test` | Validates metadata endpoint | No |
| `oid4vci-1_0-issuer-happy-flow` | Complete issuance flow | **Yes - OAuth login** |

## Credential Configuration IDs

issuer-api2 exposes many credential configurations. The test uses SD-JWT VC format credentials.

Available SD-JWT VC configurations (sample):
- `identity_credential_dc+sd-jwt`
- `photoID_credential_dc+sd-jwt`
- `my_custom_vct_dc+sd-jwt`

Available mDOC configurations:
- `org.iso.18013.5.1.mDL`
- `org.iso.23220.photoid.1`
- `urn:eu.europa.ec.eudi:pid:1`

## Expected Results

Successful test run:
```
================================================================================
OpenID4VCI Issuer Conformance Tests
================================================================================

Conformance suite: localhost.emobix.co.uk:8443
Issuer URL: https://xxxx.ngrok-free.app/openid4vci

================================================================================
Running issuer plan: Oid4vciIssuerClientAttestationDpop
================================================================================

[1/2] Running module: oid4vci-1_0-issuer-metadata-test
  Status: FINISHED, Result: PASSED

[2/2] Running module: oid4vci-1_0-issuer-happy-flow
  Status: WAITING (complete OAuth login in browser)
  Status: FINISHED, Result: PASSED

================================================================================
Results: 2/2 PASSED
================================================================================
```

## Troubleshooting

### "Invalid parameter: redirect_uri" in Keycloak

The Keycloak client's redirect URIs don't include your ngrok URL. Update the client:

```bash
# Add your ngrok URL to redirectUris, including the callback path:
# https://YOUR-NGROK.ngrok-free.app/openid4vci/external/oauth/callback
```

### Test times out in WAITING status

You have ~60 seconds to complete the OAuth login. If you miss it:
1. Run the test again
2. Quickly open the `test-info` URL
3. Complete the login

### Conformance suite can't reach issuer

- Issuer must be accessible from Docker (not just localhost)
- Use ngrok or your host's network IP
- Verify: `curl https://YOUR-NGROK.ngrok-free.app/.well-known/openid-credential-issuer/openid4vci`

### Metadata endpoint 404

issuer-api2 uses the path `/.well-known/openid-credential-issuer/openid4vci` (not just `/.well-known/openid-credential-issuer`).

Verify:
```bash
curl https://YOUR-NGROK.ngrok-free.app/.well-known/openid-credential-issuer/openid4vci | jq .
```

### OAuth metadata endpoint

```bash
curl https://YOUR-NGROK.ngrok-free.app/.well-known/oauth-authorization-server/openid4vci | jq .
```

## Quick Reference

### Full test run commands

```bash
# Terminal 1: Keycloak (if not already running)
docker run -d --name keycloak -p 8080:8080 \
  -e KC_HOSTNAME=keycloak.localhost \
  -e KEYCLOAK_ADMIN=admin -e KEYCLOAK_ADMIN_PASSWORD=admin \
  quay.io/keycloak/keycloak:latest start-dev

# Terminal 2: issuer-api2
cd ~/dev/walt-id/waltid-unified-build/waltid-identity
./gradlew :waltid-services:waltid-issuer-api2:run

# Terminal 3: ngrok
ngrok http 7002

# Terminal 4: Run tests
export OPENID4VCI_CONFORMANCE_CREDENTIAL_ISSUER_URL="https://YOUR-NGROK.ngrok-free.app/openid4vci"
cd ~/dev/walt-id/waltid-unified-build/waltid-identity
./gradlew :waltid-services:waltid-openid4vp-conformance-runners:test --tests "IssuerConformanceTests"
```

### Test user credentials

- **Username:** testuser
- **Password:** testuser

### Key URLs

| URL | Purpose |
|-----|---------|
| `https://localhost.emobix.co.uk:8443/` | Conformance suite UI |
| `http://keycloak.localhost:8080/admin` | Keycloak admin (admin/admin) |
| `https://YOUR-NGROK.ngrok-free.app/openid4vci` | Issuer credential issuer URL |
| `https://YOUR-NGROK.ngrok-free.app/.well-known/openid-credential-issuer/openid4vci` | Issuer metadata |
| `https://YOUR-NGROK.ngrok-free.app/.well-known/oauth-authorization-server/openid4vci` | OAuth metadata |

## Related Documentation

- [issuer-req.md](../../issuer-req.md) - Conformance requirements
- [README.md](README.md) - General conformance runner setup
- [VERIFIER2-TESTS.md](VERIFIER2-TESTS.md) - Verifier conformance tests
- [OpenID4VCI 1.0 Specification](https://openid.net/specs/openid-4-verifiable-credential-issuance-1_0.html)
