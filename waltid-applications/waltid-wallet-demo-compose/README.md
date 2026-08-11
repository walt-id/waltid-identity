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

## Public demo backend defaults

Clean demo installs use the public walt.id demo profile endpoint for OpenID4VP transaction-data support:

```text
https://wallet.demo.walt.id/wallet-api/transaction-data-profiles
```

Android builds can override it with `-PtransactionDataProfiles.url=...`. Compose iOS can override it with the `TRANSACTION_DATA_PROFILES_URL` launch environment variable or `UserDefaults` key. Wallet attestation values remain explicit overrides through `attestation.*` Gradle properties on Android and `ATTESTATION_*` environment/UserDefaults values on iOS; no bearer token is defaulted.

## Target status

- Android and iOS are the supported mobile demo targets for wallet SDK issuance, presentation, platform-backed keys, and persistence.
- Web/Wasm is currently a mock UI preview wired to `createMockDemoWallet()`. It does not exercise the mobile wallet SDK, platform key storage, SQLDelight persistence, EUDI flows, or Enterprise flows.
- Production web wallet support is expected to live outside this mobile demo app. If a shared web UI is needed later, the shared UI module may need to move or split around the final web architecture.

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
exists only for Wallet-role discoverability and is unrelated to Credential Manager Digital
Credentials issuance/presentation.

## Digital Credentials API

Android builds register with Credential Manager for:

- **Presentation (`GET_CREDENTIAL`)** — OpenID4VP unsigned and ISO 18013-7 Annex C, via `DigitalCredentialProviderActivity` (full-screen consent for now).
- **Issuance (`CREATE_CREDENTIAL`)** — OpenID4VCI (`openid4vci-v1` and common aliases), via `DigitalCredentialCreateActivity`.

Issuance uses a translucent create Activity and a Material bottom sheet for offer review (including transaction-code entry). Pre-authorized offers complete in that sheet. Authorization-code offers embed issuer/Keycloak sign-in in a WebView inside the same sheet and capture the `openid://` redirect without opening an external browser tab. The Credential Manager create-option picker remains system-owned; the sheet is wallet fulfillment UI after the user selects this wallet.

Receive-tab authorization-code offers (outside Credential Manager) still use the system browser + `openid://` deep link.

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
