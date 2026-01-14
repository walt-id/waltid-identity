<div align="center">
<h1>Ktor Notifications - Core</h1>
 <span>by </span><a href="https://walt.id">walt.id</a>
 <p>Lightweight, multiplatform core models for server notifications in Ktor apps</p>

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

Minimal, shared data models to describe server‑side session notifications and updates in Ktor‑based projects. These models are used by higher‑level notifiers (SSE, webhooks) and can be reused across JVM, JS, and iOS targets.

- `KtorSessionNotifications` — notification configuration (e.g., webhook target incl. auth)
- `KtorSessionUpdate` — a typed event describing an update for a specific session/target

## Main Purpose

Provide a stable, serialization‑friendly contract for emitting and delivering notifications, without imposing any concrete transport. Use together with the companion library `waltid-ktor-notifications` for ready‑made SSE and webhook delivery.

## Key Concepts

- **Session update**: What happened (`event`) and the updated `session` payload for a logical `target` (e.g., verification session id)
- **Notification config**: Where to additionally send updates (e.g., a webhook URL with auth)
- **Transport‑agnostic**: These models don’t depend on specific protocols; they serialize cleanly via Kotlinx Serialization

## Assumptions and Dependencies

- Multiplatform: JVM, JavaScript, iOS (enable iOS by setting Gradle property `enableIosBuild=true`)
- Serialization: Kotlinx Serialization JSON
- Optional transports (SSE/Webhook) live in `waltid-ktor-notifications`

## How to Use This Library

1) Add the dependency and use the types in your application/service layer
2) Emit or forward `KtorSessionUpdate` instances to your chosen transport implementation

```kotlin
import id.walt.ktornotifications.core.KtorSessionUpdate
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

val update = KtorSessionUpdate(
    target = "session-123",
    event = "VERIFICATION_COMPLETED",
    session = buildJsonObject {
        put("status", "SUCCESSFUL")
        put("timestamp", "2025-11-05T12:00:00Z")
    }
)
```

Use together with the higher‑level delivery library:
- `waltid-ktor-notifications` (SSE streams and optional webhooks)

## Related Libraries

- `waltid-ktor-notifications` — SSE and webhook delivery helpers for these models

## Join the community

* Connect and get the latest updates: [Discord](https://discord.gg/AW8AgqJthZ) | [Newsletter](https://walt.id/newsletter) | [YouTube](https://www.youtube.com/channel/UCXfOzrv3PIvmur_CmwwmdLA) | [LinkedIn](https://www.linkedin.com/company/walt-id/)
* Get help, request features and report bugs: [GitHub Issues](https://github.com/walt-id/waltid-identity/issues)
* Find more indepth documentation on our [docs site](https://docs.walt.id)

## License

Licensed under the [Apache License, Version 2.0](https://github.com/walt-id/waltid-identity/blob/main/LICENSE)

<div align="center">
<img src="../../../assets/walt-banner.png" alt="walt.id banner" />
</div>
