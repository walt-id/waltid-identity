<div align="center">
<h1>walt.id Crypto2</h1>
 <span>by </span><a href="https://walt.id">walt.id</a>
 <p>Next generation cryptography primitives for walt.id</p>

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

## ⚠️ Work in Progress

**This library is currently under active development and is not intended for use by external developers.**

The API, functionality, and structure are subject to change without notice. This library is for internal use only while it's being developed and refined.

## What This Library Contains

`waltid-crypto2` is the next-generation cryptography foundation for walt.id. It separates key material, signature algorithms, key capabilities, storage, and provider selection instead of treating a JWK or JOSE implementation as the cryptographic abstraction.

## Main Purpose

This library is being developed to:

- Provide modern, multiplatform cryptographic primitives
- Offer improved APIs compared to the original `waltid-crypto` library
- Provide versioned, provider-independent key persistence
- Support software, device, remote KMS, and HSM providers through small capability interfaces
- Enable suspend-friendly (coroutine) operations
- Keep platform cryptography in maintained underlying libraries

## Current Features

- **Stored Keys**: Versioned software and managed-key records with JWK, SPKI DER, and PKCS8 DER material
- **Key Encoding**: Strict `PUBLIC KEY` and `PRIVATE KEY` PEM helpers plus cross-format JWK/DER conversion
- **Provider Runtime**: Immutable provider selection with strict overrides and explicit fallback
- **External Providers**: Kotlin SPI plus Java `CompletionStage` and `ServiceLoader` adapters
- **Software Keys**: ECDSA, Ed25519, RSA PKCS#1, and RSA-PSS through cryptography-kotlin
- **Encryption**: RSA-OAEP encryption and decryption
- **Key Agreement**: NIST ECDH and X25519/XDH shared-secret generation
- **Portable Signatures**: Explicit P1363 or DER ECDSA encoding
- **Primitives**: Direct access to cryptography-kotlin for hashing, MAC, KDF, and symmetric encryption
- **Multiplatform**: Common implementation for JVM, JavaScript, Android, iOS, Linux, Windows, and macOS targets

Encoding support is provider and target specific. Private JWK import derives and validates the public component before
exposing capabilities. Android supports this validation for RSA, but fails closed for EC, Ed25519, and X25519 private
JWK import because its cryptography backend cannot derive those public keys from private-only material. Public JWK and
SPKI paths remain available. Android also does not advertise PKCS8 generation, import, or private export.

## secp256k1 target support

secp256k1 is deliberately excluded from `CryptographyCapabilityProfile.Portable`. cryptography-kotlin 0.6.0 uses
different Optimal backends by target, and their curve support is not uniform:

| Target | Optimal backend | Local secp256k1 support |
| --- | --- | --- |
| JVM | JDK | Stock JDK providers reject the curve. Use `BouncyCastleSecp256k1SoftwareKeyProvider`. |
| Linux and Windows native | OpenSSL 3 | Use `Openssl3Secp256k1SoftwareKeyProvider`. |
| JavaScript | WebCrypto | Unavailable. WebCrypto supports only the NIST EC curves. |
| iOS and macOS | Apple Security/CryptoKit | Unavailable. The backend supports only the NIST EC curves. |
| Android JVM | JDK/platform JCA | Not exposed because platform provider support is not consistent. |

The opt-in providers advertise only secp256k1, SHA-256 ECDSA, and P1363/DER signature encodings. Register one
explicitly in `CryptoRuntime`; the default software provider remains portable and never advertises ES256K.

## Key persistence

`Key`, `SoftwareKey`, and `ManagedKey` have kotlinx.serialization serializers. They encode the underlying versioned
`StoredKey` directly, without another envelope:

```kotlin
val persisted = Json.encodeToString(generatedSoftwareKey)
val storableKey = Json.decodeFromString<SoftwareKey>(persisted)
check(storableKey.capabilities.signer == null)
val restored = runtime.restore(storableKey)
```

Decoding is synchronous and provider-independent. It returns a non-operational storable handle and never resolves a
provider or blocks on suspend restoration. Call `CryptoRuntime.restore(storableKey)` with an explicitly configured
runtime before using cryptographic capabilities. `StoredKeyCodec` remains available when code works directly with
`StoredKey` records. Managed records contain provider references and public material, not credentials. Serialized
software keys can contain private material and must be protected at rest.

The `SoftwareKey` and `ManagedKey` restore overloads preserve their static return types. Runtime provider generation,
restoration, and close are lifecycle-serialized; generation and restoration fail clearly after close, while key sign
and other operations are not routed through that lifecycle lock. Managed restore also requires the provider to return
the exact persisted public key and metadata without capabilities beyond the stored usages. Managed public JWKs are
parsed and rejected if malformed or if they contain private/secret parameters, regardless of metadata flags.

The `waltid-crypto2-examples` `stored-key` command intentionally prints a disposable private `StoredKey` record to
demonstrate this shape. It emits a warning immediately beforehand and must never be copied as an application logging
pattern.

Run `./gradlew :waltid-libraries:crypto:waltid-crypto2:benchmarkStoredKeys` to compare record encoding, decoding,
provider restoration, first-use materialization, and cached signing on the current JVM.

## Join the community

* Connect and get the latest updates: [Discord](https://discord.gg/AW8AgqJthZ) | [Newsletter](https://walt.id/newsletter) | [YouTube](https://www.youtube.com/channel/UCXfOzrv3PIvmur_CmwwmdLA) | [LinkedIn](https://www.linkedin.com/company/walt-id/)
* Get help, request features and report bugs: [GitHub Issues](https://github.com/walt-id/waltid-identity/issues)
* Find more indepth documentation on our [docs site](https://docs.walt.id)

## License

Licensed under the [Apache License, Version 2.0](https://github.com/walt-id/waltid-identity/blob/main/LICENSE)

<div align="center">
<img src="../../../assets/walt-banner.png" alt="walt.id banner" />
</div>
