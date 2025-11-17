<div align="center">
<h1>walt.id Web Libraries</h1>
 <span>by </span><a href="https://walt.id">walt.id</a>
 <p>Libraries for web server functionality and notifications</p>

<a href="https://walt.id/community">
<img src="https://img.shields.io/badge/Join-The Community-blue.svg?style=flat" alt="Join community!" />
</a>
<a href="https://www.linkedin.com/company/walt-id/">
<img src="https://img.shields.io/badge/-LinkedIn-0072b1?style=flat&logo=linkedin" alt="Follow walt_id" />
</a>
</div>

## Overview

This directory contains libraries for web server functionality, specifically focused on session notifications and real-time updates for Ktor-based applications.

## Libraries

### [waltid-ktor-notifications-core](./waltid-ktor-notifications-core)
Core library for Ktor server notifications. Provides multiplatform (JVM/JS/iOS) abstractions for publishing session updates and managing notification channels. Supports both Server-Sent Events (SSE) and webhook-based notifications.

**Use when:** You need the core notification functionality and want to build custom notification implementations, or you're building multiplatform applications that need notification support.

### [waltid-ktor-notifications](./waltid-ktor-notifications)
Ktor plugin for session notifications. Provides ready-to-use Ktor routes and plugins for implementing SSE endpoints and webhook notifications in Ktor applications.

**Use when:** You're building a Ktor-based service that needs to notify clients about session updates via SSE or webhooks.

## Join the community

* Connect and get the latest updates: [Discord](https://discord.gg/AW8AgqJthZ) | [Newsletter](https://walt.id/newsletter) | [YouTube](https://www.youtube.com/channel/UCXfOzrv3PIvmur_CmwwmdLA) | [LinkedIn](https://www.linkedin.com/company/walt-id/)
* Get help, request features and report bugs: [GitHub Issues ](https://github.com/walt-id/waltid-identity/issues)

## License

Licensed under the [Apache License, Version 2.0](https://github.com/walt-id/waltid-identity/blob/main/LICENSE)

