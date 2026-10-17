# Browser account visual pilot

This small [Playwright comparison lane](https://playwright.dev/docs/test-snapshots) runs the
production Wasm host. It covers the browser-specific account form at compact/wide widths,
busy submission, invalid credentials, registration conflict and expired sessions. Shared wallet
content stays in the [mobile catalogue](../../../waltid-wallet-demo-test-fixtures/visual/README.md).
The [browser catalogue](catalogue.json) records exact states, requirements and environment.

The tests intercept only synthetic local account/wallet HTTP requests. They exercise the actual
Compose form, validate submitted credentials and recovery messages, and check that empty/busy
actions cannot send an extra request. Submitted responses are held until the working state
has rendered; recovery cases release that gate after its image settles, so fast synthetic
responses cannot skip the intermediate state. No application test route, separate gallery app or live
account is needed. Real API2 authentication and protocol tests remain separate evidence.
A separate immediate-response interaction case uses actual canvas pointer/backing-editor input to
prove that registration failure restores editable fields and permits a retry with new credentials.

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
This pilot does not prove screen-reader acceptance, every browser/viewport, browser authorization
redirects or mobile system hosts.
