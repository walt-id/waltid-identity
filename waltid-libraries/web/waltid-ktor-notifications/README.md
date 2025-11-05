<div align="center">
<h1>Ktor Notifications</h1>
 <span>by </span><a href="https://walt.id">walt.id</a>
 <p>SSE streams and webhook delivery for session updates in Ktor apps<p>

<a href="https://walt.id/community">
<img src="https://img.shields.io/badge/Join-The Community-blue.svg?style=flat" alt="Join community!" />
</a>
<a href="https://www.linkedin.com/company/walt-id/">
<img src="https://img.shields.io/badge/-LinkedIn-0072b1?style=flat&logo=linkedin" alt="Follow walt_id" />
</a>
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

## Related Libraries

- `waltid-ktor-notifications-core` — transport‑agnostic models used here

## Join the community

* Connect and get the latest updates: [Discord](https://discord.gg/AW8AgqJthZ) | [Newsletter](https://walt.id/newsletter) | [YouTube](https://www.youtube.com/channel/UCXfOzrv3PIvmur_CmwwmdLA) | [LinkedIn](https://www.linkedin.com/company/walt-id/)
* Get help, request features and report bugs: [GitHub Issues ](https://github.com/walt-id/waltid-identity/issues)

## License

Licensed under the [Apache License, Version 2.0](https://github.com/walt-id/waltid-identity/blob/main/LICENSE)
