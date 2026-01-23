<div align="center">
<h1>walt.id Crypto Android</h1>
 <span>by </span><a href="https://walt.id">walt.id</a>
 <p>Android-specific cryptographic key operations using Android Keystore</p>

<a href="https://walt.id/community">
<img src="https://img.shields.io/badge/Join-The Community-blue.svg?style=flat" alt="Join community!" />
</a>
<a href="https://www.linkedin.com/company/walt-id/">
<img src="https://img.shields.io/badge/-LinkedIn-0072b1?style=flat&logo=linkedin" alt="Follow walt_id" />
</a>
  
  <h2>Status</h2>
  <img src="https://img.shields.io/badge/🟡%20Unmaintained-yellow?style=for-the-badge&logo=warning" alt="Status: Unmaintained" />
  <br/>
  <em>This project is not actively maintained. Certain features may be outdated or not working as expected.<br />We encourage users to contribute to the project to help keep it up to date.</em>
  </p>

</div>

## What This Library Contains

`waltid-crypto-android` is an Android-specific extension library that provides `AndroidKey`, a cryptographic key implementation backed by Android Keystore. It enables secure key storage and operations on Android devices using the hardware-backed Android Keystore system.

## Main Purpose

This library enables:

- **Android Keystore Integration**: Direct access to Android Keystore for secure key storage
- **Hardware-Backed Security**: Leverages Android's hardware security module when available
- **Key Operations**: Generate, load, sign, and verify cryptographic keys on Android
- **User Authentication**: Optional key protection requiring user authentication (PIN, pattern, biometric)
- **Seamless Integration**: Works with the core `waltid-crypto` library's `Key` interface

## Key Concepts

### Android Keystore

Android Keystore provides secure key storage:

- **Hardware Security**: Keys can be stored in hardware-backed secure storage (when available)
- **Key Isolation**: Keys are isolated from the app's process
- **Certificate Generation**: Automatically generates self-signed certificates for key pairs
- **User Authentication**: Optional requirement for user authentication to use keys

### Supported Key Types

- **RSA**: RSA keys with configurable key sizes
- **secp256r1 (P-256)**: Elliptic curve keys using secp256r1 curve

### Key Protection

Keys can be protected with user authentication:

- **User Authentication Required**: Optional flag to require user authentication (PIN, pattern, fingerprint, face)
- **Digest Algorithms**: Supports SHA-256, SHA-384, SHA-512
- **Signature Padding**: RSA PKCS1 padding for RSA keys

## Assumptions and Dependencies

### Platform Support

- **Android Only**: This is an Android-specific library
- **Android SDK 28+**: Requires Android API level 28 (Android 9.0) or higher
- **Kotlin Multiplatform**: Uses Kotlin Multiplatform Android target

### Dependencies

- **waltid-crypto**: Core cryptographic library (API dependency)
- **Kotlinx Coroutines**: Coroutine support
- **Kotlinx Serialization**: JSON serialization
- **Kotlin Logging**: Logging utilities

### Build Requirements

- **Android SDK**: Android SDK must be configured
- **Gradle Properties**: Requires `enableAndroidBuild=true` in `gradle.properties`
- **Local Properties**: Requires `sdk.dir` configured in `local.properties`

## Usage

### Prerequisites

1. **Enable Android Build**:

Add to `gradle.properties`:
```properties
enableAndroidBuild=true
```

2. **Configure Android SDK**:

Add to `local.properties`:
```properties
sdk.dir=/path/to/android/sdk
```

### Adding Dependency

```kotlin
dependencies {
    implementation(project(":waltid-libraries:crypto:waltid-crypto-android"))
}
```

### Basic Usage

```kotlin
import id.walt.crypto.keys.AndroidKey
import id.walt.crypto.keys.AndroidKeyGenerator
import id.walt.crypto.keys.KeyType

// Generate a new key
val key = AndroidKeyGenerator.generate(
    type = KeyType.secp256r1,
    metadata = AndroidKeyParameters(
        keyId = "my-key-id",
        isProtected = false // Set to true to require user authentication
    )
)

// Load an existing key
val existingKey = AndroidKeystoreLoader.load(
    type = KeyType.secp256r1,
    keyId = "my-key-id"
)

// Use the key (implements Key interface from waltid-crypto)
val signature = key.signRaw(data)
val jwk = key.exportJWK()
```

### Key Operations

- **Generate**: Create new keys in Android Keystore
- **Load**: Load existing keys from Android Keystore
- **Sign**: Sign data with private keys
- **Verify**: Verify signatures with public keys
- **Export**: Export public keys as JWK

## Related Libraries

- **[waltid-crypto](../waltid-crypto)**: Core multiplatform cryptographic library
- **[waltid-crypto-ios](../waltid-crypto-ios)**: iOS equivalent using iOS Keychain

## Join the community

* Connect and get the latest updates: [Discord](https://discord.gg/AW8AgqJthZ) | [Newsletter](https://walt.id/newsletter) | [YouTube](https://www.youtube.com/channel/UCXfOzrv3PIvmur_CmwwmdLA) | [LinkedIn](https://www.linkedin.com/company/walt-id/)
* Get help, request features and report bugs: [GitHub Issues](https://github.com/walt-id/waltid-identity/issues)
* Find more indepth documentation on our [docs site](https://docs.walt.id)

## License

Licensed under the [Apache License, Version 2.0](https://github.com/walt-id/waltid-identity/blob/main/LICENSE)

<div align="center">
<img src="../../../assets/walt-banner.png" alt="walt.id banner" />
</div>
