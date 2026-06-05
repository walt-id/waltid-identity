# walt.id Services

by [walt.id](https://walt.id)

Production-ready services for credential issuance, verification, and wallet management



## Statuses Explained


|                                                                                                                                                                            |                                                                                                                                                                                     |
| -------------------------------------------------------------------------------------------------------------------------------------------------------------------------- | ----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| *This project is being actively maintained by the development team at walt.id. Regular updates, bug fixes, and new features are being added.*                              | *This project is not actively maintained. Certain features may be outdated or not working as expected. We encourage users to contribute to the project to help keep it up to date.* |
| *This project is deprecated and no longer maintained. It should not be used in new projects. Please use our alternative libraries or migrate to recommended replacements.* | *This project is still supported by the development team at walt.id, but is planned for deprecation. We encourage users to migrate to using our alternative libraries.*             |


## Overview

This directory contains production-ready services built on top of the walt.id libraries. These services provide complete, deployable solutions for credential issuance, verification, wallet management, and testing infrastructure.

## Services

### Core Services

### [🟢 waltid-issuer-api](./waltid-issuer-api)

Credential Issuer API service. Provides a complete REST API for issuing verifiable credentials in multiple formats (W3C, SD-JWT, mdoc). Supports OpenID4VCI flows, credential templates, and issuer metadata management.

**Use when:** You need a production-ready service for issuing verifiable credentials to wallets and supporting credential issuance workflows.

### [🟠 waltid-verifier-api](./waltid-verifier-api)

Verifier API service (legacy). Provides a REST API for requesting and verifying verifiable presentations using OpenID4VP draft implementations (draft 14, draft 20) with Presentation Definition support.

**Use when:** You need a verifier service that supports OpenID4VP draft specifications or requires Presentation Definition-based verification.

### [🟢 waltid-verifier-api2](./waltid-verifier-api2)

Verifier API 2 service (OpenID4VP 1.0). Modern verifier service implementing OpenID4VP 1.0 with DCQL (Digital Credentials Query Language) support. Provides session management, SSE/webhook notifications, and comprehensive verification policy support.

**Use when:** You need a production-ready verifier service that supports OpenID4VP 1.0, DCQL queries, and modern verification workflows.

### [🟢 waltid-wallet-api](./waltid-wallet-api)

Wallet API service (legacy). Complete wallet backend service providing credential storage, presentation management, OpenID4VCI/OpenID4VP draft flows, and wallet management APIs. Supports multiple credential formats and provides comprehensive wallet functionality.

**Note:** This service supports OpenID4VCI Draft 11/13 and OpenID4VP Draft 14/20. For OpenID4VCI 1.0 and OpenID4VP 1.0 support, see the new Wallet SDK libraries.

**Use when:** You need a production-ready wallet backend service that supports OpenID4VC draft specifications.

### [🟢 waltid-wallet-migration](./waltid-wallet-migration)

Wallet migration tool. Migrates data from the legacy `waltid-wallet-api` (v1) database schema to the new Wallet SDK schema. Supports SQLite and PostgreSQL, with dry-run mode for previewing migrations.

**Use when:** You need to migrate existing wallet data from the legacy wallet service to the new Wallet SDK schema.

### [🟡 waltid-web3login-microservice](./waltid-web3login-microservice)

Web3 login microservice. Provides authentication services using Web3 wallet signatures for decentralized identity applications.

**Use when:** You need Web3-based authentication or want to integrate wallet-based login into your application.

  
  


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

- **Issuing credentials?** See [waltid-issuer-api](./waltid-issuer-api) for a complete issuer service
- **Verifying credentials?** Check [waltid-verifier-api2](./waltid-verifier-api2) for OpenID4VP 1.0 or [waltid-verifier-api](./waltid-verifier-api) for draft implementations
- **Building a wallet?** Start with [waltid-wallet-api](./waltid-wallet-api) for draft specifications, or use the [Wallet SDK libraries](../waltid-libraries/protocols/waltid-openid4vc-wallet) for OpenID4VCI 1.0 and OpenID4VP 1.0
- **Migrating wallet data?** Use [waltid-wallet-migration](./waltid-wallet-migration) to migrate from the legacy wallet schema
- **Running tests?** See [waltid-integration-tests](./waltid-integration-tests).

## Deployment

All services are built with Ktor and can be deployed as standalone JVM applications. See individual service READMEs for deployment instructions, configuration options, and Kubernetes deployment manifests.

## Join the community

- Connect and get the latest updates: [Discord](https://discord.gg/AW8AgqJthZ) | [Newsletter](https://walt.id/newsletter) | [YouTube](https://www.youtube.com/channel/UCXfOzrv3PIvmur_CmwwmdLA) | [LinkedIn](https://www.linkedin.com/company/walt-id/)
- Get help, request features and report bugs: [GitHub Issues](https://github.com/walt-id/waltid-identity/issues)
- Find more indepth documentation on our [docs site](https://docs.walt.id)

## License

Licensed under the [Apache License, Version 2.0](https://github.com/walt-id/waltid-identity/blob/main/LICENSE)

