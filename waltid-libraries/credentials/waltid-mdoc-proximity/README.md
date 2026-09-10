<div align="center">
  <h1>Kotlin Multiplatform mdoc proximity library</h1>
  <p>by <a href="https://walt.id">walt.id</a></p>
  <p>Shared holder protocol for local presentation of ISO mdoc credentials.</p>
</div>

## Overview

`waltid-mdoc-proximity` implements the radio-independent holder session: device
engagement, session encryption, request matching, reader authentication, consent
binding and response construction. It uses the existing mdoc, COSE and crypto
libraries and has no wallet database, UI, Android or Apple radio dependency.

The baseline is [ISO/IEC 18013-5:2021](https://www.iso.org/standard/69084.html).
Edition-2 draft features use an explicit versioned profile. Implemented protocol
paths and deterministic tests do not establish certification or interoperability
with every reader.

## Getting started

Within the coordinated source build, add the module to the consuming source set:

```kotlin
commonMain.dependencies {
    implementation(project(":waltid-libraries:credentials:waltid-mdoc-proximity"))
}
```

Use this lower-level engine directly when implementing a wallet integration or a
protocol harness with its own credential source and consent boundary:

```kotlin
import id.walt.crypto2.keys.Key
import id.walt.mdoc.proximity.*

suspend fun runHolderSession(
    ephemeralDeviceKey: Key,
    sources: List<MdocEngagementSource>,
    requestProcessor: MdocHolderRequestProcessor,
    consentHandler: MdocConsentHandler,
    context: EngagementContext,
    capabilities: MdocSessionCapabilities,
): MdocHolderSessionResult {
    val engine = MdocHolderProtocolEngine(
        eDeviceKey = ephemeralDeviceKey,
        engagementSources = sources,
        requestProcessor = requestProcessor,
        consentHandler = consentHandler,
        engagementContext = context,
        capabilities = capabilities,
    )
    return engine.run()
}
```

The caller supplies a fresh supported ephemeral key, compatible engagement
sources and matching context/capability profiles. Collect `engine.state` in the
same lifecycle scope when rendering protocol progress. Run an engine once;
cancel its coroutine when the host ends an active session. Do not reuse session
keys or engagement material for a later presentation.

`ProximityConnection.awaitClosed()` reports the first observed connection closure
without receiving messages or starting another inactivity timer. The engine races
this signal against request preparation, consent and response authorization. Loss
of the selected connection cancels that application work and produces a transport
failure; a disconnected reader cannot leave an actionable review waiting for a tap.
Consent handlers and request processors must cooperate with coroutine cancellation.
Cancelling a closure observer does not close the connection. Hybrid adapters report
closure only when no bearer can continue.

A valid request with no returnable data sends an encrypted empty response and
ends in `MdocHolderSessionResult.NoData` without requesting consent or resolving
holder keys. `NoData.exchange` identifies that final request; an earlier exchange
may already have shared approved data. Holder decline remains a distinct outcome.

## Integration boundaries

| Component | Responsibility |
|---|---|
| `MdocEngagementSource` | Prepare QR or NFC engagement and compatible retrieval providers |
| `MdocHolderRequestProcessor` | Match available credentials, build the preview and resolve an approved submission |
| `MdocConsentHandler` | Return an explicit holder decision for the immutable review |
| `MdocHolderProtocolEngine` | Own encrypted exchange, repeated requests, limits, timeouts and terminal cleanup |

A request can have multiple matching credentials. The wallet selects a credential
for each requested document and then chooses fields from that credential. The
engine binds the submission to the reviewed request and rejects changed or stale
choices. A valid reader signature is evidence of authentication; trust depends on
the application's configured Reader CA and policy. Neither replaces holder consent.

Reader authentication is scoped to `Document(index)` or `WholeRequest`. Only a
valid result carries verified evidence and a trust decision. Public previews and
capabilities retain owned collections and return detached views; exact signed
bytes and session-transcript encodings remain authoritative.

Wallet-owned application profiles can contribute validated, display-safe
authorization details and an opaque result digest. The engine binds those values
through review and submission without interpreting application-specific extensions.
Platform adapters keep ISO parsing, cryptography, credential choice and disclosure
decisions out of radio code.

## Testing and related modules

Deterministic loopback fixtures in `src/commonTestFixtures/kotlin` are included
only by consumer test source sets. They do not add a production transport kind.
From the unified-build root, run the focused engine suite with:

```bash
./gradlew :waltid-libraries:credentials:waltid-mdoc-proximity:jvmTest
```

- [mdoc data model and issuance](../waltid-mdoc-credentials2/README.md)
