# Observe and refresh credential status

Display local status and request checks without making presentation depend on issuer availability.

## Overview

``Wallet/credentials()`` includes optional status on each ``Credential``.
``Wallet/credentialStatusSnapshot()`` and ``Wallet/credentialStatuses`` read the same local state
without rebuilding credential artwork or making issuer requests. The stream has one latest-value
buffer and also updates when evidence becomes stale. Cancel its consuming task when the screen or
wallet owner ends; cancellation releases the native collector.

```swift
let observation = Task {
    for try await snapshot in await wallet.credentialStatuses {
        renderStatus(snapshot)
    }
}

// App-owned foreground trigger; fresh evidence can be reused.
let results = try await wallet.refreshCredentialStatuses()

// An explicit user action revalidates the selected stored credential.
let manualResults = try await wallet.refreshCredentialStatuses(ids: [credentialID], force: true)

observation.cancel()
try await wallet.close()
```

The rendering function and credential ID above belong to your app. Refresh returns per-credential
completed, unchanged, deferred or removed outcomes; inspect each result instead of assuming that
the whole batch succeeded. Re-read the snapshot or observe the stream for evaluated presentation.
Closing the wallet cancels owned status work and rejects new operations while preserving stored data.

### Display the shared meaning

Use the presentation's kind, issue, freshness, coverage and action. Do not interpret protocol bits in
Swift. No status, unchecked, unsupported, unavailable, stale and partially checked are distinct from
a fresh clear observation. Failure can retain a dated previous result. A clear result does not prove
credential validity, issuer trust or verifier acceptance.

Keep home-card decoration limited to an issue icon. Put the result, checked time and check action in
a standalone Credential status section; expand explanations and partial coverage under Details.
Sharing reviews use cached observations and selected-only warnings. They do not contact issuers.

If your UI records the presented `advisoryRevision`, use
``Wallet/submitStatusReviewedPresentation(previewHandle:selectedCredentialOptions:selectedDisclosureOptions:reviewedStatus:did:runPolicies:paymentConsentRevision:)``
for ordinary reviewed submission. A changed warning throws
``WalletError/credentialStatusReviewChanged`` before delivery. Refresh the local review and ask for
the existing consent again. A timestamp-only update does not invalidate consent. Adverse status is
advisory; existing payment, reader-trust and protocol acceptance rules still apply.

### Own the trigger and persistence

The SDK installs no timer, WorkManager job or BGTask. The app owns foreground/manual triggers,
preferences and lifecycle cancellation. Use the optional snapshot `nextRefreshAt` to schedule the
next IfDue check while the app remains active, and cancel that timer during lock, background or consent.
Apply bounded retry spacing when a due time remains in the past. Avoid refreshing from status getters, credential matching
or system sharing sheets. A failed check retains useful previous observations rather than hiding
the credential.

Managed encrypted persistence stores minimal last-known metadata, never signed lists or history.
The default mobile trust policy restores this as historical, stale evidence after reopening the
wallet. A response with `no-store` is not persisted. Custom credential stores do not gain status
durability automatically. Advanced signer/reference authorization and stable policy identities are
configured in the shared core; the Swift facade does not expose arbitrary policy callbacks.
