<div align="center">
    <h1>walt.id Crypto iOS Test App</h1>
    <span>by </span><a href="https://walt.id">walt.id</a>
    <p>iOS test application for validating cryptographic operations and iOS Keychain integration</p>
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

## What This Application Contains

`waltid-crypto-ios-testApp` is an iOS test application for validating cryptographic operations using the `waltid-crypto-ios` library. It provides a comprehensive test interface for key generation, signing, and key management operations using iOS Keychain and Secure Enclave.

## Main Purpose

This test application enables developers to:

- **Test iOS Keychain Integration**: Verify that cryptographic keys can be stored and retrieved from iOS Keychain
- **Validate Key Operations**: Test key generation, signing, and export operations on iOS
- **Test Multiple Algorithms**: Validate support for secp256r1, Ed25519, and RSA key types
- **Secure Enclave Support**: Test Secure Enclave integration for hardware-backed keys
- **JWK Export**: Verify JWK (JSON Web Key) export functionality

## Key Concepts

### iOS Keychain Storage

Keys are stored securely in iOS Keychain:

- **Persistent Storage**: Keys persist across app launches
- **Secure Access**: Keys are protected by iOS security mechanisms
- **Key Identification**: Keys are identified by a unique key ID (kid)
- **Algorithm Support**: Supports secp256r1 (P-256), Ed25519, and RSA

### Secure Enclave

For supported devices and key types:

- **Hardware Security**: Keys can be stored in the Secure Enclave (hardware security module)
- **Enhanced Protection**: Provides additional security for sensitive keys
- **Limited Support**: Secure Enclave supports specific algorithms (e.g., secp256r1)

### Cryptographic Operations

The app tests various cryptographic operations:

- **Key Generation**: Create new keys in iOS Keychain
- **Raw Signing**: Sign arbitrary data with private keys
- **JWS Signing**: Create JSON Web Signatures (JWS) with proper headers
- **JWK Export**: Export keys in JSON Web Key format
- **Public Key Extraction**: Retrieve public key representations

## Assumptions and Dependencies

### Platform Support

- **iOS**: Native iOS application
- **Xcode**: Requires Xcode for building and running
- **iOS Keychain**: Uses iOS Keychain for secure key storage
- **Secure Enclave**: Optional support for hardware-backed keys (on supported devices)

### Dependencies

- **waltid-crypto-ios**: iOS-specific cryptographic library with Keychain integration
- **SwiftUI**: Modern iOS UI framework
- **CocoaPods**: Dependency management (if used)

## Usage

### Prerequisites

- Xcode 14.0 or later
- iOS device or simulator
- CocoaPods installed (if dependencies require it)

### Setup

1. **Open the project**:

```bash
open waltid.crypto.ios.testApp.xcworkspace
# or
open waltid.crypto.ios.testApp.xcodeproj
```

2. **Build and run** in Xcode:
   - Select a target device or simulator
   - Press ⌘R to build and run

### Using the Application

The app provides test interfaces for three key types:

#### secp256r1 (P-256) Keys

1. **Generate Key**: Enter a key ID and tap "Generate key"
2. **Test Operations**:
   - Get public key representation
   - Export private key as JWK
   - Sign raw data
   - Sign JWS (JSON Web Signature)

#### Ed25519 Keys

1. **Generate Key**: Create an Ed25519 key in Keychain
2. **Test Operations**: Same operations as secp256r1

#### RSA Keys

1. **Generate Key**: Create an RSA key pair
2. **Test Operations**: Public key operations and JWK export

### Key Operations

- **Generate Key**: Creates a new key in iOS Keychain with the specified key ID
- **Public Key Representation**: Retrieves the public key in byte format
- **Export JWK**: Exports the key in JSON Web Key format
- **Sign Raw**: Signs arbitrary data with the private key
- **Sign JWS**: Creates a JSON Web Signature with proper JOSE headers

### Key Components

- **ContentView.swift**: SwiftUI interface with test buttons for each operation
- **IosKey**: Kotlin Multiplatform key interface exposed to Swift
- **Keychain Integration**: Handled by `waltid-crypto-ios` library

## Related Libraries

- **[waltid-crypto-ios](../../waltid-libraries/crypto/waltid-crypto-ios)**: iOS Keychain integration library
- **[waltid-crypto](../../waltid-libraries/crypto/waltid-crypto)**: Core multiplatform cryptographic library

## Development Notes

- Keys are identified by a unique key ID (kid)
- Keys persist in iOS Keychain after app termination
- Secure Enclave support is optional and device-dependent
- The app uses async completion handlers for Swift-Kotlin interop
- All cryptographic operations are performed by the `waltid-crypto-ios` library

## Troubleshooting

- **Key not found**: Ensure the key ID matches the one used during generation
- **Secure Enclave errors**: Secure Enclave is only available on supported devices and for specific algorithms
- **Build errors**: Ensure all CocoaPods dependencies are installed (`pod install`)

## Join the community

* Connect and get the latest updates: [Discord](https://discord.gg/AW8AgqJthZ) | [Newsletter](https://walt.id/newsletter) | [YouTube](https://www.youtube.com/channel/UCXfOzrv3PIvmur_CmwwmdLA) | [LinkedIn](https://www.linkedin.com/company/walt-id/)
* Get help, request features and report bugs: [GitHub Issues](https://github.com/walt-id/waltid-identity/issues)
* Find more indepth documentation on our [docs site](https://docs.walt.id)

## License

Licensed under the [Apache License, Version 2.0](https://github.com/walt-id/waltid-identity/blob/main/LICENSE)

<div align="center">
<img src="../../assets/walt-banner.png" alt="walt.id banner" />
</div>

