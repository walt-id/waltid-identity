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

## Common checks

Android:

```bash
./gradlew :waltid-applications:waltid-wallet-demo-compose:androidApp:assembleDebug
./gradlew :waltid-libraries:protocols:waltid-openid4vc-wallet-mobile:connectedAndroidDeviceTest
```

iOS (Compose):
```bash
cd waltid-applications/waltid-wallet-demo-compose/iosApp
open iosApp.xcodeproj
```

iOS (Native)
```bash
cd waltid-applications/waltid-wallet-demo-ios/iosApp
open iosApp.xcworkspace
```

Public EUDI and local Enterprise E2E scripts live under:

```text
waltid-applications/waltid-wallet-demo-compose/androidApp/scripts/
waltid-applications/waltid-wallet-demo-compose/iosApp/scripts/
waltid-applications/waltid-wallet-demo-ios/scripts/
```

Local Enterprise E2E is local-only for now and is not self-contained. It requires a provisioned `waltid-enterprise-quickstart` stack. From a clean quickstart checkout, configure `config/enterprise.conf` for public mobile redirects before starting the stack:

```hocon
baseDomain = "enterprise.localhost"
baseSsl = true
# basePort = 7500
```

Start Docker Desktop or another Docker daemon, run `docker compose up`, start `ngrok http 7500`, then provision the baseline resources without running the quickstart's built-in primary use case:

```bash
cd cli
npm install
HOST_ALIAS_DOMAIN=<your-ngrok-domain> npx tsx walt.ts --init-system
HOST_ALIAS_DOMAIN=<your-ngrok-domain> npx tsx walt.ts --setup-all
```

Set the same `HOST_ALIAS_DOMAIN` in each platform's `scripts/e2e.env`. The mobile scripts validate that generated credential-offer and verifier URLs use the public ngrok HTTPS origin before launching the app tests. They fail fast if the quickstart baseline resources or the mobile-only helper resources are missing.

From either platform scripts directory, create the mobile-only helper resources once:

```bash
./e2e-local-enterprise.sh --prepare-only
```

This explicit preparation creates `issuer2-noattest` for non-attested issuance and `verifier2-mobile` for public verifier URLs. The normal test command validates existing resources and does not create them. The baseline organization, tenant, KMS, certificates, VICAL, trust registry, issuer2, verifier2, client attester, and mDL profile still come from quickstart.

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

## Troubleshooting

- **Android modules missing:** set `enableAndroidBuild=true` in `local.properties`, or pass `-PenableAndroidBuild=true`, then reload Gradle.
- **iOS modules missing:** set `enableIosBuild=true` in `local.properties`, or pass `-PenableIosBuild=true`, then reload Gradle.
- **Web/Wasm preview module missing:** set `enableWalletDemoComposeWeb=true` in `local.properties`, or pass `-PenableWalletDemoComposeWeb=true`, then reload Gradle.
- **Android SDK not found:** check `sdk.dir` in `local.properties`.
- **IntelliJ Android import fails:** use Android Studio for Android modules, or keep `enableAndroidBuild=false` for shared Kotlin/JVM work.
- **Local Enterprise E2E cannot reach services:** check `HOST_ALIAS_DOMAIN`, the running Enterprise stack, `baseSsl=true`, and omitted `basePort`.
