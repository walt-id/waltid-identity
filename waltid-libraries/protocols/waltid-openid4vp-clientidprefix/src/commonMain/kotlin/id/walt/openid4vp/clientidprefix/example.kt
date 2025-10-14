package id.walt.openid4vp.clientidprefix

import id.walt.openid4vp.clientidprefix.prefixes.ClientId

suspend fun main() {
    // Example 1: A valid x509_san_dns request
    val validX509Context = RequestContext(
        clientId = "x509_san_dns:client.example.org",
        clientMetadataJson = "{\"vp_formats_supported\":{...}}",
        requestObjectJws = "ey..." // A valid JWS with proper x5c header
    )

    // The parser creates the correct handler instance
    val x509Handler = ClientId.Parser.parse(validX509Context)

    // The logic is called polymorphically
    when (val result = x509Handler.validate()) {
        is ClientValidationResult.Success -> println("✅ Success! Client metadata: ${result.clientMetadata.rawJson}")
        is ClientValidationResult.Failure -> println("❌ Failure: ${result.error.message}")
    }

    println("---")

    // Example 2: A pre-registered client that isn't found
    val preRegContext = RequestContext(clientId = "unknown-client-app")
    val preRegHandler = ClientId.Parser.parse(preRegContext)

    when (val result = preRegHandler.validate()) {
        is ClientValidationResult.Success -> println("✅ Success! Client metadata: ${result.clientMetadata.rawJson}")
        is ClientValidationResult.Failure -> println("❌ Failure: ${result.error.message}") // Will fail with not found error
    }
}
