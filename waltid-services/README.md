<div align="center">
<h1>walt.id Services</h1>
 <span>by </span><a href="https://walt.id">walt.id</a>
 <p>Open-source reference services for credential issuance, verification, and wallet management</p>

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

These Community Stack services demonstrate how to issue, verify, and manage credentials using the walt.id libraries. The shipped quickstart configurations are for development and testing.

## Production deployment

**Do not expose Issuer API 2 or Verifier API 2 directly to the internet.** Neither service has built-in authentication or authorization for its management API. A self-managed deployment must protect those routes with its own authenticated backend or gateway. If you serve multiple tenants, enforce tenant isolation there or use separate deployments. Wallet API v2 ships with optional account authentication and wallet ownership checks. Docker Compose enables them; the standalone sample configuration leaves them disabled.

Review the checklist for [Issuer API 2](./waltid-issuer-api2/README.md#production-deployment), [Verifier API 2](./waltid-verifier-api2/README.md#production-deployment), or [Wallet API v2](./waltid-wallet-api2/README.md#production-deployment), along with these controls for all three services:

- **Restrict ingress.** Put the service behind a TLS reverse proxy or gateway, block direct access to its port, and allowlist routes and HTTP methods. Leave development mode off and keep debug, health, and Swagger endpoints off public ingress. Restrict CORS to the browser origins you use, and let the gateway set trusted forwarding headers.
- **Limit abuse.** Set route-specific rate limits, request size limits, and timeouts. If you use Server-Sent Events (SSE), limit concurrent streams and configure and test the proxy's streaming timeouts.
- **Operate and monitor.** Deploy a pinned release and apply security updates. Monitor availability, latency, errors, resource use, and abuse signals. Audit privileged actions and redact credentials, tokens, session links, and personal data from logs and traces. Test load, restarts, and recovery before launch.

The commercial [Enterprise Stack](https://docs.walt.id/enterprise-stack/home/the-enterprise-stack) includes built-in API authentication, role-based authorization, tenant controls, and audit logs.

## Services

### Core Services

### [🟢 waltid-issuer-api2](./waltid-issuer-api2)
Issuer API 2 service (OpenID4VCI 1.0). Modern issuer service for profile-based credential offers, OpenID4VCI metadata, authorization and token flows, credential issuance, nonce handling, session inspection, and SSE session updates.

**Use when:** You are building OpenID4VCI 1.0 issuance workflows and can provide the required deployment controls.

### [🟢 waltid-verifier-api2](./waltid-verifier-api2)
Verifier API 2 service (OpenID4VP 1.0). Modern verifier service implementing OpenID4VP 1.0 with DCQL (Digital Credentials Query Language) support. Provides session management, SSE/webhook notifications, and comprehensive verification policy support.

**Use when:** You are building OpenID4VP 1.0 verification workflows with DCQL and can provide the required deployment controls.

### [🟢 waltid-wallet-api2](./waltid-wallet-api2)
Wallet API service. Complete wallet backend service providing credential storage, presentation management, OpenID4VCI/OpenID4VP flows, and wallet management APIs. Supports multiple credential formats and provides comprehensive wallet functionality.

**Use when:** You are building a custodial wallet backend for storing credentials and handling issuance and presentation flows.

### [🟠 waltid-issuer-api](./waltid-issuer-api)
Credential Issuer API service (legacy, planned for deprecation). Provides a complete REST API for issuing verifiable credentials in multiple formats (W3C, SD-JWT, mdoc). Supports OpenID4VCI draft flows, credential templates, and issuer metadata management.

**Use when:** You need compatibility with existing draft-based issuer integrations. For new OpenID4VCI 1.0 integrations, use [waltid-issuer-api2](./waltid-issuer-api2).

### [🟠 waltid-verifier-api](./waltid-verifier-api)
Verifier API service (legacy). Provides a REST API for requesting and verifying verifiable presentations using OpenID4VP draft implementations (draft 14, draft 20) with Presentation Definition support.

**Use when:** You need a verifier service that supports OpenID4VP draft specifications or requires Presentation Definition-based verification.

### [🟠 waltid-wallet-api](./waltid-wallet-api)
Wallet API service. Complete wallet backend service providing credential storage, presentation management, OpenID4VCI/OpenID4VP flows, and wallet management APIs. Supports multiple credential formats and provides comprehensive wallet functionality.

**Use when:** You need compatibility with draft-based wallet integrations. For new OpenID4VCI/OpenID4VP 1.0 integrations, use [waltid-wallet-api2](./waltid-wallet-api2).

### [🟡 waltid-web3login-microservice](./waltid-web3login-microservice)
Web3 login microservice. Provides authentication services using Web3 wallet signatures for decentralized identity applications.

**Use when:** You need Web3-based authentication or want to integrate wallet-based login into your application.


<br />
<br />

### Supporting Services

### [🟢 waltid-openid4vp-conformance-runners](./waltid-openid4vp-conformance-runners)
OpenID4VP conformance testing utilities. Provides tools and instructions for running the official OpenID Conformance Suite against walt.id OpenID4VP implementations.

**Use when:** You need to validate walt.id OpenID4VP implementations against the official conformance suite or run compliance tests.

### [🟢 waltid-service-commons](./waltid-service-commons)
Common utilities and shared code for walt.id services. Provides shared configuration, utilities, and infrastructure code used across all services.

**Use when:** You're building custom services that need to integrate with walt.id infrastructure or share common functionality.

### [🔴 waltid-service-commons-test](./waltid-service-commons-test)
Testing utilities for walt.id services. Provides test helpers, fixtures, and utilities for testing walt.id services.

**Use when:** You're writing tests for walt.id services or building test infrastructure.

### [🟢 waltid-integration-tests](./waltid-integration-tests)
Integration test suite for walt.id services. Comprehensive integration tests covering service interactions, protocol flows, and end-to-end scenarios.

**Use when:** You need to understand how services interact, validate service integrations, or run integration test suites.

### [🔴 waltid-e2e-tests](./waltid-e2e-tests)
End-to-end test suite for walt.id services. Complete end-to-end tests covering full user workflows across all services.

**Use when:** You need to validate complete user workflows, test service deployments, or run comprehensive end-to-end tests.


## Getting Started

- **Issuing credentials?** See [waltid-issuer-api2](./waltid-issuer-api2) for OpenID4VCI 1.0 or [waltid-issuer-api](./waltid-issuer-api) for draft/legacy integrations
- **Verifying credentials?** Check [waltid-verifier-api2](./waltid-verifier-api2) for OpenID4VP 1.0 or [waltid-verifier-api](./waltid-verifier-api) for draft implementations
- **Building a wallet?** Start with [waltid-wallet-api](./waltid-wallet-api) for complete wallet backend functionality
- **Running tests?** See [waltid-integration-tests](./waltid-integration-tests).

## Deployment

All services are built with Ktor and can be deployed as standalone JVM applications. See individual service READMEs for deployment instructions, configuration options, and Kubernetes deployment manifests.

## Join the community

* Connect and get the latest updates: [Discord](https://discord.gg/AW8AgqJthZ) | [Newsletter](https://walt.id/newsletter) | [YouTube](https://www.youtube.com/channel/UCXfOzrv3PIvmur_CmwwmdLA) | [LinkedIn](https://www.linkedin.com/company/walt-id/)
* Get help, request features and report bugs: [GitHub Issues](https://github.com/walt-id/waltid-identity/issues)
* Find more indepth documentation on our [docs site](https://docs.walt.id)

## License

Licensed under the [Apache License, Version 2.0](https://github.com/walt-id/waltid-identity/blob/main/LICENSE)

<div align="center">
<img src="../assets/walt-banner.png" alt="walt.id banner" />
</div>

