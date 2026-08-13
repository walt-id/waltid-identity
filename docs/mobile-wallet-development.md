# Mobile Wallet Development Guide

This guide is for contributors working on the native mobile wallet SDK, demo apps, and mobile integration tests in this repository. It covers the local setup decisions that are easy to miss before opening the project in an IDE or running platform-specific builds.

## Project map

| Goal | Start here |
|------|------------|
| Shared mobile wallet API | [waltid-openid4vc-wallet-mobile](../waltid-libraries/protocols/waltid-openid4vc-wallet-mobile/README.md) |
| Native iOS Swift API | [WalletSDK](../waltid-libraries/protocols/waltid-wallet-sdk-ios/README.md) |
| Mobile persistence | [waltid-openid4vc-wallet-persistence-mobile](../waltid-libraries/protocols/waltid-openid4vc-wallet-persistence-mobile/README.md) |
| Compose demo app | [waltid-wallet-demo-compose](../waltid-applications/waltid-wallet-demo-compose/README.md) |
| iOS demo app | [waltid-wallet-demo-ios](../waltid-applications/waltid-wallet-demo-ios/README.md) |
| Mobile key storage | [waltid-crypto](../waltid-libraries/crypto/waltid-crypto/README.md) |

## IDE guidance

Use Android Studio for Android application and Android Gradle Plugin work. IntelliJ IDEA can still be useful for common Kotlin/JVM code, but should not be treated as the supported Android import/build path while Android plugin support is incomplete for the Android Gradle Plugin used by this repository.

Use Xcode for the iOS app and simulator work. Use the command line for CI-like checks.

The Compose demo app's Android and iOS targets are the supported mobile demo targets. Its Web/Wasm module is a mock UI preview only; it does not run the mobile wallet SDK, platform-backed keys, persistence, EUDI flows, or Enterprise flows.

## Local setup

Start from the repository root:

```bash
cp local.properties.example local.properties
```

Configure machine-local paths and platform flags in `local.properties`:

```properties
sdk.dir=/path/to/android-sdk

enableAndroidBuild=true
enableIosBuild=true
# Optional: enables the mock Web/Wasm preview module.
# enableWalletDemoComposeWeb=true
```

`local.properties` is ignored by Git and overrides the tracked defaults in `gradle.properties`. Command-line `-P` flags still take highest precedence for CI and one-off overrides.

Enable only the platform builds your machine can support. Android requires an Android SDK; native iOS requires macOS and Xcode. The Web/Wasm flag only enables the mock Compose preview module.

## iOS physical-device and Digital Credentials setup

Physical-device Digital Credentials development must use the walt.id Apple Developer team:

```text
Team ID: 9BB78CFW7Y
```

Do not sign the demo applications or their identity document provider extensions with a Personal Team.

The Apple Developer account is preconfigured with these application identities:

```text
Native
  App:       id.walt.wallet.native
  Provider:  id.walt.wallet.native.identitydocumentprovider
  App Group: group.id.walt.wallet.native

Compose
  App:       id.walt.wallet.compose
  Provider:  id.walt.wallet.compose.identitydocumentprovider
  App Group: group.id.walt.wallet.compose
```

Both host/provider pairs require the Digital Credentials API Mobile Document Provider capability and
share their respective App Group. The demos advertise:

```text
org.iso.18013.5.1.mDL
eu.europa.ec.eudi.pid.1
```

Keychain sharing is configured by the Xcode projects rather than as a separate Developer Portal
identifier:

```text
Native:  id.walt.wallet.native.shared
Compose: id.walt.wallet.compose.shared
```

The host and its provider must resolve the same `$(AppIdentifierPrefix)`-prefixed shared Keychain
group. Only the Info.plist processor expands `$(AppIdentifierPrefix)`; as an `INFOPLIST_KEY_` build
setting it resolves to the bare suffix and matches no entitlement.

Running on a physical device needs:

- access to the walt.id Apple Developer team;
- a valid Apple Development certificate for that team;
- a device registered with that team;
- Xcode automatic signing resolving `DEVELOPMENT_TEAM = 9BB78CFW7Y`.

If signing reports that the provisioning profile does not contain the Mobile Document Provider or App
Group entitlement, do not switch to a Personal Team and do not remove the entitlement. Verify the App
ID and capability assignment in the walt.id Apple Developer account, then refresh automatic
provisioning.

A simulator running iOS 26 or later is a usable end-to-end target: it registers the provider
extension with the system and mediates real requests from Safari. Only `org-iso-mdoc` is available
there, because `DigitalCredential.userAgentAllowsProtocol()` rejects every `openid4vp` variant, so
the OpenID4VP variants of this flow can only be exercised on Android.

What a simulator does not establish is physical-device provisioning or cross-process Keychain access:
its entitlements are the simulated ones the build applied rather than a provisioning profile, and the
shared Keychain group resolves against a simulated `AppIdentifierPrefix`.

## Common checks

Android:

```bash
./gradlew :waltid-applications:waltid-wallet-demo-compose:androidApp:assembleDebug
./gradlew :waltid-libraries:protocols:waltid-openid4vc-wallet-mobile:connectedAndroidDeviceTest -PenableAndroidBuild=true
./gradlew :waltid-applications:waltid-wallet-demo-compose:androidApp:connectedDebugAndroidTest -PenableAndroidBuild=true
```

iOS (Compose):

```bash
cd waltid-applications/waltid-wallet-demo-compose/iosApp
open iosApp.xcodeproj
xcodebuild test -project iosApp.xcodeproj -scheme iosApp -destination "platform=iOS Simulator,name=iPhone 17" -only-testing:iosAppUITests/PublicDemoBackendE2ETests -parallel-testing-enabled NO
```

iOS (Native)

```bash
cd waltid-applications/waltid-wallet-demo-ios/iosApp
open iosApp.xcodeproj
xcodebuild test -project iosApp.xcodeproj -scheme iosApp -destination "platform=iOS Simulator,name=iPhone 17" -only-testing:iosAppTests/MobileWalletIntegrationTests -parallel-testing-enabled NO
xcodebuild test -project iosApp.xcodeproj -scheme iosApp -destination "platform=iOS Simulator,name=iPhone 17" -only-testing:iosAppUITests/PublicDemoBackendE2ETests -parallel-testing-enabled NO
```

The public mobile integration tests run through the normal Gradle Android
instrumentation and Xcode XCTest entry points. The Android commands above run
the full connected test tasks. The tests cover the EUDI public backend and the
public OSS issuer2/verifier2 demo backend:

```text
https://issuer2.demo.walt.id/
https://verifier2.demo.walt.id/
```

Run public-backend tests serially on iOS. The tests depend on public network
services, so a transient simulator networking failure should be retried before
treating it as a product regression.

Identity document provider configuration is checked on the built products rather than on the source
`.entitlements` and `Info.plist` files, because the interesting values contain `$(AppIdentifierPrefix)`
or come from `INFOPLIST_KEY_` build settings and can silently resolve to nothing. Run the same script
CI runs, from the repository root:

```bash
.github/scripts/mobile-ci/verify-ios-identity-document-provider.sh \
  --project-dir waltid-applications/waltid-wallet-demo-ios/iosApp \
  --destination "platform=iOS Simulator,name=iPhone 17" \
  --derived-data /tmp/xcode-derived \
  --app-group group.id.walt.wallet.native \
  --keychain-group-suffix id.walt.wallet.native.shared \
  --app-bundle-id id.walt.wallet.native \
  --appex-bundle-id id.walt.wallet.native.identitydocumentprovider \
  --development-team 9BB78CFW7Y \
  --build-setting OVERRIDE_KOTLIN_BUILD_IDE_SUPPORTED=YES

.github/scripts/mobile-ci/verify-ios-identity-document-provider.sh \
  --project-dir waltid-applications/waltid-wallet-demo-compose/iosApp \
  --destination "platform=iOS Simulator,name=iPhone 17" \
  --derived-data /tmp/xcode-derived-compose \
  --app-group group.id.walt.wallet.compose \
  --keychain-group-suffix id.walt.wallet.compose.shared \
  --app-bundle-id id.walt.wallet.compose \
  --appex-bundle-id id.walt.wallet.compose.identitydocumentprovider \
  --development-team 9BB78CFW7Y
```

Each run builds the demo scheme with its provider extension and asserts that the built host and
provider agree on bundle identifier nesting, App Group, default Keychain group, advertised doctypes,
extension embedding, and the provider's extension point. It also asserts that both targets resolve the
Walt team with automatic signing, in both Debug and Release. Doctypes default to mDL and EU PID and can
be overridden with repeated `--doctype` arguments.

## Enterprise mobile platform tests

Enterprise mobile platform coverage is owned by the Enterprise integration test
module in the coordinated unified-build checkout. These tests are
self-contained: the Gradle tasks start an Enterprise mobile fixture server,
provision the required issuer2/verifier2 resources, and run the actual Android
or iOS mobile wallet calls against that fixture.

Run these commands from the unified-build root, where `waltid-identity` and
`waltid-identity-enterprise` are sibling checkouts:

```bash
cd ..
./gradlew :waltid-enterprise-integration-tests:enterpriseAndroidMobileIntegrationTest --no-configuration-cache
./gradlew :waltid-enterprise-integration-tests:enterpriseIosMobileIntegrationTest --no-configuration-cache -Penterprise.ios.destination="platform=iOS Simulator,name=iPhone 17"
```

The aggregate task runs both platforms:

```bash
cd ..
./gradlew :waltid-enterprise-integration-tests:enterpriseMobilePlatformIntegrationTest --no-configuration-cache
```

Android requires a booted emulator or device with `adb` available. The Gradle
task configures the required `adb reverse` ports while the fixture is running.
iOS requires Xcode and a matching simulator destination. If no destination is
provided, the Enterprise Gradle task defaults to `platform=iOS Simulator,name=iPhone 16`.

## API documentation checks

The SDK-facing Kotlin mobile modules use KDoc and Dokka for Kotlin API
reference docs. Dokka is configured to fail on warnings and undocumented public
API for these modules:

```bash
./gradlew :waltid-libraries:protocols:waltid-openid4vc-wallet-mobile:dokkaGeneratePublicationHtml -PenableAndroidBuild=true -PenableIosBuild=true
./gradlew :waltid-libraries:protocols:waltid-openid4vc-wallet-persistence-mobile:dokkaGeneratePublicationHtml -PenableAndroidBuild=true -PenableIosBuild=true
```

The native iOS Swift facade uses DocC. Generate and validate the Swift archive
after assembling the local `WalletCore.xcframework`:

```bash
./gradlew :waltid-libraries:protocols:waltid-openid4vc-wallet-mobile:assembleWalletCoreReleaseXCFramework -PenableIosBuild=true
waltid-libraries/protocols/waltid-wallet-sdk-ios/scripts/generate-docc.sh
```

The mobile SDK docs CI workflow runs both documentation paths.

## API contract checks

The Kotlin mobile SDK modules use explicit API mode. Public and protected
declarations must name their visibility and public return types, which keeps the
Android/KMP source API intentional before it reaches generated docs or Swift.

Kotlin ABI validation is enabled for:

- `waltid-openid4vc-wallet-mobile`
- `waltid-openid4vc-wallet-persistence-mobile`

The Kotlin Gradle plugin writes the tracked KMP/native ABI baselines under each
module's `api/` directory. Check them with:

```bash
./gradlew :waltid-libraries:protocols:waltid-openid4vc-wallet-mobile:checkKotlinAbi :waltid-libraries:protocols:waltid-openid4vc-wallet-persistence-mobile:checkKotlinAbi -PenableAndroidBuild=true -PenableIosBuild=true
```

When the public KMP surface intentionally changes, regenerate the baselines:

```bash
./gradlew :waltid-libraries:protocols:waltid-openid4vc-wallet-mobile:updateKotlinAbi :waltid-libraries:protocols:waltid-openid4vc-wallet-persistence-mobile:updateKotlinAbi -PenableAndroidBuild=true -PenableIosBuild=true
```

If those ABI baselines change, reviewers also need evidence that the Swift
facade was considered. This can be a Swift source/test/docs update, or an entry
in `waltid-libraries/protocols/waltid-wallet-sdk-ios/SwiftParityDecisions.md`
explaining why the KMP capability is intentionally not mirrored in Swift.

Run the local parity gate with:

```bash
scripts/check-mobile-swift-parity-decision.sh
```

The script resolves the PR base when GitHub metadata is available. For detached
or non-PR comparisons, pass an explicit base with `--base-ref origin/<base-branch>`.

## Troubleshooting

- **Android modules missing:** set `enableAndroidBuild=true` in `local.properties`, or pass `-PenableAndroidBuild=true`, then reload Gradle.
- **iOS modules missing:** set `enableIosBuild=true` in `local.properties`, or pass `-PenableIosBuild=true`, then reload Gradle.
- **Web/Wasm preview module missing:** set `enableWalletDemoComposeWeb=true` in `local.properties`, or pass `-PenableWalletDemoComposeWeb=true`, then reload Gradle.
- **Android SDK not found:** check `sdk.dir` in `local.properties`.
- **IntelliJ Android import fails:** use Android Studio for Android modules, or keep `enableAndroidBuild=false` for shared Kotlin/JVM work.
- **Enterprise mobile fixture cannot be reached from Android:** make sure an emulator or device is booted and `adb reverse` is available.
- **Enterprise mobile fixture cannot be reached from iOS:** pass a valid `-Penterprise.ios.destination` value for an installed simulator.
- **iOS device signs with a Personal Team:** select the Walt team `9BB78CFW7Y` and let automatic signing resolve it; do not alter the required entitlements to make signing succeed.
- **Provisioning profile is missing Mobile Document Provider or the App Group:** verify the corresponding App ID and its capabilities in the walt.id Apple Developer account, then refresh automatic signing.
- **Provider installs but cannot reach the wallet or its keys:** verify the built App Group and `keychain-access-groups` of both host and provider with `verify-ios-identity-document-provider.sh`; simulator success does not establish physical-device Keychain sharing.
