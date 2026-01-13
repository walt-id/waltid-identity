<div align="center">
<h1>walt.id Ktor AuthNZ</h1>
 <span>by </span><a href="https://walt.id">walt.id</a>
 <p>Flexible authentication and authorization framework for Ktor applications</p>

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

`waltid-ktor-authnz` is a comprehensive authentication and authorization framework for Ktor applications. It provides a flexible, multi-method authentication system that supports complex authentication flows, session management, and account identification across various authentication mechanisms.

## Main Purpose

This library enables developers to build secure authentication systems in Ktor applications with:

- **Multiple Authentication Methods**: Support for username/password, email/password, TOTP, LDAP, RADIUS, OIDC, JWT, Web3, and Verifiable Credentials
- **Multi-Step Authentication Flows**: Chain multiple authentication methods together (e.g., username/password → TOTP)
- **Flexible Session Management**: Session-based authentication with configurable token handling
- **Account Management**: Unified account abstraction with multiple identifier types
- **Password Security**: Configurable password hashing algorithms (PBKDF2, SHA, etc.)

## Key Concepts

### Authentication Methods

Authentication methods are pluggable components that handle specific authentication mechanisms:

- **UserPass**: Username and password authentication
- **EmailPass**: Email and password authentication
- **TOTP**: Time-based One-Time Password (2FA)
- **LDAP**: Lightweight Directory Access Protocol authentication
- **RADIUS**: Remote Authentication Dial-In User Service
- **OIDC**: OpenID Connect authentication
- **JWT**: JSON Web Token validation
- **Web3**: Web3 wallet signature-based authentication
- **VerifiableCredential**: Verifiable credential-based authentication

### Authentication Flows

Authentication flows define sequences of authentication methods that must be completed:

```json
{
  "method": "userpass",
  "continue": [{
    "method": "totp",
    "success": true
  }]
}
```

This flow requires username/password authentication, followed by TOTP verification before authentication succeeds.

### Sessions

Authentication sessions track the progress of multi-step authentication flows:

- **Session States**: `INIT`, `CONTINUE_NEXT_FLOW`, `CONTINUE_NEXT_STEP`, `SUCCESS`, `FAILURE`
- **Session Tokens**: Generated upon successful authentication
- **Session Storage**: Configurable storage backends (in-memory, Redis/Valkey)

### Account Store

The account store manages account data and authentication method-specific stored data:

- **Account**: Core account entity (id, name)
- **Stored Data**: Method-specific data (e.g., password hashes, TOTP secrets)
- **EditableAccountStore**: Interface for account management operations

## Assumptions and Dependencies

### Platform Support
- **Ktor Framework**: Requires Ktor server framework
- **Kotlin Coroutines**: Uses coroutines for asynchronous operations

## Usage

### Basic Setup

1. **Configure the KtorAuthnzManager**:

```kotlin
import id.walt.ktorauthnz.KtorAuthnzManager
import id.walt.ktorauthnz.accounts.ExampleAccountStore
import id.walt.ktorauthnz.sessions.InMemorySessionStore

// Configure account store
KtorAuthnzManager.accountStore = ExampleAccountStore()

// Configure session store (or use Redis/Valkey for production)
KtorAuthnzManager.sessionStore = InMemorySessionStore()
```

2. **Register Authentication Methods**:

```kotlin
import id.walt.ktorauthnz.methods.*
import id.walt.ktorauthnz.methods.AuthMethodManager

// Methods are auto-registered, but you can register custom ones:
AuthMethodManager.registerAuthenticationMethod(CustomAuthMethod())
```

3. **Define Authentication Flows**:

```kotlin
import id.walt.ktorauthnz.flows.AuthFlow

val flow = AuthFlow(
    method = "userpass",
    continueWith = setOf(
        AuthFlow(
            method = "totp",
            success = true
        )
    )
)
```

4. **Register Routes in Ktor**:

```kotlin
import id.walt.ktorauthnz.*
import id.walt.ktorauthnz.methods.*
import io.ktor.server.routing.*

routing {
    // Register authentication methods
    registerAuthenticationMethods(
        methods = listOf(UserPass, TOTP),
        authContext = { 
            AuthContext(
                initialFlow = flow,
                implicitSessionGeneration = false
            )
        }
    )
    
    // Protected routes
    authenticate {
        get("/protected") {
            call.respond("Authenticated!")
        }
    }
}
```

### Authentication Flow Examples

#### Single-Step Flow (Implicit Session)

```http
POST /auth/flows/global-implicit1/userpass
Content-Type: application/json

{"username": "alice", "password": "password123"}
```

Response:
```json
{
  "session_id": "cf7951d3-54e7-4c3f-8a11-0b6d06dd7503",
  "status": "OK",
  "token": "70d2ac4a-3e69-4475-aa8b-9dc2a17e2a6e"
}
```

#### Multi-Step Flow (Explicit Session)

1. **Start Session**:
```http
POST /auth/flows/global-explicit2/start
```

Response:
```json
{
  "session_id": "caf4d705-be57-4c6d-b265-8fed79e061e1",
  "status": "CONTINUE_NEXT_FLOW",
  "next_method": ["userpass"]
}
```

2. **Authenticate with Username/Password**:
```http
POST /auth/flows/global-explicit2/caf4d705-be57-4c6d-b265-8fed79e061e1/userpass
Content-Type: application/json

{"username": "alice", "password": "password123"}
```

Response:
```json
{
  "session_id": "caf4d705-be57-4c6d-b265-8fed79e061e1",
  "status": "CONTINUE_NEXT_FLOW",
  "next_method": ["totp"]
}
```

3. **Complete with TOTP**:
```http
POST /auth/flows/global-explicit2/caf4d705-be57-4c6d-b265-8fed79e061e1/totp
Content-Type: application/json

{"code": "768944"}
```

Response:
```json
{
  "session_id": "caf4d705-be57-4c6d-b265-8fed79e061e1",
  "status": "OK",
  "token": "26755b7d-c369-4341-aee5-ed40a68bce9e"
}
```

### Custom Authentication Methods

To add a custom authentication method:

1. **Create the Authentication Method**:

```kotlin
import id.walt.ktorauthnz.methods.AuthenticationMethod
import id.walt.ktorauthnz.AuthContext
import io.ktor.server.routing.*

object CustomAuth : AuthenticationMethod("custom") {
    override fun Route.registerAuthenticationRoutes(
        authContext: ApplicationCall.() -> AuthContext,
        functionAmendments: Map<AuthMethodFunctionAmendments, suspend (Any) -> Unit>?
    ) {
        post("custom") {
            val session = call.getAuthSession(authContext)
            // Implement authentication logic
            call.handleAuthSuccess(session, authContext(), accountId = "account-123")
        }
    }
}
```

2. **Register the Method**:

```kotlin
AuthMethodManager.registerAuthenticationMethod(CustomAuth)
```

### Session Storage

#### In-Memory (Development)

```kotlin
import id.walt.ktorauthnz.sessions.InMemorySessionStore

KtorAuthnzManager.sessionStore = InMemorySessionStore()
```

#### Redis/Valkey (Production)

```kotlin
import id.walt.ktorauthnz.sessions.ValkeySessionStore

KtorAuthnzManager.sessionStore = ValkeySessionStore(
    host = "localhost",
    port = 6379
)
```

### Password Hashing

Configure password hashing algorithms:

```kotlin
import id.walt.ktorauthnz.security.PasswordHashingConfiguration
import id.walt.ktorauthnz.security.algorithms.PBKDF2PasswordHashAlgorithm

KtorAuthnzManager.passwordHashingConfig = PasswordHashingConfiguration(
    algorithm = PBKDF2PasswordHashAlgorithm(
        iterations = 100000,
        keyLength = 256
    )
)
```

## Advanced Features

### Flow Amendments

Flow amendments allow customizing authentication method behavior:

```kotlin
val amendments = mapOf(
    AuthMethodFunctionAmendments.BEFORE_AUTH to { data: Any ->
        // Custom logic before authentication
    },
    AuthMethodFunctionAmendments.AFTER_AUTH to { data: Any ->
        // Custom logic after authentication
    }
)

registerAuthenticationMethod(
    method = UserPass,
    authContext = { AuthContext(...) },
    functionAmendments = amendments
)
```

### Account Registration

Some authentication methods support automatic registration:

```kotlin
// Methods that support registration
if (authMethod.supportsRegistration) {
    // Register registration routes
    authMethod.registerRegistrationRoutes(authContext)
}
```

### External ID Mapping

Map external identifiers to internal session IDs:

```kotlin
session.storeExternalIdMapping(
    namespace = "oidc",
    externalId = "external-user-id"
)
```

## Examples

See the test directory for complete examples:

- **AuthFlowExampleApplication**: Complete authentication flow example
- **OidcExample**: OpenID Connect integration example
- **KtorAuthnzE2ETest**: End-to-end authentication test

## Further Documentation

Additional documentation is available in the `docs/` directory:

- [1.Quickstart.md](docs/1.Quickstart.md): Quick start guide
- [new-auth-method.md](docs/new-auth-method.md): Guide for adding new authentication methods
- [oidc.md](docs/oidc.md): OpenID Connect configuration
- [radius.md](docs/radius.md): RADIUS configuration
- [account-flow-amendments.md](docs/account-flow-amendments.md): Flow amendment documentation

## Join the community

* Connect and get the latest updates: [Discord](https://discord.gg/AW8AgqJthZ) | [Newsletter](https://walt.id/newsletter) | [YouTube](https://www.youtube.com/channel/UCXfOzrv3PIvmur_CmwwmdLA) | [LinkedIn](https://www.linkedin.com/company/walt-id/)
* Get help, request features and report bugs: [GitHub Issues](https://github.com/walt-id/waltid-identity/issues)
* Find more indepth documentation on our [docs site](https://docs.walt.id)

## License

Licensed under the [Apache License, Version 2.0](https://github.com/walt-id/waltid-identity/blob/main/LICENSE)

<div align="center">
<img src="../../../assets/walt-banner.png" alt="walt.id banner" />
</div>

