<div align="center">
<h1>walt.id Protocol Libraries</h1>
 <span>by </span><a href="https://walt.id">walt.id</a>
 <p>Libraries implementing identity and credential protocols (OpenID4VC, OpenID4VP, SIOPv2)</p>

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

This directory contains libraries implementing identity and credential protocols, including OpenID for Verifiable Credentials (OID4VCI, OID4VP) and Self-Issued OpenID Provider v2 (SIOPv2). These libraries provide multiplatform support for building issuers, verifiers, and wallets.

## Libraries

### Main Libraries:

### [🟠 waltid-openid4vc](./waltid-openid4vc)
Multiplatform library implementing OpenID for Verifiable Credentials specifications. Supports OID4VCI (Credential Issuance) Draft 11/13, OID4VP (Presentation) Draft 14/20, and SIOPv2. Provides data models, protocol flows, and client/server implementations for both issuance and presentation.

**Use when:** You're building issuers, verifiers, or wallets that need to support OpenID4VC draft specifications, or you need multiplatform protocol implementations.

### [🟢 waltid-openid4vci](./waltid-openid4vci)
OpenID4VCI 1.0 issuer implementation. Provides integration-ready components for building issuer services that issue verifiable credentials using OpenID4VCI 1.0.

**Use when:** You're building an issuer service that needs to issue verifiable credentials using OpenID4VCI 1.0.

### [🟢 waltid-openid4vp-verifier](./waltid-openid4vp-verifier)
OpenID4VP 1.0 verifier implementation. Provides integration-ready components for building verifier services that request verifiable presentations from wallets using OpenID4VP 1.0.

**Use when:** You're building a verifier service that needs to request verifiable presentations from wallets using OpenID4VP 1.0.

### [🟢 waltid-openid4vp-wallet](./waltid-openid4vp-wallet)
OpenID4VP 1.0 wallet implementation. Provides integration-ready components for building wallets that respond to verifier presentation requests using OpenID4VP 1.0.

**Use when:** You're building a wallet application that needs to handle verifier presentation requests using OpenID4VP 1.0.

<br />
<br />

### Helper Libraries:

### [🟢 waltid-openid4vp](./waltid-openid4vp)
Core OpenID4VP 1.0 library with DCQL (Digital Credentials Query Language) support. Provides the core abstractions, data models, and protocol flow for OpenID4VP 1.0 verifiable presentation requests.

**Use when:** You need to understand or work with OpenID4VP 1.0 core concepts, DCQL queries, or the base protocol implementation.

### [🟢 waltid-openid4vp-clientidprefix](./waltid-openid4vp-clientidprefix)
Client ID prefix parsing and authentication for OpenID4VP. Provides utilities for parsing and validating client identifier prefixes (x509_san_dns, redirect_uri, did, web-origin, etc.) used in OpenID4VP flows.

**Use when:** You need to parse or validate client identifier prefixes in OpenID4VP flows, or implement dynamic verifier identification.

### [🟢 waltid-openid4vp-verifier-openapi](./waltid-openid4vp-verifier-openapi)
OpenAPI schema generation for OpenID4VP verifier endpoints. Provides tools for generating OpenAPI documentation and example request bodies for OpenID4VP verifier API endpoints.

**Use when:** You need to generate OpenAPI documentation for OpenID4VP verifier endpoints or provide API documentation for verifier services.

## Protocol Versions

- **Draft Implementations**: Use [waltid-openid4vc](./waltid-openid4vc) for OID4VCI Draft 11/13 and OID4VP Draft 14/20
- **OpenID4VP 1.0**: Use the `waltid-openid4vp-*` libraries for the final OpenID4VP 1.0 specification
- **OpenID4VCI 1.0**: Will be released as separate libraries (coming soon)

## Join the community

* Connect and get the latest updates: [Discord](https://discord.gg/AW8AgqJthZ) | [Newsletter](https://walt.id/newsletter) | [YouTube](https://www.youtube.com/channel/UCXfOzrv3PIvmur_CmwwmdLA) | [LinkedIn](https://www.linkedin.com/company/walt-id/)
* Get help, request features and report bugs: [GitHub Issues](https://github.com/walt-id/waltid-identity/issues)
* Find more indepth documentation on our [docs site](https://docs.walt.id)

## License

Licensed under the [Apache License, Version 2.0](https://github.com/walt-id/waltid-identity/blob/main/LICENSE)

<div align="center">
<img src="../../assets/walt-banner.png" alt="walt.id banner" />
</div>

