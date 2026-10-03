# Wallet visual evidence

The catalogue connects the existing Compose Android, Compose iOS and native SwiftUI test lanes. Fixtures render production content with synthetic inputs from `resources/files/wallet-visual-data.json`; they never create wallet keys, fetch issuer metadata or start real proximity sessions. Existing semantic and lifecycle tests remain separate.

`catalogue.json` lists each captured state, its test, renderer and readable visual requirements. It is the current deterministic catalogue, not a claim that every wallet screen, system prompt or physical flow is covered. Payment fixtures include ordinary and payment credentials, authoritative consent and pinned actions; the native consent component also has a focused capture. Compose provider fixtures capture the actual modal window, while native provider fixtures render its shared content at a bounded device size. These are wallet-owned surfaces, not system picker evidence. Home content uses the real normalized card input; asynchronous app loading and navigation remain in the behavior/UI suites. Native media rows wait for decoded thumbnail layout and compare actual pixels. Compose media waits for successful image-loader completion. Nothing uses a fixed capture delay. PIN setup captures show the single form with empty, mismatched and biometric-enabled synthetic in-memory state; separate UI tests drive creation, authentication failure, keyboard dismissal and completion.

Provider states include preparing, browser authorization, failure and a partial issuance receipt with
saved, pending and unattempted targets. Compose iOS captures that reusable content without claiming
an Apple issuance-provider integration. Request-model and UI tests separately prove selection and
transaction-code retention, stale-consent rejection, correlated callbacks and no implicit remote retry.

## Compare

Run from the identity repository. Android uses Robolectric API 35 at mdpi; Compose iOS uses a 393×852 headless Skia surface. SwiftUI uses a hosted iPhone 13 layout at scale 3 on iOS 27.0 with Xcode 27.0. Choose a test-owned iOS 27.0 simulator. Do not reuse baselines on another runtime without an explicit environment review.

The existing build workflow runs reporting contracts on Linux and selects complete renderer comparisons through its platform eligibility rules. Android comparisons run on Linux. Compose iOS comparisons share the Kotlin simulator job's compilation cache; SwiftUI comparisons reuse the consumer job's verified release WalletCore artifact and native build directory. Functional suites keep their own selection. Drafts can use `ci:visual` for comparisons alone; `ci:mobile` and `ci:macos` retain their wider gates. Forks require maintainer dispatch. Path selection chooses renderers, never a subset of their catalogue cells.

Those paths describe the default environment. Variant IDs and catalogue notes override its viewport/theme/text scale: `batch.offer.compact_dark_large_text` and `sharing.provider.compact_dark_large_text` use 320×568, dark mode, Compose font scale 1.5 and SwiftUI accessibilityMedium. The Compose final-target variant scrolls to the selection summary and asserts that copy controls and confirmation remain visible. It replaces redundant second-target captures from the previous full-width-card layout. The native sharing final-row variant scrolls after the snapshot viewport has been laid out and asserts actual overflow; the complete credential row stays above the pinned actions.

Install the small Python reporting dependency in a virtual environment with `python3 -m pip install -r waltid-applications/waltid-wallet-demo-test-fixtures/visual/requirements.txt`. Then run the existing renderer tasks through the comparison entry point:

```sh
python3 waltid-applications/waltid-wallet-demo-test-fixtures/visual/verify.py --simulator "$WALLET_VISUAL_SIMULATOR_ID"
```

Use `--renderers android`, `--renderers compose-ios`, or `--renderers swiftui` for a scoped comparison. `--gradle-root` may point to a coordinated unified build only when its Identity checkout resolves to this repository. Reports default to `build/reports/wallet-visual`; open `index.html` locally after downloading a CI artifact. Its scenario, renderer, status and evidence filters help inspect the full catalogue. Artifacts are downloadable bundles, not hosted websites.

`timings.json` records each build, comparison and report phase in seconds, including failures. For an existing release build, use `--reuse-framework path/to/artifact`; the artifact helper verifies its Identity commit, Xcode version and complete device/simulator binary inventory before comparing. An invalid artifact fails without rebuilding a replacement. Compare cold build cost and warm comparison cost separately; CI queue time is not renderer time.

The entry point builds the native framework before freezing the source fingerprint, forces only selected test tasks to execute, and forwards a unique run ID, exact tested commit and fingerprint to XCTest. Native tests retain a receipt and the actual compared PNG for every comparison. The report reads the complete per-test status from the Xcode result bundle and requires matching test, receipt, baseline digest, capture digest and configuration before accepting a pass. A passing XCTest or an existing baseline alone is insufficient. Missing, stale, skipped, recorded and failed results remain distinct. Pre-comparison readiness failures retain their diagnostic image.

For custom orchestration, call `report.py begin` first and pass the generated `native-runner-env.json` to XcodeBuildMCP's `--json` argument. Call `report.py finish` with the native JSON result afterward. The fingerprint conservatively includes tracked files and non-ignored additions across Identity source, SDKs and build logic, plus coordinated build-root inputs when selected. Ignored build outputs and local settings are excluded. Reusing an older receipt or changing these inputs during a run invalidates the report.

`source-inputs.json` retains the complete path, kind and SHA-256 inventory used by that fingerprint, without file contents. It is bound to the run marker and retained with the comparison artifact, so intentional CI configuration edits and non-ignored additions can be audited against the checked-out commits. Missing or changed inventories invalidate the report; a dirty checkout is still labelled as such.

Native failed comparisons export their actual/difference images from the test xcresult bundle; they are included beside expected images in the report. The native renderer permits a maximum difference of 5/255 in every sRGB channel, measured from identical SF Symbol edge rendering. Image dimensions must match and no percentage of pixels is ignored. PNG normalization keeps the encoded reference and actual in the same color representation; the pinned Core Image perceptual comparator produced inconsistent color results.

Settled-state captures advance the Compose virtual clock before capture. Android's native RenderThread ripple is excluded in the snapshot adapter using test-only `LocalRippleConfiguration`; it is not governed by that clock. Production interaction feedback and behavior tests remain unchanged. This follows the [Material ripple subtree override](https://developer.android.com/develop/ui/compose/touch-input/user-interactions/migrate-indication-ripple). The native snapshot host also freezes UIKit activity-indicator layer clocks at one frame. These images do not assert animation timing.

The wallet module defaults ordinary Gradle tests to verification from both Identity and coordinated build roots, with mode and baselines tracked as test inputs. The `roborazzi.test.verify` project property remains an explicit override; CI rejects disabled verification and recording or compare-only modes. SwiftUI defaults to `.never` recording. Missing baselines fail. Do not enable verify-and-record in CI.

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

Add shared data only for a distinct risk. Render the real component through `WalletVisualScenarios` and/or `WalletVisualTests`, assert its content/actions, and wait for asynchronous readiness. Add the stable state ID, test method, concrete visible requirements and exact renderer applicability to the catalogue. Record on the pinned environment, inspect and verify. Include compact, large-text, dark, RTL or other variants where they expose a different layout risk instead of multiplying every state by every configuration.

Simulator pixels do not prove external activity/scene lifecycle, OS-owned DC API sheets, signing, physical BLE/NFC or TS-12 conformance. Keep those evidence lanes explicit.

Native comparison normalizes both PNGs to the same representation and bounds measured iOS 27 SF Symbol edge noise to 6/255 per sRGB channel, with equal dimensions and no ignored percentage of pixels. Boundary tests reject larger channel changes, a missing detail and different dimensions. Recording never implies acceptance.

Payment display responsibilities, placement, explicit unsigned confirmation and evidence boundaries are described in [the payment review contract](payment-consent.md). The localized compact variant uses 320×568 with large text; it checks wrapping, not translation of wallet-owned strings.
