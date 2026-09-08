<div align="center">
<h1>walt.id iOS Wallet Demo</h1>
 <span>by </span><a href="https://walt.id">walt.id</a>
 <p>Native iOS demo app for wallet SDK credential issuance and presentation.</p>

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

Native iOS demo app for the mobile wallet SDK. It uses SwiftUI with the `WalletSDK` Swift package and demonstrates OpenID4VCI credential issuance, OpenID4VP credential presentation, iOS Keychain / Secure Enclave-backed keys, and SQLDelight-backed wallet persistence.

For setup, IDE guidance, and mobile integration test commands, see the [Mobile Wallet Development Guide](../../docs/mobile-wallet-development.md).

## In-person presentation

The Present tab includes a dedicated **Present in person** journey for holder-side ISO mdoc
proximity presentation. The native SwiftUI view model consumes the Wallet SDK session directly and
does not reconstruct protocol state, reader trust, request meaning, or disclosure rules.

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

**Settings → Credential Sharing → Nearby sharing** stores the connection profile. Changing it
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
purpose/retention requires another decision. iOS NFC-only uses this review/reconnect
flow because Core NFC owns the screen during transfer; Bluetooth retains ordinary
connected review. Only an actively owned NFC sheet exempts background cancellation.

Completion shows the locally shared selection and **Prepare another share** while
the recent request remains valid. Preparing again requires a new review and approval;
retry never reuses an armed approval. **Done** forgets the plan. Plans expire after
ten minutes, approvals after 60 seconds, and neither is persisted. Reader/key checks
remain in the SDK. A local receipt does not confirm the reader's verification result.

The journey displays Device Engagement as an accessible QR code, retrieves over the available
Bluetooth Low Energy method, and presents authentication scope, signature validity, certificate-path,
revocation, optional RICAL, and product-trust evidence as separate facts. It exposes reader-stated
purpose and retention intent, supports per-document credential and element selection, and obtains
fresh consent for repeated exchanges. Bluetooth authorization, app settings, lifecycle, screen-awake,
and temporary brightness behavior stay in the iOS host and are restored on exit.

QR rendering remains a private demo-package concern rather than a Wallet SDK API. The native app
uses the pinned ZXing-C++ dependency for Device Engagement only. It accepts bounded ASCII `mdoc:`
text, uses low error correction without ECI, and fails closed instead of truncating an oversized
payload. A package-private Objective-C++ adapter exposes the no-ECI writer mode that the pinned Swift
wrapper lacks; it adds neither another QR implementation nor a public module. Compose iOS and native
SwiftUI pin the same proximity module fingerprint and render an exact four-module quiet zone using
whole physical-pixel modules.

This demo proves the wallet-side SDK integration. External reader interoperability, prolonged
reliability, and release qualification are tracked separately and must not be inferred from the demo.

**Settings → Credential Sharing → Reader Authentication** exposes the reader policy and holder-owned
Reader CA/RICAL configuration. Imports use the native document picker and accept DER,
certificate-only PEM, or versioned walt.id JSON trust bundles. The app previews validated public trust
material before saving it in the wallet App Group, rejects private keys and PKCS#12/PFX reader
identities, and applies one immutable settings snapshot to each new proximity session.

## Local wallet data

The demo uses the default managed encrypted local persistence. Wallet database files are SQLCipher-encrypted, and managed database keys live in iOS Keychain. During local development, reset wallet state by calling `Wallet.deleteLocalData()` from the SDK facade, deleting the app from the simulator/device, or removing the app's local data.

The UI stays focused on the production default. Non-default persistence options, including provided database keys and custom credential stores, are documented and covered by SDK and demo integration tests.

## Whitelabel branding

Edit `WalletDemoBranding.default` in `waltid-wallet-demo-shared-ios` to change the in-app wallet title and brand colours (`primary`, `secondary`, `primaryContainer`, and their on-colours). The app root can also inject a custom value with `.environment(\.walletDemoBranding, ...)`.

The home-screen name stays in `CFBundleDisplayName` in the app and document-provider `Info.plist` files.

## Public demo backend defaults

Clean demo installs use the public walt.id demo profile endpoint for OpenID4VP transaction-data support:

```text
https://wallet.demo.walt.id/wallet-api/transaction-data-profiles
```

Override it with the `TRANSACTION_DATA_PROFILES_URL` launch environment variable or `UserDefaults` key. Wallet attestation values remain explicit overrides through `ATTESTATION_*` environment/UserDefaults values; no bearer token is defaulted.

The ready screen prioritizes the full QR within the available space. **Prepare sharing** is a single
switch: off means review each request; on means review, approve, then reconnect. Selecting the mode
does not authorize disclosure. The same switch is available in Nearby sharing settings. Once armed,
the reader and expiry countdown stay visible, and **Approved data** opens the already reviewed
selection. Cancel stays separate from scrolling content. Short screens and larger text retain
scrolling for secondary controls; landscape places the QR beside the controls.

## Common commands

```bash
cd waltid-applications/waltid-wallet-demo-ios/iosApp
open iosApp.xcodeproj
```

Mobile integration tests run through XCTest and the self-contained Enterprise
fixture Gradle tasks documented in the mobile guide.

## Related modules

- [WalletSDK](../../waltid-libraries/protocols/waltid-wallet-sdk-ios/README.md)
- [waltid-openid4vc-wallet-mobile](../../waltid-libraries/protocols/waltid-openid4vc-wallet-mobile/README.md)
- [waltid-openid4vc-wallet-persistence-mobile](../../waltid-libraries/protocols/waltid-openid4vc-wallet-persistence-mobile/README.md)
- [waltid-crypto](../../waltid-libraries/crypto/waltid-crypto/README.md)

Review actions carry the identity of the displayed review. Each new review resets
holder choices and continuation. Permission prompts are needed only when no
selected route can start; terminal recovery creates a new single-use session.
