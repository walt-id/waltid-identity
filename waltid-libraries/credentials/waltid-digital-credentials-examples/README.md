<div align="center">
 <h1>Kotlin Multiplatform Digital Credentials Examples library</h1>
 <span>by </span><a href="https://walt.id">walt.id</a>
 <p>Example credentials in various formats for testing and development</p>

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

This library provides a collection of example credentials in various formats for use in testing, development, and demonstration purposes. The examples include:

- **W3C Credential Examples**: W3C Verifiable Credentials in different signature types (unsigned, Data Integrity Proof with ECDSA, EdDSA, ECDSA-SD, BBS)
- **SD-JWT VC Examples**: Selective Disclosure JWT Verifiable Credentials with various disclosure patterns
- **mdoc Examples**: ISO/IEC 18013-5 mobile driver's license credentials in Base64Url and hexadecimal formats

These examples are designed to work seamlessly with the `waltid-digital-credentials` library for parsing, detection, and verification testing.

## Main Purpose

This library serves as a companion to `waltid-digital-credentials`, providing:

- **Testing Credentials**: Pre-formatted credentials for unit and integration testing
- **Development Examples**: Real-world credential examples to understand different formats
- **Demonstration Data**: Sample credentials for demos and proof-of-concepts
- **Format Reference**: Examples showing the structure of different credential types

The examples are particularly useful when:
- Writing tests for credential parsing and verification
- Developing wallet or verifier applications
- Understanding the structure of different credential formats
- Creating tutorial or documentation materials

## Key Concepts

### Example Categories

The library organizes examples into three main categories:

- **W3C Examples**: Examples of W3C Verifiable Credentials including:
  - Unsigned W3C credentials
  - Data Integrity Proof signed credentials (ECDSA, EdDSA, ECDSA-SD, BBS)
  - Both W3C 1.1 and W3C 2.0 data model versions

- **SD-JWT Examples**: Examples of Selective Disclosure JWT VCs including:
  - Unsigned SD-JWT VCs without disclosables
  - SD-JWT VCs with selective disclosure arrays
  - Signed SD-JWT VCs with disclosures
  - Expired SD-JWT VCs for testing expiration logic

- **mdoc Examples**: Examples of ISO/IEC 18013-5 credentials including:
  - Base64Url encoded mdoc credentials
  - Hexadecimal encoded mdoc credentials

### Usage Pattern

Examples are provided as string constants that can be directly used with `CredentialParser.detectAndParse()`:

```kotlin
import id.walt.credentials.examples.*
import id.walt.credentials.CredentialParser

// Use W3C examples
val (detection, credential) = CredentialParser.detectAndParse(W3CExamples.w3cCredential)

// Use SD-JWT examples
val (detection, credential) = CredentialParser.detectAndParse(SdJwtExamples.unsignedSdJwtVcNoDisclosablesExample)

// Use mdoc examples
val (detection, credential) = CredentialParser.detectAndParse(MdocsExamples.mdocsExampleBase64Url)
```

## Assumptions and Dependencies

This library makes several important assumptions:

- **Companion Library**: This library is designed to work with `waltid-digital-credentials`. Examples are formatted to be compatible with the credential parser.
- **Example Format**: Examples are provided as JSON strings (for W3C and SD-JWT) or encoded strings (for mdocs) that can be directly parsed.
- **Testing Purpose**: Examples are primarily for testing and development. They may not represent production-ready credentials.
- **No Private Keys**: Examples do not include private keys or sensitive production data.

## How to Use This Library

### Basic Workflow

1. **Import Examples**: Import the example objects (`W3CExamples`, `SdJwtExamples`, `MdocsExamples`)
2. **Select Example**: Choose an appropriate example for your use case
3. **Parse Example**: Use `CredentialParser.detectAndParse()` to parse the example
4. **Test Your Code**: Use the parsed credential to test your application logic

### Key Source Files

For detailed implementation examples and understanding the available examples, refer to:

- **`W3CExamples.kt`**: Contains W3C Verifiable Credential examples in various formats
- **`SdJwtExamples.kt`**: Contains SD-JWT VC examples with different disclosure patterns
- **`MdocsExamples.kt`**: Contains mdoc credential examples in different encodings
- **Main library**: See `waltid-digital-credentials` for parsing and verification logic

### Writing Tests with Examples

Examples are particularly useful for writing tests:

```kotlin
import id.walt.credentials.examples.*
import id.walt.credentials.CredentialParser
import kotlin.test.Test
import kotlin.test.assertEquals

class CredentialParsingTest {
    
    @Test
    fun testW3CCredentialParsing() {
        val (detection, credential) = CredentialParser.detectAndParse(
            W3CExamples.w3cCredential
        )
        
        assertEquals(CredentialPrimaryDataType.W3C, detection.credentialPrimaryType)
        assertEquals(W3CSubType.W3C_2, detection.credentialSubType)
        assertEquals("jwt_vc_json", credential.format)
    }
    
    @Test
    fun testSdJwtWithDisclosures() {
        val (detection, credential) = CredentialParser.detectAndParse(
            SdJwtExamples.sdJwtVcWithDisclosablesExample
        )
        
        assertEquals(CredentialPrimaryDataType.SDJWTVC, detection.credentialPrimaryType)
        assertEquals(true, detection.containsDisclosables)
    }
    
    @Test
    fun testMdocParsing() {
        val (detection, credential) = CredentialParser.detectAndParse(
            MdocsExamples.mdocsExampleBase64Url
        )
        
        assertEquals(CredentialPrimaryDataType.MDOCS, detection.credentialPrimaryType)
        assertEquals("mso_mdoc", credential.format)
    }
}
```

### Accessing Example Values

Some examples include associated value maps for testing:

```kotlin
// W3C examples include value maps
val w3cCred = W3CExamples.w3cCredential
val values = W3CExamples.w3cCredentialValues

println("Expected issuer: ${values["issuer"]}")
println("Expected subject: ${values["subject"]}")

// Parse and verify values match
val (_, credential) = CredentialParser.detectAndParse(w3cCred)
assertEquals(values["issuer"], credential.issuer)
assertEquals(values["subject"], credential.subject)
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

