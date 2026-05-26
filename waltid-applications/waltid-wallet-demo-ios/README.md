# walt.id Wallet Demo — iOS

Native iOS (SwiftUI) demo app demonstrating OpenID4VCI credential issuance and OpenID4VP credential presentation using the walt.id Wallet SDK via Kotlin Multiplatform.

## Prerequisites

- Xcode 15+
- iOS 15.4+ simulator or device
- CocoaPods (`gem install cocoapods`)
- Gradle (for building the Kotlin shared framework)

## Build

### 1. Build the shared Kotlin framework

From the `waltid-identity` directory:

```bash
./gradlew :waltid-applications:waltid-wallet-demo-ios:shared:podspec \
  :waltid-applications:waltid-wallet-demo-ios:shared:generateDummyFramework \
  -PenableIosBuild=true
```

### 2. Install CocoaPods

```bash
cd waltid-applications/waltid-wallet-demo-ios/iosApp
pod install
```

### 3. Open and run in Xcode

```bash
open iosApp.xcworkspace
```

Select an iOS simulator and press Cmd+R to build and run.

## Demo Runbook

### 1. Initialize the Wallet

Tap **Initialize Wallet** on the home screen. This generates a `secp256r1` key and a `did:key` DID using the Kotlin/Native wallet SDK.

### 2. Receive a Credential

**Option A — Native SDK (in-memory wallet):**
1. Tap **Receive** from the home screen
2. Paste a credential offer URL
3. Tap **Receive (Native)**

**Option B — Enterprise wallet-service:**
1. Configure the enterprise environment in **Settings** (gear icon)
2. Tap **Receive** and enter the offer URL
3. Tap **Enterprise**

### 3. Present a Credential

1. Tap **Present** from the home screen
2. Paste a presentation request URL
3. Tap **Present (Native)** or **Enterprise**

### Deep Links

The app registers for `openid-credential-offer://` and `openid4vp://` URL schemes via `Info.plist`. On a simulator you can test with:

```bash
xcrun simctl openurl booted "openid-credential-offer://credential_offer=..."
```

### Enterprise Quickstart

In **Settings**, tap **Quickstart** to pre-fill the local enterprise environment. The iOS app connects directly to localhost (no emulator rewriting needed when running on a simulator with the stack on the host machine).

## Architecture

```
iosApp/
├── iosAppApp.swift           — App entry point + deep link handling
├── ViewModels/
│   └── WalletViewModel.swift — ObservableObject wrapping shared bridge
├── Views/
│   ├── HomeView.swift        — Credential list + navigation
│   ├── ReceiveView.swift     — Offer URL input + progress
│   ├── PresentView.swift     — VP request input + progress
│   └── SettingsView.swift    — Environment + attestation config
├── Components/
│   ├── CredentialCardView.swift
│   └── StatusBannerView.swift
└── Theme/
    └── WalletColors.swift    — walt.id brand colors

shared/src/commonMain/
├── WalletDemoBridge.kt       — Controller facade for Swift
└── IosMobileWalletAdapter.kt — WalletClientAdapter using native Wallet2 handlers
```

The shared Kotlin module implements `WalletClientAdapter` using the same `WalletIssuanceHandler` and `WalletPresentationHandler` used by the Android demo — identical wallet logic on both platforms.

## Verify Kotlin Compilation

```bash
./gradlew :waltid-applications:waltid-wallet-demo-ios:shared:compileKotlinIosSimulatorArm64 \
  -PenableIosBuild=true
```
