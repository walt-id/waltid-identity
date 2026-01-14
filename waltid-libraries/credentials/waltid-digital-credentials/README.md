<div align="center">
 <h1>Kotlin Multiplatform Digital Credentials library</h1>
 <span>by </span><a href="https://walt.id">walt.id</a>
 <p>Unified credential abstraction layer for parsing, detecting, and verifying digital credentials</p>

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

This library provides a unified abstraction layer for handling digital credentials across multiple formats and signature types. The library enables you to:

- **Detect and parse credentials** automatically from various formats (W3C, SD-JWT VC, mdocs) without knowing the format upfront
- **Support multiple credential types**: W3C Verifiable Credentials (v1.1 and v2.0), SD-JWT VCs, and ISO mdoc credentials
- **Handle different signature types**: JWT, SD-JWT (with selective disclosures), COSE, unsigned credentials and could handle Data Integrity Proofs (ECDSA, EdDSA, ECDSA-SD, BBS) in future
- **Verify credentials** using issuer key resolution from various sources (DID keys, X.509 certificates, well-known endpoints)
- **Work with presentations** in multiple formats (JWT VC JSON, SD-JWT, LDP VC, mdoc)
- **Support selective disclosure** for privacy-preserving credential presentations

## Main Purpose

This library solves the challenge of handling credentials in heterogeneous formats within a single codebase. It's particularly useful when you need to:

- Build wallet applications that store and manage credentials from different issuers in various formats
- Create verifier services that need to accept credentials in multiple formats
- Implement credential processing pipelines without hardcoding format-specific logic
- Support interoperability across different credential ecosystems (W3C, SD-JWT, mdoc)

The library provides a single entry point (`CredentialParser.detectAndParse()`) that automatically identifies the credential format, signature type, and data model version, then parses it into a unified `DigitalCredential` interface.

## Key Concepts

### Credential Primary Types

The library categorizes credentials into three primary types:
- **W3C**: W3C Verifiable Credentials following the W3C Verifiable Credentials Data Model
- **SDJWTVC**: Selective Disclosure JWT Verifiable Credentials (SD-JWT VC)
- **MDOCS**: ISO/IEC 18013-5 mobile driver's license (mdoc) credentials

### W3C Credential Subtypes

W3C credentials can be further categorized by data model version:
- **W3C_1_1**: W3C Verifiable Credentials Data Model v1.1
- **W3C_2**: W3C Verifiable Credentials Data Model v2.0

The library automatically detects the version based on the `@context` field in the credential.

### SD-JWT VC Subtypes

SD-JWT VCs can follow different data models:
- **sdjwtvc**: Standard SD-JWT VC format
- **sdjwtvcdm**: SD-JWT VC following the W3C data model structure

### Signature Types

Credentials can be signed using various signature mechanisms:
- **JWT**: JSON Web Token signatures (JWS)
- **SDJWT**: Selective Disclosure JWT (JWT with disclosures)
- **COSE**: CBOR Object Signing and Encryption (used for mdocs)
- **UNSIGNED**: Credentials without cryptographic signatures

### Selective Disclosure

The library supports selective disclosure mechanisms:
- **Contains Disclosables**: The credential has fields that can be selectively disclosed (indicated by `_sd` arrays)
- **Provides Disclosures**: The credential includes actual disclosure values that have been revealed

### Credential Detection

The `CredentialParser.detectAndParse()` function automatically:
1. Detects the credential format by examining the input structure
2. Identifies the signature type and data model version
3. Parses the credential into the appropriate type-specific class
4. Extracts selective disclosure information if present
5. Returns both detection metadata and the parsed credential object

### Key Resolution

The library includes key resolution mechanisms to verify credentials:
- **DID Key Resolution**: Resolves keys from DID documents
- **X.509 Certificate Resolution**: Extracts keys from X.509 certificate chains
- **Well-Known Endpoint Resolution**: Fetches keys from well-known endpoints

## Assumptions and Dependencies

This library makes several important assumptions:

- **Multiplatform Support**: Works on JVM (Kotlin/Java), JavaScript, and iOS platforms (iOS requires `enableIosBuild=true` Gradle property). The same detection and parsing logic works identically across platforms.
- **Credential Format Detection**: The library can automatically detect credential formats from the input structure, but inputs must be in a recognizable format (JWT strings, JSON objects, CBOR-encoded mdocs).
- **Signature Verification**: Verification requires access to the issuer's public key, which is resolved using the key resolver system.
- **W3C Context Detection**: W3C credential versions are detected based on the `@context` field values.
- **SD-JWT Format**: SD-JWT credentials must follow the SD-JWT specification format with `~` separator between JWT and disclosures.
- **mdoc Encoding**: mdoc credentials can be provided as Base64Url or hexadecimal strings.

## How to Use This Library

### Basic Workflow

1. **Detect and Parse**: Use `CredentialParser.detectAndParse()` to automatically detect and parse a credential string
2. **Access Detection Info**: Use the detection result to understand the credential's type, subtype, and signature
3. **Work with Credential**: Use the parsed credential object to access credential data, issuer, subject, and signature information
4. **Verify Credential**: Use the credential's `verify()` method with the issuer's public key to verify the signature

### Key Source Files

For detailed implementation examples and understanding the library internals, refer to:

- **`CredentialParser.kt`**: Main entry point for credential detection and parsing
- **`CredentialDetectorTypes.kt`**: Type definitions for credential classification (primary types, subtypes, signature types)
- **`formats/DigitalCredential.kt`**: Base interface for all credential formats
- **`formats/w3c/`**: W3C credential implementations (W3C11, W3C2, AbstractW3C)
- **`formats/sdjwtvc/SdJwtCredential.kt`**: SD-JWT VC credential implementation
- **`formats/mdocs/MdocsCredential.kt`**: mdoc credential implementation
- **`signatures/`**: Signature type implementations (JWT, SD-JWT, Data Integrity Proof, COSE)
- **`keyresolver/`**: Key resolution mechanisms for credential verification
- **`presentations/`**: Verifiable presentation format implementations
- **Test files**: Located in `src/jvmTest`, these provide comprehensive examples of all credential types and signature combinations

## JVM/Kotlin Usage

### Installation

Add the library as a dependency in your `build.gradle.kts`:

```kotlin
repositories {
    maven { url = uri("https://maven.waltid.dev/releases") }
}

dependencies {
    implementation("id.walt.credentials:waltid-digital-credentials:<version>")
}
```

### Basic Example

```kotlin
import id.walt.credentials.*
import id.walt.credentials.formats.*

fun main() {
    // A credential in any format (JWT string, SD-JWT, JSON, mdoc)
    val credentialString = "eyJraWQiOiJkaWQ6a2V5Ono2TWtqb1JocTFqU05KZExpcnVTWHJGRnhhZ3FyenRaYVhIcUhHVVRLSmJjTnl3cCN6Nk1ram9SaHExalNOSmRMaXJ1U1hyRkZ4YWdxcnp0WmFYSHFIR1VUS0piY055d3AiLCJ0eXAiOiJKV1QiLCJhbGciOiJFZERTQSJ9..."
    
    // Automatically detect and parse
    val (detection, credential) = CredentialParser.detectAndParse(credentialString)
    
    // Access detection information
    println("Primary Type: ${detection.credentialPrimaryType}") // W3C, SDJWTVC, or MDOCS
    println("Sub Type: ${detection.credentialSubType}")         // W3C_1_1, W3C_2, etc.
    println("Signature: ${detection.signaturePrimary}")       // JWT, SDJWT, DATA_INTEGRITY_PROOF, etc.
    println("Has Disclosables: ${detection.containsDisclosables}")
    println("Provides Disclosures: ${detection.providesDisclosures}")
    
    // Work with the credential
    println("Format: ${credential.format}")              // e.g., "jwt_vc_json", "ldp_vc", "mso_mdoc"
    println("Issuer: ${credential.issuer}")
    println("Subject: ${credential.subject}")
    
    // Access credential data
    val credentialData = credential.credentialData
    println("Credential Data: $credentialData")
}
```

### Working with W3C Credentials

W3C credentials are automatically parsed into `W3C11` or `W3C2` objects:

```kotlin
val (detection, credential) = CredentialParser.detectAndParse(w3cCredentialString)

when (credential) {
    is W3C11 -> {
        println("W3C 1.1 Credential")
        println("Has selective disclosures: ${credential.disclosables != null}")
    }
    is W3C2 -> {
        println("W3C 2.0 Credential")
    }
    else -> {
        println("Not a W3C credential")
    }
}
```

### Working with SD-JWT Credentials

SD-JWT credentials include selective disclosure information:

```kotlin
val (detection, credential) = CredentialParser.detectAndParse(sdJwtCredentialString)

when (credential) {
    is SdJwtCredential -> {
        println("SD-JWT VC Credential")
        println("Disclosures: ${credential.disclosures}")
        
        // Access disclosed claims
        credential.disclosures?.forEach { disclosure ->
            println("${disclosure.name} = ${disclosure.value}")
        }
    }
}
```

### Working with mdoc Credentials

mdoc credentials can be provided as Base64Url or hex strings:

```kotlin
// Base64Url encoded mdoc
val mdocBase64 = "omdkb2NUeXBldW9yZy5pc28uMTgwMTMuNS4xLm1ETGxpc3N1ZXJTaWduZWQ..."

val (detection, credential) = CredentialParser.detectAndParse(mdocBase64)

when (credential) {
    is MdocsCredential -> {
        println("mdoc Credential")
        println("DocType: ${credential.credentialData["docType"]}")
    }
}

// Or hex encoded
val mdocHex = "a06d646f6354797065756f72672e69736f2e31383031332e352e312e6d444c6c6973737565725369676e6564..."
val (_, mdocCredential) = CredentialParser.detectAndParse(mdocHex)
```

### Verifying Credentials

Verify credentials using issuer key resolution:

```kotlin
import id.walt.crypto.keys.Key

suspend fun verifyCredential(credential: DigitalCredential) {
    // Get the issuer's public key
    val issuerKey = credential.getSignerKey()
    
    if (issuerKey != null) {
        // Verify the credential
        val verificationResult = credential.verify(issuerKey)
        
        if (verificationResult.isSuccess) {
            println("Credential is valid!")
        } else {
            println("Verification failed: ${verificationResult.exceptionOrNull()?.message}")
        }
    } else {
        println("Could not resolve issuer key")
    }
}
```

### Working with Presentations

The library supports multiple presentation formats:

```kotlin
import id.walt.credentials.presentations.formats.*

// Parse presentations in different formats
// JWT VC JSON presentation
val jwtVcJsonPresentation = JwtVcJsonPresentation(...)

// SD-JWT presentation
val sdJwtPresentation = DcSdJwtPresentation(...)

// LDP VC presentation
val ldpVcPresentation = LdpVcPresentation(...)

// mdoc presentation
val mdocPresentation = MsoMdocPresentation(...)
```

## JavaScript Usage

### Installation

Install the library via npm:

```bash
npm install waltid-digital-credentials
```

### Basic Example

```javascript
import { CredentialParser } from 'waltid-digital-credentials';

// Detect and parse a credential
const credentialString = "eyJraWQiOiJkaWQ6a2V5Ono2TWtqb1JocTFqU05KZExpcnVTWHJGRnhhZ3FyenRaYVhIcUhHVVRLSmJjTnl3cCN6Nk1ram9SaHExalNOSmRMaXJ1U1hyRkZ4YWdxcnp0WmFYSHFIR1VUS0piY055d3AiLCJ0eXAiOiJKV1QiLCJhbGciOiJFZERTQSJ9...";

const [detection, credential] = await CredentialParser.detectAndParse(credentialString);

console.log("Primary Type:", detection.credentialPrimaryType);
console.log("Sub Type:", detection.credentialSubType);
console.log("Signature:", detection.signaturePrimary);
console.log("Format:", credential.format);
console.log("Issuer:", credential.issuer);
```

## iOS/Swift Usage

### Installation

Add the library as a dependency in your `Package.swift` or Xcode project:

```swift
dependencies: [
    .package(url: "https://github.com/walt-id/waltid-identity", from: "x.x.x")
]
```

Or add it to your `build.gradle.kts` in a Kotlin Multiplatform Mobile project:

```kotlin
kotlin {
    iosArm64()
    iosSimulatorArm64()
    
    sourceSets {
        val iosMain by creating {
            dependencies {
                implementation("id.walt.credentials:waltid-digital-credentials:<version>")
            }
        }
    }
}
```

### Basic Example

```swift
import waltid_digital_credentials

func detectAndParseCredential() async throws {
    let credentialString = "eyJraWQiOiJkaWQ6a2V5Ono2TWtqb1JocTFqU05KZExpcnVTWHJGRnhhZ3FyenRaYVhIcUhHVVRLSmJjTnl3cC..."
    
    // Detect and parse
    let result = try await CredentialParser.Companion.shared.detectAndParse(credentialString: credentialString)
    let detection = result.component1()
    let credential = result.component2()
    
    print("Primary Type: \(detection.credentialPrimaryType)")
    print("Sub Type: \(detection.credentialSubType)")
    print("Signature: \(detection.signaturePrimary)")
    print("Format: \(credential.format)")
    print("Issuer: \(credential.issuer ?? "unknown")")
    print("Subject: \(credential.subject ?? "unknown")")
}
```

## Join the community

* Connect and get the latest updates: [Discord](https://discord.gg/AW8AgqJthZ) | [Newsletter](https://walt.id/newsletter) | [YouTube](https://www.youtube.com/channel/UCXfOzrv3PIvmur_CmwwmdLA) | [LinkedIn](https://www.linkedin.com/company/walt-id/)
* Get help, request features and report bugs: [GitHub Issues](https://github.com/walt-id/waltid-identity/issues)
* Find more indepth documentation on our [docs site](https://docs.walt.id)


## License

Licensed under the [Apache License, Version 2.0](https://github.com/walt-id/waltid-identity/blob/main/LICENSE)

<div align="center">
<img src="../../../assets/walt-banner.png" alt="walt.id banner" />
</div>
