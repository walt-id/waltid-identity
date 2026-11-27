<div align="center">
<h1>walt.id OpenID4VC Wallet Swift Facade</h1>
 <span>by </span><a href="https://walt.id">walt.id</a>
 <p>Swift facade for the walt.id mobile wallet SDK.</p>

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


This package is the WAL-1065 iOS consumer boundary. It exposes Swift-native
types and a `Wallet` actor while keeping Kotlin Multiplatform, SKIE, and
generated bridge symbols behind the package implementation.

## Source map

Start with [Wallet](Sources/WalletSDK/Wallet.swift) and
[WalletConfiguration](Sources/WalletSDK/WalletConfiguration.swift).
The [Identity](Sources/WalletSDK/Identity), [Credentials](Sources/WalletSDK/Credentials),
[Issuance](Sources/WalletSDK/Issuance) and [Presentation](Sources/WalletSDK/Presentation)
directories follow the same journey as the native API. [Proximity](Sources/WalletSDK/Proximity)
owns nearby sessions and reader trust. [DigitalCredentials](Sources/WalletSDK/DigitalCredentials)
owns system registration and Annex C requests. [Persistence](Sources/WalletSDK/Persistence) contains database key ownership and the credential and DID store protocols. [Interop](Sources/WalletSDK/Interop)
connects the actor to the shared core. All of these features use `import WalletSDK`.

## Status

- Minimum iOS version: 15.4
- Runtime platform: iOS only
- Public API style: `async`/`await`, `Sendable` values, typed `WalletError`
- Documentation: DocC catalog in `Sources/WalletSDK/Documentation.docc`
- Local binary dependency: `WalletCore.xcframework`
- Current delivery mode: local package plus locally assembled XCFramework

## Credential status

Read `credentialStatusSnapshot()` or observe `credentialStatuses` without issuer networking.
`refreshCredentialStatuses(ids:force:)` explicitly checks stored credentials; your app owns
foreground/manual triggers and preferences. Cancel stream tasks with their UI owner and call
`close()` to release the instance without deleting wallet data. See the
[credential-status guide](Sources/WalletSDK/Documentation.docc/CheckingCredentialStatus.md) for semantics,
reviewed sharing, persistence and lifecycle examples.

## Local Build

Generate the KMP core XCFramework before building an iOS consumer:

```bash
./gradlew :waltid-libraries:protocols:waltid-openid4vc-wallet-mobile:assembleWalletCoreReleaseXCFramework -PenableIosBuild=true
```

The Swift package expects the local artifact at:

```text
../waltid-openid4vc-wallet-mobile/build/XCFrameworks/release/WalletCore.xcframework
```

Then build or test the Swift facade:

```bash
swift test --package-path waltid-libraries/protocols/waltid-wallet-sdk-ios -Xswiftc -strict-concurrency=complete -Xswiftc -warnings-as-errors
```

Host Swift tests do not exercise the conditional WalletCore bridge. Run its package target on an
explicit simulator from the Identity root:

```bash
.github/scripts/mobile-ci/run-ios-wallet-sdk-tests.sh 'platform=iOS Simulator,id=<simulator-UDID>'
```

The runner builds WalletCore from the selected checkout and separately compiles the same SDK sources
with native flow/receipt fixtures under `build/bridge-fixtures`. These fixtures never enter the default
framework or published API. The runner selects that test binary with `WALLET_SDK_BRIDGE_FIXTURES=1`,
checks both framework headers, and requires the named review, approval, receipt, cancellation and
error tests in JUnit results. Missing frameworks, undiscovered tests and unexpected skips fail the run.
Source revision, binary hashes and results are recorded under `build/proximity-tests/swift-bridge.*`.

Generate and validate the Swift DocC archive:

```bash
waltid-libraries/protocols/waltid-wallet-sdk-ios/scripts/generate-docc.sh
```

The validation keeps owned public Swift symbols covered by DocC abstracts and
parameter documentation. The mobile docs workflow also checks that persistence
examples in README and DocC pages match the compiled Kotlin and Swift snippet
tests:

```bash
python3 waltid-libraries/protocols/waltid-wallet-sdk-ios/scripts/check-mobile-wallet-doc-snippets.py
```

## Using the wallet

Open `Wallet()` and initialize its signing identity. Pass an offer to `startIssuance` to preview it, then accept the selected grant. Register the client and redirect URI when beginning browser authorization. Use `WalletConfiguration.Issuance.receiptAuthorities` for host-provisioned credential issuer keys.

A ready presentation preview supplies the choices for the holder. Show the wallet-issued review and submit it after confirmation. The host delivers a prepared redirect or form.

- [Compiled Swift journey](Tests/WalletSDKTests/WalletJourney.swift)
- [Receiving credentials](Sources/WalletSDK/Documentation.docc/IssuingCredentials.md)
- [Presenting credentials](Sources/WalletSDK/Documentation.docc/PresentingCredentials.md)
- [Nearby transports and reader trust](Sources/WalletSDK/Documentation.docc/ProximityPresentation.md)
- [Signing identities and recovery](Sources/WalletSDK/Documentation.docc/IdentityRecovery.md)
- [Persistence and custom stores](docs/persistence.md)

## Native iOS Consumer

The native iOS consumer proof lives in the existing demo app:

```text
waltid-applications/waltid-wallet-demo-ios
```

That app imports `WalletSDK` directly from SwiftUI and exercises the same
Swift package boundary a native iOS integrator would use. The separate Compose
Multiplatform demo remains the KMP/Compose consumer proof and uses its generated
Kotlin `sharedUI` framework instead of routing through this Swift facade.

## Interop Boundary

`WalletSDK` is the intended public iOS API. It exposes Swift-owned models,
errors, and the `Wallet` actor so app code does not need to import or handle
generated Kotlin, Objective-C, or SKIE symbols directly.

`WalletCore` is the local binary implementation dependency behind that facade.
It is assembled from `waltid-openid4vc-wallet-mobile` with SKIE enabled for the
KMP features the facade consumes: cancellable Swift `async` calls for Kotlin
`suspend` functions, Flow-to-`AsyncSequence` support for wallet events, and
Swift-friendly enum/sealed wrappers at the bridge boundary.

The facade is currently kept as a separate SwiftPM source target instead of
bundling the hand-written Swift wrappers into `WalletCore`. That keeps the
public SDK module small for DocC, symbol graph checks, and host-side SwiftPM
tests. SKIE Swift code bundling remains a viable future packaging option if the
release shape should become a single binary framework that includes both the
KMP core and the Swift facade.

The Compose Multiplatform demo intentionally does not apply this Swift facade.
Its iOS app hosts Kotlin Compose UI through the generated `sharedUI` framework,
which is the right proof path for KMP/Compose integrators. SKIE would become
useful there only if the demo adds a native SwiftUI path that directly consumes
shared Kotlin state, flows, sealed UI state, or suspend APIs.

## Publishing Follow-up

This package currently uses a local binary target path for the spike. A customer
preview or release should switch the binary target to a URL plus checksum after
the team chooses the private artifact host and release CI workflow.

## Join the community

* Connect and get the latest updates: [Discord](https://discord.gg/AW8AgqJthZ) | [Newsletter](https://walt.id/newsletter) | [YouTube](https://www.youtube.com/channel/UCXfOzrv3PIvmur_CmwwmdLA) | [LinkedIn](https://www.linkedin.com/company/walt-id/)
* Get help, request features and report bugs: [GitHub Issues](https://github.com/walt-id/waltid-identity/issues)
* Find more indepth documentation on our [docs site](https://docs.walt.id)

## License

Licensed under the [Apache License, Version 2.0](https://github.com/walt-id/waltid-identity/blob/main/LICENSE)

<div align="center">
<img src="../../../assets/walt-banner.png" alt="walt.id banner" />
</div>
