<div align="center">
<h1>walt.id SD-JWT iOS</h1>
 <span>by </span><a href="https://walt.id">walt.id</a>
 <p>iOS-specific JWT crypto providers for Selective Disclosure JWT (SD-JWT) operations</p>

<a href="https://walt.id/community">
<img src="https://img.shields.io/badge/Join-The Community-blue.svg?style=flat" alt="Join community!" />
</a>
<a href="https://www.linkedin.com/company/walt-id/">
<img src="https://img.shields.io/badge/-LinkedIn-0072b1?style=flat&logo=linkedin" alt="Follow walt_id" />
</a>
  
  <h2>Status</h2>
  <p align="center">
      <img src="https://img.shields.io/badge/🟡%20Unmaintained-yellow?style=for-the-badge&logo=warning" alt="Status: Unmaintained" />
  <br/>
  <em>This project is not actively maintained. Certain features may be outdated or not working as expected. We encourage users to contribute to the project to help keep it up to date.</em>
  </p>

</div>

## What This Library Contains

`waltid-sdjwt-ios` is an iOS-specific extension library that provides JWT crypto provider implementations for the SD-JWT library. It enables signing and verification of SD-JWT tokens using iOS-native cryptographic operations, including HMAC and digital signature algorithms.

## Main Purpose

This library enables:

- **iOS-Native Crypto Operations**: Use iOS Keychain and native cryptographic APIs for SD-JWT operations
- **HMAC Support**: HMAC-based JWT signing and verification using iOS cryptographic functions
- **Digital Signature Support**: Digital signature-based JWT operations using iOS SecKeyRef
- **Seamless Integration**: Works with the core `waltid-sdjwt` library's `JWTCryptoProvider` interface
- **Secure Key Management**: Leverages iOS Keychain for secure key storage and operations

## Key Concepts

### JWT Crypto Providers

The library provides two iOS-specific implementations of the `JWTCryptoProvider` interface:

#### HMACJWTCryptoProvider

- **Algorithm Support**: HMAC-based algorithms (HS256, HS384, HS512)
- **Key Format**: Byte array (shared secret)
- **Use Case**: Symmetric key signing and verification
- **Implementation**: Uses iOS native HMAC operations via `HMAC_Operations`

#### DigitalSignaturesJWTCryptoProvider

- **Algorithm Support**: Digital signature algorithms (ES256, ES384, ES512, RS256, etc.)
- **Key Format**: iOS SecKeyRef (from iOS Keychain)
- **Use Case**: Asymmetric key signing and verification
- **Implementation**: Uses iOS native digital signature operations via `DS_Operations`

### Integration with SD-JWT

The crypto providers integrate seamlessly with the multiplatform SD-JWT library:

- **Sign SD-JWT Tokens**: Create and sign SD-JWT tokens with selective disclosure support
- **Verify SD-JWT Tokens**: Verify signatures and validate SD-JWT token integrity
- **Key Binding**: Support for key binding JWTs in SD-JWT presentations
- **Selective Disclosure**: Full support for selective field disclosure in SD-JWT tokens

## Assumptions and Dependencies

### Platform Support

- **iOS 15.4+**: Requires iOS 15.4 or later
- **Kotlin Multiplatform**: Uses Kotlin Multiplatform iOS targets (iosArm64, iosSimulatorArm64)
- **CocoaPods**: Uses CocoaPods for iOS dependency management

### Dependencies

- **waltid-sdjwt**: Core multiplatform SD-JWT library (required)
- **waltid-crypto-ios**: iOS-specific cryptographic operations (required)
- **JOSESwift**: JOSE library for iOS (via CocoaPods, version 3.0.0)

### Build Requirements

- **Xcode**: Required for building iOS frameworks
- **macOS**: Required for compilation (Xcode requirement)
- **CocoaPods**: For dependency management
- **Kotlin Multiplatform**: Kotlin Multiplatform plugin configured

## Usage

### Prerequisites

- Xcode 14.0 or later
- iOS 15.4+ device or simulator
- CocoaPods installed
- Kotlin Multiplatform project setup

### Adding Dependency

**Gradle** (for Kotlin Multiplatform projects):

```kotlin
dependencies {
    implementation(project(":waltid-libraries:sdjwt:waltid-sdjwt-ios"))
}
```

**CocoaPods** (for iOS projects):

The library is configured as a CocoaPods framework. Add to your `Podfile`:

```ruby
pod 'waltid-sdjwt-ios', :path => '../waltid-sdjwt-ios'
```

### Basic Usage

#### HMAC-based SD-JWT

```kotlin
import id.walt.sdjwt.*
import id.walt.sdjwt.HMACJWTCryptoProvider

// Create HMAC crypto provider with shared secret
val sharedSecret = "your-shared-secret-key"
val cryptoProvider = HMACJWTCryptoProvider("HS256", sharedSecret.encodeToByteArray())

// Create original payload
val originalPayload = buildJsonObject {
    put("sub", JsonPrimitive("123"))
    put("aud", JsonPrimitive("456"))
    put("email", JsonPrimitive("user@example.com"))
}

// Create undisclosed payload (fields to hide)
val undisclosedPayload = buildJsonObject {
    put("aud", JsonPrimitive("456"))
    // sub and email will be selectively disclosed
}

// Create SD payload
val sdPayload = SDPayload.createSDPayload(originalPayload, undisclosedPayload)

// Sign SD-JWT
val sdJwt = SDJwt.sign(sdPayload, cryptoProvider)

// Present SD-JWT (disclose all fields)
val presentedJwt = sdJwt.present(discloseAll = true)

// Verify SD-JWT
val verificationResult = presentedJwt.verify(cryptoProvider)
println("Verified: ${verificationResult.verified}")
```

#### Digital Signature-based SD-JWT

```kotlin
import id.walt.sdjwt.*
import id.walt.sdjwt.DigitalSignaturesJWTCryptoProvider
import platform.Security.SecKeyRef
import id.walt.crypto.IosKey
import id.walt.crypto.keys.KeyType

// Create or load a key from iOS Keychain
val iosKey = IosKey.create(
    IosKey.Options(
        kid = "my-signing-key",
        keyType = KeyType.secp256r1,
        inSecureElement = false
    )
)

// Get SecKeyRef from iOS key (implementation depends on your key management)
val secKeyRef: SecKeyRef = // ... obtain from IosKey or Keychain

// Create digital signature crypto provider
val cryptoProvider = DigitalSignaturesJWTCryptoProvider("ES256", secKeyRef)

// Create and sign SD-JWT
val originalPayload = buildJsonObject {
    put("sub", JsonPrimitive("123"))
    put("iss", JsonPrimitive("https://issuer.example.com"))
}

val undisclosedPayload = buildJsonObject {
    put("iss", JsonPrimitive("https://issuer.example.com"))
}

val sdPayload = SDPayload.createSDPayload(originalPayload, undisclosedPayload)
val sdJwt = SDJwt.sign(sdPayload, cryptoProvider)

// Verify SD-JWT
val verificationResult = sdJwt.verify(cryptoProvider)
println("Verified: ${verificationResult.verified}")
```

### Selective Disclosure

```kotlin
// Present with selective field disclosure
val sdMap = SDMapBuilder()
    .addField("sub", true)
    .addField("email", true)
    .build()

val selectivelyDisclosedJwt = sdJwt.present(sdMap)

// Or use JSON paths
val sdMapFromPaths = SDMap.generateSDMap(listOf("sub", "email"))
val selectivelyDisclosedJwt2 = sdJwt.present(sdMapFromPaths)
```

### Key Binding

```kotlin
// Present with key binding JWT
val audience = "verifier.example.com"
val nonce = "random-nonce-value"
val presentedJwtWithKeyBinding = sdJwt.present(
    discloseAll = true,
    audience = audience,
    nonce = nonce,
    cryptoProvider = cryptoProvider
)

// Key binding JWT is automatically included
println("Key binding JWT: ${presentedJwtWithKeyBinding.keyBindingJwt}")
```


## Development Notes

- The library uses C interop to bridge Swift and Kotlin code
- HMAC operations use iOS native cryptographic functions
- Digital signature operations require SecKeyRef from iOS Keychain
- All cryptographic operations are performed by native iOS code
- The library is designed to work seamlessly with the multiplatform SD-JWT library
- JOSESwift is used internally for JOSE operations

## Join the community

* Connect and get the latest updates: [Discord](https://discord.gg/AW8AgqJthZ) | [Newsletter](https://walt.id/newsletter) | [YouTube](https://www.youtube.com/channel/UCXfOzrv3PIvmur_CmwwmdLA) | [LinkedIn](https://www.linkedin.com/company/walt-id/)
* Get help, request features and report bugs: [GitHub Issues](https://github.com/walt-id/waltid-identity/issues)
* Find more indepth documentation on our [docs site](https://docs.walt.id)

## License

Licensed under the [Apache License, Version 2.0](https://github.com/walt-id/waltid-identity/blob/main/LICENSE)

<div align="center">
<img src="../../../../assets/walt-banner.png" alt="walt.id banner" />
</div>

