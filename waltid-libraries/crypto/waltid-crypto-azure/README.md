<div align="center">
<h1>Kotlin Azure Key Vault Cryptography Library</h1>
 <span>by </span><a href="https://walt.id">walt.id</a>
 <p>Azure Key Vault integration for cryptographic key management and operations</p>

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

## Overview

This library provides Azure Key Vault integration for the walt.id crypto library, enabling secure key management and cryptographic operations using Azure's cloud-based Hardware Security Module (HSM) service.

## Features

- **Key Generation** - Create EC (P-256, secp256k1) and RSA keys directly in Azure Key Vault
- **Signing Operations** - Sign data using keys stored in Azure Key Vault (ES256, ES256K, RS256)
- **Verification** - Verify signatures using Azure Key Vault keys
- **JWS Support** - Sign and verify JSON Web Signatures
- **Managed Identity** - Support for Azure Managed Identity authentication
- **Key Lifecycle** - Create, retrieve, and delete keys

## Supported Key Types

| Key Type | Azure Curve | JWS Algorithm |
|----------|-------------|---------------|
| `secp256r1` | P-256 | ES256 |
| `secp256k1` | P-256K | ES256K |
| `RSA` | - | RS256 |

## Installation

Add the dependency to your `build.gradle.kts`:

```kotlin
repositories {
    maven("https://maven.waltid.dev/releases")
}

dependencies {
    implementation("id.walt.crypto:waltid-crypto-azure:<version>")
}
```

## Configuration

### Authentication

The library uses Azure's default credential chain, which supports:
- Environment variables
- Managed Identity
- Azure CLI credentials
- Visual Studio Code credentials

Configure your Key Vault URL in the `AzureKeyMetadataSDK`:

```kotlin
val config = AzureKeyMetadataSDK(
    auth = AzureAuth(keyVaultUrl = "https://your-vault.vault.azure.net/"),
    keyName = "my-key-name",
    tags = mapOf("environment" to "production")
)
```

## Usage

### Generate a Key

```kotlin
import id.walt.crypto.keys.azure.AzureKey
import id.walt.crypto.keys.azure.AzureKeyMetadataSDK
import id.walt.crypto.keys.KeyType

val config = AzureKeyMetadataSDK(
    auth = AzureAuth(keyVaultUrl = "https://your-vault.vault.azure.net/"),
    keyName = "my-signing-key"
)

val key = AzureKey.generateKey(KeyType.secp256r1, config)
```

### Sign Data

```kotlin
val signature = key.signRaw(plaintext.toByteArray())
```

### Sign JWS

```kotlin
val jws = key.signJws(
    plaintext = payload.toByteArray(),
    headers = mapOf("typ" to JsonPrimitive("JWT"))
)
```

### Verify Signature

```kotlin
val result = key.verifyJws(signedJws)
result.onSuccess { payload ->
    println("Verified payload: $payload")
}.onFailure { error ->
    println("Verification failed: ${error.message}")
}
```

### Delete Key

```kotlin
val deleted = key.deleteKey()
```

## Azure RBAC Permissions

The following Azure Key Vault RBAC roles are required:

| Operation | Required Role |
|-----------|---------------|
| Create keys | Key Vault Crypto Officer |
| Sign/Verify | Key Vault Crypto User |
| Delete keys | Key Vault Crypto Officer |
| Get public key | Key Vault Crypto User |

## Error Handling

The library provides specific exceptions for common error scenarios:

- `KeyNotFoundException` - Key does not exist in the vault
- `UnauthorizedKeyAccess` - Insufficient RBAC permissions
- `KeyVaultUnavailable` - Azure Key Vault service unavailable
- `KeyTypeNotSupportedException` - Unsupported key type requested
- `SigningException` - Signing operation failed
- `VerificationException` - Signature verification failed

## Related Libraries

- **[waltid-crypto](../waltid-crypto)** - Core cryptographic library (required dependency)
- **[waltid-crypto-aws](../waltid-crypto-aws)** - AWS KMS integration
- **[waltid-crypto-oci](../waltid-crypto-oci)** - Oracle Cloud Infrastructure KMS integration

## Join the community

* Connect and get the latest updates: [Discord](https://discord.gg/AW8AgqJthZ) | [Newsletter](https://walt.id/newsletter) | [YouTube](https://www.youtube.com/channel/UCXfOzrv3PIvmur_CmwwmdLA) | [LinkedIn](https://www.linkedin.com/company/walt-id/)
* Get help, request features and report bugs: [GitHub Issues](https://github.com/walt-id/waltid-identity/issues)
* Find more in-depth documentation on our [docs site](https://docs.walt.id)

## License

Licensed under the [Apache License, Version 2.0](https://github.com/walt-id/waltid-identity/blob/main/LICENSE)

<div align="center">
<img src="../../../assets/walt-banner.png" alt="walt.id banner" />
</div>
