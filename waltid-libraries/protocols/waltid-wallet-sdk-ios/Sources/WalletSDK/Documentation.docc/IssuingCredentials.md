# Issuing Credentials

Use ``Wallet/receive(offer:txCode:clientID:)`` to process an OpenID4VCI
credential offer and persist the issued credentials in the wallet.

## Overview

### Receive an Offer

Pass the offer URL from a QR scan, deep link, universal link, or another app
handoff into the wallet actor.

```swift
let credentialIDs = try await wallet.receive(
    offer: credentialOfferURL,
    txCode: userEnteredTransactionCode
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
