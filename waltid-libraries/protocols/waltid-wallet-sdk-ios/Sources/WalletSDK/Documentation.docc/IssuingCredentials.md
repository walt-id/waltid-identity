# Issuing Credentials

Start an OpenID4VCI issuance session, collect a transaction code when the
issuer requires one, and continue the session to persist issued credentials in
the wallet.

## Overview

### Configure Required Key Attestations

When issuer metadata advertises `key_attestations_required`, supply a
``KeyAttestationProvider`` through ``WalletConfiguration/keyAttestationProvider``
before creating the wallet. The provider receives the selected public proof key,
current nonce and issuer constraints. Its signed response is checked against the
provider's independently configured verification key before being added to the proof.

Supply this runtime dependency again when recreating a wallet. A missing or invalid
required attestation fails issuance. Issuer trust and evidence supporting the
provider's assurance claims must be established separately.

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
let outcome: IssuanceOutcome
switch session.offer.grant {
case .preAuthorizedCode:
    let transactionCode: String?
    if let requirement = session.offer.transactionCode {
        transactionCode = await collectTransactionCode(
            inputMode: requirement.inputMode,
            expectedLength: requirement.length,
            description: requirement.descriptionText
        )
    } else {
        transactionCode = nil
    }
    outcome = try await wallet.continuePreAuthorizedIssuance(
        sessionID: session.id,
        transactionCode: transactionCode
    )
case .authorizationCode:
    let authorization = try await wallet.beginAuthorizationIssuance(sessionID: session.id)
    await openBrowser(authorization.url)
    let callbackURI = await receiveAuthorizationCallback()
    outcome = try await wallet.continueAuthorizationIssuance(
        sessionID: session.id,
        callbackURI: callbackURI
    )
}

guard case let .stored(_, credentialIDs) = outcome else {
    // Handle deferred, cancelled, or failed issuance as appropriate for the app.
    return
}
```

Issuance uses DPoP consistently for authorization binding, token exchange, and
protected credential requests whenever the authorization server advertises
supported DPoP signing algorithms.

The returned identifiers can be used to refresh local UI or to load credential
metadata through ``Wallet/credentials()``.

```swift
let credentials = try await wallet.credentials()
let issuedCredentials = credentials.filter { credentialIDs.contains($0.id) }
```

> Tip: Collect ``Wallet/events`` while issuance is running if the UI needs
> progress updates for issuer communication, credential storage, or completion.

If the user closes the review without accepting it, call
``Wallet/cancelIssuance(sessionID:)``. Offer-level cancel happens before the
credential response, so there is no `notification_id` yet. Authorization-code issuance creates its
browser URL only after ``Wallet/beginAuthorizationIssuance(sessionID:credentials:)`` is
called following acceptance.

Continuing a session posts OpenID4VCI `credential_accepted` when the issuer
advertised a notification endpoint. After an isolated fetch that left
`storeInWallet` false, call
``Wallet/rejectIssuedCredential(notificationID:accessToken:credentialIssuerBaseURL:notificationEndpoint:eventDescription:)``
to post `credential_deleted` instead of storing the credential.


### Select Configurations and Copies

Omitting selections requests one instance of each offered configuration. The issuer's
``IssuanceOfferPreview/batchSize`` is a maximum, not a requested count. Different
configurations or issuer-granted datasets use separate requests; explicit holder
bindings request copies of one target. The core matches each received credential to
its holder key, independently of response order. Each copy requires a distinct stored
holder key; different IDs for the same public key are rejected before grant redemption.

After review, request new keys inside acceptance or select existing wallet keys:

```swift
guard let configuration = session.offer.credentials.first,
      (session.offer.batchSize ?? 1) >= 2 else { return }
let selections = [try IssuanceCredentialSelection(
    configurationID: configuration.configurationID,
    holders: .newKeys(count: 2)
)]
let result = try await wallet.continuePreAuthorizedIssuance(
    sessionID: session.id,
    transactionCode: transactionCode,
    credentials: selections
)
```

Use `.existing(bindings)` to select existing keys. Newly prepared keys are removed
when preparation or validation fails before acceptance. Once accepted, they remain
wallet-owned through persistence errors and uncertain issuer outcomes. Retries reuse
the accepted bindings, including after restart; changing holders or copy counts then
requires a new session. Preview never generates keys.

For authorization-code issuance, pass the same `credentials` argument to
``Wallet/beginAuthorizationIssuance(sessionID:credentials:)``. The retained session
preserves those bindings through the browser callback and deferred polling.
``IssuanceCredentialSelection/credentialIdentifier`` may select an identifier already
granted by the issuer. Do not invent dataset identifiers or infer them from offer order.

### Keep Partial and Deferred Progress

A failed operation can still contain stored credentials and accepted deferred targets.
``IssuanceFailure/targetFailure`` identifies the stopped target, processing stage and
unattempted targets. A request-stage transport failure may leave remote processing
unknown; do not blindly redeem the original single-use grant again.

```swift
switch result {
case let .stored(_, ids):
    showStoredCredentials(ids)
case let .deferred(_, storedIDs, pending):
    showStoredCredentials(storedIDs)
    showDeferredCredentials(pending)
case let .failed(_, failure, storedIDs, pending):
    showStoredCredentials(storedIDs)
    showDeferredCredentials(pending)
    showIssuanceError(failure)
case .cancelled:
    dismissIssuance()
}
```

After recreating the wallet, recover pending work with ``Wallet/listDeferredIssuance()``.
Wait at least ``DeferredCredential/intervalSeconds`` before polling with
``Wallet/resumeDeferredIssuance(deferredCredentialID:)``. A still-pending outcome retains
the handle and updates the interval. The wallet persists the earliest permitted poll time
across restarts. An early resume returns a deferred outcome with the rounded-up remaining
wait, without contacting the issuer. A received batch awaiting local storage has no polling
interval and can be resumed immediately. Transient failures retain recoverable handles;
terminal denial consumes them. Listing handles exposes no access token or private key.


``IssuanceErrorCode/remoteOutcomeUncertain`` and ``IssuanceErrorCode/storageOutcomeUncertain``
retain ownership; neither permits automatic retry or takeover by another runtime. Preserve
handles and stored IDs. A recoverable local-save handle resumes only missing saves and
never repeats the issuer request. Its configuration ID may be nil for isolated calls.
