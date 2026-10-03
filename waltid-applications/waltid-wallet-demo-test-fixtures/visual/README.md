# Wallet visual evidence

The catalogue connects the existing Compose Android, Compose iOS and native SwiftUI test lanes. Fixtures render production content with synthetic inputs from `resources/files/wallet-visual-data.json`; they never create wallet keys, fetch issuer metadata or start real proximity sessions. Existing semantic and lifecycle tests remain separate.

`catalogue.json` lists each captured state, its test, renderer and requirements. It is the current deterministic catalogue, not a claim that every wallet screen, system prompt or physical flow is covered. Native payment consent is currently a component capture; Compose payment fixtures include the ordinary and payment credential groups. Home content uses the real normalized card input; asynchronous app loading and navigation remain in the behavior/UI suites. Native media rows wait for decoded thumbnail layout and compare actual pixels. Compose media waits for successful image-loader completion. Nothing uses a fixed capture delay. PIN setup captures show the single form with empty, mismatched and biometric-enabled synthetic in-memory state; separate UI tests drive creation, authentication failure, keyboard dismissal and completion.

## Compare

Run from the identity repository. Android uses Robolectric API 35 at mdpi; Compose iOS uses a 393×852 headless Skia surface. SwiftUI uses a hosted iPhone 13 layout at scale 3 on iOS 26.5. Choose a test-owned iOS 26.5 simulator; the native baselines were generated with Xcode 27. Do not reuse baselines on another runtime without an explicit environment review.

Those paths describe the default environment. Variant IDs and catalogue notes override its viewport/theme/text scale: `batch.offer.compact_dark_large_text` uses 320×568, dark mode, Compose font scale 1.5 and SwiftUI accessibilityMedium. The Compose final-target variant scrolls to the selection summary and asserts that copy controls and confirmation remain visible. It replaces redundant second-target captures from the previous full-width-card layout.

```sh
python3 waltid-applications/waltid-wallet-demo-test-fixtures/visual/report.py begin --output build/reports/wallet-visual

./gradlew :waltid-applications:waltid-wallet-demo-compose:sharedUI:verifyRoborazziAndroidHostTest --tests '*WalletVisualAndroidTest' :waltid-applications:waltid-wallet-demo-compose:sharedUI:verifyRoborazziIosSimulatorArm64 :waltid-applications:waltid-wallet-demo-compose:sharedUI:iosSimulatorArm64Test --device "$WALLET_VISUAL_SIMULATOR_ID" --tests '*WalletVisualIosTest' -PenableAndroidBuild=true -PenableIosBuild=true --max-workers=2

xcodebuildmcp simulator test --project-path "$PWD/waltid-applications/waltid-wallet-demo-ios/iosApp/iosApp.xcodeproj" --scheme iosApp --configuration Debug --simulator-id "$WALLET_VISUAL_SIMULATOR_ID" --derived-data-path /tmp/wallet-visual-derived --extra-args '-only-testing:iosAppTests/WalletVisualTests' 'CODE_SIGNING_ALLOWED=YES' 'CODE_SIGN_IDENTITY=-' --json '{"testRunnerEnv":{"E2E_USE_MOCK_WALLET":"1"}}' --output json > build/reports/wallet-visual/native-results.json

python3 waltid-applications/waltid-wallet-demo-test-fixtures/visual/report.py finish --output build/reports/wallet-visual --native-results build/reports/wallet-visual/native-results.json
```

The native app requires the existing release WalletCore XCFramework build first; follow its README. Use `--renderers android`, `--renderers compose-ios`, or `--renderers swiftui` on `begin` for a scoped report. Only selected renderers are claimed. The report refuses stale test results, missing tests, unlisted baselines, record-only results, and source/baseline changes after `begin`. Force the selected test task to execute when Gradle would otherwise reuse old results; do not rerun all dependency compilation just to refresh a report. `index.html` contains the contact sheet and `manifest.json` its machine-readable evidence.

Native failed comparisons export their actual/difference images from the test xcresult bundle; they are included beside expected images in the report. The native renderer permits a maximum difference of 5/255 in every sRGB channel, measured from identical SF Symbol edge rendering. Image dimensions must match and no percentage of pixels is ignored. PNG normalization keeps the encoded reference and actual in the same color representation; the pinned Core Image perceptual comparator produced inconsistent color results.

Settled-state captures advance the Compose virtual clock before capture. Android's native RenderThread ripple is excluded in the snapshot adapter using test-only `LocalRippleConfiguration`; it is not governed by that clock. Production interaction feedback and behavior tests remain unchanged. This follows the [Material ripple subtree override](https://developer.android.com/develop/ui/compose/touch-input/user-interactions/migrate-indication-ripple). These images do not assert animation timing.

Ordinary Gradle runs verify through the `roborazzi.test.verify` project property; the plugin tracks its mode as a test input. SwiftUI defaults to `.never` recording. Missing baselines fail. Do not enable verify-and-record in CI.

## Deliberate baseline updates

Use the same destinations and fixture configuration. Replace `verifyRoborazzi…` with `recordRoborazzi…` for Compose. For native tests, add `"WALLET_VISUAL_RECORD":"1"` to `testRunnerEnv`. Point-Free deliberately reports record-mode assertions as failures; this is not a successful verification run.

Inspect each expected/actual/diff image and its semantic assertions before committing an intentional change. Then compare again with recording disabled. Both integrations reject recording when a CI environment is detected. Do not relax pixel thresholds to conceal missing text, images or action clipping. Keep toolchain-only refreshes separate from design changes.

Committed PNGs are synthetic test assets. Generated reports and diffs stay under ignored build directories and may be retained as CI artifacts. Inline PR screenshots still require GitHub `user-attachments`; repository baselines are not a substitute screenshot host.

## Adding a state

Add shared data only for a distinct risk. Render the real component through `WalletVisualScenarios` and/or `WalletVisualTests`, assert its content/actions, and wait for asynchronous readiness. Add the stable state ID, test method, requirement IDs and exact renderer applicability to the catalogue. Record on the pinned environment, inspect and verify. Include compact, large-text, dark, RTL or other variants where they expose a different layout risk instead of multiplying every state by every configuration.

Simulator pixels do not prove external activity/scene lifecycle, OS-owned DC API sheets, signing, physical BLE/NFC or TS-12 conformance. Keep those evidence lanes explicit.
