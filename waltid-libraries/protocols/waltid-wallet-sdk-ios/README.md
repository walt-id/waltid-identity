# WaltidWalletSDK

Swift Package facade for the walt.id mobile wallet SDK.

This package is the WAL-1065 iOS consumer boundary. It exposes Swift-native
types and a `WalletClient` actor while keeping Kotlin Multiplatform, SKIE, and
generated bridge symbols behind the package implementation.

## Status

- Minimum iOS version: 15.4
- Public API style: `async`/`await`, `Sendable` values, typed `WalletError`
- Documentation: DocC catalog in `Sources/WaltidWalletSDK/Documentation.docc`
- Local binary dependency: `WaltidWalletCore.xcframework`
- Current delivery mode: local package plus locally assembled XCFramework

## Local Build

Generate the KMP core XCFramework before building an iOS consumer:

```bash
./gradlew :waltid-libraries:protocols:waltid-openid4vc-wallet-mobile:assembleWaltidWalletCoreReleaseXCFramework -PenableIosBuild=true --no-configuration-cache
```

The Swift package expects the local artifact at:

```text
../waltid-openid4vc-wallet-mobile/build/XCFrameworks/release/WaltidWalletCore.xcframework
```

Then build or test the Swift facade:

```bash
swift test --package-path waltid-libraries/protocols/waltid-wallet-sdk-ios -Xswiftc -strict-concurrency=complete -Xswiftc -warnings-as-errors
```

## Usage Sketch

```swift
import WaltidWalletSDK

let client = try await WalletClient(
    configuration: WalletConfiguration(walletID: "consumer-wallet")
)

let bootstrap = try await client.bootstrap(didMethod: "key")
let credentialIDs = try await client.receive(offer: credentialOfferURL)
let credentials = try await client.credentials()
let presentation = try await client.present(
    request: authorizationRequestURL,
    did: bootstrap.did
)
```

## Sample Consumer

The minimal SwiftPM/XCFramework consumer fixture lives at:

```text
waltid-applications/waltid-wallet-sdk-ios-sample
```

The existing `waltid-wallet-demo-ios` app remains the richer product demo. Its
KMP `shared` bridge delegates to the same `WalletSdkBridge` boundary used by
this package.

## Publishing Follow-up

This package currently uses a local binary target path for the spike. A customer
preview or release should switch the binary target to a URL plus checksum after
the team chooses the private artifact host and release CI workflow.
