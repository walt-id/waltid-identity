<div align="center">
<h1>OpenID4VP Verifier OpenAPI Helper</h1>
 <span>by </span><a href="https://walt.id">walt.id</a>
 <p>OpenAPI schema generation and example request bodies for OpenID4VP Verifier endpoints</p>

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

Helper library providing OpenAPI schema generation and example request bodies for OpenID4VP Verifier endpoints. Useful for API documentation, testing, and integration examples.

## Main Purpose

This library helps Verifier developers by providing:
- Pre-built OpenAPI schemas for verification session endpoints
- Example request bodies for common credential formats (SD-JWT VC, W3C VC, mdoc)
- Ready-to-use examples for testing and documentation

## Usage

### Generate OpenAPI Schema

```kotlin
import id.walt.openid4vp.verifier.openapi.VerificationSessionCreateOpenApi

val schema = VerificationSessionCreateOpenApi.generateOpenApiSchema()
println(schema.toJsonString())
```

### Use Pre-built Examples

```kotlin
import id.walt.openid4vp.verifier.openapi.Verifier2OpenApiExamples

// SD-JWT VC example
val sdJwtExample = Verifier2OpenApiExamples.basicExample

// W3C VC JSON example
val w3cExample = Verifier2OpenApiExamples.exampleOf(
    Verifier2OpenApiExamples.nestedPresentationRequestW3C
)

// mdoc example with VICAL policies
val mdocExample = Verifier2OpenApiExamples.exampleOf(
    Verifier2OpenApiExamples.VicalPolicyValues
)
```

### Available Examples

- `basicExample` - SD-JWT VC with common claims
- `w3cTypeValues` - Simple W3C VC with type filtering
- `nestedPresentationRequestW3C` - W3C VC with nested claim paths
- `W3CWithClaimsAndValues` - W3C VC with claim value filtering
- `W3CWithoutClaims` - W3C VC without specific claims
- `VicalPolicyValues` - mdoc with VICAL policy validation

### Integration Example

```kotlin
import id.walt.openid4vp.verifier.openapi.Verifier2OpenApiExamples

// Use in API documentation
post("/api/v2/verification/session") {
    val example = Verifier2OpenApiExamples.basicExample
    // Use example for documentation or testing
}

// Use in tests
@Test
fun testWithExample() {
    val session = Verifier2Manager.createVerificationSession(
        setup = Verifier2OpenApiExamples.basicExample,
        clientId = "did:key:z6M...",
        uriPrefix = "https://verifier.example.com/api/v2/verification"
    )
}
```

## Related Libraries

- **[waltid-openid4vp-verifier](../waltid-openid4vp-verifier/README.md)** - Core Verifier implementation
- **[waltid-openid4vp](../waltid-openid4vp/README.md)** - Core OpenID4VP models

## Join the community

* Connect and get the latest updates: [Discord](https://discord.gg/AW8AgqJthZ) | [Newsletter](https://walt.id/newsletter) | [YouTube](https://www.youtube.com/channel/UCXfOzrv3PIvmur_CmwwmdLA) | [LinkedIn](https://www.linkedin.com/company/walt-id/)
* Get help, request features and report bugs: [GitHub Issues](https://github.com/walt-id/waltid-identity/issues)
* Find more indepth documentation on our [docs site](https://docs.walt.id)

## License

Licensed under the [Apache License, Version 2.0](https://github.com/walt-id/waltid-identity/blob/main/LICENSE)

<div align="center">
<img src="../../../assets/walt-banner.png" alt="walt.id banner" />
</div>
