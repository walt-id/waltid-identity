# VCI Issuer Conformance Tests (issuer-api2)

## Overview

This document describes how to run OpenID4VCI 1.0 conformance tests against `waltid-issuer-api2`.

**Target Conformance Profile**

| Property | Value |
|----------|-------|
| Test Plan | OpenID4VCI 1.0 Final |
| FAPI | vci |
| Sender Constraint | dpop |
| Client Authentication | private_key_jwt |
| Auth code flow variant | wallet_initiated |
| Credential Format | SD-JWT VC (`dc+sd-jwt`) |
| Auth Request Type | Simple |
| Request Method | Unsigned |
| Grant Type | authorization_code |
| Credential Response Encryption | Plain |

## Architecture

```
┌─────────────────────────────┐         ┌─────────────────────────────┐
│   OpenID Conformance Suite  │         │      waltid-issuer-api2     │
│   (Acts as Wallet Client)   │  HTTP   │    (Credential Issuer)      │
│                             │ ──────► │                             │
│ - Fetches metadata          │         │ - Exposes metadata          │
│ - Initiates authorization   │         │ - Handles OAuth flow        │
│ - Exchanges tokens          │         │ - Issues credentials        │
│ - Requests credentials      │         │ - Validates DPoP proofs     │
└─────────────────────────────┘         └─────────────────────────────┘
         (Docker)                              (Host + ngrok)
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

## Running Tests

### Step 1: Start issuer-api2

```bash
cd ~/dev/walt-id/waltid-unified-build/waltid-identity
./gradlew :waltid-services:waltid-issuer-api2:run
```

### Step 2: Start ngrok tunnel

The conformance suite runs in Docker and cannot reach localhost directly:

```bash
ngrok http 7002
```

Note the ngrok URL (e.g., `https://xxxx-xxxx.ngrok-free.app`).

### Step 3: Configure issuer baseUrl

Update `waltid-issuer-api2/config/issuer-service.conf`:

```hocon
baseUrl = "https://YOUR-NGROK-URL.ngrok-free.app"
```

**Important:** Restart issuer-api2 after changing the baseUrl.

### Step 4: Run the tests

```bash
cd ~/dev/walt-id/waltid-unified-build/waltid-identity

OPENID4VCI_CONFORMANCE_CREDENTIAL_ISSUER_URL="https://YOUR-NGROK-URL.ngrok-free.app/openid4vci" \
OPENID4VCI_CONFORMANCE_SD_JWT_CREDENTIAL_CONFIGURATION_ID="identity_credential" \
OPENID4VCI_CONFORMANCE_MDOC_CREDENTIAL_CONFIGURATION_ID="org.iso.18013.5.1.mDL" \
./gradlew :waltid-services:waltid-openid4vp-conformance-runners:test --tests "IssuerConformanceTests"
```

### Environment Variables

| Variable | Description | Example |
|----------|-------------|---------|
| `OPENID4VCI_CONFORMANCE_CREDENTIAL_ISSUER_URL` | Full URL to issuer's OpenID4VCI endpoint | `https://xxxx.ngrok-free.app/openid4vci` |
| `OPENID4VCI_CONFORMANCE_SD_JWT_CREDENTIAL_CONFIGURATION_ID` | Credential config ID for SD-JWT VC tests | `identity_credential` |
| `OPENID4VCI_CONFORMANCE_MDOC_CREDENTIAL_CONFIGURATION_ID` | Credential config ID for mDOC tests | `org.iso.18013.5.1.mDL` |

## Credential Configuration IDs

The issuer-api2 exposes multiple credential configurations. Use IDs that exist in your issuer's metadata.

### Available SD-JWT VC Credentials

Check your issuer's metadata for available configurations:

```bash
curl -s "https://YOUR-NGROK-URL.ngrok-free.app/.well-known/openid-credential-issuer/openid4vci" | \
  jq '.credential_configurations_supported | to_entries[] | select(.value.format | test("sd-jwt")) | .key'
```

**Common SD-JWT VC configurations:**
- `identity_credential` → profile: `identityCredentialSdJwt`
- `urn:eudi:pid:1` → profile: `eudiPidSdJwt`
- `urn:eu.europa.ec.eudi:por:1` → profile: `powerOfRepresentationSdJwt`
- `urn:eu.europa.ec.eudi:cor:1` → profile: `certificateOfResidenceSdJwt`

### Available mDOC Credentials

```bash
curl -s "https://YOUR-NGROK-URL.ngrok-free.app/.well-known/openid-credential-issuer/openid4vci" | \
  jq '.credential_configurations_supported | to_entries[] | select(.value.format == "mso_mdoc") | .key'
```

**Common mDOC configurations:**
- `org.iso.18013.5.1.mDL` → profile: `isoMdl`
- `eu.europa.ec.eudi.pid.1` → profile: `eudiPidMdoc`
- `eu.europa.ec.av.1` → profile: `euAgeVerificationMdoc`

### List Available Profiles

```bash
curl -s "https://YOUR-NGROK-URL.ngrok-free.app/issuer2/profiles" | jq '.[].profileId'
```

## Test Results

### Expected Output

The test creates a test plan on the conformance suite and runs through the OpenID4VCI issuance flow:

```
✅ Conformance server version 5.1.44 available!
✅ Issuer metadata endpoint responding: https://xxxx.ngrok-free.app/.well-known/openid-credential-issuer/openid4vci
Resolved phase-1 issuer credential configuration ids:
  sd-jwt-vc -> identity_credential
  mdoc      -> org.iso.18013.5.1.mDL
Running issuer plan: Oid4vciIssuerClientAttestationDpop
```

### Current Test Status (2026-07-01)

| Module | Result | Notes |
|--------|--------|-------|
| `oid4vci-1_0-issuer-happy-flow` | 53 SUCCESS, 2 FAILURE | See known issues below |

### Known Issues

1. **"No 'iss' value in authorization response"** - The authorization response is missing the `iss` parameter required by OAuth 2.0 Authorization Server Issuer Identification (RFC 9207).

2. **"Invalid http status"** - An HTTP endpoint returned an unexpected status code.

## Troubleshooting

### Conformance suite can't reach issuer

- Issuer must be accessible from Docker (not just localhost)
- Use ngrok or your host's network IP
- Verify: `curl https://YOUR-NGROK.ngrok-free.app/.well-known/openid-credential-issuer/openid4vci`

### "Unknown credential configuration ID" error

The `deriveProfileId()` function in `Oid4vciIssuerClientAttestationDpop.kt` maps credential configuration IDs to issuer-api2 profile IDs. If you get this error, either:
1. Use a credential configuration ID that has a mapping
2. Add a new mapping for your credential configuration ID

### "Credential profile not found" error (404)

The profile ID derived from the credential configuration ID doesn't exist in issuer-api2. Check available profiles:

```bash
curl -s "https://YOUR-NGROK-URL.ngrok-free.app/issuer2/profiles" | jq '.[].profileId'
```

### Metadata endpoint returns wrong URLs

If metadata contains `http://localhost:7002` instead of ngrok URLs:
1. Update `baseUrl` in `issuer-service.conf`
2. Restart issuer-api2

### Test report location

After running tests, view the HTML report:
```
waltid-services/waltid-openid4vp-conformance-runners/build/reports/tests/test/index.html
```

## Quick Reference

### Full test run (copy-paste)

```bash
# Terminal 1: Start issuer-api2
cd ~/dev/walt-id/waltid-unified-build/waltid-identity
./gradlew :waltid-services:waltid-issuer-api2:run

# Terminal 2: Start ngrok
ngrok http 7002
# Note the URL, e.g., https://xxxx.ngrok-free.app

# Terminal 3: Run tests (replace YOUR-NGROK-URL)
cd ~/dev/walt-id/waltid-unified-build/waltid-identity

OPENID4VCI_CONFORMANCE_CREDENTIAL_ISSUER_URL="https://YOUR-NGROK-URL.ngrok-free.app/openid4vci" \
OPENID4VCI_CONFORMANCE_SD_JWT_CREDENTIAL_CONFIGURATION_ID="identity_credential" \
OPENID4VCI_CONFORMANCE_MDOC_CREDENTIAL_CONFIGURATION_ID="org.iso.18013.5.1.mDL" \
./gradlew :waltid-services:waltid-openid4vp-conformance-runners:test --tests "IssuerConformanceTests"
```

### Key URLs

| URL | Purpose |
|-----|---------|
| `https://localhost.emobix.co.uk:8443/` | Conformance suite UI |
| `https://YOUR-NGROK.ngrok-free.app/openid4vci` | Issuer credential issuer URL |
| `https://YOUR-NGROK.ngrok-free.app/.well-known/openid-credential-issuer/openid4vci` | Issuer metadata |
| `https://YOUR-NGROK.ngrok-free.app/.well-known/oauth-authorization-server/openid4vci` | OAuth metadata |
| `https://YOUR-NGROK.ngrok-free.app/issuer2/profiles` | List available profiles |

## Related Documentation

- [VCI-WALLET.md](VCI-WALLET.md) - VCI wallet conformance tests (140/140 passing)
- [VP-VERIFIER.md](VP-VERIFIER.md) - VP verifier conformance tests
- [VP-WALLET.md](VP-WALLET.md) - VP wallet conformance tests
- [OpenID4VCI 1.0 Specification](https://openid.net/specs/openid-4-verifiable-credential-issuance-1_0.html)
