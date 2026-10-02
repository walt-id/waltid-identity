# Wallet visual evidence

The catalogue connects the existing Compose Android, Compose iOS and native SwiftUI test lanes. Fixtures render production content with synthetic inputs from `resources/files/wallet-visual-data.json`; they never create wallet keys, fetch issuer metadata or start real proximity sessions. Existing semantic and lifecycle tests remain separate.

`catalogue.json` lists each captured state, its test, renderer and requirements. It is the current deterministic catalogue, not a claim that every wallet screen, system prompt or physical flow is covered. Native payment consent is currently a component capture; Compose payment fixtures include the ordinary and payment credential groups. Home content uses the real normalized card input; asynchronous app loading and navigation remain in the behavior/UI suites. Native media rows wait for decoded thumbnail layout and compare actual pixels. Compose media waits for successful image-loader completion. Nothing uses a fixed capture delay. PIN setup captures show the single form with empty, mismatched and biometric-enabled synthetic in-memory state; separate UI tests drive creation, authentication failure, keyboard dismissal and completion.

## Compare

Run from the identity repository. Android uses Robolectric API 35 at mdpi; Compose iOS uses a 393×852 headless Skia surface. SwiftUI uses a hosted iPhone 13 layout at scale 3 on iOS 26.5. Choose a test-owned iOS 26.5 simulator; the native baselines were generated with Xcode 27. Do not reuse baselines on another runtime without an explicit environment review.

The wallet visual workflow compares the complete catalogue on this pinned runtime. Generic iOS CI runs the functional and readiness suites and excludes the two snapshot classes. The comparison entry point selects those classes and supplies the run metadata required by the catalogue report.

Those paths describe the default environment. Variant IDs and catalogue notes override its viewport/theme/text scale: `batch.offer.compact_dark_large_text` uses 320×568, dark mode, Compose font scale 1.5 and SwiftUI accessibilityMedium. The Compose final-target variant scrolls to the selection summary and asserts that copy controls and confirmation remain visible. It replaces redundant second-target captures from the previous full-width-card layout.

Install the small Python reporting dependency in a virtual environment with `python3 -m pip install -r waltid-applications/waltid-wallet-demo-test-fixtures/visual/requirements.txt`. Then run the existing renderer tasks through the comparison entry point:

```sh
python3 waltid-applications/waltid-wallet-demo-test-fixtures/visual/verify.py --simulator "$WALLET_VISUAL_SIMULATOR_ID"
```

Use `--renderers android`, `--renderers compose-ios`, or `--renderers swiftui` for a scoped comparison. `--gradle-root` may point to a coordinated unified build only when its Identity checkout resolves to this repository. Reports default to `build/reports/wallet-visual`; open `index.html` locally after downloading a CI artifact. Its scenario, renderer, status and evidence filters help inspect the full catalogue. Artifacts are downloadable bundles, not hosted websites.

The entry point builds the native framework before freezing the source fingerprint, forces only selected test tasks to execute, and forwards a unique run ID, exact tested commit and fingerprint to XCTest. Native tests retain a receipt and the actual compared PNG for every comparison. The report requires matching test, receipt, baseline digest, capture digest and configuration before accepting a pass. A passing XCTest or an existing baseline alone is insufficient. Missing, stale, skipped, recorded and failed results remain distinct. Pre-comparison readiness failures retain their diagnostic image.

For custom orchestration, call `report.py begin` first and pass the generated `native-runner-env.json` to XcodeBuildMCP's `--json` argument. Call `report.py finish` with the native JSON result afterward. Reusing an older receipt or changing source/baselines during a run invalidates the report.

Native failed comparisons export their actual/difference images from the test xcresult bundle; they are included beside expected images in the report. The native renderer permits a maximum difference of 5/255 in every sRGB channel, measured from identical SF Symbol edge rendering. Image dimensions must match and no percentage of pixels is ignored. PNG normalization keeps the encoded reference and actual in the same color representation; the pinned Core Image perceptual comparator produced inconsistent color results.

Settled-state captures advance the Compose virtual clock before capture. Android's native RenderThread ripple is excluded in the snapshot adapter using test-only `LocalRippleConfiguration`; it is not governed by that clock. Production interaction feedback and behavior tests remain unchanged. This follows the [Material ripple subtree override](https://developer.android.com/develop/ui/compose/touch-input/user-interactions/migrate-indication-ripple). These images do not assert animation timing.

Ordinary Gradle runs verify through the `roborazzi.test.verify` project property; the plugin tracks its mode as a test input. SwiftUI defaults to `.never` recording. Missing baselines fail. Do not enable verify-and-record in CI.

## Deliberate baseline updates

Use the same destinations and fixture configuration. Replace `verifyRoborazzi…` with `recordRoborazzi…` for Compose. For native tests, add `"WALLET_VISUAL_RECORD":"1"` to `testRunnerEnv`. Point-Free deliberately reports record-mode assertions as failures; this is not a successful verification run.

Inspect each expected/actual/diff image and its semantic assertions before committing an intentional change. Then compare again with recording disabled. Both integrations reject recording when a CI environment is detected. Do not relax pixel thresholds to conceal missing text, images or action clipping. Keep toolchain-only refreshes separate from design changes.

Committed PNGs are synthetic test assets. Reuse them in PR galleries with URLs pinned to the exact comparison commits. Upload additional unversioned E2E/device captures through GitHub `user-attachments`. Actual images, differences, receipts and diagnostics stay in ignored reports and CI artifacts; never commit transient captures solely to host a gallery. Release assets, tags, storage branches and external image hosts are not screenshot-hosting alternatives.

## PR galleries

```sh
python3 waltid-applications/waltid-wallet-demo-test-fixtures/visual/gallery.py --base "$PR_BASE" --head "$PR_HEAD" --repository walt-id/waltid-identity --output build/reports/wallet-gallery
```

Use the actual PR target, including the parent branch for a stack. The generator resolves its merge-base, handles additions/removals/renames, and compares decoded pixels so encoding-only updates do not crowd the previews. Catalogue and configuration changes remain visible. For forks, set `--head-repository` to the owning fork. The collapsed description shows up to eight primary scenarios in Compose Android / Compose iOS / Native iOS columns. Default text size comes first; accessibility/layout variants and browser states have separate labelled sections. Use `galleryPreview`, `galleryVariants` and optional state `title` values to curate product-relevant examples instead of relying on filename order. `gallery.json` retains direct full-size before/after links and content digests.

The complete changed-reference inventory stays in the description. Its before/after links open each scenario in the versioned [visual catalogue](catalogue.md), where every available platform image is rendered with its capture configuration. This avoids duplicate image bytes and comment overflow. GitHub's description budget is enforced; oversized output fails with a useful error instead of publishing comments.

Regenerate the catalogue after changing states, titles or preview choices, and commit the Markdown with the existing references:

```sh
python3 waltid-applications/waltid-wallet-demo-test-fixtures/visual/gallery.py --write-index
```

CI runs `--check-index`. The publisher validates the index's Git digest and each referenced scenario/image before updating the description. Existing PRs whose older comparison side predates the index retain direct links for that side.

`--body path/to/body.md` replaces only the marked generated portion of a local PR body and preserves authored prose. Generation has no publishing permission. Before publishing, confirm the PR's current base/head, verify image access for the intended readers, assess selected E2E evidence, and use the shared mobile-evidence/PR-description workflow. Baselines show expected appearance; fresh comparison reports and observed app journeys have separate provenance. A superseded run must not overwrite a newer gallery.

## Adding a state

Add shared data only for a distinct risk. Render the real component through `WalletVisualScenarios` and/or `WalletVisualTests`, assert its content/actions, and wait for asynchronous readiness. Add the stable state ID, test method, requirement IDs and exact renderer applicability to the catalogue. Record on the pinned environment, inspect and verify. Include compact, large-text, dark, RTL or other variants where they expose a different layout risk instead of multiplying every state by every configuration.

Simulator pixels do not prove external activity/scene lifecycle, OS-owned DC API sheets, signing, physical BLE/NFC or TS-12 conformance. Keep those evidence lanes explicit.

Native comparison normalizes both PNGs to the same representation and bounds SF Symbol edge noise to 5/255 per sRGB channel, with equal dimensions and no ignored percentage of pixels. This reuses the catalogue comparison contract; recording never implies acceptance.
