<div align="center">
<h1>walt.id OpenID4VC</h1>
 <span>by </span><a href="https://walt.id">walt.id</a>
 <p>Kotlin multiplatform library implementing OpenID for Verifiable Credentials protocols (Draft specifications)</p>

<a href="https://walt.id/community">
<img src="https://img.shields.io/badge/Join-The Community-blue.svg?style=flat" alt="Join community!" />
</a>
<a href="https://www.linkedin.com/company/walt-id/">
<img src="https://img.shields.io/badge/-LinkedIn-0072b1?style=flat&logo=linkedin" alt="Follow walt_id" />
</a>
  
  <h2>Status</h2>
  <p align="center">
    <img src="https://img.shields.io/badge/🟠%20Planned%20Deprecation-orange?style=for-the-badge&logo=clock" alt="Status: Planned Deprecation" />
    <br/>
    <em>This project is still supported by the development team at walt.id, but is planned for deprecation sometime in Q2 2026.<br />We encourage users to migrate to using alternative libraries listed below.</em>
  </p>

</div>

## What This Library Contains

`waltid-openid4vc` is a Kotlin multiplatform library that implements the data models and protocols of the [OpenID for Verifiable Credentials](https://openid.net/sg/openid4vc/) specifications. It provides comprehensive support for credential issuance, verification, and self-issued identity flows using draft versions of the OpenID4VC protocols.

Learn more about OpenID4VP (Presentation) [here](https://docs.walt.id/concepts/data-exchange-protocols/openid4vp).
Learn more about OpenID4VCI (Issuance) [here](https://docs.walt.id/concepts/data-exchange-protocols/openid4vci).

## Main Purpose

This library enables:

- **Credential Issuance**: Implement OpenID4VCI (OpenID for Verifiable Credential Issuance) flows (draft 11 and draft 13)
- **Credential Verification**: Implement OpenID4VP (OpenID for Verifiable Presentations) flows (draft 14 and draft 20)
- **Self-Issued Identity**: Implement SIOPv2 (Self-Issued OpenID Provider v2) flows
- **Data Model Support**: Parse and serialize OpenID4VC request/response objects
- **Protocol Utilities**: Helper functions for authorization, token generation, and credential handling
- **Multiplatform Support**: Works on JVM, JavaScript, and iOS platforms

> **Note**: While you may be able to use this library to partially support other draft versions, the versions above are the ones that have been implemented and tested in other walt.id projects.

## Migration to OpenID4VP and OpenID4VCI 1.0

This library implements draft specifications. For production use with the final OpenID4VC 1.0 specifications:

- **OpenID4VP 1.0**: Use the `waltid-openid4vp-*` libraries in the same protocols folder
  - `waltid-openid4vp`: Core OpenID4VP 1.0 library
  - `waltid-openid4vp-verifier`: Verifier implementation
  - `waltid-openid4vp-wallet`: Wallet implementation

- **OpenID4VCI 1.0**: Separate libraries will be released for OpenID4VCI 1.0 support in Q1 2026

## Key Concepts

### OpenID4VCI (Credential Issuance)

Supporting draft versions 11 and 13 of the OpenID4VCI protocol, this enables issuers to issue verifiable credentials to wallets:

- **Credential Offers**: Generate and parse credential offer URLs
- **Authorization Flows**: Support for code flow and pre-authorized code flow
- **Token Management**: Generate and verify access tokens
- **Credential Requests**: Handle credential requests and responses
- **Proof of Possession**: Support for JWT and CWT proof types

### OpenID4VP (Verifiable Presentations)

Supporting draft versions 14 and 20 of the OpenID4VP protocol, this enables verifiers to request verifiable presentations from wallets:

- **Presentation Requests**: Create and parse presentation authorization requests
- **Presentation Definitions**: Support for DIF Presentation Definition format
- **Response Modes**: Support for `direct_post`, `direct_post.jwt`, `query`, `fragment`, `form_post`
- **VP Tokens**: Generate and parse verifiable presentation tokens
- **Presentation Submissions**: Handle presentation submission objects

### SIOPv2 (Self-Issued OpenID Provider)

Enables wallets to act as self-issued identity providers:

- **Self-Issued Credentials**: Issue credentials to themselves
- **Identity Assertions**: Present identity information via verifiable credentials
- **ID Token Generation**: Generate self-issued ID tokens

### Core Components

The library provides three main utility objects:

- **`OpenID4VCI`**: Utility functions for credential issuance flows
- **`OpenID4VP`**: Utility functions for verifiable presentation flows
- **`OpenID4VC`**: Common utility functions for token generation, verification, and authorization

### Data Models

Comprehensive data structures for:

- **Requests**: Authorization requests, credential requests, token requests
- **Responses**: Authorization responses, credential responses, token responses
- **Metadata**: Provider metadata, client metadata, credential definitions
- **DIF Objects**: Presentation definitions, presentation submissions, input descriptors

## Usage

### Installation

Add the Maven repository:

```kotlin
repositories {
    maven { url = uri("https://maven.waltid.dev/releases") }
}
```

Add the dependency:

```kotlin
dependencies {
    implementation("id.walt.openid4vc:waltid-openid4vc:<version>")
}
```

Replace `<version>` with the version of walt.id identity you want to use (the openid4vc library version matches the walt.id identity version).

### Basic Usage

The library provides utility objects and data models. To use it, you need to:

1. **Implement Session Management**: Manage authorization sessions, presentation sessions, etc.
2. **Implement REST API Endpoints**: Provide HTTP endpoints defined by the specifications
3. **Implement Business Logic**: Use the utility objects (`OpenID4VCI`, `OpenID4VP`, `OpenID4VC`) to handle protocol flows

### Example: Creating a Presentation Request

```kotlin
import id.walt.oid4vc.OpenID4VP
import id.walt.oid4vc.data.dif.PresentationDefinition

// Create a presentation definition
val presentationDefinition = PresentationDefinition(
    id = "pres-def-1",
    inputDescriptors = listOf(/* ... */)
)

// Create a presentation request
val presentationRequest = OpenID4VP.createPresentationRequest(
    presentationDefinition = PresentationDefinitionParameter.ByValue(presentationDefinition),
    responseMode = ResponseMode.direct_post,
    responseTypes = setOf(ResponseType.VpToken),
    redirectOrResponseUri = "https://verifier.example.com/callback",
    nonce = "random-nonce",
    state = "session-state",
    clientId = "verifier-id",
    clientIdScheme = null,
    clientMetadataParameter = null
)

// Generate authorization URL for QR code
val authorizationUrl = OpenID4VP.getAuthorizationUrl(presentationRequest)
```

### Example: Processing a Credential Offer

```kotlin
import id.walt.oid4vc.OpenID4VCI
import id.walt.oid4vc.requests.CredentialOfferRequest

// Parse credential offer from URL
val credentialOffer = CredentialOfferRequest.fromHttpParameters(parameters)

// Resolve credential offer
val resolvedOffer = OpenID4VCI.resolveCredentialOffer(credentialOffer)

// Generate authorization request
val authRequest = OpenID4VCI.generateAuthorizationRequest(
    credentialOffer = resolvedOffer,
    // ... other parameters
)
```

### Example: Implementing a Verifier

```kotlin
import id.walt.oid4vc.providers.OpenIDCredentialVerifier
import id.walt.oid4vc.providers.PresentationSession

class MyVerifier(config: CredentialVerifierConfig) : 
    OpenIDCredentialVerifier<PresentationSession>(config) {
    
    override fun initializeAuthorization(
        presentationDefinition: PresentationDefinition,
        responseMode: ResponseMode
    ): String {
        // Create session and return authorization URL
        // ...
    }
    
    override fun handlePresentationResponse(
        sessionId: String,
        tokenResponse: TokenResponse
    ): PresentationResult {
        // Verify presentation and return result
        // ...
    }
}
```

## Related Libraries

- **[waltid-openid4vp](../waltid-openid4vp)**: OpenID4VP 1.0 core library (recommended for new projects)
- **[waltid-openid4vp-verifier](../waltid-openid4vp-verifier)**: OpenID4VP 1.0 verifier implementation
- **[waltid-openid4vp-wallet](../waltid-openid4vp-wallet)**: OpenID4VP 1.0 wallet implementation
- **[waltid-core-wallet](../../waltid-core-wallet)**: Core wallet helpers built on OpenID4VC
- **[waltid-crypto](../../crypto/waltid-crypto)**: Cryptographic operations
- **[waltid-did](../../waltid-did)**: DID management
- **[waltid-w3c-credentials](../../credentials/waltid-w3c-credentials)**: W3C Verifiable Credentials


## Join the community

* Connect and get the latest updates: [Discord](https://discord.gg/AW8AgqJthZ) | [Newsletter](https://walt.id/newsletter) | [YouTube](https://www.youtube.com/channel/UCXfOzrv3PIvmur_CmwwmdLA) | [LinkedIn](https://www.linkedin.com/company/walt-id/)
* Get help, request features and report bugs: [GitHub Issues](https://github.com/walt-id/waltid-identity/issues)
* Find more indepth documentation on our [docs site](https://docs.walt.id)

## License

Licensed under the [Apache License, Version 2.0](https://github.com/walt-id/waltid-identity/blob/main/LICENSE)

<div align="center">
<img src="../../../assets/walt-banner.png" alt="walt.id banner" />
</div>
