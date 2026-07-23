# Issuing Credentials

Start an OpenID4VCI issuance session, collect a transaction code when the
issuer requires one, and continue the session to persist issued credentials in
the wallet.

## Overview

### Start and Continue an Issuance Session

Pass the offer URL from a QR scan, deep link, universal link, or another app
handoff into the wallet actor. Starting the session resolves the offer before
issuance, so the application can show localized issuer and credential metadata
and determine whether it must collect a separately delivered transaction code.

```swift
let session = try await wallet.startIssuance(
    IssuanceRequest(
        offer: credentialOfferURL,
        redirectURI: URL(string: "wallet.example:/callback")!
    )
)
let transactionCode: String?
if let requirement = session.offer.transactionCode {
    transactionCode = await collectTransactionCode(
        inputMode: requirement.inputMode,
        expectedLength: requirement.length,
        description: requirement.description
    )
} else {
    transactionCode = nil
}

let outcome = try await wallet.continuePreAuthorizedIssuance(
    sessionID: session.id,
    transactionCode: transactionCode
)

guard case let .stored(_, credentialIDs) = outcome else {
    // Handle deferred, cancelled, or failed issuance as appropriate for the app.
    return
}
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
``Wallet/cancelIssuance(sessionID:)``. For authorization-code issuance, open
the session's authorization URL and pass the callback URI to
``Wallet/continueAuthorizationIssuance(sessionID:callbackURI:)``.
