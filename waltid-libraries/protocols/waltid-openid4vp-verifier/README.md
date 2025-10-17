# walt.id OpenID4VP Verifier Module

A server-side implementation of the **Verifier** role as defined in the **OpenID for Verifiable
Presentations (OpenID4VP) 1.0** specification. It provides all the necessary tools to request,
receive, and validate verifiable presentations from a Holder's Wallet with the OpenID4VP 1.0 flow.

This module is designed to be a flexible backend component to integrate into Verifier
applications to verify user credentials.

## Core Concepts

To understand this module, it's helpful to be familiar with the OpenID4VP specification. Here are
the key ideas:

* **Verifier (Application)**: An entity that needs to verify credentials. This module
  implements the Verifier's logic.
* **Holder (End-User)**: The person who possesses the credentials.
* **Wallet**: The Holder's application (e.g., a mobile app) that stores credentials and creates
  presentations.
* **Presentation**: Data derived from one or more credentials, created by the Wallet for a specific
  Verifier.
* **DCQL (Digital Credentials Query Language)**: A JSON-based language used by the Verifier to
  specify exactly which credentials and claims it needs.
* **Same-Device Flow**: The user interacts with your application and their Wallet on the same
  device (e.g., a mobile phone).
* **Cross-Device Flow**: The user interacts with your application on one device (e.g., a desktop
  browser) and uses their Wallet on another (e.g., a mobile phone), typically by scanning a QR code.

-----

## Key Features

This module is packed with features that make implementing a Verifier simple and robust.

* **Specification**: Implements the final version of the **OpenID for
  Verifiable Presentations 1.0.
* **Multi-Format Support**: Natively validates presentations in various formats, including:
    * **SD-JWT VC** (`dc+sd-jwt`)
    * **ISO mdoc** (`mso_mdoc`) for Mobile Driver's Licenses (mDL) and other mobile
      documents
    * **W3C Verifiable Credentials as JWT** (`jwt_vc_json`)
* **Credential Queries**: Full support for the **Digital Credentials Query Language (
  DCQL)**. You can:
    * Request specific claims from credentials.
    * Define complex presentation requirements using `credential_sets`, including
      optional and required groups of credentials.
* **Security**: Handles various security checks out-of-the-box:
    * **Nonce validation** to prevent replay attacks.
    * **Audience validation** to ensure the presentation was intended for your
      application (`client_id` or `origin`).
    * Cryptographic validation of presentation and credential signatures.
* **Flexible Session Management**: Provides a stateful session manager (`Verifier2Manager`) to
  handle the entire verification lifecycle, from request creation to final validation.
* **Flow Agnostic**: Easily configure for both **Same-Device** and **Cross-Device**
  presentation flows.

-----

## Core Abstractions

Your application will primarily interact with these key components:

| Class/Object                     | Description                                                                                                                                                                | Source File                         |
|----------------------------------|----------------------------------------------------------------------------------------------------------------------------------------------------------------------------|-------------------------------------|
| `Verifier2Manager`               | The main entry point for creating and managing verification sessions. Use this to start a new verification flow.                                                           | `Verifier2Manager.kt`               |
| `Verification2Session`           | The central state object for a single verification flow. It holds the session ID, status, authorization request, policies, and the final results.                          | `Verification2Session.kt`           |
| `DcqlQuery`                      | A data class representing the DCQL query you send to the Wallet to define your credential requirements.                                                                    | `DcqlFulfillmentChecker.kt`         |
| `Verifier2PresentationValidator` | The core validation engine. It dispatches the raw presentation string to the appropriate format-specific validator (`SdJwt...`, `Mdoc...`, etc.).                          | `Verifier2PresentationValidator.kt` |
| `DcqlFulfillmentChecker`         | A utility that checks if the set of successfully validated presentations satisfies the rules defined in the original `DcqlQuery`, especially when using `credential_sets`. | `DcqlFulfillmentChecker.kt`         |

-----

## Usage Workflow

Here’s a step-by-step guide to integrating the verifier module into your application.

### Step 1: Create a Verification Session

The flow begins when you create a `Verification2Session`. This is done by calling
`Verifier2Manager.createVerificationSession`. You need to provide a `DcqlQuery` that specifies what
you want to verify.

```kotlin
// 1. Define your credential requirements using DCQL
val dcqlQuery = DcqlQuery(
    credentials = listOf(
        CredentialQuery(
            id = "eu-pid", // An ID to reference this query
            format = CredentialFormat.DC_SD_JWT,
            meta = MsoMdocMeta("my.doctype.here"),
            claims = listOf(
                ClaimsQuery(path = listOf("given_name")),
                ClaimsQuery(path = listOf("family_name")),
                ClaimsQuery(path = listOf("birthdate"))
            )
        )
    )
)

// 2. Set up the session
val setup = VerificationSessionSetup(dcqlQuery = dcqlQuery)

// 3. Create the session
val newSession = Verifier2Manager.createVerificationSession(
    setup = setup,
    clientId = "did:key:z6MkrpCPDs58tC97bSotN5wMv2fAFsAm5is8x8Wc7m7yTj1y", // Your Verifier's identifier
    uriPrefix = "https://verifier.example.com/api/v2/verification" // The base URL for your endpoints
)

// 4. Store the session object (e.g., in a database or in-memory cache) keyed by newSession.id
// sessionCache.put(newSession.id, newSession)

// 5. Get the response, which contains the URLs for the Wallet
val response = newSession.toSessionCreationResponse()
val authorizationRequestUrl = response.fullAuthorizationRequestUrl // For same-device
val bootstrapUrl = response.bootstrapAuthorizationRequestUrl // For cross-device (render as QR code)

// Send `authorizationRequestUrl` or `bootstrapUrl` to your frontend.
```

### Step 2: Expose Request Endpoints

Your application must expose two HTTP endpoints based on the URLs generated in Step 1.

1. **Authorization Request Endpoint** (`/api/v2/verification/{sessionId}/request`):
   When the Wallet fetches this URL, it should return the Authorization Request.

   ```kotlin
   // Example using Ktor
   get("/api/v2/verification/{sessionId}/request") {
       val sessionId = call.parameters["sessionId"]!!
       val session = sessionCache.get(sessionId) // Retrieve the session
       // The module pre-formats the request JWT for you
       call.respondText(session.authorizationRequest.toJwt())
   }
   ```

2. **Response Endpoint** (`/api/v2/verification/{sessionId}/response`):
   This is the `response_uri` where the Wallet will `POST` the `vp_token` after the user gives
   consent. This is where the core verification logic happens.

### Step 3: Handle the Presentation Submission

When a `POST` request hits your response endpoint, you need to parse the payload, retrieve the
session, and start the validation process.

```kotlin
// Example using Ktor
post("/api/v2/verification/{sessionId}/response") {
    val sessionId = call.parameters["sessionId"]!!
    val session = sessionCache.get(sessionId)
    val params = call.receiveParameters()

    val vpTokenString =
        params["vp_token"] ?: return@post call.respond(Verifier2Response.MALFORMED_VP_TOKEN)
    val state =
        params["state"] ?: return@post call.respond(Verifier2Response.MISSING_STATE_PARAMETER)

    // Check if the state matches the one in our session
    if (state != session.authorizationRequest.state) {
        return@post call.respond(Verifier2Response.INVALID_STATE_PARAMETER)
    }

    // Now, let's validate the presentation...
    // See Step 4
}
```

### Step 4: Validate Each Presentation in the `vp_token`

The `vp_token` can contain multiple presentations for different requested credentials. You must loop
through them and validate each one.

```kotlin
// Inside your response handler from Step 3...

val vpToken = Json.decodeFromString<Map<String, List<String>>>(vpTokenString)
val successfullyValidatedQueryIds = mutableSetOf<String>()
val allValidatedCredentials = mutableMapOf<String, List<DigitalCredential>>()

// Loop through each entry in the vp_token. The key is the `id` from your DCQL query.
for ((queryId, presentations) in vpToken) {
    val credentialQuery =
        session.authorizationRequest.dcqlQuery!!.credentials.first { it.id == queryId }

    for (presentationString in presentations) {
        val validationResult = Verifier2PresentationValidator.validatePresentation(
            presentationString = presentationString,
            expectedFormat = credentialQuery.format,
            expectedAudience = session.authorizationRequest.clientId!!, // Your Verifier ID
            expectedNonce = session.authorizationRequest.nonce!!,
            responseUri = session.authorizationRequest.responseUri,
            originalClaimsQuery = credentialQuery.claims
        )

        if (validationResult.isSuccess) {
            log.info { "Successfully validated presentation for query ID: $queryId" }
            successfullyValidatedQueryIds.add(queryId)
            allValidatedCredentials[queryId] = validationResult.getOrThrow().credentials
        } else {
            log.warn { "Presentation validation failed for query ID $queryId: ${validationResult.exceptionOrNull()?.message}" }
            // Decide how to handle partial failures. For now, we'll fail the whole session.
            session.status = Verification2Session.VerificationSessionStatus.FAILED
            // sessionCache.put(session.id, session)
            Verifier2Response.Verifier2Error.PRESENTATION_VALIDATION_FAILED.throwAsError()
            // or: return@post call.respond("...")
        }
    }
}
```

### Step 5: Check Overall DCQL Fulfillment

After validating the individual presentations, you need to check if the *set* of received
presentations satisfies the overall `DcqlQuery`, especially the rules in `credential_sets`.

```kotlin
// Inside your response handler, after the validation loop...

val overallFulfillment = DcqlFulfillmentChecker.checkOverallDcqlFulfillment(
    dcqlQuery = session.authorizationRequest.dcqlQuery!!,
    successfullyValidatedQueryIds = successfullyValidatedQueryIds
)

if (!overallFulfillment) {
    log.warn { "DCQL fulfillment check failed for session ${session.id}." }
    session.status = Verification2Session.VerificationSessionStatus.FAILED
    // sessionCache.put(session.id, session)
    Verifier2Response.Verifier2Error.REQUIRED_CREDENTIALS_NOT_PROVIDED.throwAsError()
    // or: return@post call.respond("...")
}
```

### Step 6: Finalize the Session

If all checks pass, the verification is successful\! Update the session status and proceed with your
application logic.

```kotlin
// At the end of your response handler...

log.info { "Verification successful for session ${session.id}!" }
session.status = Verification2Session.VerificationSessionStatus.SUCCESSFUL
session.presentedCredentials = allValidatedCredentials
// sessionCache.put(session.id, session)

// You can now use the validated credentials in `allValidatedCredentials`
// for your business logic.

call.respond(HttpStatusCode.OK, mapOf("status" to "success"))
```
