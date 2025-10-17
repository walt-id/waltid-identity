# waltid-openid4vp-clientidprefix

Kotlin Multiplatform library for handling [OpenID4VP 1.0 client id prefixes](https://openid.net/specs/openid-4-verifiable-presentations-1_0.html#name-defined-client-identifier-p).

## Usage

```kotlin
suspend fun main() {
    val rawClientId = "decentralized_identifier:did:example:12345"
    val authenticator = ClientIdPrefixAuthenticator

    // 1. Parse the raw string. This succeeds or fails based on syntax only.
    val parseResult = ClientIdPrefixParser.parse(rawClientId)

    val clientId = parseResult.getOrElse {
        println("❌ Parsing failed: ${it.message}")
        return
    }
    println("✅ Parsed successfully: $clientId")

    // 2. Create the request context for a specific transaction.
    val requestContext = RequestContext(
        clientId = rawClientId,
        requestObjectJws = "ey...", // A valid signed request object
        clientMetadataJson = "{...}"
    )

    // 3. Perform context-dependent authentication.
    println("\n--- Authenticating client ---")
    when (val authResult = authenticator.authenticate(clientId, requestContext)) {
        is ClientValidationResult.Success -> println("✅ Authentication successful!")
        is ClientValidationResult.Failure -> println("❌ Authentication failed: ${authResult.error.message}")
    }
}
```
