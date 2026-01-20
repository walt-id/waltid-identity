<div align="center">
<h1>OpenID for Verifiable Credential Issuance (OpenID4VCI) 1.0</h1>
 <span>by </span><a href="https://walt.id">walt.id</a>
 <p>Core Kotlin Multiplatform library providing OAuth2 provider implementation for OpenID4VCI 1.0</p>

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

This library provides a complete **OAuth2 provider implementation** for **OpenID for Verifiable Credential Issuance (OpenID4VCI) 1.0**. It implements the authorization and token endpoints required for credential issuance flows, enabling Issuers to issue verifiable credentials to Wallets.

Learn more about OpenID4VCI [here](https://docs.walt.id/concepts/data-exchange-protocols/openid4vci).

The library includes:
- **OAuth2 Provider** - Complete provider implementation with authorization and token endpoints
- **Grant Type Handlers** - Support for authorization code and pre-authorized code grant types
- **Authorization Endpoint** - Handles authorization requests and generates authorization codes
- **Token Endpoint** - Issues access tokens for credential requests
- **JWT Access Tokens** - JWT-based access token generation and signing
- **Request Validation** - Validates authorization and token requests according to OAuth2/OpenID4VCI specs
- **Repository Interfaces** - Pluggable storage for authorization codes and pre-authorized codes
- **Session Management** - Session tracking for issuance flows

## Main Purpose

This library serves as the foundation for building OpenID4VCI Issuer implementations. It provides a framework-agnostic OAuth2 provider that handles the authorization and token exchange flows required by the OpenID4VCI specification.

**Use this library when:**
- You're building an OpenID4VCI Issuer service and need OAuth2 provider functionality
- You need to implement authorization code or pre-authorized code grant flows
- You want a multiplatform OAuth2 provider that works on JVM, JavaScript, and iOS
- You need JWT access token generation and validation

**You typically won't use this library directly** if you're building a Wallet - instead, use wallet-specific libraries that consume issuer endpoints.

## Key Concepts

### OpenID4VCI Protocol Overview

OpenID4VCI enables Issuers to issue verifiable credentials to Wallets using OAuth2 flows. The protocol extends OAuth2 with credential-specific scopes and token handling.

**Basic Protocol Flow:**

1. **Issuer** creates a credential offer (with or without pre-authorized code)
2. **Wallet** receives the offer and initiates authorization (if required)
3. **Wallet** exchanges authorization code or pre-authorized code for access token
4. **Wallet** requests credential using access token
5. **Issuer** validates token and issues credential

### Grant Types

The library supports two main grant types for credential issuance:

#### Authorization Code Grant

The standard OAuth2 authorization code flow:
- Wallet redirects user to issuer's authorization endpoint
- User authenticates and authorizes
- Issuer returns authorization code
- Wallet exchanges code for access token
- Wallet uses access token to request credential

**Use when:** User authentication and authorization is required before issuance.

#### Pre-Authorized Code Grant

Simplified flow for pre-authorized issuance:
- Issuer generates pre-authorized code and includes it in credential offer
- Wallet exchanges pre-authorized code directly for access token
- Wallet uses access token to request credential

**Use when:** Issuer has already authorized the issuance (e.g., via out-of-band process).

### OAuth2 Provider Architecture

The library provides a flexible, framework-agnostic OAuth2 provider:

```kotlin
interface OAuth2Provider {
    // Authorization endpoint
    fun createAuthorizeRequest(parameters: Map<String, String>): AuthorizeRequestResult
    suspend fun createAuthorizeResponse(request: AuthorizationRequest, session: Session): AuthorizeResponseResult
    
    // Token endpoint
    fun createAccessRequest(parameters: Map<String, String>, session: Session? = null): AccessRequestResult
    suspend fun createAccessResponse(request: AccessTokenRequest): AccessResponseResult
    
    // Response formatting (framework-agnostic)
    fun writeAuthorizeResponse(...): AuthorizeHttpResponse
    fun writeAccessResponse(...): AccessHttpResponse
}
```

### Configuration

The provider is configured via `OAuth2ProviderConfig`:

- **Request Validators** - Parse and validate authorization/token requests
- **Endpoint Handlers** - Handle grant type-specific logic
- **Repositories** - Store authorization codes and pre-authorized codes
- **Token Service** - Generate and sign access tokens

### JWT Access Tokens

The library includes JWT-based access token generation:
- Configurable signing keys and algorithms
- Standard JWT claims (iss, sub, aud, exp, etc.)
- Custom claims support for credential scopes

### Session Management

Sessions track the state of issuance flows:
- Authorization state
- Grant type
- Client information
- Scopes and credentials requested

## Assumptions and Dependencies

### Multiplatform Support

Works on JVM (Kotlin/Java), JavaScript, and iOS platforms (iOS requires `enableIosBuild=true` Gradle property).

### Dependencies

- **waltid-crypto** - For cryptographic operations and key management
- **Kotlinx Serialization** - For JSON serialization
- **Ktor Client** - For HTTP requests
- **Kotlinx Coroutines** - For asynchronous operations
- **Kotlinx DateTime** - For timestamp handling

### Protocol Version

This library implements **OpenID for Verifiable Credential Issuance 1.0** (final specification), building on OAuth2 2.0 and OpenID Connect.

## How to Use This Library

### Basic Usage

Build an OAuth2 provider with default handlers:

```kotlin
import id.walt.openid4vci.core.*
import id.walt.openid4vci.repository.authorization.DefaultAuthorizationCodeRepository
import id.walt.openid4vci.repository.preauthorized.DefaultPreAuthorizedCodeRepository
import id.walt.openid4vci.tokens.jwt.JwtAccessTokenService

// Create configuration
val config = OAuth2ProviderConfig(
    authorizeRequestValidator = DefaultAuthorizeRequestValidator(),
    accessRequestValidator = DefaultAccessRequestValidator(),
    authorizeEndpointHandlers = AuthorizeEndpointHandlers(),
    tokenEndpointHandlers = TokenEndpointHandlers(),
    authorizationCodeRepository = DefaultAuthorizationCodeRepository(),
    preAuthorizedCodeRepository = DefaultPreAuthorizedCodeRepository(),
    preAuthorizedCodeIssuer = PreAuthorizedCodeIssuer(),
    tokenService = JwtAccessTokenService(signingKey, issuer)
)

// Build provider
val provider = buildOAuth2Provider(config)

// Handle authorization request
val authRequestResult = provider.createAuthorizeRequest(requestParameters)
when (val result = authRequestResult) {
    is AuthorizeRequestResult.Success -> {
        val response = provider.createAuthorizeResponse(result.request, session)
        // Format and return HTTP response
        val httpResponse = provider.writeAuthorizeResponse(result.request, response.response)
    }
    is AuthorizeRequestResult.Error -> {
        // Handle error
    }
}

// Handle token request
val tokenRequestResult = provider.createAccessRequest(requestParameters)
when (val result = tokenRequestResult) {
    is AccessRequestResult.Success -> {
        val response = provider.createAccessResponse(result.request)
        // Format and return HTTP response
        val httpResponse = provider.writeAccessResponse(result.request, response.response)
    }
    is AccessRequestResult.Error -> {
        // Handle error
    }
}
```

### Custom Repositories

Implement your own storage backends:

```kotlin
class DatabaseAuthorizationCodeRepository : AuthorizationCodeRepository {
    override suspend fun store(code: String, session: Session, expiresAt: Instant) {
        // Store in database
    }
    
    override suspend fun retrieve(code: String): Session? {
        // Retrieve from database
    }
    
    override suspend fun remove(code: String) {
        // Remove from database
    }
}
```

### Custom Token Service

Implement custom access token generation:

```kotlin
class CustomAccessTokenService : AccessTokenService {
    override suspend fun generateAccessToken(
        clientId: String,
        scopes: Set<String>,
        session: Session
    ): String {
        // Custom token generation logic
    }
}
```

### Key Source Files

- `core/OAuth2Provider.kt` - Main provider interface
- `core/Builder.kt` - Provider builder and configuration
- `core/Config.kt` - Configuration data classes
- `granttypehandlers/` - Grant type-specific handlers
- `tokens/jwt/JwtAccessTokenService.kt` - JWT token generation
- `validation/` - Request validation logic
- `repository/` - Storage interfaces and default implementations

## Related Libraries

This library is part of the walt.id OpenID4VCI ecosystem:

### Supporting Libraries

- **[waltid-credentials](../../credentials/waltid-credentials/README.md)** - Credential models and formats
- **[waltid-crypto](../../crypto/waltid-crypto/README.md)** - Cryptographic operations and key management
- **[waltid-did](../../waltid-did/README.md)** - DID resolution and management

### Implementation Services

- **[waltid-issuer-api2](../../../waltid-services/waltid-issuer-api2/README.md)** - Complete Issuer API service using this library

## Documentation

- [OpenID for Verifiable Credential Issuance 1.0 Specification](https://openid.net/specs/openid-4-verifiable-credential-issuance-1_0.html)
- [OAuth 2.0 Authorization Framework](https://datatracker.ietf.org/doc/html/rfc6749)
- [walt.id Documentation](https://docs.walt.id)

## Join the community

* Connect and get the latest updates: [Discord](https://discord.gg/AW8AgqJthZ) | [Newsletter](https://walt.id/newsletter) | [YouTube](https://www.youtube.com/channel/UCXfOzrv3PIvmur_CmwwmdLA) | [LinkedIn](https://www.linkedin.com/company/walt-id/)
* Get help, request features and report bugs: [GitHub Issues](https://github.com/walt-id/waltid-identity/issues)
* Find more indepth documentation on our [docs site](https://docs.walt.id)

## License

Licensed under the [Apache License, Version 2.0](https://github.com/walt-id/waltid-identity/blob/main/LICENSE)

<div align="center">
<img src="../../../assets/walt-banner.png" alt="walt.id banner" />
</div>
