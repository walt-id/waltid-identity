# Issuing Credentials

Resolve an OpenID4VCI credential offer, collect a transaction code when the
issuer requires one, and use the returned preview handle with
``Wallet/receive(previewHandle:txCode:clientID:)`` to
persist the issued credentials in the wallet.

## Overview

### Receive an Offer

Pass the offer URL from a QR scan, deep link, universal link, or another app
handoff into the wallet actor. Resolve it before issuance so the application
can show the localized issuer and credential metadata and determine whether it
must collect a separately delivered transaction code.

```swift
let resolution = try await wallet.resolveOffer(offer: credentialOfferURL)
let transactionCode: String?
if let requirement = resolution.transactionCode {
    transactionCode = await collectTransactionCode(
        inputMode: requirement.inputMode,
        expectedLength: requirement.length,
        description: requirement.description
    )
} else {
    transactionCode = nil
}

let credentialIDs = try await wallet.receive(
    previewHandle: resolution.previewHandle,
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

If the user closes the review without accepting it, call
``Wallet/discardIssuancePreview(_:)``. Failed issuance attempts retain the
handle for retry; successful issuance consumes it.
