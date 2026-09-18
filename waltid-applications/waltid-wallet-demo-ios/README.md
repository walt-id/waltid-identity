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

## Signing key setup

PIN setup controls access to the app, with optional biometric unlock. Key setup then asks three separate questions: whether to back up or restore a signing key, where to store it, and when to request system approval for signing. Each screen groups the SDK's supported options into selectable cards; Continue keeps the selection local until Create signing key or Restore signing key is pressed. The SDK revalidates the selected option before executing it.

Key recovery restores the original key and DID, not credentials. A local save does not prove delivery to another device. Unavailable providers show their reported reason and can be checked again; returning to the app also refreshes the choices.

On iOS, choose Create without a key backup to use Secure Enclave. Recoverable keys use Keychain or the encrypted wallet database because existing keys cannot be imported into Secure Enclave.

Settings → Protection and recovery shows the storage requirement, observed signing protection, key origin, signing approval and recovery status. Technical identifiers are available separately. To replace a key or its signing policy, use Reset wallet and repeat setup; this removes local credentials, which must be issued again.


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
