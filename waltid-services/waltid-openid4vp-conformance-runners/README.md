# walt.id OpenID4VP Conformance Runners

Conformance test infrastructure for validating walt.id services against the OpenID Foundation conformance suite.

Supports testing:
- **Verifier2** - OpenID4VP verifier compliance
- **Wallet** - HAIP (High Assurance Interoperability Profile) wallet compliance
- **Issuer** - OID4VCI issuer compliance (planned)

## Quick Start

### Prerequisites

- Docker and Docker Compose
- Java 21+
- ngrok (for exposing local services to Docker)
- `/etc/hosts` entry for conformance suite

```bash
# Add hosts entry
echo "127.0.0.1 localhost.emobix.co.uk" | sudo tee -a /etc/hosts

# Install ngrok if needed
# https://ngrok.com/download
```

### 1. Start the Conformance Suite

```bash
# Clone conformance suite (if not already done)
git clone https://gitlab.com/openid/conformance-suite.git ~/dev/openid/conformance-suite

# Copy walt.id docker compose
cp docker-compose-walt.yml ~/dev/openid/conformance-suite/

# Start
cd ~/dev/openid/conformance-suite
docker compose -f docker-compose-walt.yml up -d

# Verify (wait ~30s)
curl -k https://localhost.emobix.co.uk:8443/
```

### 2. Run Verifier Conformance Tests

The conformance suite runs in Docker and needs to reach the local verifier.
Use ngrok to create a tunnel:

```bash
# Terminal 1: Start ngrok
ngrok http 7003

# Note the HTTPS URL, e.g.: https://abc123.ngrok-free.app
```

```bash
# Terminal 2: Run tests with ngrok URL
cd ~/dev/walt-id/waltid-unified-build/waltid-identity

export VERIFIER_NGROK_URL="https://abc123.ngrok-free.app"  # Use your actual ngrok URL

./gradlew :waltid-services:waltid-openid4vp-conformance-runners:test \
    --tests "Verifier2ConformanceTests"
```

### 3. Stop

```bash
# Stop ngrok: Ctrl+C
# Stop conformance suite:
cd ~/dev/openid/conformance-suite
docker compose -f docker-compose-walt.yml down
```

## Test Classes

| Test Class | Purpose | Status |
|------------|---------|--------|
| `Verifier2ConformanceTests` | OpenID4VP verifier compliance | Working |
| `WalletHAIPConformanceTests` | HAIP wallet compliance | Infrastructure ready |
| `IssuerConformanceTests` | OID4VCI issuer compliance | Planned |

**Note:** `ConformanceTests` is a deprecated alias for `Verifier2ConformanceTests` (kept for CI compatibility).

## Test Plans

### Verifier Test Plans

| Plan | Format | Client ID | Request | Response |
|------|--------|-----------|---------|----------|
| MdlX509SanDnsRequestUriSignedDirectPost | ISO mDL | x509_san_dns | signed | direct_post |
| SdJwtVcX509SanDnsRequestUriSignedDirectPostJwt | SD-JWT VC | x509_hash | signed | direct_post.jwt |

### Wallet HAIP Test Plans

| Plan | Format | Description |
|------|--------|-------------|
| Plan 1 | SD-JWT VC | Baseline HAIP validation |
| Plan 2 | mDL | Mobile driving license HAIP |
| Plan 7 | SD-JWT VC | Negative tests (security validation) |

## Configuration

### Environment Variables

| Variable | Description | Example |
|----------|-------------|---------|
| `VERIFIER_NGROK_URL` | ngrok URL for verifier | `https://abc123.ngrok-free.app` |

### ConformanceConfig.kt

Central configuration in `src/main/kotlin/id/walt/openid4vp/conformance/config/ConformanceConfig.kt`:

```kotlin
object ConformanceConfig {
    const val CONFORMANCE_HOST = "localhost.emobix.co.uk"
    const val CONFORMANCE_PORT = 8443
    const val VERIFIER_LOCAL_PORT = 7003
    const val WALLET_API_URL = "http://127.0.0.1:7005"
    // ...
}
```

## Project Structure

```
src/
├── main/kotlin/id/walt/openid4vp/conformance/
│   ├── config/
│   │   └── ConformanceConfig.kt          # Central configuration
│   ├── plans/
│   │   └── ConformanceTestPlan.kt        # Base interfaces
│   ├── adapter/
│   │   └── WalletConformanceAdapter.kt   # Wallet test adapter
│   └── testplans/
│       ├── ConformanceTestRunner.kt      # Verifier test runner
│       ├── keys/
│       │   └── TestKeyMaterial.kt        # Test certificates/keys
│       ├── http/
│       │   └── ConformanceInterface.kt   # Conformance suite API
│       ├── plans/
│       │   ├── MdlX509SanDns...kt        # Verifier test plans
│       │   └── SdJwtVcX509...kt
│       ├── wallet/
│       │   ├── WalletHAIPPlan1.kt        # Wallet test plans
│       │   └── ...
│       └── runner/
│           └── TestPlanRunner.kt         # Execution engines
└── test/kotlin/id/walt/openid4vp/conformance/
    ├── Verifier2ConformanceTests.kt      # Main verifier tests
    ├── ConformanceTests.kt               # Deprecated alias
    └── WalletHAIPConformanceTests.kt     # Wallet HAIP tests
```

## SSL Configuration

The project includes `conformance-truststore.jks` with the conformance suite's certificate.
Gradle automatically configures the truststore for tests.

### IntelliJ Run Configuration

Add these VM options:
```
-Djavax.net.ssl.trustStore=/path/to/conformance-truststore.jks
-Djavax.net.ssl.trustStorePassword=changeit
```

### Updating the Certificate

If the conformance suite certificate changes:
```bash
openssl s_client -connect localhost.emobix.co.uk:8443 </dev/null 2>/dev/null | \
  openssl x509 -outform PEM > conformance-test.pem

keytool -delete -alias conformance-test-localhost -keystore conformance-truststore.jks \
  -storepass changeit 2>/dev/null || true
keytool -importcert -trustcacerts -alias conformance-test-localhost \
  -file conformance-test.pem -keystore conformance-truststore.jks \
  -storepass changeit -noprompt
```

## Troubleshooting

### Tests Skip

- Conformance suite not running: `curl -k https://localhost.emobix.co.uk:8443/`
- Missing ngrok URL: Set `VERIFIER_NGROK_URL` environment variable

### Connection Refused

- Docker networking issue: Use ngrok, not localhost
- Wrong port: Verify ngrok is tunneling to port 7003

### SSL Errors

- Rebuild nginx: `docker compose -f docker-compose-walt.yml build nginx`
- Update truststore (see SSL Configuration above)

## License

[Apache License 2.0](https://github.com/walt-id/waltid-identity/blob/main/LICENSE)
