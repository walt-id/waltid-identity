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

if result.success, let redirect = result.redirectTo {
    await openVerifierRedirect(redirect)
}
```

The returned ``PresentationResult`` includes the success flag, optional verifier
redirect, and optional raw verifier response JSON for diagnostics or host-app
workflow decisions.

> Note: ``Wallet/present(request:did:runPolicies:)`` still exists for immediate
> submission after the app has already handled request review and user consent.
> Use the preview and submit APIs when displaying verifier details, credential
> choices, selective disclosures, or transaction data.
