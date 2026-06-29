# walt.id OpenID4VP/VCI Conformance Runners

Conformance test infrastructure for validating walt.id services against the OpenID Foundation conformance suite.

## HAIP Compliance

All test suites include HAIP (High Assurance Interoperability Profile) validation for eIDAS 2.0 compliance.

HAIP requirements validated across all components:
- Signed authorization requests (MANDATORY)
- Encrypted responses (MANDATORY)
- P-256 key curve enforcement (MANDATORY)
- SHA-256 hash algorithm (MANDATORY)
- Holder binding validation

## Supported Tests

| Test Suite | Description | Status | Documentation |
|------------|-------------|--------|---------------|
| **Verifier** | OpenID4VP verifier compliance | Working | [VERIFIER-TESTS.md](VERIFIER-TESTS.md) |
| **Wallet** | OpenID4VP wallet compliance | Infrastructure ready | [WALLET-TESTS.md](WALLET-TESTS.md) |
| **Issuer** | OID4VCI issuer compliance | Working | [ISSUER-TESTS.md](ISSUER-TESTS.md) |

## Quick Start

See [QUICKSTART.md](QUICKSTART.md) for step-by-step setup instructions.

### Prerequisites

- Docker and Docker Compose
- Java 21+
- ngrok (for Verifier tests)
- `/etc/hosts` entry: `127.0.0.1 localhost.emobix.co.uk`

### Run Verifier Tests

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
./gradlew :waltid-services:waltid-openid4vp-conformance-runners:test --tests "VerifierConformanceTests"
```

### Run Issuer Tests

```bash
# 1. Start conformance suite (if not already running)
cd ~/dev/openid/conformance-suite
docker compose -f docker-compose-walt.yml up -d

# 2. Start issuer service
# (Option A: OSS issuer on port 7002)
# (Option B: Enterprise issuer)

# 3. Configure and run tests
cd ~/dev/walt-id/waltid-unified-build/waltid-identity
export OPENID4VCI_CONFORMANCE_CREDENTIAL_ISSUER_URL="http://localhost:7002"
./gradlew :waltid-services:waltid-openid4vp-conformance-runners:test --tests "IssuerConformanceTests"
```

### Run Wallet Tests

```bash
cd ~/dev/walt-id/waltid-unified-build/waltid-identity
./gradlew :waltid-services:waltid-openid4vp-conformance-runners:test --tests "WalletConformanceTests"
```

## Test Plans Overview

### Verifier Test Plans

| Plan | Format | Client ID | Response Mode | HAIP |
|------|--------|-----------|---------------|------|
| Plan 1 | ISO mDL | x509_san_dns | direct_post | No |
| Plan 2 | SD-JWT VC | x509_hash | direct_post.jwt | Yes |

See [VERIFIER-TESTS.md](VERIFIER-TESTS.md) for detailed test module descriptions.

### Issuer Test Plans

| Plan | Grant Type | Client Auth | Sender Constraint | HAIP |
|------|------------|-------------|-------------------|------|
| Plan 1 | authorization_code | client_attestation | DPoP | Yes |
| Plan 2 | pre_authorization_code | client_attestation | DPoP | Yes |

See [ISSUER-TESTS.md](ISSUER-TESTS.md) for detailed test module descriptions.

### Wallet Test Plans

| Plan | Format | Description | HAIP | Modules |
|------|--------|-------------|------|---------|
| Plan 1 | SD-JWT VC | Baseline validation | Yes | 14 |
| Plan 2 | mDL | Mobile driving license | Yes | 6 |
| Plan 7 | SD-JWT VC | Negative security tests | Yes | 9 |

See [WALLET-TESTS.md](WALLET-TESTS.md) for detailed test module descriptions.

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
      IssuerConformanceTestRunner.kt # Issuer test runner
      keys/
        TestKeyMaterial.kt          # Test certificates/keys
      http/
        ConformanceInterface.kt     # Conformance suite API client
        VerifierInterface.kt        # Verifier API client
      plans/
        IssuerTestPlan.kt           # Issuer test plan interface
        Oid4vciIssuer...kt          # Issuer test plans
        MdlX509SanDns...kt          # Verifier test plan: mDL
        SdJwtVcX509...kt            # Verifier test plan: SD-JWT VC
      wallet/
        WalletPlan1.kt              # Wallet test plans
        WalletPlan2.kt
        WalletPlan7.kt
      runner/
        TestPlanRunner.kt           # Verifier test execution
        IssuerTestPlanRunner.kt     # Issuer test execution
        WalletTestPlanRunner.kt     # Wallet test execution
  test/kotlin/id/walt/openid4vp/conformance/
    VerifierConformanceTests.kt     # Main verifier tests
    IssuerConformanceTests.kt       # Issuer conformance tests
    WalletConformanceTests.kt       # Wallet tests
    ConformanceTests.kt             # Deprecated alias
```

## Configuration

### Environment Variables

| Variable | Description | Required For |
|----------|-------------|--------------|
| `VERIFIER_NGROK_URL` | ngrok tunnel URL for verifier | Verifier tests |
| `OPENID4VCI_CONFORMANCE_CREDENTIAL_ISSUER_URL` | Issuer URL | Issuer tests |
| `OPENID4VCI_CONFORMANCE_ENTERPRISE_TARGET` | Enterprise target | Issuer tests (Enterprise) |

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
- ngrok URL not set (Verifier tests): `export VERIFIER_NGROK_URL="https://..."`
- Issuer URL not set: `export OPENID4VCI_CONFORMANCE_CREDENTIAL_ISSUER_URL="http://..."`

### Connection Refused

- Use ngrok for Verifier tests (Docker cannot reach host localhost)
- Verify port: ngrok must tunnel to port 7003

### SSL Errors

- Rebuild nginx: `docker compose -f docker-compose-walt.yml build nginx`
- Update truststore (see SSL Configuration above)

### Address Already in Use

- Kill existing process: `sudo lsof -i :7003` then `kill <PID>`
- Tests start their own embedded verifier

## Documentation

- [QUICKSTART.md](QUICKSTART.md) - Quick setup guide
- [VERIFIER-TESTS.md](VERIFIER-TESTS.md) - Verifier test plan details
- [ISSUER-TESTS.md](ISSUER-TESTS.md) - Issuer test plan details
- [WALLET-TESTS.md](WALLET-TESTS.md) - Wallet test plan details

## External Resources

- [OpenID4VP Specification](https://openid.net/specs/openid-4-verifiable-presentations-1_0.html)
- [OpenID4VCI Specification](https://openid.net/specs/openid-4-verifiable-credential-issuance-1_0.html)
- [HAIP Specification](https://openid.net/specs/openid4vc-high-assurance-interoperability-profile-1_0.html)
- [Conformance Suite](https://gitlab.com/openid/conformance-suite)

## License

[Apache License 2.0](https://github.com/walt-id/waltid-identity/blob/main/LICENSE)
