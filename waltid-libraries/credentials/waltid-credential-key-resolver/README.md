<div align="center">
<h1>Kotlin Multiplatform JWT Key Resolver</h1>
 <span>by </span><a href="https://walt.id">walt.id</a>
 <p>Resolve signing keys from JWT credentials and presentations using DID, x5c, or HTTPS well-known metadata</p>

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

## Overview

This library provides a unified interface for resolving the public key used to sign a JWT credential or presentation. It supports multiple key resolution methods and automatically selects the appropriate one based on the JWT's `iss`/`issuer` claim and header fields.

## Features

- **DID Resolution** — Resolves keys from DID URLs using the walt.id DID library
- **X.509 Certificate Chain (x5c)** — Extracts keys from inline `x5c` JWT headers with optional chain validation
- **HTTPS Well-Known** — Fetches keys from JWT VC Issuer Metadata (`.well-known/jwt-vc-issuer`)
- **Automatic Method Selection** — Chooses the appropriate resolver based on JWT content
- **Fallback Support** — Falls back to x5c if DID resolution fails but x5c header is present

## Resolution Priority

The `JwtKeyResolver` attempts key resolution in the following order:

1. **DID** — If `iss`/`issuer` is a DID URL, resolve via DID document
2. **x5c** — If the JWT header contains an `x5c` certificate chain, extract the public key
3. **HTTPS well-known** — If `iss`/`issuer` is an HTTPS URL, fetch from `.well-known/jwt-vc-issuer`

## Installation

Add the dependency to your `build.gradle.kts`:

```kotlin
repositories {
    maven("https://maven.waltid.dev/releases")
}

dependencies {
    implementation("id.walt.credentials:waltid-credential-key-resolver:<version>")
}
```

## Usage

### Basic Key Resolution

```kotlin
import id.walt.credentials.keyresolver.JwtKeyResolver
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject

suspend fun resolveSignerKey(jwtHeader: JsonObject?, jwtPayload: JsonObject) {
    val key = JwtKeyResolver.resolveFromJwt(jwtHeader, jwtPayload)
    
    if (key != null) {
        println("Resolved key: ${key.getKeyId()}")
        println("Key type: ${key.keyType}")
    } else {
        println("Could not resolve signer key")
    }
}
```

### Using Individual Resolvers

```kotlin
import id.walt.credentials.keyresolver.resolvers.DidKeyResolver
import id.walt.credentials.keyresolver.resolvers.X5CKeyResolver
import id.walt.credentials.keyresolver.resolvers.WellKnownKeyResolver

// DID resolution
val didKey = DidKeyResolver.resolveKeyFromDid(
    did = "did:key:z6MkhaXgBZDvotDkL5257faiztiGiC2QtKLGpbnnEGta2doK",
    kid = null // optional key ID for multi-key DID documents
)

// x5c certificate chain
val x5cKey = X5CKeyResolver.resolveKeyFromX5c(x5cJsonArray)

// HTTPS well-known metadata
val wellKnownKey = WellKnownKeyResolver.resolveKeyFromWellKnown(
    issuerUrl = "https://issuer.example.com",
    jwtHeader = header
)
```

## Supported Platforms

| Platform | Support |
|----------|---------|
| JVM | Full support including X.509 chain validation |
| JavaScript | Full support |
| iOS | Available when `enableIosBuild=true` |

## Key Resolution Methods

### DID Resolution

Supports all DID methods implemented by the walt.id DID library:
- `did:key`
- `did:jwk`
- `did:web`
- `did:ebsi`
- And more...

### X.509 Certificate Chain (x5c)

On JVM, the library validates the certificate chain:
- Authority Key Identifier (AKI) / Subject Key Identifier (SKI) linking
- Signature chain verification
- Extracts the public key from the leaf certificate

### HTTPS Well-Known

Fetches issuer metadata from `{issuer}/.well-known/jwt-vc-issuer` and resolves the signing key using:
- `jwks_uri` — Fetches JWKS and matches by `kid`
- `jwks` — Inline JWKS in metadata

## Related Libraries

- **[waltid-crypto](../../crypto/waltid-crypto)** — Core cryptographic operations
- **[waltid-did](../../waltid-did)** — DID resolution and management
- **[waltid-x509](../../crypto/waltid-x509)** — X.509 certificate handling (JVM)
- **[waltid-web-data-fetching](../../web/waltid-web-data-fetching)** — HTTP client for metadata fetching

## Join the community

* Connect and get the latest updates: [Discord](https://discord.gg/AW8AgqJthZ) | [Newsletter](https://walt.id/newsletter) | [YouTube](https://www.youtube.com/channel/UCXfOzrv3PIvmur_CmwwmdLA) | [LinkedIn](https://www.linkedin.com/company/walt-id/)
* Get help, request features and report bugs: [GitHub Issues](https://github.com/walt-id/waltid-identity/issues)
* Find more in-depth documentation on our [docs site](https://docs.walt.id)

## License

Licensed under the [Apache License, Version 2.0](https://github.com/walt-id/waltid-identity/blob/main/LICENSE)

<div align="center">
<img src="../../../assets/walt-banner.png" alt="walt.id banner" />
</div>
