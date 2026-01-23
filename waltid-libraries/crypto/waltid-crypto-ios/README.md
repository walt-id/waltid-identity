<div align="center">
<h1>walt.id Crypto iOS</h1>
 <span>by </span><a href="https://walt.id">walt.id</a>
 <p>iOS-specific cryptographic key operations using iOS Keychain and Secure Enclave</p>

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
  <em>This project is not actively maintained. Certain features may be outdated or not working as expected.<br />We encourage users to contribute to the project to help keep it up to date.</em>
  </p>

</div>

## What This Library Contains

`waltid-crypto-ios` is an iOS-specific extension library that provides `IosKey`, a cryptographic key implementation backed by iOS Keychain. It enables secure key storage and operations on iOS devices, with optional Secure Enclave support for hardware-backed security.

## Main Purpose

This library enables:

- **iOS Keychain Integration**: Direct access to iOS Keychain for secure key storage
- **Secure Enclave Support**: Optional hardware-backed key storage using Secure Enclave
- **Key Operations**: Generate, load, delete, sign, and verify cryptographic keys on iOS
- **Multiple Algorithms**: Support for Ed25519, secp256r1 (P-256), and RSA keys
- **Seamless Integration**: Works with the core `waltid-crypto` library's `Key` interface

## Key Concepts

### iOS Keychain Storage

Keys are stored securely in iOS Keychain:

- **Persistent Storage**: Keys persist across app launches
- **Secure Access**: Protected by iOS security mechanisms
- **Key Identification**: Keys identified by unique key ID (kid)
- **Hardware Security**: Optional Secure Enclave storage for enhanced protection

### Secure Enclave

For supported devices and key types:

- **Hardware Security Module**: Keys stored in dedicated hardware
- **Enhanced Protection**: Additional security layer for sensitive keys
- **Limited Support**: Secure Enclave supports secp256r1 only

### Supported Key Types

| Type  | Algorithm | Secure Enclave | Availability |
|:-----:|:---------:|:--------------:|:-------------|
| EdDSA | Ed25519   | ❌             | ✅           |
| ECDSA | secp256r1| ✅             | ✅           |
| ECDSA | secp256k1 | ❌             | ❌           |
| RSA   | RSA       | ❌             | ✅           |

### Implementation Architecture

The library uses a layered architecture:

- **waltid-crypto-ios**: High-level API (`IosKey`)
- **waltid-target-ios**: Low-level iOS Keychain operations
- **Swift Implementation**: Native Swift code for crypto operations

## Assumptions and Dependencies

### Platform Support

- **iOS 15.4+**: Requires iOS 15.4 or later
- **Kotlin Multiplatform**: Uses Kotlin Multiplatform iOS targets
- **CocoaPods**: Uses CocoaPods for iOS dependency management

### Dependencies

- **waltid-crypto**: Core cryptographic library (exported)
- **waltid-target-ios**: Low-level iOS Keychain operations (exported)
- **JOSESwift**: JOSE library for iOS (via CocoaPods)

### Build Requirements

- **Xcode**: Required for building iOS frameworks
- **macOS**: Required for compilation (Xcode requirement)
- **CocoaPods**: For dependency management

## Usage

### Prerequisites

- Xcode 14.0 or later
- iOS 15.4+ device or simulator
- CocoaPods installed

### Adding Dependency

**CocoaPods**:

```ruby
pod 'waltid-crypto-ios', :path => '../waltid-crypto-ios'
```

**Gradle** (for Kotlin Multiplatform projects):

```kotlin
dependencies {
    implementation(project(":waltid-libraries:crypto:waltid-crypto-ios"))
}
```

### Basic Usage

```kotlin
import id.walt.crypto.IosKey
import id.walt.crypto.keys.KeyType

// Create a new key in iOS Keychain
val key = IosKey.create(
    IosKey.Options(
        kid = "my-key-id",
        keyType = KeyType.secp256r1,
        inSecureElement = false // Set to true for Secure Enclave (secp256r1 only)
    )
)

// Load an existing key
val existingKey = IosKey.load(
    IosKey.Options(
        kid = "my-key-id",
        keyType = KeyType.secp256r1,
        inSecureElement = false
    )
)

// Use the key (implements Key interface from waltid-crypto)
val signature = key.signRaw(data)
val jwk = key.exportJWK()
val pem = key.exportPEM()

// Delete a key
IosKey.delete(kid = "my-key-id", type = KeyType.secp256r1)
```

### Key Operations

- **Create**: Generate new keys in iOS Keychain
- **Load**: Load existing keys from iOS Keychain
- **Delete**: Remove keys from iOS Keychain
- **Sign**: Sign data with private keys
- **Verify**: Verify signatures with public keys
- **Export**: Export keys as JWK or PEM

### Secure Enclave Usage

```kotlin
// Create a key in Secure Enclave (secp256r1 only)
val secureKey = IosKey.create(
    IosKey.Options(
        kid = "secure-key-id",
        keyType = KeyType.secp256r1,
        inSecureElement = true // Enable Secure Enclave
    )
)
```

## Example Application

See **[waltid-crypto-ios-testApp](../../../waltid-applications/waltid-crypto-ios-testApp)** for a complete example application demonstrating iOS crypto library capabilities.

## Related Libraries

- **[waltid-crypto](../waltid-crypto)**: Core multiplatform cryptographic library
- **[waltid-target-ios](../waltid-target-ios)**: Low-level iOS Keychain operations
- **[waltid-crypto-android](../waltid-crypto-android)**: Android equivalent using Android Keystore

## Development Notes

- Keys are identified by a unique key ID (kid)
- Keys persist in iOS Keychain after app termination
- Secure Enclave support is optional and device-dependent
- The library uses C interop to bridge Swift and Kotlin code
- All cryptographic operations are performed by native iOS code

## Join the community

* Connect and get the latest updates: [Discord](https://discord.gg/AW8AgqJthZ) | [Newsletter](https://walt.id/newsletter) | [YouTube](https://www.youtube.com/channel/UCXfOzrv3PIvmur_CmwwmdLA) | [LinkedIn](https://www.linkedin.com/company/walt-id/)
* Get help, request features and report bugs: [GitHub Issues](https://github.com/walt-id/waltid-identity/issues)
* Find more indepth documentation on our [docs site](https://docs.walt.id)

## License

Licensed under the [Apache License, Version 2.0](https://github.com/walt-id/waltid-identity/blob/main/LICENSE)

<div align="center">
<img src="../../../assets/walt-banner.png" alt="walt.id banner" />
</div>
