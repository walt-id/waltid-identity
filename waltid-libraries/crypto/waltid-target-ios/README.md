<div align="center">
    <h1>walt.id Target iOS</h1>
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

`waltid-target-ios` is a low-level iOS-specific library that provides cryptographic key operations using iOS Keychain and Secure Enclave. It contains Swift implementations and Kotlin bindings that bridge iOS native security APIs with Kotlin Multiplatform code.

## Main Purpose

This library enables:

- **iOS Keychain Integration**: Direct access to iOS Keychain for secure key storage
- **Secure Enclave Support**: Hardware-backed key generation and operations using Secure Enclave
- **Key Operations**: Generate, load, delete, sign, and verify cryptographic keys on iOS
- **Kotlin Interop**: Bridge Swift code with Kotlin Multiplatform through C interop
- **Multiple Algorithms**: Support for Ed25519, P256 (secp256r1), and RSA keys

## Key Concepts

### iOS Keychain Storage

Keys are stored securely in iOS Keychain:

- **Persistent Storage**: Keys persist across app launches
- **Secure Access**: Protected by iOS security mechanisms
- **Key Identification**: Keys identified by unique key ID (kid) and application tag
- **Hardware Security**: Optional Secure Enclave storage for enhanced protection

### Secure Enclave

For supported devices and key types:

- **Hardware Security Module**: Keys stored in dedicated hardware
- **Enhanced Protection**: Additional security layer for sensitive keys
- **Limited Support**: Secure Enclave supports specific algorithms (e.g., P256)

### Key Algorithms

The library supports:

- **P256 (secp256r1)**: Elliptic curve keys with Secure Enclave support
- **Ed25519**: Ed25519 signature scheme keys
- **RSA**: RSA keys with configurable key sizes (1024-7680 bits)

### C Interop Architecture

The library uses Kotlin/Native C interop to bridge Swift and Kotlin:

- **Swift Implementation**: Native iOS code in `implementation/` directory
- **C Interop**: Kotlin/Native C interop definitions for bridging
- **Kotlin Bindings**: Kotlin Multiplatform code that calls into Swift

## Assumptions and Dependencies

### Platform Support

- **iOS 16.0+**: Requires iOS 16.0 or later (deployment target)
- **macOS**: Requires macOS for compilation (Xcode requirement)
- **Xcode**: Requires Xcode for building Swift code
- **Kotlin Multiplatform**: Uses Kotlin Multiplatform for shared code

### Dependencies

- **JOSESwift**: JOSE library for iOS (via CocoaPods)
- **Kotlinx Serialization**: JSON serialization
- **Kotlinx Coroutines**: Coroutine support
- **Okio**: I/O utilities

### Build Requirements

- **Xcode**: Required for compiling Swift code
- **kdoctor**: Kotlin environment checker (should report no issues)
- **CocoaPods**: For iOS dependency management
- **Gradle**: For Kotlin Multiplatform build

## Usage

### Prerequisites

- macOS with Xcode installed
- Kotlin Multiplatform development environment
- `kdoctor` should report no issues

### Building

The library requires building both Swift and Kotlin components:

1. **Build Swift implementation**:

The Swift code in `implementation/` must be built first to generate headers for C interop.

2. **Build Kotlin framework**:

```bash
./gradlew :waltid-libraries:crypto:waltid-target-ios:build
```

3. **Generate CocoaPods framework**:

```bash
./gradlew :waltid-libraries:crypto:waltid-target-ios:generateDummyFramework
```

### Integration

This library is typically used indirectly through `waltid-crypto-ios`, which provides a higher-level API. For direct usage:

1. **Add to CocoaPods**:

```ruby
pod 'waltid-target-ios', :path => '../waltid-target-ios'
```

2. **Use in Kotlin Multiplatform code**:

The library provides low-level keychain operations that are wrapped by higher-level libraries.

### Key Components

- **KeychainOperations**: Low-level keychain operations (create, load, delete, sign, verify)
- **P256**: P256 (secp256r1) key operations with Secure Enclave support
- **RSA**: RSA key operations
- **Ed25519**: Ed25519 key operations
- **C Interop**: Bridges Swift code with Kotlin through C interop

## Related Libraries

- **[waltid-crypto-ios](../waltid-crypto-ios)**: Higher-level iOS crypto library that uses this library
- **[waltid-crypto](../waltid-crypto)**: Core multiplatform cryptographic library

## Development Notes

- This is a low-level library primarily used by `waltid-crypto-ios`
- Direct usage requires understanding of iOS Keychain APIs
- Swift code must be compiled before Kotlin code can use it
- C interop requires proper header generation from Swift code
- Secure Enclave support is device-dependent

## Join the community

* Connect and get the latest updates: [Discord](https://discord.gg/AW8AgqJthZ) | [Newsletter](https://walt.id/newsletter) | [YouTube](https://www.youtube.com/channel/UCXfOzrv3PIvmur_CmwwmdLA) | [LinkedIn](https://www.linkedin.com/company/walt-id/)
* Get help, request features and report bugs: [GitHub Issues](https://github.com/walt-id/waltid-identity/issues)
* Find more indepth documentation on our [docs site](https://docs.walt.id)

## License

Licensed under the [Apache License, Version 2.0](https://github.com/walt-id/waltid-identity/blob/main/LICENSE)

<div align="center">
<img src="../../../assets/walt-banner.png" alt="walt.id banner" />
</div>
