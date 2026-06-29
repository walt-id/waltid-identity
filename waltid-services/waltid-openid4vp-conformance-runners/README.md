# walt.id OpenID4VP Conformance Runners

Utilities and instructions to run OpenID4VP 1.0 conformance tests against walt.id services.

## Quick Start (Docker)

The fastest way to run conformance tests locally.

### Prerequisites
- Docker and Docker Compose
- Java 21+
- `/etc/hosts` entry: `127.0.0.1 localhost.emobix.co.uk`

Add the hosts entry:
```bash
echo "127.0.0.1 localhost.emobix.co.uk" | sudo tee -a /etc/hosts
```

### 1. Clone and Start the Conformance Suite

```bash
# Clone the conformance suite (if not already done)
git clone https://gitlab.com/openid/conformance-suite.git ~/dev/openid/conformance-suite

# Copy walt.id specific docker compose
cp docker-compose-walt.yml ~/dev/openid/conformance-suite/

# Start with Docker
cd ~/dev/openid/conformance-suite
docker compose -f docker-compose-walt.yml up -d
```

Wait approximately 30 seconds for the server to start, then verify:
```bash
curl -k https://localhost.emobix.co.uk:8443/
```

You should see the conformance suite web interface.

### 2. Run Verifier Conformance Tests

```bash
# From the waltid-unified-build directory
cd ~/dev/walt-id/waltid-unified-build/waltid-identity

# Run verifier tests
./gradlew :waltid-services:waltid-openid4vp-conformance-runners:test \
    --tests "id.walt.openid4vp.conformance.ConformanceTests"
```

Or run the main application:
```bash
./gradlew :waltid-services:waltid-openid4vp-conformance-runners:run
```

### 3. Run Wallet HAIP Conformance Tests

```bash
cd ~/dev/walt-id/waltid-unified-build/waltid-identity

# Run all wallet HAIP tests
./gradlew :waltid-services:waltid-openid4vp-conformance-runners:test \
    --tests "*WalletHAIPConformanceTests"

# Run specific plan
./gradlew :waltid-services:waltid-openid4vp-conformance-runners:test \
    --tests "*WalletHAIPConformanceTests.HAIP Plan 1*"
```

### 4. Stop the Conformance Suite

```bash
cd ~/dev/openid/conformance-suite
docker compose -f docker-compose-walt.yml down
```

## SSL Certificate (Already Configured)

This project includes a bundled truststore (`conformance-truststore.jks`) with the conformance suite's self-signed certificate. It's automatically used when running via Gradle.

### Updating the Certificate

If you rebuild the conformance suite's nginx container, extract and import the new certificate:

```bash
# Extract certificate from running server
openssl s_client -connect localhost.emobix.co.uk:8443 -servername localhost.emobix.co.uk </dev/null 2>/dev/null | \
  openssl x509 -outform PEM > conformance-test.pem

# Update truststore
keytool -delete -alias conformance-test-localhost -keystore conformance-truststore.jks -storepass changeit 2>/dev/null || true
keytool -importcert -trustcacerts -alias conformance-test-localhost \
  -file conformance-test.pem -keystore conformance-truststore.jks \
  -storepass changeit -noprompt
```

### Running from IntelliJ

Add these VM options to your run configuration:
```
-Djavax.net.ssl.trustStore=/absolute/path/to/waltid-unified-build/waltid-identity/waltid-services/waltid-openid4vp-conformance-runners/conformance-truststore.jks
-Djavax.net.ssl.trustStorePassword=changeit
```

## Test Plans

### Verifier Tests (Implemented)

OpenID4VP 1.0 verifier conformance:
- `MdlX509SanDnsRequestUriSignedDirectPost` - ISO mDL with signed request
- `SdJwtVcX509SanDnsRequestUriSignedDirectPostJwt` - SD-JWT VC with signed request and encrypted response

### Wallet HAIP Tests (Implemented)

High Assurance Interoperability Profile wallet conformance:

| Plan | Format | Client ID | Response Mode | Modules | Status |
|------|--------|-----------|---------------|---------|--------|
| Plan 1 | SD-JWT VC | x509_san_dns | direct_post.jwt | 11 | MVP |
| Plan 2 | mDL | x509_san_dns | direct_post.jwt | 6 | MVP |
| Plan 7 | SD-JWT VC | x509_san_dns | direct_post.jwt | 9 | MVP (Negative) |

All HAIP tests validate:
- Signed request authentication (MANDATORY)
- Encrypted response generation (MANDATORY)
- P-256 key curve (MANDATORY)
- SHA-256 hash algorithm (MANDATORY)
- Holder binding (KB-JWT for SD-JWT, DeviceAuth for mdoc)

### Test Coverage

**Implemented:**
- Verifier-side: SD-JWT VC, ISO mDL
- Wallet-side: HAIP Plans 1, 2, 7

**Pending:**
- Wallet HAIP Plans 3-6 (PhotoID, multi-credential, DC API, alternative client ID schemes)

## Configuration Files

- `conformance-config1.json` - Example verifier test plan configuration
- `conformance-verifier-config1.conf` - Example verifier configuration
- `conformance-truststore.jks` - SSL truststore for conformance suite
- `docker-compose-walt.yml` - Docker compose with custom nginx for proper SSL
- `nginx/` - Custom nginx configuration directory

## Troubleshooting

### SSL Handshake Errors
If you see `SSLHandshakeException` or certificate errors:
1. Ensure `docker-compose-walt.yml` was used (builds nginx with proper cert)
2. Verify truststore is being used (check Gradle output for JVM args)
3. Try rebuilding: `docker compose -f docker-compose-walt.yml build nginx`
4. Re-extract and import the certificate (see SSL Certificate section above)

### Conformance Suite Not Starting
Check container logs:
```bash
docker logs conformance-suite-server-1
docker logs conformance-suite-nginx-1
```

Common issues:
- Port 8443 already in use
- MongoDB initialization taking longer than expected
- Docker network issues

### Tests Skip or Fail

**Tests are skipped:**
- Conformance suite not available (check with `curl -k https://localhost.emobix.co.uk:8443/`)
- Conformance suite version check failed

**Wallet tests fail:**
- Wallet HAIP features not yet fully implemented (WAL-896 in progress)
- Wallet endpoint not responding at expected URL
- Security policies not configured

### Wallet Test Requirements

For wallet tests to PASS, you need:
1. Conformance suite running (`docker-compose-walt.yml up`)
2. Wallet implementation with HAIP support:
   - Signed request authentication
   - Encrypted response generation
   - Security policy configuration
   - KB-JWT/DeviceAuth holder binding
3. Wallet HTTP endpoint responding at configured URL

## Alternative: Setup with Devenv (Nix)

For the full nix/devenv setup (creates CA, manages hosts file automatically):

### Install Nix

```bash
# Option 1: Native package manager (if available)
sudo pacman -S nix  # Arch
# or
sudo apt install nix  # Debian/Ubuntu

# Option 2: Official installer
sh <(curl --proto '=https' --tlsv1.2 -L https://nixos.org/nix/install) --daemon
```

Enable and start the nix daemon:
```bash
sudo systemctl enable --now nix-daemon.service
```

### Install Devenv

```bash
nix-env --install --attr devenv -f https://github.com/NixOS/nixpkgs/tarball/nixpkgs-unstable
```

### Run Conformance Suite with Devenv

```bash
cd ~/dev/openid/conformance-suite
devenv up
```

In another terminal:
```bash
cd ~/dev/openid/conformance-suite
mvn spring-boot:run
```

Visit: https://localhost.emobix.co.uk:8443/

## Documentation

- **WALLET-HAIP-TESTS.md** - Detailed wallet HAIP test documentation
- **README.md** - This file (general setup and usage)
- [OpenID4VP Spec](https://openid.net/specs/openid-4-verifiable-presentations-1_0.html)
- [HAIP Spec](https://openid.net/specs/openid4vc-high-assurance-interoperability-profile-1_0.html)
- [Conformance Suite GitLab](https://gitlab.com/openid/conformance-suite)

## Join the Community

- Connect: [Discord](https://discord.gg/AW8AgqJthZ) | [Newsletter](https://walt.id/newsletter) | [YouTube](https://www.youtube.com/channel/UCXfOzrv3PIvmur_CmwwmdLA) | [LinkedIn](https://www.linkedin.com/company/walt-id/)
- Support: [GitHub Issues](https://github.com/walt-id/waltid-identity/issues)
- Docs: [docs.walt.id](https://docs.walt.id)

## License

Licensed under the [Apache License, Version 2.0](https://github.com/walt-id/waltid-identity/blob/main/LICENSE)
