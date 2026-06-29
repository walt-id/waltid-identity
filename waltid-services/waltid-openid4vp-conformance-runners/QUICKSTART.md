# Quick Setup Guide - Running Wallet HAIP Conformance Tests Locally

This guide walks you through running wallet HAIP conformance tests against the local OpenID conformance suite.

## Prerequisites

- Docker and Docker Compose installed
- Java 21+ installed
- Ubuntu/Linux environment

## Step 1: Setup /etc/hosts

Add the conformance suite hostname:

```bash
echo "127.0.0.1 localhost.emobix.co.uk" | sudo tee -a /etc/hosts
```

## Step 2: Clone and Setup Conformance Suite

```bash
# Clone the OpenID conformance suite repository
git clone https://gitlab.com/openid/conformance-suite.git ~/dev/openid/conformance-suite

# Copy walt.id specific docker compose file
cp ~/dev/walt-id/waltid-unified-build/waltid-identity/waltid-services/waltid-openid4vp-conformance-runners/docker-compose-walt.yml ~/dev/openid/conformance-suite/

# Copy nginx configuration
cp -r ~/dev/walt-id/waltid-unified-build/waltid-identity/waltid-services/waltid-openid4vp-conformance-runners/nginx ~/dev/openid/conformance-suite/
```

## Step 3: Start Conformance Suite

```bash
cd ~/dev/openid/conformance-suite
docker compose -f docker-compose-walt.yml up -d
```

Wait approximately 30 seconds for initialization.

## Step 4: Verify Conformance Suite is Running

```bash
curl -k https://localhost.emobix.co.uk:8443/
```

You should see HTML output from the conformance suite web interface.

You can also open in browser: https://localhost.emobix.co.uk:8443/
(Accept the self-signed certificate warning)

## Step 5: Run Wallet HAIP Tests

```bash
cd ~/dev/walt-id/waltid-unified-build/waltid-identity

# Run all wallet HAIP tests
./gradlew :waltid-services:waltid-openid4vp-conformance-runners:test \
    --tests "*WalletHAIPConformanceTests"
```

### Run Specific Test Plans

```bash
# Run only Plan 1 (SD-JWT VC baseline)
./gradlew :waltid-services:waltid-openid4vp-conformance-runners:test \
    --tests "*WalletHAIPConformanceTests.HAIP Plan 1*"

# Run only Plan 2 (mDL baseline)
./gradlew :waltid-services:waltid-openid4vp-conformance-runners:test \
    --tests "*WalletHAIPConformanceTests.HAIP Plan 2*"

# Run only Plan 7 (negative tests)
./gradlew :waltid-services:waltid-openid4vp-conformance-runners:test \
    --tests "*WalletHAIPConformanceTests.HAIP Plan 7*"
```

## Step 6: View Test Results

Test results are generated at:
```
waltid-identity/waltid-services/waltid-openid4vp-conformance-runners/build/reports/tests/test/index.html
```

Open in browser to see detailed test results.

## Step 7: Stop Conformance Suite

When done testing:

```bash
cd ~/dev/openid/conformance-suite
docker compose -f docker-compose-walt.yml down
```

## Expected Results

Currently, the wallet tests will SKIP because:
- Wallet HAIP features are not yet fully implemented (WAL-896 in progress)
- Tests check for conformance suite availability and skip gracefully

Once WAL-896 wallet implementation is complete, tests will execute and validate:
- Signed request authentication
- Encrypted response generation
- KB-JWT holder binding (SD-JWT VC)
- DeviceAuth holder binding (mDL)
- P-256 key curve enforcement
- SHA-256 hash algorithm enforcement

## Troubleshooting

### Conformance Suite Won't Start

**Check Docker containers:**
```bash
docker ps
docker logs conformance-suite-server-1
docker logs conformance-suite-nginx-1
```

**Common issues:**
- Port 8443 already in use (check with `sudo lsof -i :8443`)
- MongoDB initialization slow (wait 60 seconds)
- Docker daemon not running

### SSL Certificate Errors

If you see `SSLHandshakeException`:

1. Verify truststore exists:
```bash
ls -la ~/dev/walt-id/waltid-unified-build/waltid-identity/waltid-services/waltid-openid4vp-conformance-runners/conformance-truststore.jks
```

2. Re-extract certificate from running server:
```bash
cd ~/dev/walt-id/waltid-unified-build/waltid-identity/waltid-services/waltid-openid4vp-conformance-runners

openssl s_client -connect localhost.emobix.co.uk:8443 -servername localhost.emobix.co.uk </dev/null 2>/dev/null | \
  openssl x509 -outform PEM > conformance-test.pem

keytool -delete -alias conformance-test-localhost -keystore conformance-truststore.jks -storepass changeit 2>/dev/null || true
keytool -importcert -trustcacerts -alias conformance-test-localhost \
  -file conformance-test.pem -keystore conformance-truststore.jks \
  -storepass changeit -noprompt
```

### Tests Fail (Not Skip)

If tests execute but FAIL:
- Wallet implementation incomplete (expected during WAL-896 development)
- Check wallet logs for errors
- Verify wallet endpoint is accessible
- Check security policy configuration

### Can't Access Web Interface

If browser can't reach https://localhost.emobix.co.uk:8443/:
- Check `/etc/hosts` has entry: `127.0.0.1 localhost.emobix.co.uk`
- Check nginx container is running: `docker ps | grep nginx`
- Check nginx logs: `docker logs conformance-suite-nginx-1`
- Try rebuilding nginx: `docker compose -f docker-compose-walt.yml build nginx`

## Next Steps

Once conformance suite is running:

1. **Implement WAL-896 wallet features** (signed request auth, encrypted response)
2. **Configure wallet security policies** (HAIP mode)
3. **Re-run tests** - they should execute and validate compliance
4. **Fix any failures** iteratively
5. **Submit for OpenID certification** when all tests PASS

## Additional Resources

- **Full README:** `waltid-services/waltid-openid4vp-conformance-runners/README.md`
- **Wallet HAIP Tests:** `waltid-services/waltid-openid4vp-conformance-runners/WALLET-HAIP-TESTS.md`
- **OpenID4VP Spec:** https://openid.net/specs/openid-4-verifiable-presentations-1_0.html
- **HAIP Spec:** https://openid.net/specs/openid4vc-high-assurance-interoperability-profile-1_0.html
- **Conformance Suite:** https://gitlab.com/openid/conformance-suite

## Support

For issues or questions:
- Check existing documentation first
- Open issue in walt.id GitHub repository
- Join walt.id Discord community
