# VP-Verifier Conformance Test Documentation

Tests OpenID4VP verifier compliance (verifier-api2 / "Verifier2") against the OpenID Foundation
Conformance Suite. One Gradle test run drives all 14 applicable variants
(`VerifierVariantMatrix.all()` — SD-JWT VC and mDL, every client-id scheme, request method and
response mode, plus the two HAIP points) automatically, no browser needed: the suite acts as the
wallet and verifier-api2 answers over HTTP.

## Quick Start

1. **Start the conformance suite** 

   ```bash
   cd ~/dev/openid/conformance-suite
   docker compose -f docker-compose-prebuilt.yml up -d
   ```

   The suite's own self-signed nginx cert only covers `CN=localhost` with no SAN, but the suite is
   accessed as `localhost.emobix.co.uk` (matches `/etc/hosts` and `BASE_URL`). Modern JDKs reject
   that on hostname verification. This bug is in the upstream repo too, so patch it once per
   checkout of `~/dev/openid/conformance-suite` (already fixed in this repo's own
   `nginx/Dockerfile*` for the `docker-compose-walt.yml` fallback):

   ```diff
   -		-subj "/CN=localhost" \
   +		-subj "/CN=localhost.emobix.co.uk" \
   +		-addext "subjectAltName=DNS:localhost.emobix.co.uk,DNS:localhost" \
   ```

   in `nginx/Dockerfile`, then rebuild and recreate just that container:

   ```bash
   docker build -t registry.gitlab.com/openid/conformance-suite/nginx:latest ./nginx
   docker compose -f docker-compose-prebuilt.yml up -d --force-recreate nginx
   ```

2. **Trust that cert for the Gradle JVM** (self-signed, not in any public CA bundle) - the
   `openssl`/`export CONFORMANCE_EXTRA_CA_PEM` lines in step 5 below do this; no separate action
   needed here, this step just explains why they're there.

3. **Start verifier-api2** — check the log for the port it actually binds
   ([config/web.conf](../../waltid-verifier-api2/config/web.conf) currently says `7004`, older docs said `7003`):

   ```bash
   cd ~/dev/walt-id/waltid-unified-build
   ./gradlew :waltid-services:waltid-verifier-api2:run
   ```

4. **Tunnel it**:

   ```bash
   ngrok http 7004
   ```

5. **Run the whole suite** (~9 minutes for all 14 variants). This block is self-contained - it
   re-does the cert trust step, so it works even in a fresh terminal that never ran step 2:

   ```bash
   cd ~/dev/walt-id/waltid-unified-build

   echo | openssl s_client -connect localhost.emobix.co.uk:8443 -servername localhost.emobix.co.uk 2>/dev/null \
     | openssl x509 > /tmp/conformance-suite-cert.pem
   export CONFORMANCE_EXTRA_CA_PEM=/tmp/conformance-suite-cert.pem

   export VERIFIER_NGROK_URL="https://<your-ngrok-url>.ngrok-free.app"  # from step 4's output

   ./gradlew :waltid-services:waltid-openid4vp-conformance-runners:test --tests "VerifierConformanceTests" --rerun
   ```

   `--rerun` matters: Gradle doesn't see `VERIFIER_NGROK_URL`/`CONFORMANCE_EXTRA_CA_PEM` as task
   inputs, so a cached `test` task silently no-ops without it.

   If this still gets skipped instead of running, check `build/reports/openid-conformance/vp-verifier/summary.md`'s
   "not available" error, or just rerun with `--info` and look for `PKIX path building failed`
   (cert not trusted - re-run the `openssl`/`export CONFORMANCE_EXTRA_CA_PEM` lines above) versus
   `Cannot reach verifier` (ngrok URL wrong, or verifier-api2/ngrok not actually running).

6. **Read the results**:
   - `build/reports/openid-conformance/vp-verifier/summary.md` and `results.json` (this run only, gitignored)
   - `./export-verifier-results.py` turns that into a committable snapshot at
     [docs/VP-VERIFIER-RESULTS.md](VP-VERIFIER-RESULTS.md) — run it after every suite run and commit
     the result to keep a tracked history
   - Per-module suite logs: `https://localhost.emobix.co.uk:8443/log-detail.html?log=<test_id>`

## Test Results

Current per-profile, per-module breakdown: [docs/VP-VERIFIER-RESULTS.md](VP-VERIFIER-RESULTS.md)
(regenerate with `./export-verifier-results.py` after a run — see Quick Start step 6).

## Prerequisites

- **verifier-api2** running (see Quick Start for the current port)
- **ngrok** exposing that port to the internet
- **Conformance Suite** running locally via Docker (see Quick Start)

## Environment Variables

| Variable | Description | Example |
|----------|--------------|---------|
| `VERIFIER_NGROK_URL` | ngrok HTTPS URL for verifier-api2 | `https://844a-xxx.ngrok-free.app` |
| `CONFORMANCE_EXTRA_CA_PEM` | Extra CA/cert to trust in addition to the committed truststore (needed for a devenv- or manually-run suite whose cert isn't the one baked into `conformance-truststore.jks`) | `/tmp/conformance-suite-cert.pem` |
| `CONFORMANCE_HOST` / `CONFORMANCE_PORT` | Override the suite's host/port | default `localhost.emobix.co.uk:8443` |

## Test Configuration Details

### Certificate Chain

All tests use the same verifier certificate chain:
- **Leaf**: `CN=verifier.example.com` (P-256, signed by Intermediate CA)
- **Intermediate**: `CN=walt.id Verifier Intermediate CA` (signed by Root CA)
- **Root**: `CN=walt.id Verifier Root CA` (self-signed, not included in chain)

**Important**: The conformance suite validates that the leaf certificate is NOT self-signed.

### DCQL Queries

#### mDL Query
```json
{
  "credentials": [{
    "id": "my_mdl",
    "format": "mso_mdoc",
    "meta": { "doctype_value": "org.iso.18013.5.1.mDL" },
    "claims": [
      { "path": ["org.iso.18013.5.1", "family_name"] },
      { "path": ["org.iso.18013.5.1", "given_name"] },
      ...
    ]
  }]
}
```

#### SD-JWT VC Query
```json
{
  "credentials": [{
    "id": "pid",
    "format": "dc+sd-jwt",
    "meta": { "vct_values": ["https://credentials.example.com/identity_credential"] },
    "claims": [
      { "path": ["given_name"] },
      { "path": ["family_name"] },
      { "path": ["birthdate"] },
      { "path": ["age_in_years"] }
    ]
  }]
}
```

## Troubleshooting

### "Waited for 30 tries, but test is still not ready"

This occurs when the conformance suite doesn't receive the authorization request from the verifier. Check:
1. ngrok is running and URL is correct
2. verifier-api2 is listening on the port ngrok is forwarding (check its startup log)
3. `VERIFIER_NGROK_URL` environment variable is set

### `PKIX path building failed` / `No name matching localhost.emobix.co.uk found`

The suite's self-signed cert isn't trusted, or lacks a SAN for `localhost.emobix.co.uk`. See
Quick Start steps 1–2.

### Test Logs

View detailed test logs in the conformance suite UI:
```
https://localhost.emobix.co.uk:8443/log-detail.html?log=<test_id>
```

## CI reports and soft-fail

When verifier conformance runs in CI (or locally), the runner writes:

```text
build/reports/openid-conformance/vp-verifier/summary.md
build/reports/openid-conformance/vp-verifier/results.json
```

The OSS Gradle workflow appends these to the GitHub Actions job summary.
Soft-fail is controlled by the repo Actions variable `CONFORMANCE_ALLOW_FAILURE`
(see the module [README](../README.md#ci-summaries-and-soft-fail)).

## References

- [OpenID4VP 1.0 Final Spec](https://openid.net/specs/openid-4-verifiable-presentations-1_0.html)
- [HAIP Profile](https://openid.net/specs/openid4vc-high-assurance-interoperability-profile-sd-jwt-vc-1_0.html)
- [Conformance Suite](https://openid.net/certification/faq/)
