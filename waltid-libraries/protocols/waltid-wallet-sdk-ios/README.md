# WaltIDWalletSDK

Swift Package facade for the walt.id mobile wallet SDK.

This package is the WAL-1065 iOS consumer boundary. It exposes Swift-native
types and a `Wallet` actor while keeping Kotlin Multiplatform, SKIE, and
generated bridge symbols behind the package implementation.

## Status

- Minimum iOS version: 15.4
- Runtime platform: iOS only
- Public API style: `async`/`await`, `Sendable` values, typed `WalletError`
- Documentation: DocC catalog in `Sources/WaltIDWalletSDK/Documentation.docc`
- Local binary dependency: `WaltIDWalletCore.xcframework`
- Current delivery mode: local package plus locally assembled XCFramework

## Local Build

Generate the KMP core XCFramework before building an iOS consumer:

```bash
./gradlew :waltid-libraries:protocols:waltid-openid4vc-wallet-mobile:assembleWaltIDWalletCoreReleaseXCFramework -PenableIosBuild=true --no-configuration-cache
```

The Swift package expects the local artifact at:

```text
../waltid-openid4vc-wallet-mobile/build/XCFrameworks/release/WaltIDWalletCore.xcframework
```

Then build or test the Swift facade:

```bash
swift test --package-path waltid-libraries/protocols/waltid-wallet-sdk-ios -Xswiftc -strict-concurrency=complete -Xswiftc -warnings-as-errors
```

## Usage Sketch

```swift
import WaltIDWalletSDK

let wallet = try await Wallet(
    configuration: WalletConfiguration(walletID: "consumer-wallet")
)

let bootstrap = try await wallet.bootstrap(didMethod: "key")
let credentialIDs = try await wallet.receive(offer: credentialOfferURL)
let credentials = try await wallet.credentials()
let presentation = try await wallet.present(
    request: authorizationRequestURL,
    did: bootstrap.did
)
```

## Native iOS Consumer

The native iOS consumer proof lives in the existing demo app:

```text
waltid-applications/waltid-wallet-demo-ios
```

That app imports `WaltIDWalletSDK` directly from SwiftUI and exercises the same
Swift package boundary a native iOS integrator would use. The separate Compose
Multiplatform demo remains the KMP/Compose consumer proof and uses its generated
Kotlin `sharedUI` framework instead of routing through this Swift facade.

## Publishing Follow-up

This package currently uses a local binary target path for the spike. A customer
preview or release should switch the binary target to a URL plus checksum after
the team chooses the private artifact host and release CI workflow.
