# Presenting Credentials

Use ``Wallet/previewPresentation(request:)`` and
``Wallet/submitPresentation(request:selectedCredentialOptions:selectedDisclosureOptions:did:runPolicies:)``
to review and answer an OpenID4VP authorization request with credentials from
the local wallet.

## Overview

### Review and Present to a Verifier

Pass the verifier request URL and, when needed, the DID returned by
``Wallet/bootstrap(keyType:didMethod:)``.

```swift
let preview = try await wallet.previewPresentation(request: authorizationRequestURL)

let result = try await wallet.submitPresentation(
    request: authorizationRequestURL,
    selectedCredentialOptions: preview.credentialOptions.map(\.selection),
    did: bootstrap.did
)

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

### Reject a Presentation Request

Use ``Wallet/rejectPresentation(request:error:errorDescription:)`` for
user rejection and handle its continuation exactly like a credential-bearing
response:

```swift
let result = try await wallet.rejectPresentation(
    request: authorizationRequestURL,
    error: .accessDenied,
    errorDescription: "The user declined the request"
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

> Note: ``Wallet/present(request:did:runPolicies:)`` still exists for immediate
> submission after the app has already handled request review and user consent.
> Use the preview and submit APIs when displaying verifier details, credential
> choices, selective disclosures, or transaction data.
