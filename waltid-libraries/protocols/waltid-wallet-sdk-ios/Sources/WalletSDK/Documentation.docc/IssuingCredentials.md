# Issuing Credentials

Resolve an OpenID4VCI credential offer, collect a transaction code when the
issuer requires one, and use ``Wallet/receive(offer:txCode:clientID:)`` to
persist the issued credentials in the wallet.

## Overview

### Receive an Offer

Pass the offer URL from a QR scan, deep link, universal link, or another app
handoff into the wallet actor. Resolve it before issuance so the application
can determine whether it must collect a separately delivered transaction code.

```swift
let resolution = try await wallet.resolveOffer(offer: credentialOfferURL)
let transactionCode: String?
if resolution.transactionCodeRequired {
    transactionCode = await collectTransactionCode()
} else {
    transactionCode = nil
}

let credentialIDs = try await wallet.receive(
    offer: credentialOfferURL,
    txCode: transactionCode
)
```

The returned identifiers can be used to refresh local UI or to load credential
metadata through ``Wallet/credentials()``.

```swift
let credentials = try await wallet.credentials()
let issuedCredentials = credentials.filter { credentialIDs.contains($0.id) }
```

> Tip: Collect ``Wallet/events`` while issuance is running if the UI needs
> progress updates for issuer communication, credential storage, or completion.
