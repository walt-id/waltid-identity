<div align="center">
<h1>walt.id SD-JWT Libraries</h1>
 <span>by </span><a href="https://walt.id">walt.id</a>
 <p>Libraries for Selective Disclosure JWT (SD-JWT) credentials</p>

<a href="https://walt.id/community">
<img src="https://img.shields.io/badge/Join-The Community-blue.svg?style=flat" alt="Join community!" />
</a>
<a href="https://www.linkedin.com/company/walt-id/">
<img src="https://img.shields.io/badge/-LinkedIn-0072b1?style=flat&logo=linkedin" alt="Follow walt_id" />
</a>
</div>

## Overview

This directory contains libraries for working with Selective Disclosure JWT (SD-JWT) credentials. SD-JWT enables selective disclosure of claims from verifiable credentials, allowing users to share only the information they choose while maintaining credential integrity.

## Libraries

### [waltid-sdjwt](./waltid-sdjwt)
Multiplatform SD-JWT implementation with selective disclosure support. Provides complete SD-JWT functionality including issuance, presentation, verification, and selective disclosure operations across JVM, JavaScript, and iOS platforms.

**Use when:** You need to work with SD-JWT credentials, implement selective disclosure, or build issuers/verifiers that support SD-JWT format credentials.

### [waltid-sdjwt-ios](./waltid-sdjwt-ios)
iOS-specific SD-JWT implementations. Provides iOS platform optimizations and native iOS support for SD-JWT operations.

**Use when:** You're building iOS applications and need native iOS SD-JWT support or platform-specific optimizations.

## Related Libraries

SD-JWT credentials are also supported through the unified credential abstraction in [waltid-digital-credentials](../credentials/waltid-digital-credentials), which can automatically detect and parse SD-JWT credentials alongside other formats.

## Join the community

* Connect and get the latest updates: [Discord](https://discord.gg/AW8AgqJthZ) | [Newsletter](https://walt.id/newsletter) | [YouTube](https://www.youtube.com/channel/UCXfOzrv3PIvmur_CmwwmdLA) | [LinkedIn](https://www.linkedin.com/company/walt-id/)
* Get help, request features and report bugs: [GitHub Issues ](https://github.com/walt-id/waltid-identity/issues)

## License

Licensed under the [Apache License, Version 2.0](https://github.com/walt-id/waltid-identity/blob/main/LICENSE)

