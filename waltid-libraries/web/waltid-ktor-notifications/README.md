<div align="center">
<h1>Ktor Notifications</h1>
 <span>by </span><a href="https://walt.id">walt.id</a>
 <p>SSE streams and webhook delivery for session updates in Ktor apps</p>

<a href="https://walt.id/community">
<img src="https://img.shields.io/badge/Join-The Community-blue.svg?style=flat" alt="Join community!" />
</a>
<a href="https://www.linkedin.com/company/walt-id/">
<img src="https://img.shields.io/badge/-LinkedIn-0072b1?style=flat&logo=linkedin" alt="Follow walt_id" />
</a>
  
  <h2>Status</h2>
  <p align="center">
    <img src="https://img.shields.io/badge/🟢%20Actively%20Maintained-success?style=for-the-badge&logo=check-circle" alt="Status: Actively Maintained" />
    <br/>
    <em>This project is being actively maintained by the development team at walt.id.<br />Regular updates, bug fixes, and new features are being added.</em>
  </p>
</div>

## What This Library Contains

Production‑ready utilities to deliver session updates via:
- **Server‑Sent Events (SSE)** — in‑app streaming of `KtorSessionUpdate` per session/target
- **Webhooks** — push JSON payloads to external systems with optional Basic/Bearer authentication

Built on top of the core models from `waltid-ktor-notifications-core`.

## Main Purpose

Give your Ktor service a simple, consistent way to stream real‑time updates to UIs and/or integrate with external systems via webhooks without re‑implementing plumbing.

## Key Concepts

- **Target**: A logical channel (e.g., verification session id) that receives updates
- **Update**: A `KtorSessionUpdate` event with `event` and `session` JSON data
- **SSE Flow**: A hot stream per target (with small replay buffer) your UI can subscribe to
- **Webhook**: Optional, per‑session configuration to POST updates to an external URL

## Assumptions and Dependencies

- Runtime: JVM (delivery implementations provided here are JVM Ktor based)
- Models: `waltid-ktor-notifications-core` (shared types)
- HTTP: Ktor client for webhook delivery

## How to Use This Library

### 1) Publish updates

```kotlin
import id.walt.ktornotifications.KtorNotifications.notifySessionUpdate
import id.walt.ktornotifications.core.KtorSessionUpdate
import id.walt.ktornotifications.core.KtorSessionNotifications
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

val update = KtorSessionUpdate(
    target = "session-123",
    event = "STATUS_CHANGED",
    session = buildJsonObject { put("status", "ACTIVE") }
)

val notifications = KtorSessionNotifications(
    webhook = KtorSessionNotifications.VerificationSessionWebhookNotification(
        url = "https://example.com/webhook",
        bearerToken = "secret-token"
    )
)

// Emits to SSE and (optionally) to webhook
update.notifySessionUpdate(sessionId = "session-123", sessionNotifications = notifications)
```

### 2) Subscribe via SSE (server side wiring)

```kotlin
import id.walt.ktornotifications.SseNotifier
import io.ktor.server.application.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.http.cio.*
import kotlinx.coroutines.flow.collect

routing {
    get("/sse/{sessionId}") {
        val sessionId = call.parameters["sessionId"]!!
        val flow = SseNotifier.getSseFlow(sessionId)

        call.response.cacheControl(CacheControl.NoCache(null))
        call.respondTextWriter(contentType = ContentType.Text.EventStream) {
            flow.collect { update ->
                write("event: ${update.event}\n")
                write("data: ${update.session}\n\n")
                flush()
            }
        }
    }
}
```

Notes:
- SSE uses a per‑target `SharedFlow` with small replay to catch recent events
- Webhook delivery supports Basic and Bearer auth

## Reliable webhook delivery

Webhook events use at-least-once delivery. Calling `notifySessionUpdate` first writes the exact serialized JSON
payload to a local durable outbox and then returns; a background worker performs the HTTP request. Pending records
are resumed when the service starts again. Exhausted and permanently rejected deliveries are retained under the
outbox's `dead-letter` directory and can be inspected or redriven through `WebhookNotifier.deadLetters()` and
`WebhookNotifier.redrive(deliveryId)`.

The default retry policy is five attempts with exponential backoff from 1 to 60 seconds and jitter. Network
failures, HTTP 408, 429, and 5xx responses are retried. The worker honors `Retry-After`. Other non-2xx responses
are dead-lettered immediately. The policy can be overridden per webhook:

```json
{
  "url": "https://example.com/webhook",
  "retry_policy": {
    "max_attempts": 8,
    "initial_backoff_seconds": 2,
    "max_backoff_seconds": 120,
    "backoff_multiplier": 2.0
  }
}
```

The outbox root defaults to the operating system's temporary directory. Production deployments must set the JVM
property `waltid.webhook.delivery.directory` or environment variable `WALTID_WEBHOOK_DELIVERY_DIRECTORY` to put
it on a durable volume. Issuer2 and Verifier2 use separate subdirectories. The file store is intended for a single
service process per directory; replicas must not share an outbox directory.

Outbox records contain the callback URL, payload, and configured Basic/Bearer credentials so they can be delivered
after a restart. Directory and files are owner-only on POSIX file systems. Protect the configured volume and do not
expose or back it up as public application data.

### Receiver contract

Every attempt sends the following headers:

| Header | Meaning |
| --- | --- |
| `Webhook-Id` | Stable unique ID for the logical delivery |
| `Idempotency-Key` | Same value as `Webhook-Id`, for deduplication |
| `Webhook-Timestamp` | Attempt time as Unix epoch seconds |
| `Webhook-Attempt` | One-based attempt number |
| `Content-Digest` | RFC 9530-style SHA-256 digest of the exact JSON body |

A receiver should atomically record `Idempotency-Key` with its business update. If the key is already present, it
must return success without applying the update again. The receiver should also recompute `Content-Digest` before
processing. The digest detects body changes; request authentication and signed callbacks are a separate concern
from delivery idempotency and should be used together with HTTPS.

Events raised by one request are enqueued in order. A failed delivery is retried independently, so receivers must
not assume global ordering between different events or sessions.

## Related Libraries

- `waltid-ktor-notifications-core` — transport‑agnostic models used here

## Join the community

* Connect and get the latest updates: [Discord](https://discord.gg/AW8AgqJthZ) | [Newsletter](https://walt.id/newsletter) | [YouTube](https://www.youtube.com/channel/UCXfOzrv3PIvmur_CmwwmdLA) | [LinkedIn](https://www.linkedin.com/company/walt-id/)
* Get help, request features and report bugs: [GitHub Issues](https://github.com/walt-id/waltid-identity/issues)
* Find more indepth documentation on our [docs site](https://docs.walt.id)

## License

Licensed under the [Apache License, Version 2.0](https://github.com/walt-id/waltid-identity/blob/main/LICENSE)

<div align="center">
<img src="../../../assets/walt-banner.png" alt="walt.id banner" />
</div>
