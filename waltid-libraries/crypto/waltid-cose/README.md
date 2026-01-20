<div align="center">
 <h1>Kotlin Multiplatform Crypto - COSE library</h1>
 <span>by </span><a href="https://walt.id">walt.id</a>
  <p>Create COSE messages for digital signing and authentication</p>

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

## What it provides

A Kotlin multiplatform library for creating and verifying COSE (CBOR Object Signing and Encryption) messages, implementing the [RFC 8152](https://www.rfc-editor.org/rfc/rfc8152.html) standard. This library provides a complete implementation for digitally signing and authenticating data using the compact CBOR binary format, making it ideal for applications where message size and processing efficiency matter.

## What This Library Contains

This library implements the core COSE message types and cryptographic operations:

- **COSE_Sign1**: Single-signer digital signatures for authenticating data payloads
- **COSE_Mac0**: Message Authentication Code (MAC) operations for symmetric key authentication
- **COSE Headers**: Support for protected and unprotected header parameters, including algorithm identifiers, key IDs, content types, and certificate chains
- **COSE Keys and Key Sets**: Representation and conversion of cryptographic keys in COSE format
- **CBOR Serialization**: Full encoding and decoding of COSE messages using CBOR format

The library is built on top of `kotlinx-serialization-cbor` for CBOR handling and integrates seamlessly with `waltid-crypto` for cryptographic operations.

## Main Purpose

This library enables you to create cryptographically signed and authenticated messages using the COSE standard. It's particularly useful for:
- Creating verifiable credentials and digital documents
- Implementing secure data exchange protocols
- Building systems that require compact, efficient message signing
- Interoperating with other COSE-compliant systems

## Key Concepts

### COSE vs JOSE

If you're familiar with **JOSE** (JSON Object Signing and Encryption), COSE is its binary counterpart. While JOSE uses JSON text format, COSE uses CBOR binary format, making messages significantly smaller and faster to process. The COSE_Sign1 message type is the equivalent of a JWS (JSON Web Signature) with a single signature.

### Protected vs Unprotected Headers

COSE messages have two types of headers:
- **Protected headers**: Cryptographically protected and included in the signature calculation. These typically contain the algorithm identifier and other security-critical parameters.
- **Unprotected headers**: Not protected by the signature but still part of the message. These can include metadata like content type or key identifiers that don't need cryptographic protection.

### Attached vs Detached Payloads

This library supports two payload modes:
- **Attached payloads**: The payload is included directly in the COSE message. This is the standard mode for most use cases.
- **Detached payloads**: The payload is kept separate from the signature object (set to null). This is useful when you want to verify a signature without including the payload in the message, or when the payload is very large and you want to keep the signature compact.

### External Authenticated Data (AAD)

COSE supports external authenticated data—additional context that is authenticated as part of the signature but not included in the message itself. This allows you to bind signatures to specific contexts or protocols without modifying the payload.

### Message Types

- **COSE_Sign1**: A single-signer signature structure. Use this when you have one signer and want to create a digital signature over a payload.
- **COSE_Mac0**: A MAC authentication structure. Use this when you have a shared symmetric key and want to authenticate (rather than sign) a message.

## Assumptions and Dependencies

This library makes several important assumptions:

- **Key Management**: The library integrates with `waltid-crypto` for key operations. You'll need to use `Key` objects from that library, which can be converted to `CoseSigner` and `CoseVerifier` using extension functions.
- **Multiplatform Support**: The library works on JVM, JavaScript, and iOS platforms. All cryptographic operations are implemented using multiplatform-compatible libraries.
- **CBOR Format**: All messages are encoded in CBOR format, not JSON. This is a fundamental aspect of COSE and provides compact binary representation.
- **RFC 8152 Compliance**: The library follows the RFC 8152 standard, ensuring interoperability with other COSE implementations.

## How to Use This Library

### Basic Workflow

1. **Prepare your key**: Use a `Key` object from `waltid-crypto`. For signing, you need a key with a private component. For verification, you can use the corresponding public key.

2. **Create headers**: Define your protected and unprotected headers. At minimum, you'll need to specify the algorithm in the protected headers using constants from `Cose.Algorithm`.

3. **Sign your data**: Use `CoseSign1.createAndSign()` to create a signed message. Provide your payload as a `ByteArray`, along with the headers and a signer created from your key using `key.toCoseSigner()`.

4. **Serialize**: Convert the signed message to its tagged CBOR representation using `toTagged()` if you need to transmit or store it.

5. **Verify**: When verifying, deserialize the message using `CoseSign1.fromTagged()`, then call `verify()` with a verifier created from the public key using `key.toCoseVerifier()`.

### Code Examples

The following examples demonstrate the basic operations. For more comprehensive examples, see the test files mentioned below.

#### Creating and Signing a COSE_Sign1 Message

This example shows how to create a signed COSE message with protected and unprotected headers, a payload, and optional external authenticated data:

```kotlin
val signer = key.toCoseSigner() // your key from waltid-crypto
val signed = CoseSign1.createAndSign(
    protectedHeaders = protectedHeaders,
    unprotectedHeaders = unprotectedHeaders,
    payload = payload,
    signer = signer,
    externalAad = externalAad
)

val signedHex: String = signed.toTagged().toHexString()
```

The `toTagged()` method encodes the message with the CBOR tag 18 (COSE_Sign1) as required by the standard. You can then transmit or store this as a hex string or byte array.

#### Verifying a COSE_Sign1 Message

To verify a signed message, deserialize it from its tagged CBOR representation and verify using the public key:

```kotlin
val signedHex = "d28443a10126a1044231315454..."

val coseSign1 = CoseSign1.fromTagged(signedHex) // accepts hex string or ByteArray

val verifier = key.toCoseVerifier()
val verified: Boolean = coseSign1.verify(verifier, externalAad)
```

The `verify()` method will return `true` if the signature is valid and the message hasn't been tampered with. If you used external authenticated data during signing, you must provide the same data during verification.

### Key Source Files

For detailed implementation examples and understanding the library internals, refer to:

- **`Cose.kt`**: Contains the main `CoseSign1` and `CoseMac0` classes with all their methods for creation, signing, and verification
- **`CoseHeaders.kt`**: Defines the header structure and content type handling
- **`CoseCrypto.kt`**: Contains the integration with `waltid-crypto`, including the `toCoseSigner()` and `toCoseVerifier()` extension functions
- **`CoseHmacCrypto.kt`**: HMAC-specific cryptographic operations for MAC operations
- **Test files**: Located in `src/commonTest` and `src/jvmTest`, these provide comprehensive examples of all library features including edge cases like detached payloads, external AAD, and various header configurations

### Supported Algorithms

The library supports a wide range of cryptographic algorithms defined in the COSE standard, including:
- ECDSA variants (ES256, ES384, ES512, ES256K)
- EdDSA (Ed25519, Ed448)
- RSA variants (RS256, RS384, RS512, PS256, PS384, PS512)
- HMAC variants (HMAC_256, HMAC_384, HMAC_512)

Algorithm constants are available in `Cose.Algorithm`. See the test files for examples of using different algorithms.

## Join the community

* Connect and get the latest updates: [Discord](https://discord.gg/AW8AgqJthZ) | [Newsletter](https://walt.id/newsletter) | [YouTube](https://www.youtube.com/channel/UCXfOzrv3PIvmur_CmwwmdLA) | [LinkedIn](https://www.linkedin.com/company/walt-id/)
* Get help, request features and report bugs: [GitHub Issues](https://github.com/walt-id/waltid-identity/issues)
* Find more indepth documentation on our [docs site](https://docs.walt.id)


## License

Licensed under the [Apache License, Version 2.0](https://github.com/walt-id/waltid-identity/blob/main/LICENSE)
<div align="center">
<img src="../../../assets/walt-banner.png" alt="walt.id banner" />
</div>
