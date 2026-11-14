# Presenting Credentials

Use ``Wallet/previewPresentation(request:)`` and
``Wallet/submitPresentation(previewHandle:selectedCredentialOptions:selectedDisclosureOptions:did:runPolicies:paymentConsentRevision:)``
to review and answer an OpenID4VP authorization request with credentials from
the local wallet.

## Overview

### Review and Present to a Verifier

Pass the verifier request URL and, when needed, the DID returned by
``SigningIdentityManager/initialize()``.

```swift
let previewResult = try await wallet.previewPresentation(request: authorizationRequestURL)

let result: PresentationResult
switch previewResult {
case .ready(let preview):
    if let verifierName = preview.request.verifierMetadata?.display?.name {
        showVerifierName(verifierName)
    }
    switch preview.request.responseEncryption {
    case .notRequired:
        showPlainResponseNotice()
    case .required(let details):
        showEncryptedResponseNotice(
            algorithm: details.keyManagementAlgorithm,
            contentEncryption: details.contentEncryptionAlgorithm,
            verifierKeyID: details.verifierKeyID,
            verifierKeyThumbprint: details.verifierKeyThumbprint
        )
    }
    result = try await wallet.submitPresentation(
        previewHandle: preview.previewHandle,
        selectedCredentialOptions: preview.credentialOptions.map(\.selection),
        did: identity.did
    )
case .invalid(let error):
    showRequestError(error)
    // After user interaction, either dismiss locally or notify the verifier.
    result = try await wallet.rejectPresentation(previewHandle: error.previewHandle)
}

switch result {
case .transmitted(.succeeded(_, let redirectURL)):
    if let redirectURL {
        await openVerifierRedirect(redirectURL)
    } else {
        showPresentationComplete()
    }
case .prepared(.openURL(let url)):
    await openVerifierRedirect(url)
case .prepared(.submitForm(let html)):
    await renderAndSubmitFormPost(html)
case .transmitted(.failed):
    showPresentationFailure()
}
```

The returned ``PresentationResult`` identifies the host's next action without
combining mutually exclusive response artifacts. Keep `.prepared(.openURL)` and
`.prepared(.submitForm)` operations pending until the handoff or navigation
succeeds, and surface delivery failures so the user can retry.

### Review the Effective Claims

After selecting credentials and disclosures, call `reviewPresentation` on the retained preview handle.
The returned claims include clear-text data carried by selected ancestor disclosures. Preserve each query's
contribution and use source paths for labels/controls, received paths for its compacted presentation data.
Do not fill withheld values from the stored credential. Keep optional candidate values visibly separate.

```swift
let information = try await wallet.reviewPresentation(
    previewHandle: preview.previewHandle,
    selectedCredentialOptions: selectedCredentials,
    selectedDisclosureOptions: selectedDisclosures,
    did: identity.did
)
// Display information.credentials; recompute whenever these choices change.
// Prepare any payment consent sequentially on the same preview handle.
let result = try await wallet.submitPresentation(
    previewHandle: preview.previewHandle,
    selectedCredentialOptions: selectedCredentials,
    selectedDisclosureOptions: selectedDisclosures,
    did: identity.did,
    paymentConsentRevision: paymentConsent?.revision
)
```

Disable confirmation while preparing or after an error. Discard late results after selections change,
and submit the same selection snapshot that was displayed. The SDK does not bind claim review to
submission; apps own this consistency guard. Reviewing does not sign, refetch the authorization
request, or send a presentation. Payment consent keeps its independent revision checks.

### Reject a Presentation Request

Use ``Wallet/rejectPresentation(previewHandle:error:errorDescription:)`` after a preview.
For a valid request, omitting `error` sends `access_denied`. For an invalid preview,
omitting `error` sends the error classified by the wallet. Handle its continuation
exactly like a credential-bearing response:

```swift
let result = try await wallet.rejectPresentation(
    previewHandle: previewHandle
)

switch result {
case .transmitted(.succeeded(_, let redirectURL)):
    if let redirectURL {
        await openVerifierRedirect(redirectURL)
    } else {
        showRejectionComplete()
    }
case .prepared(.openURL(let url)):
    await openVerifierRedirect(url)
case .prepared(.submitForm(let html)):
    await renderAndSubmitFormPost(html)
case .transmitted(.failed):
    showRejectionFailure()
}
```

``PresentationResponseEncryption`` describes protection of the authorization
response selected for the reviewed request. Its verifier key identifier and
thumbprint are technical identity details, not evidence that the verifier is trusted.

Submission and ``Wallet/rejectPresentation(previewHandle:error:errorDescription:)``
consume the selected preview. A local dismissal should instead call
``Wallet/discardPresentationPreview(_:)``.

> Note: ``Wallet/present(request:did:runPolicies:)`` still exists for immediate
> submission after the app has already handled request review and user consent.
> Use the preview and submit APIs when displaying verifier details, credential
> choices, selective disclosures, or transaction data.
