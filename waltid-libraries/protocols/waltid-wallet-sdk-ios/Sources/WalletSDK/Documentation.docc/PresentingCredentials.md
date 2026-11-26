# Presenting Credentials

Resolve the request with ``Wallet/previewPresentation(request:)``. Resolution can contact the verifier.
An invalid preview retains a safe error destination. The app can dismiss it locally or return the
classified error. A ready preview supplies candidate credentials and claim paths.

## Overview

### Review the effective claims

Start with the ready `preview` and its `previewHandle`. The host's `ui.choose` selects credentials and
claim paths. `ui.confirm` shows the effective claims, the selected credentials' status warnings and any
payment instructions, and waits for the holder. A nil disclosure selection enables request-matched
defaults. An explicit list captures the user's selection. Review again whenever that selection changes.

```swift
let handle = preview.previewHandle
let selection = await ui.choose(preview)
let review = try await wallet.reviewPresentation(previewHandle: handle, selection: selection)
if await ui.confirm(review.claims, review.statuses, review.payment) {
    ui.deliver(try await wallet.submitPresentation(review))
} else {
    try await wallet.discardPresentationPreview(handle)
}
```

For SD-JWT, selecting an ancestor disclosure can include clear-text children. Render the actual claims
in the review, keeping each query's contribution separate. For mdoc, a location marked `isBinary` carries
an unpadded base64url string. Use source paths for controls and received paths for rendered data.

A new review supersedes the previous one. Submit the wallet-issued review after confirmation. Status or selection changes can require another review before delivery. A prepared URL or form needs host delivery. A transmitted result contains the verifier outcome.

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
