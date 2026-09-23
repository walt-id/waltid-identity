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

## In-person presentation

The Android and iOS apps expose a dedicated **Present in person** journey for holder-side ISO mdoc
proximity presentation. The Wallet SDK remains the source of session, request, reader-authentication,
trust, disclosure, and terminal-state meaning; the shared Compose UI renders those facts and performs
only platform-owned permission, settings, lifecycle, screen-awake, and brightness actions.
Before creating a session, the Compose demo queries the SDK capabilities and offers an explicit action for any
runtime permission required by the selected proximity configuration. A permission that Android no
longer allows the app to request is shown as an explicit **Open app settings** action; returning from
Settings rechecks the selected configuration before session creation. Radio, power, and settings
remediation otherwise remains an explicit user action.

The sharing screen offers **Hold near the reader** and **Show QR code** only for engagements actually prepared
by the SDK. A single ready QR method opens directly. On iOS, choosing NFC explicitly opens the system
presentation sheet, including when NFC is the only available method.
It does not depend on the optional short-lived presentment assertion. Choosing an already prepared
engagement preserves the session and payload. Once connecting starts, method controls disappear;
reader consent remains the focus, and the actual route is available under **Connection details**.
Completion shows the result and **Done**, without radio controls.

Permission setup explains the required action before opening an OS prompt. Users can skip optional
setup only when the SDK reports another complete route that can start. Returning from Settings rechecks
availability; declined permissions are not requested again automatically. QR visibility alone controls
temporary screen brightness, and the QR is hidden once connecting begins.

**Settings → Nearby sharing → Connection method** stores the connection profile. Changing it
before connection or approval replaces the open engagement and rechecks availability. The previous
QR and choices remain hidden until the new profile is ready. Connected exchanges and approved shares
keep their configuration. New presentations use the latest preference.

Automatic uses the available reader-compatible routes. Compatibility profiles narrow transfer to
Bluetooth or Wi-Fi Aware, or select provisional NFCv2 direct/handover modes. These choices stay in Settings.
Device support and permissions are checked at startup; NFCv2 retains its mandatory NFC channel.

**Approval** in Nearby sharing settings stores **Ask each time** (default) or
**Prepare sharing**, independently of the connection profile. The same choice is
available before connecting. Both switches update one saved preference, retained
for subsequent shares. Changing it before connection refreshes the engagement in
place; the previous QR is hidden until its replacement is ready. Changes made
during an exchange apply to the next presentation. Preparation first identifies a
named authenticated trusted reader and collects its request without sharing credentials. After the
connection closes, review the reader, purpose, retention and selected data, then
choose **Approve and get ready**. Several matching credentials require an explicit
choice; requested mDL portrait data is marked required. Missing required data
prevents approval and explains why another credential is needed.

The ready screen shows a cancellable 60-second, one-use approval and its scope.
Approval automatically reopens the previous engagement method when available.
The reader must start a fresh request; additional data, another reader or changed
purpose/retention requires another decision. While the Core NFC sheet owns the
screen, the SDK uses this review/reconnect flow even if NFC v2 has an alternate
bearer. Conventional handover permits connected review after the sheet closes. Only an actively owned NFC sheet exempts background cancellation.

Completion shows the locally shared selection and **Prepare another share** while
the recent request remains valid. Preparing again requires a new review and approval;
retry never reuses an armed approval. **Done** forgets the plan. Plans expire after
ten minutes, approvals after 60 seconds, and neither is persisted. Reader/key checks
remain in the SDK. A local receipt does not confirm the reader's verification result.

The current journey selects Bluetooth Low Energy, conventional NFC, and Wi-Fi Aware as alternative
retrieval methods. Capability and permission failures are explained during setup. Eligible Android API 33+
devices may advertise the NCS-SK-128 Wi-Fi Aware holder path after runtime permissions and radio
resources pass; iOS shows the precise unsupported result while retaining BLE/NFC fallback. The
journey displays Device Engagement as an accessible QR code and supports per-document credential and element selection,
shows reader-stated purpose and retention intent, and presents authentication scope, signature
validity, certificate-path, revocation, optional RICAL, and product-trust evidence as separate facts.
It requests fresh consent for repeated exchanges and restores temporary display changes on every exit
path. Raw engagement data is never exposed through accessibility labels.

QR rendering remains a demo-host concern rather than a Wallet SDK API. The shared Compose renderer
uses ZXing on Android and ZXing-C++ on iOS for Device Engagement only. It accepts bounded ASCII
`mdoc:` text, uses low error correction without ECI, and fails closed instead of truncating an
oversized payload. Compose iOS pins the resulting module fingerprint to the native SwiftUI renderer,
and both renderers add an exact four-module quiet zone.

This demo proves the wallet-side SDK integration. Wi-Fi Aware physical discovery/data-path/HTTP
interoperability, external reader interoperability, prolonged
reliability, and release qualification are tracked separately and must not be inferred from the demo.

The mobile settings screen exposes **Settings → Nearby sharing → Reader authentication** on Compose Android
and Compose iOS. It supports a permissive or trusted-reader-only policy, lists and removes configured
Reader CAs/RICAL providers, and imports DER, certificate-only PEM, or versioned walt.id JSON trust
bundles through the platform document picker. Every import is validated and previewed before an atomic
save to app-private storage; private keys and PKCS#12/PFX reader identities are deliberately rejected.
Each new proximity session freezes the current settings, so an active exchange cannot be reconfigured.

The ready screen prioritizes the full QR within the available space. **Prepare sharing** is a single
switch: off means review each request; on means review, approve, then reconnect. Selecting the mode
does not authorize disclosure. The same switch is available in Nearby sharing settings. Once armed,
the reader and expiry countdown stay visible, and **Approved data** opens the already reviewed
selection. Cancel stays separate from scrolling content. Short screens and larger text retain
scrolling for secondary controls; landscape places the QR beside the controls.

Review actions carry the identity of the displayed review. Each new review resets
holder choices and continuation. Selected permissions are offered explicitly; an optional
permission can be skipped only when the SDK reports a complete viable alternative route.
Terminal recovery creates a new single-use session.

## Signing key setup

PIN setup controls access to the app, with optional biometric unlock. Signing-key setup has three steps: choose whether to create or restore a key, choose its storage, and choose when signing requires system approval. New keys can be created with or without a backup. Each screen groups the SDK's supported options into choice rows; a single supported option is shown as read-only. Continue keeps the selection local until Create signing key or Restore signing key is pressed. The SDK revalidates the selected option before executing it.

Key recovery restores the original key and DID, not credentials. A local save does not prove delivery to another device. Unavailable providers show their reported reason and can be checked again; returning to the app also refreshes the choices.

Android offers encrypted-cloud backup and device transfer separately. Cloud backup requires Google to report end-to-end encryption available. Device transfer is performed by a supported Android phone setup or migration flow, requires the source device and does not request a cloud copy. Hardware-required storage must pass the hardware check; Android Keystore without that requirement leaves the protection level to the platform.

On iOS, choose Without a backup under Create a new key to use Secure Enclave. Recoverable keys use Keychain or the encrypted wallet database because existing keys cannot be imported into Secure Enclave.

Settings → Signing key shows the storage policy, observed key protection, key origin, signing approval, and key backup status. The Technical details page shows the wallet DID, key ID, and public key (JWK), with copy controls. Nearby sharing groups sharing approval, connection methods, and reader authentication. Reader-trust imports are reviewed before saving; resets and removals require confirmation. The Digital Credentials API page controls the additional wallet review, while Lock wallet and Reset wallet remain on the Settings root. To replace a key or its signing policy, use Reset wallet and repeat setup; this removes local credentials, which must be issued again.


The web demo uses account sign-in instead of a local PIN. Its Settings root retains Technical details, Sign out, and Reset wallet; device-only signing and sharing controls are hidden.

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
- Web/Wasm is a custodial demo against **wallet-api2 with the `auth` feature enabled**. It uses email/password JWT auth (`POST /auth/register`, `POST /auth/emailpass`) and isolated wallet-api2 HTTP routes for receive/present. It does not run the mobile wallet SDK, platform keys, SQLDelight, DC API, BLE/NFC, in-person proximity, or PIN/biometrics.
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

Each GitHub release attaches a debug-signed `waltid-wallet-demo-compose-<version>.apk`, built with `assembleProductionDebug`. Because CI signs with a fresh, throwaway debug key per release, a release APK **cannot upgrade a previously installed one** — uninstall the existing app first, then install the new one. Uninstalling resets local wallet data (see [Local wallet data](#local-wallet-data)).

## Common commands

Android and shared UI:

```bash
./gradlew :waltid-applications:waltid-wallet-demo-compose:androidApp:assembleProductionDebug
./gradlew :waltid-applications:waltid-wallet-demo-compose:androidApp:installProductionDebug
./gradlew :waltid-applications:waltid-wallet-demo-compose:androidApp:assemblePreviewDebug
./gradlew :waltid-applications:waltid-wallet-demo-compose:androidApp:installPreviewDebug
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
