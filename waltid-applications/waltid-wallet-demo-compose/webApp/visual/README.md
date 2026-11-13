# Browser wallet visual checks

This [Playwright comparison lane](https://playwright.dev/docs/test-snapshots) runs the production
Wasm host. Account cases cover compact/wide forms, busy submission, invalid credentials,
registration conflict and expired sessions. Credential dates retain the two 02:30 instants in
Budapest's DST overlap, nanosecond precision and an unchanged calendar date.

Status cases share producer assessments and unchanged public artwork with the
[mobile catalogue](../../../waltid-wallet-demo-test-fixtures/visual/README.md). They cover mixed
chip/no-chip Home cards, the centered bounded wide column, simple clear and expanded adverse details,
inline sharing facts and manual refresh, selected-only confirmation, and signed-in settings with
expanded help. Interaction cases check review-owned refresh, deliberate guarded submission and
context loss with unchanged issuer facts, including the first deliberate consent to the recovered
review. Generic transaction metadata has its own sharing reference; this does not imply native
prepared-payment support in the browser. The older-server case retains explicit capability fallback
without an unsupported refresh request. The browser never fetches an issuer status list directly.
The [browser catalogue](catalogue.json) records exact states, requirements and environment.

The tests intercept synthetic account/wallet HTTP and serve the shared artwork bytes locally. Forced
status overlays on public artwork are component fixtures, not issuer results for PID/EMVCo/KYC.
They exercise the actual
Compose form, validate submitted credentials and recovery messages, and check that empty/busy
actions cannot send an extra request. Submitted responses are held until the working state
has rendered; recovery cases release that gate after its image settles, so fast synthetic
responses cannot skip the intermediate state. No application test route, separate gallery app or live
account is needed. Real API2 authentication, issuance and verifier journeys remain separate evidence.
A separate immediate-response interaction case uses actual canvas pointer/backing-editor input to
prove that registration failure restores editable fields and permits a retry with new credentials.
Credential cases open the real stored card through the API2 mapper, shared normalizer and browser
Intl adapter. Their catalogue entries explicitly override the default locale/timezone with en-GB
and Europe/Budapest. No date formatter is copied into the test harness. The browser CI lane also
runs the complete shared Wasm logic suite. Its date regressions cover DST gaps, non-hour offsets, local date
rollover, BCE, fixed zones and locale-specific fractions that do not each need a screenshot.

From the Identity repository:

```sh
./gradlew :waltid-applications:waltid-wallet-demo-compose:webApp:wasmJsBrowserDistribution \
  -PenableAndroidBuild=false -PenableIosBuild=false -PenableWalletDemoComposeWeb=true
cd waltid-applications/waltid-wallet-demo-compose/webApp/visual
npm ci
npx playwright install chromium --only-shell
npm test
```

Use Ubuntu 24.04 x86_64, Playwright 1.62.0 and its bundled Chromium 151.0.7922.34. CI uses the
version-pinned `mcr.microsoft.com/playwright:v1.62.0-noble` image. On another OS, run the built
host and this test directory in that Linux container. Other environments require a separate
baseline review; the adapter fails instead of silently choosing another renderer. The
test-owned server binds localhost:8073 and is removed by Playwright.

After building the host, compare from macOS with the same container as CI. Mount Git's common
directory as well so worktree provenance remains available:

```sh
WALLET_VISUAL_ROOT=$(git rev-parse --show-toplevel)
WALLET_VISUAL_GIT=$(git rev-parse --path-format=absolute --git-common-dir)
docker run --rm --init --ipc=host --platform linux/amd64 \
  -v "${WALLET_VISUAL_ROOT}:${WALLET_VISUAL_ROOT}" -v "${WALLET_VISUAL_GIT}:${WALLET_VISUAL_GIT}:ro" \
  -w "$WALLET_VISUAL_ROOT/waltid-applications/waltid-wallet-demo-compose/webApp/visual" \
  -e CI -e GIT_CONFIG_COUNT=1 -e GIT_CONFIG_KEY_0=safe.directory -e GIT_CONFIG_VALUE_0="$WALLET_VISUAL_ROOT" \
  mcr.microsoft.com/playwright:v1.62.0-noble@sha256:baed2032d533817f3dbe6425de795788430ba345e819a1201337009ba17c9d07 \
  bash -lc 'npm ci && npm test'
```

`npm run record` deliberately replaces references outside CI. Inspect those images, then run
`npm test` twice to check determinism. Record output is not comparison evidence. CI rejects
recording flags; ordinary comparisons reject missing/orphaned references and tolerate zero
different pixels. Assertions establish the intended state, then capture requires unchanged
pixels for at least 0.5 seconds within a bounded ten-second wait. Compose canvas transitions
are not stopped by Playwright's CSS-animation controls; two equal frames alone can precede
their completion. The native HTML/JSON reports include source SHA, dirty status, renderer, requirements,
duration, rendered images and failure differences under `webApp/build/reports/browser-visual`.
Hosted execution uploads those files even on failure. Reuse committed browser baselines in PR galleries with exact-SHA repository image URLs.
They show expected fixture appearance; fresh comparison and real-backend app evidence remain separately labelled.

Compose's ARIA nodes mirror the canvas geometry. Pointer actions target their measured bounds.
Text entry waits for the actual backing editor's input mode and focus, fills that input,
then verifies its value. Keyboard navigation also targets that editor. Filling the contenteditable
ARIA mirror can race canvas focus without entering text into Compose. ARIA focus and disabled attributes are not reliable
proxies for Compose state in the pinned version; submitted request assertions remain required.
The sharing driver enters its URL through the scanner's actual keyboard path and asserts the exact
preview request. It scrolls within the real sheet and checks that controls fit above the pinned
actions; ARIA visibility alone does not prove canvas hit testing or reachability. Help exercises
Space activation without changing preference or batch results. The pinned
renderer can leave its ARIA mirror stale after closing a popup, so the Escape assertion compares
the actual canvas backdrop. This does not establish screen-reader acceptance after popup dismissal.
These fixtures do not prove screen-reader acceptance, every browser/viewport, browser authorization
redirects or mobile system hosts.

## Real server journey

`live-status.cjs` is an explicit, separate journey through the production Wasm app and a real
loopback Wallet API 2 service with auth disabled. It creates a new wallet, issues public-demo PID
and controlled conference/workplace credentials, checks signed suspension/recovery/revocation,
refreshes expanded sharing details and completes a real public-verifier presentation. It does not
intercept wallet, issuer or verifier responses. This qualifies the supported browser host, not
account authentication or native prepared payments.

Start the built app and an immutable copy of the current wallet-service JAR on loopback, and run
the [controlled issuer](../../../mobile-e2e-fixtures/status/README.md). Keep its control port private.
Run serially with mobile controlled-status journeys: their outage control is shared. From this
directory, on a clean committed checkout:

```sh
WALLET_API2_E2E_BASE_URL=http://127.0.0.1:7018 \
WALLET_STATUS_CONTROL_URL=http://127.0.0.1:8090 \
WALLET_BROWSER_APP_URL=http://127.0.0.1:8074 \
WALLET_API2_E2E_PRODUCER=/private/evidence/backend-producer.json \
WALLET_BROWSER_EVIDENCE_DIR=/private/evidence/unused-browser-run \
node live-status.cjs
```

The producer receipt identifies the service source revision and immutable JAR digest. The runner
retains its own source revision, browser/platform, twelve full-frame captures, producer projections,
hash-only request bodies and the verifier's terminal verdict in `evidence.json`. The output must not
already exist; diagnostics and request-derived data remain private. A page reload verifies server
retention after issuance; the browser's runtime-owned manual preference is explicitly restored.
Refreshes respect the API's existing 30-second admission cooldown without automatic retries.

Set `WALLET_BROWSER_EXECUTABLE` explicitly when using an installed Chromium instead of Playwright's
bundled browser, and label that actual environment separately from Linux reference comparisons.
For a free ngrok test tunnel, optional `WALLET_BROWSER_USER_AGENT=waltid-evidence/1` uses its
[documented programmatic-access option](https://ngrok.com/docs/pricing-limits/free-plan-limits#using-a-custom-user-agent)
to avoid an HTML interstitial replacing public artwork. Record that transport accommodation;
it adds no application networking exception or simulated result.
