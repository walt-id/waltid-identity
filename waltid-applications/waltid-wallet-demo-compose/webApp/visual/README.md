# Browser account visual pilot

This small [Playwright comparison lane](https://playwright.dev/docs/test-snapshots) runs the
production Wasm host. It covers the browser-specific account form at compact/wide widths,
busy submission, invalid credentials, registration conflict and expired sessions. Shared wallet
content stays in the [mobile catalogue](../../../waltid-wallet-demo-test-fixtures/visual/README.md).
The [browser catalogue](catalogue.json) records exact states, requirements and environment.

The tests intercept only synthetic local account/wallet HTTP requests. They exercise the actual
Compose form, validate submitted credentials and recovery messages, and check that empty/busy
actions cannot send an extra request. No application test route, separate gallery app or live
account is needed. Real API2 authentication and protocol tests remain separate evidence.

From the Identity repository:

```sh
./gradlew :waltid-applications:waltid-wallet-demo-compose:webApp:wasmJsBrowserDistribution \
  -PenableAndroidBuild=false -PenableIosBuild=false -PenableWalletDemoComposeWeb=true
cd waltid-applications/waltid-wallet-demo-compose/webApp/visual
npm ci
npx playwright install chromium --only-shell
npm test
```

Use macOS 27 arm64, Playwright 1.62.0 and its bundled Chromium 151.0.7922.34. Other OS baselines
require a separate environment review; the adapter fails explicitly instead of silently choosing
another browser. The test-owned server binds localhost:8073 and is removed by Playwright.

`npm run record` deliberately replaces references outside CI. Inspect those images, then run
`npm test` twice to check determinism. Record output is not comparison evidence. CI rejects
recording flags; ordinary comparisons reject missing/orphaned references and tolerate zero
different pixels. Assertions establish the intended state before Playwright waits for stable
screenshots. The native HTML/JSON reports include source SHA, dirty status, renderer, requirements,
duration, rendered images and failure differences under `webApp/build/reports/browser-visual`.
Hosted execution uploads those files even on failure. Synthetic baselines are test fixtures,
not PR screenshot hosting.

Compose's ARIA nodes mirror the canvas geometry. Pointer actions target their measured bounds;
DOM focus and disabled attributes are not reliable proxies for Compose state in the pinned
version. The tests assert actual requests instead. This pilot does not prove screen-reader
acceptance, every browser/viewport, browser authorization redirects or mobile system hosts.
