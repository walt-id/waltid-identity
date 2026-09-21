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

2. **Trust that cert for the Gradle JVM** (self-signed, not in any public CA bundle):

   ```bash
   echo | openssl s_client -connect localhost.emobix.co.uk:8443 -servername localhost.emobix.co.uk 2>/dev/null \
     | openssl x509 > /tmp/conformance-suite-cert.pem
   export CONFORMANCE_EXTRA_CA_PEM=/tmp/conformance-suite-cert.pem
   ```

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

5. **Run the whole suite** (~9 minutes for all 14 variants):

   ```bash
   cd ~/dev/walt-id/waltid-unified-build
   export VERIFIER_NGROK_URL="https://<your-ngrok-url>.ngrok-free.app"
   ./gradlew :waltid-services:waltid-openid4vp-conformance-runners:test --tests "VerifierConformanceTests" --rerun
   ```

   `--rerun` matters: Gradle doesn't see `VERIFIER_NGROK_URL`/`CONFORMANCE_EXTRA_CA_PEM` as task
   inputs, so a cached `test` task silently no-ops without it.

6. **Read the results**:
   - `build/reports/openid-conformance/vp-verifier/summary.md` and `results.json`
   - Per-module suite logs: `https://localhost.emobix.co.uk:8443/log-detail.html?log=<test_id>`

## Test Profiles (last run: 2026-09-21, suite v5.3.1)

14 variants × up to 8 test modules each = 106 total: **49 passed, 56 failed, 1 skipped**.

Every variant follows the same pattern: all positive (ACCEPT) modules pass, every negative
(REJECT) module comes back `WARNING` from the suite instead of the expected `PASSED` — see
[Open Issues](#open-issues) below.

| Variant (format-clientIdScheme-requestMethod-responseMode-profile) | Positive modules | Negative modules |
|---|---|---|
| sdjwt-redirecturi-urlquery-directpost-plainvp | 4/4 ✅ | 0/7 ❌ |
| sdjwt-redirecturi-urlquery-directpostjwt-plainvp | 4/4 ✅ | 0/7 ❌ |
| sdjwt-x509sandns-requrisigned-directpost-plainvp | 4/4 ✅ | 0/7 ❌ |
| sdjwt-x509sandns-requrisigned-directpostjwt-plainvp | 4/4 ✅ | 0/7 ❌ |
| sdjwt-x509hash-requrisigned-directpost-plainvp | 4/4 ✅ | 0/7 ❌ |
| sdjwt-x509hash-requrisigned-directpostjwt-plainvp | 4/4 ✅ | 0/7 ❌ |
| sdjwt-x509hash-requrisigned-directpostjwt-haip | 4/4 ✅ | 0/7 ❌ |
| mdl-redirecturi-urlquery-directpost-plainvp | 3/3 ✅ | 0/1 ❌ |
| mdl-redirecturi-urlquery-directpostjwt-plainvp | 3/3 ✅ | 0/1 ❌ |
| mdl-x509sandns-requrisigned-directpost-plainvp | 3/3 ✅ | 0/1 ❌ |
| mdl-x509sandns-requrisigned-directpostjwt-plainvp | 3/3 ✅ | 0/1 ❌ |
| mdl-x509hash-requrisigned-directpost-plainvp | 3/3 ✅ | 0/1 ❌ |
| mdl-x509hash-requrisigned-directpostjwt-plainvp | 3/3 ✅ | 0/1 ❌ |
| mdl-x509hash-requrisigned-directpostjwt-haip | 3/3 ✅ | 0/1 ❌ |

Positive modules (all pass, every variant): `happy-flow`, `minimal-cnf-jwk`,
`request-uri-method-post`, `request-uri-fetched-twice`.

Negative modules (all `WARNING`, every SD-JWT variant): `invalid-kb-jwt-signature`,
`invalid-credential-signature`, `invalid-sd-hash`, `invalid-kb-jwt-nonce`, `invalid-kb-jwt-aud`,
`kb-jwt-iat-in-past`, `kb-jwt-iat-in-future`. mDL variants only run `invalid-session-transcript`.

Good news: the two HAIP variants (`x509_hash` client-id scheme + encrypted response) now pass
their `happy-flow` module, meaning the KB-JWT/DeviceAuth audience is accepted correctly under
`x509_hash`. The `AUDIENCE_MISMATCH` bug this doc used to describe here (as of 2026-07-08) is no
longer reproducing — worth a deliberate re-check before closing it out, since nothing yet directly
tests the encrypted-response `aud` path in isolation.

## Open Issues

1. **Negative/REJECT modules return `WARNING`, not `PASSED`, everywhere.** `ExpectedModuleOutcome`
   ([src/main/kotlin/.../runner/req/ExpectedModuleOutcome.kt](../src/main/kotlin/id/walt/openid4vp/conformance/testplans/runner/req/ExpectedModuleOutcome.kt))
   expects a `REJECT` module to reach suite result `PASSED` because "the verifier's 4xx is itself
   the pass criterion." Getting `WARNING` instead on *every single one* of these modules, across
   every variant, needs isolating: is verifier-api2 actually failing to reject the bad KB-JWT
   signature / credential signature / SD-hash / nonce / audience / iat / mdoc session transcript
   presentations, or has the suite's module behavior changed (e.g. it now always parks on
   `WARNING` for manual review instead of resolving straight to `PASSED`)? Check one log via the
   suite UI and correlate with the verifier's own session status first.
2. **Upstream `~/dev/openid/conformance-suite/nginx/Dockerfile` has no SAN on its self-signed
   cert.** Fixed locally per checkout (see Quick Start step 1); not something walt.id can fix by
   editing this repo. Worth a merge request upstream, or at minimum re-applying after every
   `git pull` there.
3. **`config/web.conf` for verifier-api2 now defaults to port 7004, was 7003.** Docs and env-var
   guidance (`VERIFIER_NGROK_URL`, `ngrok http <port>`) updated to match; confirm this is the
   intended stable port before anyone automates around it.

### Fixed this pass (for context, not follow-up)

- `nginx/Dockerfile`, `-static`, `-nodocker` in this repo: added the same SAN fix as the upstream
  one above, so `docker-compose-walt.yml` works without the manual patch.
- `VerifierConformanceTests.kt` used `kotlinx.coroutines.test.runTest` instead of `runBlocking` +
  `withTimeout` (every sibling test class — issuer, VCI wallet, VP wallet — already used the
  latter). `runTest`'s virtual-time dispatcher does not reliably wait for real async I/O completions
  arriving off its dispatcher: every second real HTTP call in a run hung for the full 60s timeout
  even though the server had already answered. Now matches the sibling pattern.
- `ConformanceInterface.kt` pins the CIO engine explicitly instead of relying on Ktor's classpath-order
  auto-selection between `ktor-client-cio` and `ktor-client-java` (both are on the classpath). This
  turned out not to be the cause of the hang above, but is a reasonable pin to keep regardless.

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
