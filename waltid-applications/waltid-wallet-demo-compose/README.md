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

## Target status

- Android and iOS are the supported mobile demo targets for wallet SDK issuance, presentation, platform-backed keys, and persistence.
- Web/Wasm is currently a mock UI preview wired to `createMockDemoWallet()`. It does not exercise the mobile wallet SDK, platform key storage, SQLDelight persistence, EUDI flows, or Enterprise flows.
- Production web wallet support is expected to live outside this mobile demo app. If a shared web UI is needed later, the shared UI module may need to move or split around the final web architecture.

## Common commands

```bash
./gradlew :waltid-applications:waltid-wallet-demo-compose:androidApp:assembleDebug
./gradlew :waltid-applications:waltid-wallet-demo-compose:androidApp:installDebug
./gradlew :waltid-applications:waltid-wallet-demo-compose:sharedUI:allTests
./gradlew :waltid-applications:waltid-wallet-demo-compose:webApp:wasmJsBrowserDevelopmentRun -PenableWalletDemoComposeWeb=true
```

Backend E2E fixtures are intentionally shared:

- Android tests use `waltid-mobile-test-utils` for public EUDI, public demo, and Enterprise fixture backend operations.
- iOS UI tests use the shared Swift `TestHelpers` backend fixtures from `../mobile-e2e-fixtures/ios/TestHelpers`.
- Public demo UI tests run through the normal Android instrumentation and XCTest runners without app-specific shell wrappers.

## Related modules

- [waltid-openid4vc-wallet-mobile](../../waltid-libraries/protocols/waltid-openid4vc-wallet-mobile/README.md)
- [waltid-openid4vc-wallet-persistence-mobile](../../waltid-libraries/protocols/waltid-openid4vc-wallet-persistence-mobile/README.md)
- [waltid-mobile-test-utils](../../waltid-libraries/protocols/waltid-mobile-test-utils/README.md)
