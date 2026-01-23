
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
  
  <h2>Statuses Explained</h2>
  <table>
    <tr>
      <td align="center" width="50%">
        <img src="https://img.shields.io/badge/🟢%20Actively%20Maintained-success?style=for-the-badge&logo=check-circle" alt="Status: Actively Maintained" />
        <br/>
        <em>This project is being actively maintained by the development team at walt.id. Regular updates, bug fixes, and new features are being added.</em>
      </td>
      <td align="center" width="50%">
        <img src="https://img.shields.io/badge/🟡%20Unmaintained-yellow?style=for-the-badge&logo=warning" alt="Status: Unmaintained" />
        <br/>
        <em>This project is not actively maintained. Certain features may be outdated or not working as expected. We encourage users to contribute to the project to help keep it up to date.</em>
      </td>
    </tr> 
    <tr>
      <td align="center" width="50%">
        <img src="https://img.shields.io/badge/🔴%20Deprecated-red?style=for-the-badge&logo=no-entry" alt="Status: Deprecated" />
        <br/>
        <em>This project is deprecated and no longer maintained. It should not be used in new projects. Please use our alternative libraries or migrate to recommended replacements.</em>
      </td>
      <td align="center" width="50%">
        <img src="https://img.shields.io/badge/🟠%20Planned%20Deprecation-orange?style=for-the-badge&logo=clock" alt="Status: Planned Deprecation" />
        <br/>
        <em>This project is still supported by the development team at walt.id, but is planned for deprecation. We encourage users to migrate to using our alternative libraries.</em>
      </td>
    </tr>
  </table>
</div>

## Overview

This directory contains libraries for implementing authentication and authorization systems in walt.id applications. These libraries provide flexible authentication mechanisms, permission management, and identity provider functionality.

## Libraries

### [🟢 waltid-ktor-authnz](./waltid-ktor-authnz)
Flexible authentication and authorization framework for Ktor applications. Supports multiple authentication methods (username/password, email/password, TOTP, LDAP, RADIUS, OIDC, JWT, Web3, Verifiable Credentials), multi-step authentication flows, session management, and configurable account stores.

**Use when:** You're building a Ktor-based application that needs flexible, multi-method authentication with support for complex authentication flows.

### [🟢 waltid-permissions](./waltid-permissions)
Permission management system for access control. Provides a flexible framework for defining and checking permissions across different contexts and resources.

**Use when:** You need to implement fine-grained permission-based access control in your application.

### [🟡 waltid-idpkit](./waltid-idpkit)
Identity Provider toolkit for OIDC-based authentication with verifiable credentials. Enables building identity providers that authenticate users using verifiable credentials through OpenID Connect flows.

**Use when:** You're building an identity provider that needs to authenticate users using verifiable credentials and issue OIDC tokens.

## Join the community

* Connect and get the latest updates: [Discord](https://discord.gg/AW8AgqJthZ) | [Newsletter](https://walt.id/newsletter) | [YouTube](https://www.youtube.com/channel/UCXfOzrv3PIvmur_CmwwmdLA) | [LinkedIn](https://www.linkedin.com/company/walt-id/)
* Get help, request features and report bugs: [GitHub Issues](https://github.com/walt-id/waltid-identity/issues)
* Find more indepth documentation on our [docs site](https://docs.walt.id)

## License

Licensed under the [Apache License, Version 2.0](https://github.com/walt-id/waltid-identity/blob/main/LICENSE)

<div align="center">
<img src="../../assets/walt-banner.png" alt="walt.id banner" />
</div>

