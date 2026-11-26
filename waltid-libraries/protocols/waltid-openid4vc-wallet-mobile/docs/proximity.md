# In-person proximity presentation

Proximity presentation is a distinct session API rather than an OpenID4VP URL
flow. Query capabilities without creating ephemeral keys or radio resources,
then create one single-use session and render its authoritative state:

<!-- doc-snippet:start kotlin-proximity-session -->
```kotlin
val configuration = ProximityConfiguration()
val capabilities = wallet.proximityPresentationCapabilities(configuration)
showUnavailableMethods(capabilities)
val session = wallet.startProximityPresentation(configuration)

try {
    session.state.collect { state ->
        when (state) {
            is ProximityState.CheckingPrerequisites -> showUnavailableMethods(state.capabilities)
            is ProximityState.EngagementReady -> showEngagements(state.engagements)
            is ProximityState.ReviewRequired -> showProximityReview(state.review)
            is ProximityState.PreparationRequired -> showPreparationReview(state.plan, state.reason)
            is ProximityState.AuthorizingHolderKey -> showHolderAuthorization(state.authorization)
            is ProximityState.Completed -> showCompletion(state.exchanges, state.declined)
            is ProximityState.NoData -> showNoData(state.exchange, state.unsatisfiedRequest)
            is ProximityState.Failed -> showProximityError(state.error)
            ProximityState.Cancelled -> showCancelled()
            is ProximityState.Preparing,
            is ProximityState.Connecting,
            is ProximityState.AwaitingRequest,
            is ProximityState.SendingResponse,
            is ProximityState.AwaitingNextRequest,
            is ProximityState.Terminating -> showProximityProgress(state)
        }
    }
} finally {
    withContext(NonCancellable) { session.close() }
}
```
<!-- doc-snippet:end kotlin-proximity-session -->

The `show*` functions are application UI callbacks. Collect in a screen-owned
coroutine and cancel it on leaving the screen; the `finally` block releases the
session even during cancellation. Import `NonCancellable` and `withContext` from
`kotlinx.coroutines`. Handle the capability snapshot before starting; remediations
and protected-key authorization remain explicit host actions. A `StateFlow` does
not complete automatically on a terminal state.

### Approving before connection

`ProximityConfiguration.approval` defaults to `AskEachTime`. `PrepareBeforeSharing`
authenticates the reader and collects a request, declines that connection without
credential disclosure, then emits terminal `PreparationRequired` after transport
cleanup. Close that session and show the plan's reader, credential choices, exact
fields, declared retention, purpose and application authorizations. Require a
credential choice when several match; honour `requiredElements` (including a
requested mDL portrait). The existing wire-level portrait-denial rule remains in force.

Only after the holder deliberately approves that review, call `plan.approve(submission)`.
A `Prepared` result supplies an opaque `sharing` for a **new** configuration with
`approval = ProximityApproval.Prepared(sharing)`. Do not dispatch `Approve` to the
old connection. `Rejected` gives a display-safe error and requires another review
or reader connection. Plans are recent, in-memory requests that expire after ten
minutes; they are not authorizations or persistent reader templates.

An approval handler can return the new session (or no session after rejection):

<!-- doc-snippet:start kotlin-proximity-preparation -->
```kotlin
return when (val result = plan.approve(submission)) {
    is ProximityPreparationResult.Prepared -> wallet.startProximityPresentation(
        configuration.copy(approval = ProximityApproval.Prepared(result.sharing))
    )
    is ProximityPreparationResult.Rejected -> {
        showProximityError(result.error)
        null
    }
}
```
<!-- doc-snippet:end kotlin-proximity-preparation -->

A prepared approval lasts 60 seconds (monotonic time) and is claimed once, before
prerequisites or radios start. It is bound to one wallet, the exact authenticated
reader leaf certificate, request/profile, selected credential contents, fields,
retention, purpose and application authorizations. Fresh reader, credential,
revocation and holder-key checks still run. A changed request falls back to
`ReviewRequired(PreparedSharingChanged)` on interactive routes, or ends with a new
`PreparationRequired` on a noninteractive route. It never expands an earlier
approval. Matching requests proceed through normal protected-key authorization;
advance consent does not bypass device authentication or guarantee that an OS
prompt can run during NFC emulation.

Preparation requires one named, authenticated, trusted reader covering all
requested documents. Unnamed, unsigned, untrusted or multiple-reader requests
cannot produce a plan; their ordinary interactive policies remain unchanged.
`NfcHostPlatformAdapter.isUserInteractionBlocked` reports the host's current modal
presentation state. The bundled iOS adapters report `true` while the Core NFC
system sheet is presenting. During that interval, even `AskEachTime` first
collects and declines a trusted request so review can happen after the sheet
closes, followed by a new connection. Conventional NFC-to-Bluetooth handover
permits interactive review after NFC closes. NFC v2 may retain its system sheet
while an alternate bearer connects or carries messages, so the SDK checks the
current host presentation state for each request.

Persist a mode preference only. Keep a plan/approval inside the active journey,
call `sharing.revoke()` and `session.close()` on cancellation or genuine backgrounding,
and exempt only the lifecycle transition owned by the active Core NFC sheet.
Every retry requires a fresh deliberate approval; repeated exchanges cannot use
one prepared approval. `remainingSeconds` is suitable for a ready-screen countdown.
`Completed.receipt` records the most recent locally completed approved selection
and its approval timing; it neither authorizes reuse nor confirms reader verification.

Kotlin and Swift proximity types use the `Proximity` prefix.
`ProximityRetrievalOptions` selects conventional retrieval bearers and defaults to BLE.

The default configuration selects QR engagement and BLE retrieval. NFC supports
conventional static/negotiated handover, conventional retrieval, and the explicit
provisional NFCv2 session variant. A retrieval plan can select Wi-Fi Aware with
`wifiAware = true`, including a Wi-Fi-only QR plan with `bluetoothLowEnergy = null`.
Android implements the mandatory shared-key path; iOS reports it unimplemented.
Runtime observations distinguish `NotChecked`, `Available`, and
`Unavailable`; selection is independent. Startability follows each selected
route's own retrieval plan, so an unavailable optional bearer cannot block a
usable route or lend an unrelated bearer to another route.

```kotlin
val nfcConfiguration = ProximityConfiguration(
    session = ProximitySessionConfiguration.ConventionalNfc(
        handover = ProximityNfcHandover.Negotiated,
        retrieval = ProximityRetrievalOptions(
            nfc = ProximityNfcRetrievalConfiguration(),
        ),
        qrFallback = ProximityRetrievalOptions(),
    ),
)
```

The session variant owns engagement and compatible retrieval together. Optional
QR fallback requires a nonempty conventional plan. Shared BLE role/policy and
conventional NFC length limits must match across routes. The provisional NFCv2
variant always includes same-channel retrieval and owns its distinct command
limit; the ISO/IEC 18013-5:2021 profile rejects that variant. Its optional hybrid
Wi-Fi bearer is selected with `wifiAware = true`; QR fallback remains a separate
nonempty conventional plan. The mandatory NCS-SK-128 cipher is enforced by the
transport and has no redundant public policy switch.

When QR and NFC both select Wi-Fi Aware, the SDK generates independent ephemeral
keys and publishers. Each service name follows the required EDeviceKeyBytes
derivation, and the selected connection carries its own key, exact engagement,
and handover into session crypto. Static NFC dual-role BLE uses the shared UUID
that its carrier format can encode; QR retains distinct role UUIDs.

Device signature is the default holder-authentication policy. Applications may
require MAC or choose an explicit pre-review preference with
`deviceAuthenticationPolicy`; the selected method is shown on each credential
option, bound into the immutable review, and never changed after consent. The
pinned EUDI profile currently requires device signature.

`session.connectedRoute` reports the winning engagement and actual connected bearer. It remains
available after the brief connecting state, including during review and after completion. Configured
or advertised methods alone do not establish which route a reader used.

Terminal errors expose display-safe `code`, `message`, and `remediationActions`. For example, NFC
access not accepted maps to application settings. Perform that host action, wait for the application
to return, close the failed session, and create a new session. `ReportRemediation` belongs only to the
active prerequisite loop. The radio-independent engine retains unexpected exception causes on its
failure result for diagnostics; raw exception text must never be rendered as presentation content.

Host applications perform permission or settings effects named by
`capabilities.remediationActions`, report the privacy-safe outcome with
`ProximityAction.ReportRemediation`, and let the SDK re-check the
platform. Approve and decline require the `reviewId` from the displayed review.
A successful decision consumes that identity once; stale, duplicate, and
cross-session actions are rejected. Invalid submissions leave the review open
for correction. Acceptance records a decision; signing or transmission can still
fail.

The session owns configuration and nested review/profile/selection values.
Changing caller collections or exported projections cannot alter accepted claims
or continuation. The accepted choice has its own retained binding, while the
preview digest still represents the request and fresh wallet/profile state.
Only the ordered session owner publishes state, so delayed observations cannot
replace a newer review or a terminal state. Error `recovery` distinguishes
prerequisite retry in an active session from starting a new session after failure.

Review approval uses only the credential and element choices in the displayed
review. The SDK revalidates credential, holder key, reader
trust, status, disclosure, and application-profile state before sending.
Multiple reader-authentication statements retain their independent
`authenticationIndex`, and holder-key authorization is reported per document
request so mixed signature/MAC responses cannot be collapsed into one prompt.

Android Wi-Fi Aware remediation distinguishes nearby-Wi-Fi permission,
target-37 local-network permission, Wi-Fi/radio state, exhausted resources, and
unsupported API/feature/cipher states. This draft implements only NCS-SK-128 and
the mandatory 2.4 GHz NAN baseline. NCS-PK-2WDH-128, optional 5 GHz advertisement,
and physical independent-reader qualification remain explicit follow-ups; this
transport adds no server-retrieval or internet path.

Reader authentication validity does not establish reader trust. To require a
trusted reader, provision Reader CA certificates out of band and pass the shared
evaluator explicitly:

```kotlin
val readerTrust = ProximityConfiguredReaderTrustEvaluator(
    ProximityReaderTrustConfiguration(
        trustAnchors = listOf(
            ProximityReaderTrustAnchor(
                certificateDerBase64Url = readerCaDerBase64Url,
                displayName = "Example reader authority",
            )
        ),
        revocationPolicy = ProximityReaderRevocationPolicy.Check(
            applicationRevocationEvaluator
        ),
    )
)
val configuration = ProximityConfiguration(
    readerPolicy = ProximityReaderPolicy.RequireTrusted,
    readerTrustEvaluator = readerTrust,
)
```

The SDK performs certificate profile, time, and explicit-anchor path validation,
but performs no hidden network lookup and ships no reader trust list. A root sent
by the reader is only path evidence; it is never trusted unless the application
provisioned the same certificate. Optional RICAL providers use separate explicit
provider roots, signer policy, revocation, and constraint boundaries. Demo apps
can pass a named test anchor through the same configuration constructor, but
test anchors must not become production defaults.

For CRL checking, configure the shared verifier with public certificates from the
reader's issuer path and application-owned transport:

```kotlin
val applicationRevocationEvaluator = ProximityCrlRevocationEvaluator(
    issuerCertificatesDerBase64Url = listOf(readerCaDerBase64Url),
    scope = ProximityCrlScope.ValidatedPath,
    fetcher = ProximityCrlFetcher { url, maximumBytes ->
        // Apply application destination, redirect, timeout, and byte-limit policy.
        // Return Available with unpadded Base64URL DER, or Unavailable.
        applicationCrlClient.fetch(url, maximumBytes)
    },
)
```

Install this evaluator as `ProximityReaderTrustConfiguration.revocationPolicy =
ProximityReaderRevocationPolicy.Check(applicationRevocationEvaluator)`. `ValidatedPath`
checks the reader and non-anchor intermediates on the exact validated direct or RICAL
path, stopping at its selected configured anchor. Calling this scope with raw evidence
alone returns `Indeterminate`. The explicit `ReaderCertificateAndIssuingAuthorities`
policy retains additional status checking through the terminal self-signed authority;
that authority must advertise an applicable distribution point.
A current, authenticated complete CRL must cover every checked certificate;
verified revocation of either the reader or an authority prevents disclosure.
The issuer list supplies path material and does not add trust anchors.

The supported CRL profile is direct X.509 v2 with authority key identifier, CRL
number, `thisUpdate` and `nextUpdate`, and ECDSA or RSA PKCS#1 signatures using
SHA-256/384/512. CRLs are limited to 2 MiB and 10,000 entries. Delta, indirect,
partitioned, stale or unverifiable CRLs remain indeterminate. Missing evidence
never becomes `Good`. The SDK performs no implicit fetch and keeps no cache
between evaluations. The default revocation policy remains `NotChecked`; demo
trust settings do not configure a CRL client. OCSP needs a separate request and
signed-response verifier and is not implemented by this evaluator.

Configuration snapshots detach collection data while retaining provider/evaluator service
references. Providers and revocation sources are queried at evaluation time. Persisted
settings decoding checks structure; import checks current CA usage and RICAL material;
session evaluation establishes current trust. `applyTo` replaces the supplied trust evaluator,
so applications needing CRL/IACA/custom policy must configure that evaluator after applying
holder-managed settings. Imports do not add network services or infer issuer roles.

Wallet applications that let holders manage this policy can persist a canonical
`ProximityReaderTrustSettings` snapshot. Use
`ProximityReaderTrustSettingsCodec.prepareImport` to validate and
preview public trust material before saving the returned settings. The importer
accepts DER or certificate-only PEM Reader CAs and versioned walt.id JSON trust
bundles containing named Reader CAs and static signed RICAL configuration. It
rejects private keys, PKCS#12/PFX files, unknown JSON fields or versions,
duplicates, non-CA or expired anchors, invalid RICAL signatures and paths, and
files larger than 1 MiB. The codec performs no persistence or network access.

The version-1 bundle shape is deliberately narrow; every encoded value is
unpadded Base64URL and unknown fields are rejected:

```json
{
  "version": 1,
  "type": "org.waltid.wallet.reader-trust",
  "readerAuthorities": [
    {
      "name": "Example Reader CA",
      "certificateDerBase64Url": "<public DER certificate>"
    }
  ],
  "ricalProviders": [
    {
      "providerId": "<RICAL provider identifier>",
      "acceptedTypes": ["<RICAL type>"],
      "providerTrustAnchorsDerBase64Url": ["<public DER certificate>"],
      "acceptedSignerCertificatePolicyOids": ["<certificate-policy OID>"],
      "establishReaderTrust": false,
      "signedRicalBase64Url": "<untagged COSE_Sign1>"
    }
  ]
}
```

Read one immutable settings snapshot when a new session starts and apply it with
`ProximityReaderTrustSettings.applyTo`. Settings changed during a
session therefore affect only the next session. The demo wallets expose this as
**Settings → Credential Sharing → Reader Authentication** and store only the
canonical public configuration in app-private storage.

Only one proximity session may be active per wallet. Always call `close()` when
the journey leaves the screen; closing and cancellation are idempotent and every
new session creates fresh engagement identifiers and ephemeral key material.

An unmatched request ends in `ProximityState.NoData` without holder
consent or key authorization. Its `exchange` identifies the final request;
earlier exchanges may already have shared approved data. Its required
`state.unsatisfiedRequest` contains the original requested documents, their zero-based indices,
namespace/element identifiers and reader-declared retention intent. It contains all requested
documents, including individually satisfiable ones, without wallet values or credential identifiers.
Reader authentication was not evaluated on this path. Holder decline and sharing failures
use their existing completion/failure states.
The summary remains available after session cleanup; the SDK does not persist a history entry.

Use
`ProximityReview.readerAuthenticationSummary` for the collapsed reader summary.
The shared SDK accounts for whole-request coverage while preserving malformed,
invalid, and revoked authentication warnings. Raw authentication entries remain
available for detailed inspection.
