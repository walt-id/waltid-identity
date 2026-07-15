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

guard result.success else {
    showPresentationFailure()
    return
}

if let redirect = result.redirectTo ?? result.responseURL {
    await openVerifierRedirect(redirect)
} else if let html = result.formPostHTML {
    await renderAndSubmitFormPost(html)
} else {
    showPresentationComplete()
}
```

The returned ``PresentationResult`` includes the protocol success flag, optional
front-channel response artifacts, and optional raw verifier response JSON. A
successful result means the SDK either transmitted the response or prepared it
for the host. When `responseURL` or `formPostHTML` is present, keep the operation
pending until the host opens the URL or loads the self-submitting HTML and the
navigation succeeds. Surface handoff failures so the user can retry.

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

guard result.success else {
    showRejectionFailure()
    return
}

if let redirect = result.redirectTo ?? result.responseURL {
    await openVerifierRedirect(redirect)
} else if let html = result.formPostHTML {
    await renderAndSubmitFormPost(html)
} else {
    showRejectionComplete()
}
```

> Note: ``Wallet/present(request:did:runPolicies:)`` still exists for immediate
> submission after the app has already handled request review and user consent.
> Use the preview and submit APIs when displaying verifier details, credential
> choices, selective disclosures, or transaction data.
