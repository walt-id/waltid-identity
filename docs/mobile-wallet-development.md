# Mobile Wallet Development Guide

This guide is for contributors working on the native mobile wallet SDK, demo apps, and mobile integration tests in this repository. It covers the local setup decisions that are easy to miss before opening the project in an IDE or running platform-specific builds.

## Project map

| Goal | Start here |
|------|------------|
| Shared mobile wallet API | [waltid-openid4vc-wallet-mobile](../waltid-libraries/protocols/waltid-openid4vc-wallet-mobile/README.md) |
| Mobile persistence | [waltid-openid4vc-wallet-persistence-mobile](../waltid-libraries/protocols/waltid-openid4vc-wallet-persistence-mobile/README.md) |
| Compose demo app | [waltid-wallet-demo-compose](../waltid-applications/waltid-wallet-demo-compose/README.md) |
| iOS demo app | [waltid-wallet-demo-ios](../waltid-applications/waltid-wallet-demo-ios/README.md) |
| Android key storage | [waltid-crypto-android](../waltid-libraries/crypto/waltid-crypto-android/README.md) |
| iOS key storage | [waltid-crypto-ios](../waltid-libraries/crypto/waltid-crypto-ios/README.md) |

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
kotlin.apple.cocoapods.bin=/path/to/pod

enableAndroidBuild=true
enableIosBuild=true
# Optional: enables the mock Web/Wasm preview module.
# enableWalletDemoComposeWeb=true
```

`local.properties` is ignored by Git and overrides the tracked defaults in `gradle.properties`. Command-line `-P` flags still take highest precedence for CI and one-off overrides.

Enable only the platform builds your machine can support. Android requires an Android SDK; iOS requires macOS, Xcode, and CocoaPods. The Web/Wasm flag only enables the mock Compose preview module.

## Common checks

Android:

```bash
./gradlew :waltid-applications:waltid-wallet-demo-compose:androidApp:assembleDebug
./gradlew :waltid-libraries:protocols:waltid-openid4vc-wallet-mobile:connectedAndroidDeviceTest
```

iOS:

```bash
cd waltid-applications/waltid-wallet-demo-ios/iosApp
pod install
open iosApp.xcworkspace
```

Public EUDI and local Enterprise E2E scripts live under:

```text
waltid-applications/waltid-wallet-demo-compose/androidApp/scripts/
waltid-applications/waltid-wallet-demo-compose/iosApp/scripts/
waltid-applications/waltid-wallet-demo-ios/scripts/
```

Local Enterprise E2E requires a running Enterprise stack and `HOST_ALIAS_DOMAIN` in each platform's `scripts/e2e.env`. Android emulator verifier callbacks also require:

```bash
adb reverse tcp:7500 tcp:7500
```

## Troubleshooting

- **Android modules missing:** set `enableAndroidBuild=true` in `local.properties`, or pass `-PenableAndroidBuild=true`, then reload Gradle.
- **iOS modules missing:** set `enableIosBuild=true` in `local.properties`, configure `kotlin.apple.cocoapods.bin`, or pass `-PenableIosBuild=true`, then reload Gradle.
- **Web/Wasm preview module missing:** set `enableWalletDemoComposeWeb=true` in `local.properties`, or pass `-PenableWalletDemoComposeWeb=true`, then reload Gradle.
- **Android SDK not found:** check `sdk.dir` in `local.properties`.
- **CocoaPods not found:** check `kotlin.apple.cocoapods.bin` in `local.properties`.
- **IntelliJ Android import fails:** use Android Studio for Android modules, or keep `enableAndroidBuild=false` for shared Kotlin/JVM work.
- **Local Enterprise E2E cannot reach services:** check `HOST_ALIAS_DOMAIN`, the running Enterprise stack, and `adb reverse tcp:7500 tcp:7500` for Android emulator flows.
