# ``WalletSDK``

Swift-friendly iOS facade for the walt.id mobile wallet SDK.

## Overview

`WalletSDK` gives iOS apps a native Swift entry point for wallet setup,
OpenID4VCI credential issuance, local credential listing, OpenID4VP
presentation, and progress events.

Create a ``Wallet`` in an app process, bootstrap wallet key material, and
then call the async wallet operations with Foundation-native `URL` values:

```swift
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

The public API intentionally exposes Swift actors, structs, enums, and typed
errors. Kotlin Multiplatform and bridge implementation details stay behind this
module boundary.

## Topics

### Integration Guides

- <doc:GettingStarted>
- <doc:IssuingCredentials>
- <doc:PresentingCredentials>
- <doc:ObservingEventsAndErrors>

### Wallet

- ``Wallet``
- ``WalletConfiguration``
- ``WalletAttestationConfiguration``

### Wallet Data

- ``Credential``
- ``WalletBootstrapResult``
- ``PresentationResult``

### Events

- ``WalletEvent``
- ``WalletEventPhase``
- ``WalletEventStatus``

### Errors And Key Types

- ``WalletError``
- ``WalletKeyType``
