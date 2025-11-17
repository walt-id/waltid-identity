<div align="center">
<h1>walt.id Authentication & Authorization Libraries</h1>
 <span>by </span><a href="https://walt.id">walt.id</a>
 <p>Libraries for implementing authentication and authorization systems</p>

<a href="https://walt.id/community">
<img src="https://img.shields.io/badge/Join-The Community-blue.svg?style=flat" alt="Join community!" />
</a>
<a href="https://www.linkedin.com/company/walt-id/">
<img src="https://img.shields.io/badge/-LinkedIn-0072b1?style=flat&logo=linkedin" alt="Follow walt_id" />
</a>
</div>

## Overview

This directory contains libraries for implementing authentication and authorization systems in walt.id applications. These libraries provide flexible authentication mechanisms, permission management, and identity provider functionality.

## Libraries

### [waltid-ktor-authnz](./waltid-ktor-authnz)
Flexible authentication and authorization framework for Ktor applications. Supports multiple authentication methods (username/password, email/password, TOTP, LDAP, RADIUS, OIDC, JWT, Web3, Verifiable Credentials), multi-step authentication flows, session management, and configurable account stores.

**Use when:** You're building a Ktor-based application that needs flexible, multi-method authentication with support for complex authentication flows.

### [waltid-permissions](./waltid-permissions)
Permission management system for access control. Provides a flexible framework for defining and checking permissions across different contexts and resources.

**Use when:** You need to implement fine-grained permission-based access control in your application.

### [waltid-idpkit](./waltid-idpkit)
Identity Provider toolkit for OIDC-based authentication with verifiable credentials. Enables building identity providers that authenticate users using verifiable credentials through OpenID Connect flows.

**Use when:** You're building an identity provider that needs to authenticate users using verifiable credentials and issue OIDC tokens.

## Join the community

* Connect and get the latest updates: [Discord](https://discord.gg/AW8AgqJthZ) | [Newsletter](https://walt.id/newsletter) | [YouTube](https://www.youtube.com/channel/UCXfOzrv3PIvmur_CmwwmdLA) | [LinkedIn](https://www.linkedin.com/company/walt-id/)
* Get help, request features and report bugs: [GitHub Issues ](https://github.com/walt-id/waltid-identity/issues)

## License

Licensed under the [Apache License, Version 2.0](https://github.com/walt-id/waltid-identity/blob/main/LICENSE)

