# walt.id Wallet Demo — Android

Native Android demo app demonstrating OpenID4VCI credential issuance and OpenID4VP credential presentation using the walt.id Wallet SDK.

## Prerequisites

- Android Studio or Gradle CLI
- Android SDK 35 (compile) / SDK 28 (min)
- An Android emulator or device

## Build

From the `waltid-unified-build` root:

```bash
./gradlew :waltid-applications:waltid-wallet-demo-android:assembleDebug \
  -PenableWalletDemoAndroidBuild=true
```

## Install and Run

```bash
./gradlew :waltid-applications:waltid-wallet-demo-android:installDebug \
  -PenableWalletDemoAndroidBuild=true
```

The app launches as **walt.id Wallet** on the device.

## Demo Runbook

### 1. Initialize the Wallet

Tap **Initialize Wallet** on the home screen. This generates a `secp256r1` key and a `did:key` DID.

### 2. Receive a Credential

**Option A — Native SDK (in-memory wallet):**
1. Navigate to **Receive**
2. Paste a credential offer URL (`openid-credential-offer://...`)
3. Tap **Receive (Native SDK)**

**Option B — Enterprise wallet-service:**
1. Configure the enterprise environment in **Settings** (or use the Quickstart Local preset)
2. Navigate to **Receive**
3. Paste the offer URL
4. Tap **Enterprise Receive**

### 3. Present a Credential

1. Navigate to **Present**
2. Paste a presentation request URL (`openid4vp://...` or `https://...`)
3. Tap **Present (Native SDK)** or **Enterprise Present**

### Deep Links

The app registers for `openid-credential-offer://` and `openid4vp://` URL schemes. You can trigger these from a terminal:

```bash
adb shell am start -a android.intent.action.VIEW \
  -d "openid-credential-offer://credential_offer=..."
```

### Enterprise Quickstart (Local Docker)

1. Start the enterprise quickstart stack locally
2. In the app, go to **Settings** and tap **Quickstart** (pre-fills `http://10.0.2.2` with host header rewriting)
3. Optionally obtain a client attestation via **Obtain** button
4. Use offer/request URLs from the quickstart CLI output

## Architecture

```
app/MainActivity.kt          — Entry point + deep link routing
app/navigation/              — Navigation Compose graph
viewmodel/WalletViewModel.kt — State management + operations
ui/screens/                  — Home, Receive, Present, Settings
ui/components/               — CredentialCard, StatusBanner
ui/theme/                    — walt.id brand colors + Material3
features/walletsdk/          — InMemoryWalletSdkAdapter (Wallet2 handlers)
```

The app consumes the shared `waltid-openid4vc-wallet-client` KMP module for environment configuration and enterprise API calls.

## Tests

```bash
./gradlew :waltid-applications:waltid-wallet-demo-android:testDebugUnitTest \
  -PenableWalletDemoAndroidBuild=true
```
