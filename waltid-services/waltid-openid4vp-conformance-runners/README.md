# walt.id OpenID4VP Conformance Runners

Conformance test infrastructure for validating walt.id services against the OpenID Foundation conformance suite.

## Supported Tests

| Test Suite | Description | Status | Documentation |
|------------|-------------|--------|---------------|
| **Verifier2** | OpenID4VP verifier compliance | Working | [VERIFIER2-TESTS.md](VERIFIER2-TESTS.md) |
| **Wallet HAIP** | HAIP wallet compliance | Infrastructure ready | [WALLET-HAIP-TESTS.md](WALLET-HAIP-TESTS.md) |
| **Issuer** | OID4VCI issuer compliance | Planned | - |

## Quick Start

See [QUICKSTART.md](QUICKSTART.md) for step-by-step setup instructions.

### Prerequisites

- Docker and Docker Compose
- Java 21+
- ngrok (for Verifier2 tests)
- `/etc/hosts` entry: `127.0.0.1 localhost.emobix.co.uk`

### Run Verifier2 Tests

```bash
# 1. Start conformance suite
cd ~/dev/openid/conformance-suite
docker compose -f docker-compose-walt.yml up -d

# 2. Start ngrok tunnel (in separate terminal)
ngrok http 7003
# Note the HTTPS URL

# 3. Run tests
cd ~/dev/walt-id/waltid-unified-build/waltid-identity
export VERIFIER_NGROK_URL="https://your-ngrok-url.ngrok-free.app"
./gradlew :waltid-services:waltid-openid4vp-conformance-runners:test --tests "Verifier2ConformanceTests"
```

### Run Wallet HAIP Tests

```bash
cd ~/dev/walt-id/waltid-unified-build/waltid-identity
./gradlew :waltid-services:waltid-openid4vp-conformance-runners:test --tests "WalletHAIPConformanceTests"
```

## Test Plans Overview

### Verifier2 Test Plans

| Plan | Format | Client ID | Response Mode | Modules |
|------|--------|-----------|---------------|---------|
| Plan 1 | ISO mDL | x509_san_dns | direct_post | 3 |
| Plan 2 | SD-JWT VC | x509_hash | direct_post.jwt | 10 |

**Plan 1: mDL with X.509 SAN DNS (Plain VP)**
- Happy flow verification
- Request URI via POST method
- Invalid session transcript rejection

**Plan 2: SD-JWT VC with X.509 Hash (HAIP)**
- Happy flow with encrypted response
- Minimal confirmation key
- Invalid KB-JWT signature rejection
- Invalid credential signature rejection
- Invalid selective disclosure hash rejection
- Invalid KB-JWT nonce/audience rejection
- KB-JWT timestamp validation

See [VERIFIER2-TESTS.md](VERIFIER2-TESTS.md) for detailed test module descriptions.

### Wallet HAIP Test Plans

| Plan | Format | Description | Modules |
|------|--------|-------------|---------|
| Plan 1 | SD-JWT VC | Baseline HAIP validation | 11 |
| Plan 2 | mDL | Mobile driving license HAIP | 6 |
| Plan 7 | SD-JWT VC | Negative security tests | 9 |

See [WALLET-HAIP-TESTS.md](WALLET-HAIP-TESTS.md) for detailed test module descriptions.

## Project Structure

```
src/
  main/kotlin/id/walt/openid4vp/conformance/
    config/
      ConformanceConfig.kt          # Central configuration
    plans/
      ConformanceTestPlan.kt        # Base interfaces
    adapter/
      WalletConformanceAdapter.kt   # Wallet test adapter
    testplans/
      ConformanceTestRunner.kt      # Verifier test runner
      keys/
        TestKeyMaterial.kt          # Test certificates/keys
      http/
        ConformanceInterface.kt     # Conformance suite API client
      plans/
        MdlX509SanDns...kt          # Verifier test plan: mDL
        SdJwtVcX509...kt            # Verifier test plan: SD-JWT VC
      wallet/
        WalletHAIPPlan1.kt          # Wallet test plans
        WalletHAIPPlan2.kt
        WalletHAIPPlan7.kt
      runner/
        TestPlanRunner.kt           # Test execution engine
  test/kotlin/id/walt/openid4vp/conformance/
    Verifier2ConformanceTests.kt    # Main verifier tests
    ConformanceTests.kt             # Deprecated alias
    WalletHAIPConformanceTests.kt   # Wallet HAIP tests
```

## Configuration

### Environment Variables

| Variable | Description | Required For |
|----------|-------------|--------------|
| `VERIFIER_NGROK_URL` | ngrok tunnel URL for verifier | Verifier2 tests |

### ConformanceConfig.kt

Central configuration object:

```kotlin
object ConformanceConfig {
    const val CONFORMANCE_HOST = "localhost.emobix.co.uk"
    const val CONFORMANCE_PORT = 8443
    const val VERIFIER_LOCAL_HOST = "0.0.0.0"
    const val VERIFIER_LOCAL_PORT = 7003
    const val WALLET_API_URL = "http://127.0.0.1:7005"
    const val WALLET_ADAPTER_PORT = 7006
}
```

## SSL Configuration

The project includes `conformance-truststore.jks` with the conformance suite's self-signed certificate.
Gradle automatically configures this truststore when running tests.

### IntelliJ Run Configuration

Add VM options:
```
-Djavax.net.ssl.trustStore=/path/to/conformance-truststore.jks
-Djavax.net.ssl.trustStorePassword=changeit
```

### Update Certificate (if needed)

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
- ngrok URL not set: `export VERIFIER_NGROK_URL="https://..."`

### Connection Refused

- Use ngrok for Verifier2 tests (Docker cannot reach host localhost)
- Verify port: ngrok must tunnel to port 7003

### SSL Errors

- Rebuild nginx: `docker compose -f docker-compose-walt.yml build nginx`
- Update truststore (see SSL Configuration above)

### Address Already in Use

- Kill existing process: `sudo lsof -i :7003` then `kill <PID>`
- Tests start their own embedded verifier

## Documentation

- [QUICKSTART.md](QUICKSTART.md) - Quick setup guide
- [VERIFIER2-TESTS.md](VERIFIER2-TESTS.md) - Verifier2 test plan details
- [WALLET-HAIP-TESTS.md](WALLET-HAIP-TESTS.md) - Wallet HAIP test plan details

## External Resources

- [OpenID4VP Specification](https://openid.net/specs/openid-4-verifiable-presentations-1_0.html)
- [HAIP Specification](https://openid.net/specs/openid4vc-high-assurance-interoperability-profile-1_0.html)
- [Conformance Suite](https://gitlab.com/openid/conformance-suite)

## License

[Apache License 2.0](https://github.com/walt-id/waltid-identity/blob/main/LICENSE)
