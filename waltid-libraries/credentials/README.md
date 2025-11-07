# walt.id Credential Libraries

This directory contains a comprehensive set of libraries for working with digital credentials across multiple formats and use cases. The libraries are organized into several categories based on their primary purpose.

## Overview

The credential libraries provide end-to-end support for digital credentials, from creation and issuance to verification and presentation. They support multiple credential formats (W3C, SD-JWT, mdoc) and provide unified abstractions for working with credentials across different ecosystems.

## Library Categories

### Core Credential Format Libraries

These libraries provide support for specific credential formats:

#### **[waltid-digital-credentials](./waltid-digital-credentials)**
Unified abstraction layer for parsing, detecting, and verifying digital credentials across multiple formats. This library provides a single entry point to work with W3C, SD-JWT VC, and mdoc credentials without knowing the format upfront.

**Use when:** You need to handle credentials from multiple issuers in various formats, or you want automatic format detection and parsing.

#### **[waltid-w3c-credentials](./waltid-w3c-credentials)**
Complete implementation for creating, issuing, and managing W3C Verifiable Credentials (v1.1 and v2.0). Supports JWT and SD-JWT issuance with static or dynamic configuration.

**Use when:** You're building an issuer service that needs to create W3C Verifiable Credentials, or a wallet that needs to build Verifiable Presentations.

#### **[waltid-mdoc-credentials](./waltid-mdoc-credentials)**
Implementation of ISO/IEC 18013-5:2021 mdoc (mobile document) specification. Provides parsing, verification, creation, and presentation of mdoc credentials with full Device Engagement support.

**Use when:** You need to work with ISO mdoc credentials (mobile driver's licenses, etc.) and require full mdoc specification compliance.

#### **[waltid-mdoc-credentials2](./waltid-mdoc-credentials2)**
Modern implementation of mdoc credentials with improved API and structure. This is the newer version of the mdoc library.

**Use when:** You're building new mdoc applications and want to use the latest mdoc library implementation.

### Query and Matching Libraries

These libraries enable querying and matching credentials against specifications:

#### **[waltid-dcql](./waltid-dcql)**
Digital Credentials Query Language (DCQL) implementation for matching credentials against OpenID4VP 1.0 queries. DCQL is the successor to Presentation Definition.

**Use when:** You're implementing OpenID4VP 1.0 and need to match credentials against DCQL queries, or you want the modern query language for credential matching.

#### **[waltid-dif-definitions-parser](./waltid-dif-definitions-parser)**
Parser and matcher for DIF Presentation Definition objects. Enables matching credentials against Presentation Exchange specifications (draft 14, draft 20).

**Use when:** You're working with DIF Presentation Exchange (draft implementations) or need to match credentials against Presentation Definition objects.

### Verification Libraries

These libraries provide credential and presentation verification capabilities:

#### **[waltid-verification-policies2](./waltid-verification-policies2)**
Modern verification policy system for OpenID4VP 1.0. Provides composable policies with a consistent `Result<JsonElement>` contract, working with the unified `DigitalCredential` interface.

**Use when:** You're building verifier services and need JSON-configurable verification policies.

#### **[waltid-verification-policies](./waltid-verification-policies)**
Legacy verification policy system supporting OpenID4VP draft implementations. Provides comprehensive policy support including credential status checks, revocation, and presentation-level policies.

**Use when:** You need advanced status/revocation policy support.

### Policy and Access Control Libraries

These libraries provide policy-based access control:

#### **[waltid-holder-policies](./waltid-holder-policies)**
Policy-based access control system for wallet holders to control credential reception and presentation. Enables fine-grained policies to block or allow credential operations based on issuer, format, claims, or DCQL queries.

**Use when:** You're building wallet applications that need to protect users from malicious credentials or implement privacy controls for credential presentation.

### Supporting Libraries

These libraries provide additional functionality:

#### **[waltid-digital-credentials-examples](./waltid-digital-credentials-examples)**
Collection of example credentials in various formats (W3C, SD-JWT, mdoc) for testing and development purposes.

**Use when:** You need example credentials for testing, development, or demonstration purposes.

#### **[waltid-vical](./waltid-vical)**
VICAL (Verifiable Issuer Certificate Authority List) trust list validation for mdoc credentials. Provides trust anchor validation and certificate chain verification.

**Use when:** You need to validate mdoc credentials against VICAL trust lists for trust anchor verification.

## Library Relationships

```
┌─────────────────────────────────────────────────────────┐
│         waltid-digital-credentials                      │
│     (Unified abstraction layer)                         │
└──────────────┬──────────────────────────────────────────┘
               │
    ┌──────────┼─────┬─────────────┬──────────────┐
    │                │             │              │
    ▼                ▼             ▼              ▼
┌───────────┐   ┌───────────┐ ┌────────────┐ ┌──────────────┐
│waltid-    │   │waltid-    │ │waltid-     │ │waltid-       │
│w3c-       │   │mdoc-      │ │mdoc-       │ │sdjwt (in     │
│credentials│   │credentials│ │credentials2│ │sdjwt lib)    │
└───────────┘   └───────────┘ └────────────┘ └──────────────┘

┌─────────────────────────────────────────────────────────┐
│         Query/Matching Libraries                        │
├─────────────────────────┬───────────────────────────────┤
│ waltid-dcql             │ waltid-dif-definitions-parser │
│ (OpenID4VP 1.0)         │ (DIF Presentation Exchange)   │
└─────────────────────────┴───────────────────────────────┘

┌─────────────────────────────────────────────────────────┐
│         Verification Libraries                          │
├─────────────────────────┬───────────────────────────────┤
│ waltid-verification-    │ waltid-verification-          │
│ policies2               │ policies                      │
└─────────────────────────┴───────────────────────────────┘

┌─────────────────────────────────────────────────────────┐
│         Supporting Libraries                            │
├─────────────────────────┬───────────────────────────────┤
│ waltid-holder-policies  │ waltid-digital-credentials-   │
│                         │ examples                      │
│                         ├───────────────────────────────┤
│                         │ waltid-vical                  │
└─────────────────────────┴───────────────────────────────┘
```

## Common Use Cases

### Building an Issuer Service

1. Use **waltid-w3c-credentials** to build and issue W3C Verifiable Credentials
2. Use **waltid-mdoc-credentials** or **waltid-mdoc-credentials2** for mdoc issuance
3. Use **waltid-digital-credentials** if you need to support multiple formats

### Building a Wallet Application

1. Use **waltid-digital-credentials** to parse and detect credentials from various issuers
2. Use **waltid-dcql** or **waltid-dif-definitions-parser** to match credentials against verifier queries
3. Use **waltid-holder-policies** to implement access control for credential reception and presentation
4. Use **waltid-w3c-credentials** to build Verifiable Presentations

### Building a Verifier Service

1. Use **waltid-verification-policies2** for multiplatform verification policies
2. Use **waltid-verification-policies** for legacy verification policies
3. Use **waltid-digital-credentials** to parse credentials for verification
4. Use **waltid-dcql** to create credential queries for OpenID4VP 1.0
5. Use **waltid-dif-definitions-parser** to create Presentation Definitions for draft implementations

### Working with mdoc Credentials

1. Use **waltid-mdoc-credentials2** for new mdoc implementations (recommended)
2. Use **waltid-mdoc-credentials** if you need full ISO/IEC 18013-5:2021 compliance
3. Use **waltid-vical** to validate mdoc credentials against VICAL trust lists
4. Use **waltid-digital-credentials** to parse mdocs alongside other credential formats

## Getting Started

Each library has its own README with detailed documentation. For quick reference:

- **New to credentials?** Start with [waltid-digital-credentials](./waltid-digital-credentials) to understand the unified abstraction
- **Building an issuer?** See [waltid-w3c-credentials](./waltid-w3c-credentials) for W3C credentials or [waltid-mdoc-credentials2](./waltid-mdoc-credentials2) for mdocs
- **Building a verifier?** See [waltid-verification-policies2](./waltid-verification-policies2) for OpenID4VP 1.0
- **Building a wallet?** See [waltid-digital-credentials](./waltid-digital-credentials) and [waltid-holder-policies](./waltid-holder-policies)

## Dependencies Between Libraries

The libraries have the following dependency relationships:

- **waltid-digital-credentials** depends on:
  - waltid-w3c-credentials (for W3C credential support)
  - waltid-mdoc-credentials (for mdoc support)
  - waltid-sdjwt (for SD-JWT support, located in ../sdjwt)

- **waltid-verification-policies2** depends on:
  - waltid-digital-credentials (for unified credential interface)

- **waltid-holder-policies** depends on:
  - waltid-digital-credentials (for credential interface)
  - waltid-dcql (for DCQL query matching)

- **waltid-dcql** and **waltid-dif-definitions-parser** can work with:
  - waltid-digital-credentials (for credential matching)

## Join the community

* Connect and get the latest updates: [Discord](https://discord.gg/AW8AgqJthZ) | [Newsletter](https://walt.id/newsletter) | [YouTube](https://www.youtube.com/channel/UCXfOzrv3PIvmur_CmwwmdLA) | [LinkedIn](https://www.linkedin.com/company/walt-id/)
* Get help, request features and report bugs: [GitHub Issues ](https://github.com/walt-id/waltid-identity/issues)

## License

Licensed under the [Apache License, Version 2.0](https://github.com/walt-id/waltid-identity/blob/main/LICENSE)
