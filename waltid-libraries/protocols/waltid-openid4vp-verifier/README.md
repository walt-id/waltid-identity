<div align="center">
<h1>OpenID4VP Verifier Implementation</h1>
 <span>by </span><a href="https://walt.id">walt.id</a>
 <p>Complete server-side implementation of the Verifier role for OpenID4VP 1.0</p>

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

A complete server-side implementation of the **Verifier** role as defined in **OpenID for Verifiable Presentations (OpenID4VP) 1.0**. This library provides all the necessary tools to request, receive, and validate verifiable presentations from a Holder's Wallet.

Learn more about OpenID4VP [here](https://docs.walt.id/concepts/data-exchange-protocols/openid4vp).

**This library handles:**
- Creating and managing verification sessions
- Generating authorization requests with DCQL queries
- Validating presentations across multiple credential formats (SD-JWT VC, W3C VC, mdoc)
- Checking DCQL fulfillment and credential set requirements
- Session state management and lifecycle

## Main Purpose

This library enables you to build Verifier applications that request and verify digital credentials from Wallets. It's designed to be a flexible backend component that integrates into your Verifier application.

**Use this library when:**
- Building a service that needs to verify user credentials
- Implementing OpenID4VP 1.0 verifier functionality
- Creating verification flows for age verification, identity proofing, or access control
- Building kiosk or web applications that request credentials

## Key Concepts

### Verification Session

A `Verification2Session` represents a single verification flow from creation to completion. It contains:
- Session ID and status
- Authorization request with DCQL query
- Validation results and presented credentials
- Expiration and retention dates

### DCQL Query

The Digital Credentials Query Language (DCQL) query specifies what credentials you need. It defines:
- Credential formats (e.g., `jwt_vc_json`, `dc+sd-jwt`, `mso_mdoc`)
- Required claims with JSON paths
- Claim value filters
- Credential sets (combinations of credentials)

See [waltid-dcql](../../credentials/waltid-dcql/README.md) for complete DCQL documentation.

### Response Modes

The library supports multiple response delivery modes:
- **Same-Device Flow**: User interacts with your application and Wallet on the same device (redirect-based)
- **Cross-Device Flow**: User interacts with your application on one device and Wallet on another (QR code + `direct_post`)

### Presentation Validation

The library validates presentations in multiple formats:
- **SD-JWT VC** (`dc+sd-jwt`) - Including selective disclosure verification
- **W3C VC** (`jwt_vc_json`) - JWT-signed Verifiable Credentials
- **mdoc** (`mso_mdoc`) - ISO mobile documents with full chain validation

## Assumptions and Dependencies

### Multiplatform Support

Works on JVM (Kotlin/Java), JavaScript, and iOS platforms (iOS requires `enableIosBuild=true` Gradle property).

### Dependencies

- **waltid-openid4vp** - Core OpenID4VP models
- **waltid-dcql** - DCQL query models and fulfillment checking
- **waltid-digital-credentials** - Credential format validators and parsers
- **waltid-verification-policies2** - Verification policies for credential validation

### Storage Requirements

You must provide session storage (database, cache, or in-memory). The library manages session state but doesn't persist it.

## How to Use This Library

### Integration Workflow

Here's a step-by-step guide to integrating the verifier library into your application.

#### Step 1: Create a Verification Session

Create a `Verification2Session` when you need to request credentials. Define your requirements using DCQL.

```kotlin
import id.walt.openid4vp.verifier.Verifier2Manager
import id.walt.dcql.models.*

// 1. Define your credential requirements using DCQL
val dcqlQuery = DcqlQuery(
    credentials = listOf(
        CredentialQuery(
            id = "eu-pid", // An ID to reference this query
            format = CredentialFormat.DC_SD_JWT,
            claims = listOf(
                ClaimsQuery(path = listOf("given_name")),
                ClaimsQuery(path = listOf("family_name")),
                ClaimsQuery(path = listOf("birthdate"))
            )
        )
    )
)

// 2. Set up the session
val setup = CrossDeviceFlow(core = GeneralFlowConfig(dcqlQuery = dcqlQuery))

// 3. Create the session
val newSession = Verifier2Manager.createVerificationSession(
    setup = setup,
    clientId = "did:key:z6MkrpCPDs58tC97bSotN5wMv2fAFsAm5is8x8Wc7m7yTj1y", // Your Verifier's identifier
    uriPrefix = "https://verifier.example.com/api/v2/verification" // Base URL for your endpoints
)

// 4. Store the session (e.g., in a database or cache)
sessionCache.put(newSession.id, newSession)

// 5. Get URLs for the Wallet
val response = newSession.toSessionCreationResponse()
val authorizationRequestUrl = response.fullAuthorizationRequestUrl // For same-device
val bootstrapUrl = response.bootstrapAuthorizationRequestUrl // For cross-device (render as QR code)
```

#### Step 2: Expose Request Endpoints

Your application must expose HTTP endpoints for the Wallet to interact with.

**Authorization Request Endpoint** (`/api/v2/verification/{sessionId}/request`):
```kotlin
// Example using Ktor
get("/api/v2/verification/{sessionId}/request") {
    val sessionId = call.parameters["sessionId"]!!
    val session = sessionCache.get(sessionId) // Retrieve from your storage
    call.respondText(session.authorizationRequest.toJwt())
}
```

**Response Endpoint** (`/api/v2/verification/{sessionId}/response`):
This is where the Wallet POSTs the `vp_token`. This is where validation happens (see Step 3).

#### Step 3: Handle Presentation Submission

When the Wallet POSTs the presentation, parse it and validate each credential.

```kotlin
import id.walt.openid4vp.verifier.Verifier2PresentationValidator
import id.walt.openid4vp.verifier.Verifier2Response

// Example using Ktor
post("/api/v2/verification/{sessionId}/response") {
    val sessionId = call.parameters["sessionId"]!!
    val session = sessionCache.get(sessionId)
    val params = call.receiveParameters()

    val vpTokenString = params["vp_token"] 
        ?: return@post call.respond(Verifier2Response.MALFORMED_VP_TOKEN)
    val state = params["state"] 
        ?: return@post call.respond(Verifier2Response.MISSING_STATE_PARAMETER)

    // Validate state
    if (state != session.authorizationRequest.state) {
        return@post call.respond(Verifier2Response.INVALID_STATE_PARAMETER)
    }

    // Parse and validate presentations
    val vpToken = Json.decodeFromString<Map<String, List<String>>>(vpTokenString)
    val successfullyValidatedQueryIds = mutableSetOf<String>()
    val allValidatedCredentials = mutableMapOf<String, List<DigitalCredential>>()

    // Loop through each credential query
    for ((queryId, presentations) in vpToken) {
        val credentialQuery = session.authorizationRequest.dcqlQuery!!
            .credentials.first { it.id == queryId }

        for (presentationString in presentations) {
            val validationResult = Verifier2PresentationValidator.validatePresentation(
                presentationString = presentationString,
                expectedFormat = credentialQuery.format,
                expectedAudience = session.authorizationRequest.clientId!!,
                expectedNonce = session.authorizationRequest.nonce!!,
                responseUri = session.authorizationRequest.responseUri,
                originalClaimsQuery = credentialQuery.claims
            )

            if (validationResult.isSuccess) {
                successfullyValidatedQueryIds.add(queryId)
                allValidatedCredentials[queryId] = validationResult.getOrThrow().credentials
            } else {
                // Handle validation failure
                session.status = Verification2Session.VerificationSessionStatus.FAILED
                sessionCache.put(session.id, session)
                return@post call.respond(Verifier2Response.Verifier2Error.PRESENTATION_VALIDATION_FAILED)
            }
        }
    }

    // Check overall DCQL fulfillment (especially credential_sets)
    val overallFulfillment = DcqlFulfillmentChecker.checkOverallDcqlFulfillment(
        dcqlQuery = session.authorizationRequest.dcqlQuery!!,
        successfullyValidatedQueryIds = successfullyValidatedQueryIds
    )

    if (!overallFulfillment) {
        session.status = Verification2Session.VerificationSessionStatus.FAILED
        sessionCache.put(session.id, session)
        return@post call.respond(Verifier2Response.Verifier2Error.REQUIRED_CREDENTIALS_NOT_PROVIDED)
    }

    // Verification successful!
    session.status = Verification2Session.VerificationSessionStatus.SUCCESSFUL
    session.presentedCredentials = allValidatedCredentials
    sessionCache.put(session.id, session)

    // Use validated credentials for your business logic
    call.respond(HttpStatusCode.OK, mapOf("status" to "success"))
}
```

### Key Components

| Component | Description | Usage |
|-----------|-------------|-------|
| `Verifier2Manager` | Main entry point for creating verification sessions | `Verifier2Manager.createVerificationSession()` |
| `Verification2Session` | State object for a verification flow | Store and retrieve by session ID |
| `Verifier2PresentationValidator` | Validates presentations in various formats | `validatePresentation()` |
| `DcqlFulfillmentChecker` | Checks if presentations satisfy DCQL requirements | `checkOverallDcqlFulfillment()` |

### Supported Credential Formats

- **SD-JWT VC** (`dc+sd-jwt`) - With selective disclosure verification
- **W3C VC** (`jwt_vc_json`) - JWT-signed Verifiable Credentials
- **mdoc** (`mso_mdoc`) - ISO mobile documents with certificate chain validation

### Error Handling

The library provides `Verifier2Response` error types for common failures:
- `MALFORMED_VP_TOKEN` - Invalid `vp_token` format
- `MISSING_STATE_PARAMETER` - Missing `state` in response
- `INVALID_STATE_PARAMETER` - `state` doesn't match session
- `PRESENTATION_VALIDATION_FAILED` - Presentation validation failed
- `REQUIRED_CREDENTIALS_NOT_PROVIDED` - DCQL fulfillment check failed

## Related Libraries

- **[waltid-openid4vp](../waltid-openid4vp/README.md)** - Core OpenID4VP models and types
- **[waltid-openid4vp-clientidprefix](../waltid-openid4vp-clientidprefix/README.md)** - Client ID prefix parsing and validation
- **[waltid-openid4vp-verifier-openapi](../waltid-openid4vp-verifier-openapi/README.md)** - OpenAPI schema generation utilities
- **[waltid-dcql](../../credentials/waltid-dcql/README.md)** - Digital Credentials Query Language models and fulfillment checking
- **[waltid-digital-credentials](../../credentials/waltid-digital-credentials/README.md)** - Credential format validators and parsers
- **[waltid-verification-policies2](../../credentials/waltid-verification-policies2/README.md)** - Verification policies for credential validation

## Join the community

* Connect and get the latest updates: [Discord](https://discord.gg/AW8AgqJthZ) | [Newsletter](https://walt.id/newsletter) | [YouTube](https://www.youtube.com/channel/UCXfOzrv3PIvmur_CmwwmdLA) | [LinkedIn](https://www.linkedin.com/company/walt-id/)
* Get help, request features and report bugs: [GitHub Issues](https://github.com/walt-id/waltid-identity/issues)
* Find more indepth documentation on our [docs site](https://docs.walt.id)

## License

Licensed under the [Apache License, Version 2.0](https://github.com/walt-id/waltid-identity/blob/main/LICENSE)

<div align="center">
<img src="../../../assets/walt-banner.png" alt="walt.id banner" />
</div>
