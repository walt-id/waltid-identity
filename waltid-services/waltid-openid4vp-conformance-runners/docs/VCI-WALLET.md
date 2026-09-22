# VCI Wallet Conformance Tests

Tests OpenID4VCI wallet compliance against the OpenID Foundation Conformance Suite, acting as the
issuer. One Gradle test run drives all 4 test methods (pre-authorized code, SD-JWT VC + DPoP +
authorization_code, ISO mdoc + DPoP + authorization_code, SD-JWT VC HAIP full target) — the suite
acts as the issuer and a wallet adapter answers over HTTP.

## Test Profiles

| Profile | Test Plan | Format | Grant Type | Client Auth |
|---------|-----------|--------|------------|--------------|
| SD-JWT VC + DPoP (pre-authorized code) | `oid4vci-1_0-wallet-test-plan` | SD-JWT VC | pre_authorization_code | - |
| SD-JWT VC + DPoP | `oid4vci-1_0-wallet-test-plan` | SD-JWT VC | authorization_code | private_key_jwt |
| ISO mdoc + DPoP | `oid4vci-1_0-wallet-test-plan` | mso_mdoc | authorization_code | private_key_jwt |
| SD-JWT VC HAIP | `oid4vci-1_0-wallet-haip-test-plan` | SD-JWT VC | authorization_code | client_attestation |

## Quick Start

1. **Start the conformance suite**

   ```bash
   cd ~/dev/openid/conformance-suite
   docker compose -f docker-compose-prebuilt.yml up -d
   ```

   See [docs/VP-VERIFIER.md](VP-VERIFIER.md#quick-start) Quick Start step 1 if the nginx cert needs
   the `localhost.emobix.co.uk` SAN patch — same suite checkout, same one-time fix.

2. **Nothing else to start manually.** Both the wallet adapter (port 7007) and the wallet itself
   (an in-process Wallet2 on port 7016) are spun up inside the JUnit test
   (`VciWalletConformanceTests.kt`) — there's no standalone `wallet-api2` process or pre-configured
   test wallet involved.

3. **Tunnel the adapter's port 7007** so the suite (running in Docker) can reach it:

   ```bash
   ngrok http 7007
   ```

4. **Run the whole suite**:

   ```bash
   cd ~/dev/walt-id/waltid-unified-build

   echo | openssl s_client -connect localhost.emobix.co.uk:8443 -servername localhost.emobix.co.uk 2>/dev/null \
     | openssl x509 > /tmp/conformance-suite-cert.pem
   export CONFORMANCE_EXTRA_CA_PEM=/tmp/conformance-suite-cert.pem

   export CONFORMANCE_VCI_WALLET_ADAPTER_BASE_URL="https://<your-ngrok-url>.ngrok-free.app"  # from step 3
   export CONFORMANCE_ADAPTER_HOST="<your-ngrok-url>.ngrok-free.app"  # same host, no scheme

   ./gradlew :waltid-services:waltid-openid4vp-conformance-runners:test --tests "VciWalletConformanceTests" --rerun
   ```

   `--rerun` matters: Gradle doesn't see these env vars as task inputs, so a cached `test` task
   silently no-ops without it.

5. **Read the results**:
   - `build/reports/openid-conformance/vci-wallet/summary.md` and `results.json` (this run only, gitignored)
   - `./export-verifier-results.py --results build/reports/openid-conformance/vci-wallet/results.json --output docs/VCI-WALLET-RESULTS.md --title "VCI-Wallet Conformance Results"`
     turns that into a committable snapshot at [docs/VCI-WALLET-RESULTS.md](VCI-WALLET-RESULTS.md)
   - Per-module suite logs: `https://localhost.emobix.co.uk:8443/log-detail.html?log=<test_id>`

## Test Results

Current breakdown: [docs/VCI-WALLET-RESULTS.md](VCI-WALLET-RESULTS.md) (regenerate per Quick Start
step 5 after a run).

## Prerequisites

- **Conformance Suite** running locally via Docker (see Quick Start)
- **ngrok** exposing the wallet adapter's port 7007 to the internet — in this sandboxed
  environment `host.docker.internal` doesn't route from the suite's container back to the host
  (see [docs/VCI-ISSUER.md](VCI-ISSUER.md#connect-timed-out-errors)); on a normal host, try
  `CONFORMANCE_ADAPTER_HOST=host.docker.internal` (the default) first and skip the tunnel

## Environment Variables

| Variable | Description | Default |
|----------|-------------|---------|
| `CONFORMANCE_EXTRA_CA_PEM` | Extra CA/cert to trust for the suite's self-signed cert | unset |
| `CONFORMANCE_HOST` / `CONFORMANCE_PORT` | Override the suite's host/port | `localhost.emobix.co.uk:8443` |
| `CONFORMANCE_ADAPTER_HOST` | Host the suite uses to reach the adapter | `host.docker.internal` |
| `CONFORMANCE_VCI_WALLET_ADAPTER_BASE_URL` | Public HTTPS base for the adapter when the suite cannot reach `host:port` directly | unset |

## Architecture

The **VciWalletConformanceAdapter** bridges the conformance suite (acting as issuer) with an
in-process Wallet2 instance: it receives the credential offer, discovers issuer metadata, drives
the authorization/token exchange (with DPoP), requests the credential with proof, and stores it.

```
Conformance Suite (Issuer) ──▶ Wallet Adapter (:7007, in-process) ──▶ Wallet2 (:7016, in-process)
```

Only port 7007 needs to be reachable by the suite; port 7016 is only ever called by the adapter,
on the same host.

## Troubleshooting

### "Failed to discover issuer metadata" / connect timeouts

The suite (in Docker) can't reach the adapter. Verify the tunnel is up and
`CONFORMANCE_VCI_WALLET_ADAPTER_BASE_URL`/`CONFORMANCE_ADAPTER_HOST` point at it.

### `PKIX path building failed`

The suite's self-signed cert isn't trusted by the Gradle JVM — re-run the `openssl`/
`export CONFORMANCE_EXTRA_CA_PEM` lines in Quick Start step 4.

## References

- [OpenID4VCI Spec](https://openid.net/specs/openid-4-verifiable-credential-issuance-1_0.html)
- [HAIP Profile](https://openid.net/specs/openid4vc-high-assurance-interoperability-profile-sd-jwt-vc-1_0.html)
- [Conformance Suite](https://openid.net/certification/faq/)
