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

For setup, IDE guidance, and local E2E prerequisites, see the [Mobile Wallet Development Guide](../../docs/mobile-wallet-development.md).

## Local wallet data

The demo uses the SDK default encrypted local persistence. Wallet database files are SQLCipher-encrypted, and SDK-managed database keys live in iOS Keychain. During local development, reset wallet state by calling `Wallet.deleteLocalData()` from the SDK facade, deleting the app from the simulator/device, or removing the app's local data.

## Common commands

```bash
cd waltid-applications/waltid-wallet-demo-ios/iosApp
open iosApp.xcodeproj
```

E2E scripts live in [scripts](scripts/README.md).

## Related modules

- [WalletSDK](../../waltid-libraries/protocols/waltid-wallet-sdk-ios/README.md)
- [waltid-openid4vc-wallet-mobile](../../waltid-libraries/protocols/waltid-openid4vc-wallet-mobile/README.md)
- [waltid-openid4vc-wallet-persistence-mobile](../../waltid-libraries/protocols/waltid-openid4vc-wallet-persistence-mobile/README.md)
- [waltid-crypto](../../waltid-libraries/crypto/waltid-crypto/README.md)
