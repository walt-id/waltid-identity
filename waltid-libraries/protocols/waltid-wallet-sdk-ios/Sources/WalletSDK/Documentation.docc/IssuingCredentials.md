# Issuing Credentials

Start an OpenID4VCI issuance session, collect a transaction code when the
issuer requires one, and continue the session to persist issued credentials in
the wallet.

## Overview

Use ``IssuanceIssuerPreview/display`` and ``IssuanceCredentialPreview/display`` for
issuer-provided names, descriptions and artwork. Their ``MetadataDisplay`` fields
are optional. Use the issuer identifier or credential format as a fallback when
a display name is absent.

### Configure Required Key Attestations

When issuer metadata advertises `key_attestations_required`, supply a
``KeyAttestationProvider`` through ``WalletConfiguration/Issuance/keyAttestationProvider``
before creating the wallet. The provider receives the selected public proof key,
current nonce and issuer constraints. Its signed response is checked against the
provider's independently configured verification key before being added to the proof.

Supply this runtime dependency again when recreating a wallet. A missing or invalid
required attestation fails issuance. Issuer trust and evidence supporting the
provider's assurance claims must be established separately.

For issuer-specific selection, the same property accepts a
``KeyAttestationProviderResolver``. Its resolver returns a provider with a fixed
verification key or `nil` for an unsupported issuer. The wallet only resolves when
attestation is required, then retains that provider throughout the proof collection.
Existing fixed providers use the default resolver implementation.

### Accept the selected grant

Start with the offer URL. Show its issuer and credential metadata, then handle the grant the wallet returned. The host supplies the confirmation UI, a registered browser client and the callback URL. An identified pre-authorized client can pass `clientID` to acceptance.


```swift
let session = try await wallet.startIssuance(.init(offer: offerURL))
guard await ui.accept(session.offer) else {
    _ = try await wallet.cancelIssuance(sessionID: session.id)
    return
}
let outcome: IssuanceOutcome
switch session.grant {
case .preAuthorized(let grant):
    var code: String?
    if let requirement = session.offer.transactionCode { code = await ui.code(requirement) }
    outcome = try await wallet.accept(grant, transactionCode: code)
case .authorizationCode(let grant):
    let authorization = try await wallet.beginAuthorization(grant, registration: registration)
    if let callback = try await ui.browser(authorization.request.url) {
        outcome = try await authorization.complete(callback)
    } else {
        outcome = try await wallet.cancelIssuance(sessionID: session.id)
    }
}
ui.received(outcome)
```

Save the session ID during offer review. After reopening the same wallet, call `resumeIssuance(_:)` to redisplay the offer while it is still available.

The browser authorization ID can be saved before opening the browser and restored for `continueAuthorizationIssuance(sessionID:callbackURI:)` after reopening the wallet. Supply the exact callback URI registered with the authorization server.

### Select configurations and copies

Pass `credentials` to acceptance or browser authorization when the holder selects credential configurations or copies. Each selection identifies an offered configuration and its holder keys. An optional credential identifier selects a dataset granted by the issuer. The SDK checks the advertised batch limit before generating new keys. Omission uses the wallet's default selection.

### Configure issuer authentication

The default receipt operation parses and stores credentials with the expected configuration and holder binding. To require issuer authentication before storage, configure receipt authorities supplied independently by the host. Kotlin accepts a `CredentialReceiptTrust` in the issuance configuration. Swift accepts `receiptAuthorities` in `WalletConfiguration.Issuance`.

Each authority binds an exact OpenID4VCI credential issuer identifier and format to a P-256 public JWK. The SD-JWT VC profile checks the ES256 issuer signature, signed issuer, credential type, disclosures and validity bounds. The mdoc profile checks ES256 issuer authentication, the document signer key and certificate profile, MSO validity, document type and item digests. Provision rollover keys alongside the existing key. An empty configured list accepts no credentials.

A trust failure stops the affected issuance target before storage or system registration. Swift reports this rejection as `.issuerTrust`. Earlier stored targets remain visible in the result. The same policy applies to deferred receipt and local-save recovery. Existing credentials stay in the wallet. With the profile enabled, system registration requires recorded receipt origin and successful verification with a currently configured key.

### Keep Partial and Deferred Progress

A failed operation can still contain stored credentials and accepted deferred targets.
``IssuanceFailure/targetFailure`` identifies the stopped target, processing stage and
unattempted targets. Keep Accept available for the same offer only when
``IssuanceFailure/canRetryOffer`` is true, for example when the user can correct a
rejected transaction code. Losing the token response closes the offer and leaves
the issuer's outcome unknown.

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
``Wallet/resumeDeferredIssuance(_:)``. A still-pending outcome retains
the handle and updates the interval. The wallet persists the earliest permitted poll time
across restarts. An early resume returns a deferred outcome with the rounded-up remaining
wait, without contacting the issuer. A received batch awaiting local storage has no polling
interval and can be resumed immediately. Transient failures retain recoverable handles;
terminal denial consumes them. Listing handles exposes no access token or private key.


``IssuanceErrorCode/remoteOutcomeUncertain`` and ``IssuanceErrorCode/storageOutcomeUncertain``
retain ownership; neither permits automatic retry or takeover by another runtime. Preserve
handles and stored IDs. A recoverable local-save handle resumes only missing saves and
never repeats the issuer request. Its configuration ID may be nil for isolated calls.
