# VP Wallet Conformance Tests

Tests OpenID4VP wallet compliance against the OpenID Foundation Conformance Suite, acting as the
verifier. One Gradle test run drives the full 22-variant matrix
(`WalletVariantMatrix.all()` - both credential formats, every client-id prefix, request method and
response mode, plus the two HAIP points), no browser needed: the suite acts as the verifier and a
wallet adapter answers over HTTP.

## Quick Start

1. **Start the conformance suite** - see [docs/VP-VERIFIER.md](VP-VERIFIER.md#quick-start) Quick
   Start step 1 for the one-time nginx cert SAN patch this suite checkout needs.

   ```bash
   cd ~/dev/openid/conformance-suite
   docker compose -f docker-compose-prebuilt.yml up -d
   ```

2. **Nothing else to start manually.** Both the wallet adapter (port 7006) and the wallet itself
   (an in-process Wallet2 on port 7015) are spun up inside the JUnit test
   (`VpWalletConformanceTests.kt`), which also provisions the wallet with a matching SD-JWT VC and
   mDL before running - there's no standalone `wallet-api2` process or manual credential import.

3. **Tunnel the adapter's port 7006** so the suite (running in Docker) can reach it:

   ```bash
   ngrok http 7006
   ```

4. **Run the whole suite** (full matrix can take up to ~90 minutes - see the `timeout` comment in
   `VpWalletConformanceTests.kt`; narrow it down while iterating with
   `-Dconformance.wallet.variants=x509hash,haip` or similar substring filters):

   ```bash
   cd ~/dev/walt-id/waltid-unified-build

   echo | openssl s_client -connect localhost.emobix.co.uk:8443 -servername localhost.emobix.co.uk 2>/dev/null \
     | openssl x509 > /tmp/conformance-suite-cert.pem
   export CONFORMANCE_EXTRA_CA_PEM=/tmp/conformance-suite-cert.pem

   export CONFORMANCE_VP_WALLET_ADAPTER_URL="https://<your-ngrok-url>.ngrok-free.app/openid4vp/authorize"  # from step 3
   export CONFORMANCE_ADAPTER_HOST="<your-ngrok-url>.ngrok-free.app"  # same host, no scheme

   ./gradlew :waltid-services:waltid-openid4vp-conformance-runners:test --tests "VpWalletConformanceTests" --rerun
   ```

   `--rerun` matters: Gradle doesn't see these env vars as task inputs, so a cached `test` task
   silently no-ops without it.

5. **Read the results**:
   - `build/reports/openid-conformance/vp-wallet/summary.md` and `results.json` (this run only, gitignored)
   - `./export-verifier-results.py --results build/reports/openid-conformance/vp-wallet/results.json --output docs/VP-WALLET-RESULTS.md --title "VP-Wallet Conformance Results"`
     turns that into a committable snapshot at [docs/VP-WALLET-RESULTS.md](VP-WALLET-RESULTS.md)
   - Per-module suite logs: `https://localhost.emobix.co.uk:8443/log-detail.html?log=<test_id>`

## Test Results

Current per-variant breakdown: [docs/VP-WALLET-RESULTS.md](VP-WALLET-RESULTS.md) (regenerate per
Quick Start step 5 after a run).

## Prerequisites

- **Conformance Suite** running locally via Docker (see Quick Start)
- **ngrok** exposing the wallet adapter's port 7006 to the internet - in this sandboxed
  environment `host.docker.internal` doesn't route from the suite's container back to the host
  (see [docs/VCI-ISSUER.md](VCI-ISSUER.md#connect-timed-out-errors)); on a normal host, try
  `CONFORMANCE_ADAPTER_HOST=host.docker.internal` (the default) first and skip the tunnel

## Environment Variables

| Variable | Description | Default |
|----------|-------------|---------|
| `CONFORMANCE_EXTRA_CA_PEM` | Extra CA/cert to trust for the suite's self-signed cert | unset |
| `CONFORMANCE_HOST` / `CONFORMANCE_PORT` | Override the suite's host/port | `localhost.emobix.co.uk:8443` |
| `CONFORMANCE_ADAPTER_HOST` | Host the suite uses to reach the adapter | `host.docker.internal` |
| `CONFORMANCE_VP_WALLET_ADAPTER_URL` | Public HTTPS authorization endpoint when the suite cannot reach `host:port` directly | unset (uses `http://$CONFORMANCE_ADAPTER_HOST:7006/openid4vp/authorize`) |
| `conformance.wallet.variants` (system property, `-D`) | Comma-separated substrings to select a subset of the 22 matrix points | unset (runs everything) |

## Architecture

The **VpWalletConformanceAdapter** bridges the conformance suite (acting as verifier) with an
in-process Wallet2 instance: it deliberately does nothing but forward the authorization request to
the wallet's single-call presentation endpoint, since resolving the request, matching credentials,
building the VP token and posting the response are exactly what's under test.

```
Conformance Suite (Verifier) ──▶ Wallet Adapter (:7006, in-process) ──▶ Wallet2 (:7015, in-process)
```

Only port 7006 needs to be reachable by the suite; port 7015 is only ever called by the adapter,
on the same host.

## Troubleshooting

### "Failed to discover issuer metadata" / connect timeouts

The suite (in Docker) can't reach the adapter. Verify the tunnel is up and
`CONFORMANCE_VP_WALLET_ADAPTER_URL`/`CONFORMANCE_ADAPTER_HOST` point at it.

### `PKIX path building failed`

The suite's self-signed cert isn't trusted by the Gradle JVM - re-run the `openssl`/
`export CONFORMANCE_EXTRA_CA_PEM` lines in Quick Start step 4.

## References

- [OpenID4VP 1.0 Final Spec](https://openid.net/specs/openid-4-verifiable-presentations-1_0.html)
- [HAIP Profile](https://openid.net/specs/openid4vc-high-assurance-interoperability-profile-sd-jwt-vc-1_0.html)
- [Conformance Suite](https://openid.net/certification/faq/)
