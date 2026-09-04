<div align="center">
<h1>walt.id Compose Wallet Demo</h1>
 <span>by </span><a href="https://walt.id">walt.id</a>
 <p>Compose Multiplatform demo app for wallet SDK credential issuance and presentation.</p>

<a href="https://walt.id/community">
<img src="https://img.shields.io/badge/Join-The Community-blue.svg?style=flat" alt="Join community!" />
</a>
<a href="https://www.linkedin.com/company/walt-id/">
<img src="https://img.shields.io/badge/-LinkedIn-0072b1?style=flat&logo=linkedin" alt="Follow walt_id" />
</a>

  <h2>Status</h2>
  <p align="center">
    <img src="https://img.shields.io/badge/🟢%20Actively%20Maintained-success?style=for-the-badge&logo=check-circle" alt="Status: Actively Maintained" />
    <br/>
    <em>This project is being actively maintained by the development team at walt.id.<br />Regular updates, bug fixes, and new features are being added.</em>
  </p>
</div>

## Overview

Compose Multiplatform demo app for the mobile wallet SDK. It demonstrates OpenID4VCI credential issuance, OpenID4VP credential presentation, platform-backed keys, and SQLDelight-backed wallet persistence on Android and iOS.

For setup, IDE guidance, and mobile integration test commands, see the [Mobile Wallet Development Guide](../../docs/mobile-wallet-development.md).

The Compose iOS demo uses Kotlin direct Xcode integration and a local SwiftPM linkage package for native iOS linkage.

## Local wallet data

Android and iOS demo targets use the default managed encrypted local persistence. Wallet database files are SQLCipher-encrypted, and managed database keys live in platform-protected storage. During local development, reset wallet state through `MobileWallet.deleteWallet()`, by uninstalling the app, or by deleting the app's local data.

The demo unlock PIN is stored separately as a salted PBKDF2-SHA256 verifier in app-private preferences. The PIN itself is never persisted. Clearing app data or uninstalling the app resets the PIN setup flow together with the local wallet data.

The UI stays focused on the production default. Non-default persistence options, including provided database keys and custom stores, are documented and tested at the SDK layer.

## Whitelabel branding

The in-app title and Material3 colours (`primary`, `secondary`, `primaryContainer`, and their on-colours) live on `WalletDemoBranding` in `sharedUI`. Mobile hosts can pass a custom instance into `WalletDemoApp`. The web host uses the same tokens.

Launcher names stay in platform manifests: Android `app_name` in `androidApp/src/main/res/values/strings.xml`, and iOS `CFBundleDisplayName` in the app and document-provider `Info.plist` files.

### Web (built artefact)

A production or docker image reads `branding.json` next to `index.html` at runtime, so you can rebrand without rebuilding Wasm:

```json
{
  "appTitle": "walt.id Wallet",
  "primary": "#0573F0",
  "onPrimary": "#FFFFFF",
  "secondary": "#ADC6FF",
  "onSecondary": "#002E69",
  "primaryContainer": "#D8E2FF",
  "onPrimaryContainer": "#002E69"
}
```

Replace that file in the dist folder, or set the matching `WALLET_BRAND_*` environment variables on `wallet-demo-compose-web`. For local experiments, `localStorage.setItem("waltid.wallet.branding", '{ "appTitle": "Acme Wallet", "primary": "#112233" }')` overlays the same fields.

## Public demo backend defaults

Clean demo installs use the public walt.id demo profile endpoint for OpenID4VP transaction-data support:

```text
https://wallet.demo.walt.id/wallet-api/transaction-data-profiles
```

Android builds can override it with `-PtransactionDataProfiles.url=...`. Compose iOS can override it with the `TRANSACTION_DATA_PROFILES_URL` launch environment variable or `UserDefaults` key. Wallet attestation values remain explicit overrides through `attestation.*` Gradle properties on Android and `ATTESTATION_*` environment/UserDefaults values on iOS; no bearer token is defaulted.

## Target status

- Android and iOS are the supported mobile demo targets for wallet SDK issuance, presentation, platform-backed keys, and persistence.
- Web/Wasm is a custodial demo against **wallet-api2 with the `auth` feature enabled**. It uses email/password JWT auth (`POST /auth/register`, `POST /auth/emailpass`) and isolated wallet-api2 HTTP routes for receive/present. It does not run the mobile wallet SDK, platform keys, SQLDelight, DC API, BLE/NFC, or PIN/biometrics.
- Production web wallet support still lives in `waltid-web-wallet` (wallet-api v1). This Compose web host is a no-install demo of the shared wallet UI.

## Web demo (wallet-api2)

The identity docker-compose profile serves the Compose web wallet at `http://localhost:7106` (Caddy) against
wallet-api2 on port 7006. Build the UI image locally, then start the stack:

```bash
cd docker-compose
docker compose build wallet-demo-compose-web
docker compose up
```

For a no-Docker Gradle host, enable the Wasm module and run the webpack dev server:

```bash
./gradlew :waltid-applications:waltid-wallet-demo-compose:webApp:wasmJsBrowserDevelopmentRun -PenableWalletDemoComposeWeb=true
```

The host talks to `http://localhost:7006` by default. Override the base URL with the `waltid-wallet-api2` meta tag
(docker does this from `WALLET_API2_PUBLIC_URL`) or in the browser with
`localStorage.setItem("waltid.wallet2.baseUrl", "https://your-wallet-api2")`.

Rebrand the built UI by editing `branding.json` or setting `WALLET_BRAND_APP_TITLE` / `WALLET_BRAND_PRIMARY` (and the other `WALLET_BRAND_*` colour variables) on the compose service. See [Whitelabel branding](#whitelabel-branding).

A production static bundle is:

```bash
./gradlew :waltid-applications:waltid-wallet-demo-compose:webApp:wasmJsBrowserDistribution -PenableWalletDemoComposeWeb=true
```

Register or log in with email and password. The JWT is stored in `localStorage`; the wallet id is stored in `localStorage` and a `waltid_wallet_id` cookie. After login the wallet opens unlocked (no PIN). Authorization-code issuance navigates the current tab to the issuer and returns to the same page (`redirect_uri` is the current origin).

QR scanning, Digital Credentials API, and hardware-backed keys are not available on web. Paste offer and presentation URLs instead.

## Release APK

Each GitHub release attaches a debug-signed `waltid-wallet-demo-compose-<version>.apk`, built with `assembleDebug`. Because CI signs with a fresh, throwaway debug key per release, a release APK **cannot upgrade a previously installed one** — uninstall the existing app first, then install the new one. Uninstalling resets local wallet data (see [Local wallet data](#local-wallet-data)).

## Common commands

Android and shared UI:

```bash
./gradlew :waltid-applications:waltid-wallet-demo-compose:androidApp:assembleDebug
./gradlew :waltid-applications:waltid-wallet-demo-compose:androidApp:installDebug
./gradlew :waltid-applications:waltid-wallet-demo-compose:sharedUI:allTests
./gradlew :waltid-applications:waltid-wallet-demo-compose:webApp:wasmJsBrowserDevelopmentRun -PenableWalletDemoComposeWeb=true
```

iOS:

```bash
cd waltid-applications/waltid-wallet-demo-compose/iosApp
open iosApp.xcodeproj
```

Backend E2E fixtures are intentionally shared:

- Android tests use `waltid-mobile-test-utils` for public EUDI, public demo, and Enterprise fixture backend operations.
- iOS UI tests use the shared Swift `TestHelpers` backend fixtures from `../mobile-e2e-fixtures/ios/TestHelpers`.
- Public demo UI tests run through the normal Android instrumentation and XCTest runners.

## Default wallet app (Android 15+)

The demo registers a minimal NFC Host Card Emulation service with a proprietary AID in the
`payment` category so Android lists **walt.id Wallet** under
**Settings → Apps → Default apps → Wallet app** (`ROLE_WALLET`). That service declines APDUs; it
is a **demo-only Wallet-role discoverability stub**, not a payments or contactless credential
implementation, and is unrelated to Credential Manager Digital Credentials issuance/presentation.

## Digital Credentials API

Android builds register with Credential Manager for:

- **Presentation (`GET_CREDENTIAL`)** — OpenID4VP unsigned and ISO 18013-7 Annex C, via `DigitalCredentialProviderActivity` (full-screen consent for now).
- **Issuance (`CREATE_CREDENTIAL`)** — OpenID4VCI (`openid4vci-v1` and historical aliases), via `DigitalCredentialCreateActivity`.

Issuance uses a translucent create Activity and a Material bottom sheet for offer review (including transaction-code entry). Pre-authorized offers complete in that sheet. Authorization-code offers use the same external-browser + `openid://` path as the Receive tab; `DigitalCredentialCreateAuthHandoff` returns the callback to the still-running create Activity (or completes wallet-side issuance if that Activity was destroyed). The Credential Manager create-option picker remains system-owned; the sheet is wallet fulfillment UI after the user selects this wallet.

### Manual Chrome origin-trial check

Chrome 143+ on Android can exercise create issuance with the Digital Credentials creation flag:

1. Install and open this demo once so Credential Manager registers creation options.
2. Enable `chrome://flags/#web-identity-digital-credentials-creation`.
3. Use an issuer page that calls `navigator.credentials.create({ digital: { requests: [{ protocol: "openid4vci-v1", data: <CredentialOffer> }] } })`, for example [digital-credentials.dev/dmv](https://digital-credentials.dev/dmv) (same-device or QR cross-device).
4. Select the walt.id wallet, accept the offer, and confirm the credential appears under Credentials.

iOS Identity Document providers currently cover presentation only; create/issuance is Android-first.

## Related modules

- [waltid-openid4vc-wallet-mobile](../../waltid-libraries/protocols/waltid-openid4vc-wallet-mobile/README.md)
- [waltid-openid4vc-wallet-persistence-mobile](../../waltid-libraries/protocols/waltid-openid4vc-wallet-persistence-mobile/README.md)
- [waltid-mobile-test-utils](../../waltid-libraries/protocols/waltid-mobile-test-utils/README.md)
