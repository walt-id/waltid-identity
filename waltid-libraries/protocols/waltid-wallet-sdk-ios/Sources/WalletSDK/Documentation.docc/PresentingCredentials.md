# Presenting Credentials

Use ``Wallet/present(request:did:runPolicies:)`` to answer an OpenID4VP
authorization request with credentials from the local wallet.

## Overview

### Present to a Verifier

Pass the verifier request URL and, when needed, the DID returned by
``Wallet/bootstrap(keyType:didMethod:)``.

```swift
let result = try await wallet.present(
    request: authorizationRequestURL,
    did: bootstrap.did,
    runPolicies: true
)

if result.success, let redirect = result.redirectTo {
    await openVerifierRedirect(redirect)
}
```

The returned ``PresentationResult`` includes the success flag, optional verifier
redirect, and optional raw verifier response JSON for diagnostics or host-app
workflow decisions.

> Note: Credential selection and policy behavior are delegated to the wallet
> core. Keep app-specific UX decisions around request acceptance, cancellation,
> and redirect handling in the native iOS layer.
