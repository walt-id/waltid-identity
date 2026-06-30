# OpenID4VCI Issuer Conformance Tests - Quick Start

## Prerequisites

1. **Conformance Suite**: https://localhost.emobix.co.uk:8443
2. **Cloudflare Tunnel**: Free, no account needed
3. **Issuer running**: OSS or Enterprise on port 7002

## Setup (5 minutes)

### 1. Install Cloudflare Tunnel

```bash
# Download and install
wget https://github.com/cloudflare/cloudflared/releases/latest/download/cloudflared-linux-amd64.deb
sudo dpkg -i cloudflared-linux-amd64.deb
```

### 2. Start Your Issuer

```bash
cd ~/dev/walt-id/waltid-unified-build

# Start OSS issuer on port 7002
./gradlew :waltid-services:waltid-issuer-api2:run

# OR start Enterprise issuer on port 7002 (for client attestation tests)
```

### 3. Start Cloudflare Tunnel

```bash
cloudflared tunnel --url http://localhost:7002
```

Copy the `https://...trycloudflare.com` URL from the output.

### 4. Update Issuer Config

Edit `waltid-issuer-api2/config/issuer-service.conf`:

```hocon
issuer {
  baseUrl = "https://YOUR-TUNNEL-URL.trycloudflare.com"
  # ... rest stays the same
}
```

Restart the issuer.

### 5. Run Tests

```bash
cd ~/dev/walt-id/waltid-unified-build

export OPENID4VCI_CONFORMANCE_CREDENTIAL_ISSUER_URL="https://YOUR-TUNNEL-URL.trycloudflare.com/openid4vci"
export OPENID4VCI_CONFORMANCE_SD_JWT_CREDENTIAL_CONFIGURATION_ID="photoID_credential_dc+sd-jwt"
export OPENID4VCI_CONFORMANCE_AUTHORIZATION_SERVER="https://YOUR-TUNNEL-URL.trycloudflare.com/openid4vci"

./gradlew :waltid-services:waltid-openid4vp-conformance-runners:test \
  --tests "IssuerConformanceTests" \
  --no-daemon
```

Results: `build/reports/tests/test/index.html`

---

## Test Configuration

### OSS Issuer (No Client Attestation)

Edit: `src/main/kotlin/id/walt/openid4vp/conformance/testplans/plans/Oid4vciIssuerClientAttestationDpop.kt`

```kotlin
private val moduleVariant = """
    {
      "fapi_profile": "vci",
      "sender_constrain": "dpop",
      "client_auth_type": "private_key_jwt",
      "vci_grant_type": "authorization_code",
      "vci_authorization_code_flow_variant": "wallet_initiated",
      "authorization_request_type": "simple",
      "fapi_request_method": "unsigned",
      "fapi_response_mode": "plain_response",
      "credential_format": "${credentialFormat.variantValue}"
    }
""".trimIndent()
```

### Enterprise Issuer (With Client Attestation)

```kotlin
private val moduleVariant = """
    {
      "fapi_profile": "vci",
      "sender_constrain": "dpop",
      "client_auth_type": "client_attestation",
      "vci_grant_type": "authorization_code",
      "vci_authorization_code_flow_variant": "wallet_initiated",
      "authorization_request_type": "simple",
      "fapi_request_method": "unsigned",
      "fapi_response_mode": "plain_response",
      "credential_format": "${credentialFormat.variantValue}"
    }
""".trimIndent()
```

---

## OAuth Interaction Required

When using `authorization_code` flow:

1. Test will show: `Current conformance test status: WAITING`
2. Open the log URL (shown in output): `https://localhost.emobix.co.uk:8443/log-detail.html?log=...`
3. Click the **interaction button** in the conformance UI
4. Complete the authorization within 60 seconds (PAR request_uri expires quickly)

---

## Troubleshooting

**Test stuck at WAITING?**
- Authorization code flow needs manual OAuth approval in conformance UI

**PAR request_uri expired?**
- Click interaction button faster (< 60 seconds from test start)

**Client attestation errors?**
- Use Enterprise issuer or switch to `"client_auth_type": "private_key_jwt"`

**406 Not Acceptable?**
- Use Cloudflare Tunnel, not ngrok (free ngrok shows browser warnings)

## OAuth Authorization Code Flow - Manual Testing

**Important:** Tests using OAuth authorization code flow with Keycloak **cannot run as automated JUnit tests** because they require user interaction (browser login).

### Symptoms
```
java.lang.IllegalStateException: Test xyz is stuck in WAITING status
This typically means the test requires user interaction (OAuth login)
```

### Solution: Manual Testing

1. **Start the issuer service** with public HTTPS URL (Cloudflare Tunnel)
2. **Open the conformance suite** at https://localhost.emobix.co.uk:8443
3. **Create a test plan** manually:
   - Plan: `oid4vci-1_0-issuer-happy-flow-dpop-private_key_jwt`
   - Configure issuer URL and OAuth settings
4. **Complete the test** - Browser will redirect to Keycloak for login
5. **Review results** - Test will complete after OAuth callback

### Automated Testing

For automated/headless testing, use one of:
- **Pre-authorized code flow** (`vci_grant_type: "urn:ietf:params:oauth:grant-type:pre-authorized_code"`)
- **Client credentials** (no user interaction)
- **Mock OAuth** (stub the OAuth flow)

OAuth tests are designed for **manual validation** in the conformance suite UI, not automated CI/CD pipelines.
