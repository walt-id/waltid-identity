<div align="center">
<h1>walt.id OpenID4VC Wallet Mobile</h1>
 <span>by </span><a href="https://walt.id">walt.id</a>
 <p>Mobile facade for Android and iOS wallet SDK integrations.</p>

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

Mobile facade for the walt.id wallet SDK. This module exposes the Android/iOS-facing wallet API used by the native demo apps while delegating protocol behavior to [waltid-openid4vc-wallet](../waltid-openid4vc-wallet/README.md) and persistence to [waltid-openid4vc-wallet-persistence-mobile](../waltid-openid4vc-wallet-persistence-mobile/README.md).

For local setup and platform build flags, see the [Mobile Wallet Development Guide](../../../docs/mobile-wallet-development.md).

## Capabilities

- Bootstrap a mobile wallet with platform-backed keys and DID material.
- Receive credentials using OpenID4VCI.
- List credentials stored in mobile persistence.
- Present credentials using OpenID4VP.
- Support mobile issuance flows using OAuth 2.0 client attestation.

## Demo apps

- [Compose Wallet Demo](../../../waltid-applications/waltid-wallet-demo-compose/README.md)
- [iOS Wallet Demo](../../../waltid-applications/waltid-wallet-demo-ios/README.md)

## API documentation

Generate the SDK facade API reference with Dokka:

```bash
./gradlew :waltid-libraries:protocols:waltid-openid4vc-wallet-mobile:dokkaGeneratePublicationHtml -PenableAndroidBuild=true -PenableIosBuild=true
```

The generated HTML is written to `build/dokka/html`. iOS integration examples are also documented in the iOS demo app's DocC catalog at `waltid-applications/waltid-wallet-demo-ios/iosApp/iosApp/Documentation.docc`.
