# Wallet visual evidence

Start with the [demo UI conventions](../design-system.md) and [production coverage inventory](coverage.md) when changing or adding a screen.

The catalogue connects the existing Compose Android, Compose iOS and native SwiftUI test lanes. Consent fixtures cover a selected ID, an alternative that shares additional clear-text information, two selected credentials, inline disclosure values and compact nearby field/retention review. Fixtures render production content with synthetic inputs from `resources/files/wallet-visual-data.json`; they never create wallet keys, fetch issuer metadata or start real proximity sessions. Existing semantic and lifecycle tests remain separate.

`catalogue.json` lists each captured state, its test, renderer and readable visual requirements. It is the current deterministic catalogue, not a claim that every wallet screen, system prompt or physical flow is covered. Payment fixtures include ordinary and payment credentials, authoritative consent and pinned actions; the native consent component also has a focused capture. Compose provider fixtures capture the actual modal window, while native provider fixtures render its shared content at a bounded device size. These are wallet-owned surfaces, not system picker evidence. Home content uses the real normalized card input. Opening/loading, failure with Retry and unavailable PIN storage have production-content captures; asynchronous startup, recovery and navigation retain their separate behavior/UI suites. Native media rows wait for decoded thumbnail layout and compare actual pixels. Compose media waits for successful image-loader completion. Readiness gates use actual content/image state; fixture-clock advancement settles animations without a wall-clock capture sleep. PIN captures show separate choose/confirm screens, mismatch retry, pending biometric authentication, compact dark/large-text confirmation and four-digit unlock. These are synthetic wallet-owned states; separate UI tests drive creation, Back, authentication failure, keyboard dismissal and completion. No capture imitates an OS biometric dialog.

Provider states include preparing, browser authorization, failure and a partial issuance receipt with
saved, pending and unattempted targets. Compose iOS captures that reusable content without claiming
an Apple issuance-provider integration. Request-model and UI tests separately prove selection and
transaction-code retention, stale-consent rejection, correlated callbacks and no implicit remote retry.

## Compare

Run from the identity repository. Android uses Robolectric API 35 at mdpi; Compose iOS uses a 393×852 headless Skia surface. SwiftUI uses a hosted iPhone 13 layout at scale 3 on an iPhone 17 simulator running iOS 27.0 with Xcode 27.0, matching CI. The snapshot viewport does not replace the host hardware's safe areas, so the runner rejects other simulator models for SwiftUI. Choose a test-owned simulator. Do not reuse baselines on another runtime or native simulator model without an explicit environment review.

The existing build workflow runs reporting contracts on Linux and selects complete renderer comparisons through its platform eligibility rules. Android comparisons run on Linux. Compose iOS comparisons share the Kotlin simulator job's compilation cache; SwiftUI comparisons reuse the consumer job's verified release WalletCore artifact and native build directory. Native comparisons run serially on one test-owned simulator. Functional suites keep their own selection. Drafts can use `ci:visual` for comparisons alone; `ci:mobile` and `ci:macos` retain their wider gates. Forks require maintainer dispatch. Path selection chooses renderers, never a subset of their catalogue cells.

Those paths describe the default environment. Variant IDs and catalogue notes override its viewport/theme/text scale: `batch.offer.compact_dark_large_text` and `sharing.provider.compact_dark_large_text` use 320×568, dark mode, Compose font scale 1.5 and SwiftUI accessibilityMedium. The Compose final-target variant scrolls to the selection summary and asserts that copy controls and confirmation remain visible. It replaces redundant second-target captures from the previous full-width-card layout. The native sharing final-row variant applies its scroll request while preparing the bounded viewport, then verifies overflow before capture. Readiness describes the rendered scrolled frame; the complete credential row stays above the pinned actions.

Install the small Python reporting dependency in a virtual environment with `python3 -m pip install -r waltid-applications/waltid-wallet-demo-test-fixtures/visual/requirements.txt`. Then run the existing renderer tasks through the comparison entry point:

```sh
python3 waltid-applications/waltid-wallet-demo-test-fixtures/visual/verify.py --simulator "$WALLET_VISUAL_SIMULATOR_ID"
```

Use `--renderers android`, `--renderers compose-ios`, or `--renderers swiftui` for a scoped comparison. `--gradle-root` may point to a coordinated unified build only when its Identity checkout resolves to this repository. Reports default to `build/reports/wallet-visual`; open `index.html` locally after downloading a CI artifact. Its scenario, renderer, status and evidence filters help inspect the full catalogue. Artifacts are downloadable bundles, not hosted websites.

`timings.json` records each build, comparison and report phase in seconds, including failures. For an existing release build, use `--reuse-framework path/to/artifact`; the artifact helper verifies its Identity commit, Xcode version and complete device/simulator binary inventory before comparing. An invalid artifact fails without rebuilding a replacement. Compare cold build cost and warm comparison cost separately; CI queue time is not renderer time.

The entry point builds the native framework before freezing the source fingerprint, forces only selected test tasks to execute, and forwards a unique run ID, exact tested commit and fingerprint to XCTest. Native tests retain a receipt and the actual compared PNG for every comparison. The report reads the complete per-test status from the Xcode result bundle and requires matching test, receipt, baseline digest, capture digest and configuration before accepting a pass. A passing XCTest or an existing baseline alone is insufficient. Missing, stale, skipped, recorded and failed results remain distinct. Pre-comparison readiness failures retain their diagnostic image.

For custom orchestration, call `report.py begin` first and pass the generated `native-runner-env.json` to XcodeBuildMCP's `--json` argument. Call `report.py finish` with the native JSON result afterward. The fingerprint conservatively includes tracked files and non-ignored additions across Identity source, SDKs and build logic, plus coordinated build-root inputs when selected. Ignored build outputs and local settings are excluded. Reusing an older receipt or changing these inputs during a run invalidates the report.

`source-inputs.json` retains the complete path, kind and SHA-256 inventory used by that fingerprint, without file contents. It is bound to the run marker and retained with the comparison artifact, so intentional CI configuration edits and non-ignored additions can be audited against the checked-out commits. Missing or changed inventories invalidate the report; a dirty checkout is still labelled as such.

For coordinated Enterprise/License source validation, pass `--gradle-root` with the matching
unified checkout. Its `waltid-identity` directory must resolve to this exact Identity checkout;
the runner rejects another source tree. Record the companion revisions with the local evidence.

The existing platform jobs retain the gallery, JUnit results, timings and differences as job
artifacts. Apple comparisons require **Xcode 27.0 (27A266a) and iOS 27.0**; SwiftUI also uses
**XcodeBuildMCP 2.7.0**. Android and browser comparisons run on Linux;
all fixture clocks use UTC, and the native nearby receipt pins its locale's 24-hour cycle independently
of the simulator's user preference. Its [official `xcode-27` runner](https://github.com/actions/runner-images/issues/14404)
is currently a public preview. The [image inventory](https://github.com/actions/runner-images/blob/main/images/macos/xcode-27-arm64-Readme.md)
provides the pinned Xcode and iOS 27 runtime. Each comparison creates its own simulator.
An unavailable runtime or changed Xcode fails explicitly; the workflow never
falls back to another OS or records replacement references. This pipeline requires hosted
verification before it can establish CI portability; local success alone is insufficient.

Each native capture has its own test method, so its result and failure attachments identify one catalogue state. Native failed comparisons export their actual/difference images from the test xcresult bundle; they are included beside expected images in the report. The native renderer permits a maximum difference of 11/255 in every sRGB channel, measured from identical iOS 27 SF Symbol edge rendering, including the sheet's Close icon in hosted CI. Consecutive frames must still settle within the stricter 6/255 bound. Image dimensions must match and no percentage of pixels is ignored. Boundary tests reject larger channel changes, a missing detail and different dimensions. PNG normalization keeps the encoded reference and actual in the same color representation; the pinned Core Image perceptual comparator produced inconsistent color results.

Settled-state captures advance the Compose virtual clock before capture. Android's native RenderThread ripple is excluded in the snapshot adapter using test-only `LocalRippleConfiguration`; it is not governed by that clock. Production interaction feedback and behavior tests remain unchanged. This follows the [Material ripple subtree override](https://developer.android.com/develop/ui/compose/touch-input/user-interactions/migrate-indication-ripple). The native snapshot host also freezes UIKit activity-indicator layer clocks at one frame. Asynchronous native captures temporarily disable UIKit animations while applying the snapshot viewport, then restore the prior setting; this avoids capturing intermediate toolbar positions. These images do not assert animation timing.

The wallet module defaults ordinary Gradle tests to verification from both Identity and coordinated build roots, with mode and baselines tracked as test inputs. The `roborazzi.test.verify` project property remains an explicit override; CI rejects disabled verification and recording or compare-only modes. SwiftUI defaults to `.never` recording. Missing baselines fail. Do not enable verify-and-record in CI.

## Deliberate baseline updates

Use the same destinations and fixture configuration. For Compose, invoke `sharedUI:testAndroidHostTest --tests '*WalletVisualAndroidTest'` or `sharedUI:iosSimulatorArm64Test --device "$WALLET_VISUAL_SIMULATOR_ID" --tests '*WalletVisualIosTest'` with `-Proborazzi.test.record=true -Proborazzi.test.verify=false`. The task paths use the full `:waltid-applications:waltid-wallet-demo-compose:` prefix. The outer `recordRoborazziIosSimulatorArm64` task does not accept the native test's `--device` argument. The comparison runner intentionally has no recording option. For native tests, add `"WALLET_VISUAL_RECORD":"1"` to `testRunnerEnv`. Point-Free deliberately reports record-mode assertions as failures; this is not a successful verification run.

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

The gallery also consumes the existing browser catalogue; it does not merge its synthetic HTTP evidence into the mobile comparison results.

## Adding a state

Add shared data only for a distinct risk. Render the real component through `WalletVisualScenarios` and/or `WalletVisualTests`, assert its content/actions, and wait for asynchronous readiness. Add the stable state ID, test method, concrete visible requirements and exact renderer applicability to the catalogue. Record on the pinned environment, inspect and verify. Include compact, large-text, dark, RTL or other variants where they expose a different layout risk instead of multiplying every state by every configuration.

Simulator pixels do not prove external activity/scene lifecycle, OS-owned DC API sheets, signing, physical BLE/NFC or TS-12 conformance. Keep those evidence lanes explicit.

The [browser account pilot](../../waltid-wallet-demo-compose/webApp/visual/README.md) separately
compares six web-specific account states in the production Wasm host, including compact/wide
viewports and actual form requests against synthetic HTTP responses. Its native Playwright report
stays separate from the three-renderer mobile manifest.

Payment display responsibilities, placement, explicit unsigned confirmation and evidence boundaries are described in [the payment review contract](payment-consent.md). The localized compact variant uses 320×568 with large text; it checks wrapping, not translation of wallet-owned strings.

Ordinary deep-link presentation, dismissal and platform background limits are described in [the external review contract](external-flows.md).

Nearby review, prepared summaries and receipt evidence are described in [the nearby UI contract](nearby.md).

## Component previews

`WalletControlsPreview` in Compose and SwiftUI is an interactive, side-effect-free fixture
for actions and compact copy controls. The same content is rendered by the catalogue in
LTR and RTL. Compose also includes an expired-account preview. These use no wallet,
metadata fetch, key generation, storage or permission prompts. App-host previews that
initialized real services have been removed.

Use these IDE previews for local component iteration, then the catalogue for complete
review states and platform comparisons. Canvas/IDE availability depends on local IDE
support; compilation and screenshot tests validate the content, not the IDE preview engine.
Do not build another navigation-driven gallery or duplicate the protocol model for previews.
