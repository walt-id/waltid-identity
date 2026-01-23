<div align="center">
<h1>walt.id Crypto AWS</h1>
 <span>by </span><a href="https://walt.id">walt.id</a>
 <p>AWS KMS integration for cryptographic key management using AWS SDK for Kotlin</p>

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

`waltid-crypto-aws` is a JVM-only extension library that provides `AWSKey`, a cryptographic key implementation backed by AWS Key Management Service (KMS). It uses the native AWS SDK for Kotlin to provide direct, high-performance access to AWS KMS operations. The `waltid-crypto` library provides AWS integration out of the box also, but only via the AWS REST API. If the REST API is sufficient for your use case, it is recommended to use the `waltid-crypto` library instead.

## Main Purpose

This library enables:

- **AWS KMS Integration**: Direct integration with AWS Key Management Service
- **Native SDK Access**: Uses AWS SDK for Kotlin instead of REST API calls
- **Key Management**: Create, manage, and delete keys in AWS KMS
- **Remote Signing**: Sign data using keys stored in AWS KMS
- **Remote Verification**: Verify signatures using AWS KMS public keys
- **Performance**: Improved performance compared to REST API-based implementations

## Key Concepts

### AWS KMS Keys

Keys are managed in AWS KMS:

- **Cloud Storage**: Keys are stored securely in AWS KMS
- **No Private Key Access**: Private keys never leave AWS KMS
- **Remote Operations**: Signing and verification operations are performed in AWS
- **Key Lifecycle**: Keys can be created, scheduled for deletion, and managed through AWS

### Supported Key Types

The library supports multiple key types:

- **ECDSA**: secp256r1, secp256k1, secp384r1, secp521r1
- **RSA**: RSA, RSA3072, RSA4096
- **Ed25519**: Not supported (AWS KMS limitation)

### AWS Signing Algorithms

AWS KMS uses specific signing algorithms:

- **ECDSA**: `ECDSA_SHA_256`, `ECDSA_SHA_384`, `ECDSA_SHA_512`
- **RSA**: `RSASSA_PKCS1_V1_5_SHA_256`, `RSASSA_PKCS1_V1_5_SHA_384`, `RSASSA_PKCS1_V1_5_SHA_512`

### Authentication

Uses AWS SDK's default credential provider chain:

- **Environment Variables**: `AWS_ACCESS_KEY_ID`, `AWS_SECRET_ACCESS_KEY`
- **AWS Credentials File**: `~/.aws/credentials`
- **IAM Roles**: For EC2 instances and ECS tasks
- **Container Credentials**: For containerized environments
- **SSO Credentials**: AWS SSO authentication

## Assumptions and Dependencies

### Platform Support

- **JVM Only**: This is a JVM-only library
- **Java 17+**: Requires Java 17 or later
- **AWS Account**: Requires an AWS account with KMS access

### Dependencies

- **waltid-crypto**: Core cryptographic library (API dependency)
- **AWS SDK for Kotlin**: Native AWS SDK (`aws.sdk.kotlin:kms`)
- **Nimbus JOSE JWT**: JWT/JWS support
- **Kotlinx Serialization**: JSON serialization
- **Kotlinx Coroutines**: Coroutine support
- **Kotlincrypto Hash**: SHA-2 hashing

### AWS Requirements

- **AWS Account**: Active AWS account
- **KMS Permissions**: IAM permissions for KMS operations:
  - `kms:CreateKey`
  - `kms:GetPublicKey`
  - `kms:Sign`
  - `kms:Verify`
  - `kms:ScheduleKeyDeletion`
- **Region Configuration**: AWS region must be specified

## Usage

### Prerequisites

1. **AWS Credentials**: Configure AWS credentials using one of the supported methods
2. **KMS Permissions**: Ensure IAM user/role has required KMS permissions
3. **Region**: Specify AWS region in key metadata

### Adding Dependency

```kotlin
dependencies {
    implementation(project(":waltid-libraries:crypto:waltid-crypto-aws"))
}
```

### Basic Usage

```kotlin
import id.walt.crypto.keys.aws.AWSKey
import id.walt.crypto.keys.aws.AWSKeyMetadataSDK
import id.walt.crypto.keys.KeyType

// Configure AWS connection
val config = AWSKeyMetadataSDK(
    region = "us-east-1"
)

// Generate a new key in AWS KMS
val key = AWSKey.generateKey(
    keyType = KeyType.secp256r1,
    config = config
)

// Use the key (implements Key interface from waltid-crypto)
val signature = key.signRaw(data)
val publicKey = key.getPublicKey()
val verification = key.verifyRaw(signature, data)

// Schedule key deletion (7-day pending window)
key.deleteKey()
```

### Key Operations

- **Generate**: Create new keys in AWS KMS
- **Sign**: Sign data using AWS KMS (private key never leaves AWS)
- **Verify**: Verify signatures using AWS KMS
- **Get Public Key**: Retrieve public key from AWS KMS
- **Delete**: Schedule key deletion (7-day pending window)

## Related Libraries

- **[waltid-crypto](../waltid-crypto)**: Core multiplatform cryptographic library (includes `AWSKeyRestAPI` for cross-platform use)
- **[waltid-crypto-oci](../waltid-crypto-oci)**: Similar extension for Oracle Cloud Infrastructure

## Join the community

* Connect and get the latest updates: [Discord](https://discord.gg/AW8AgqJthZ) | [Newsletter](https://walt.id/newsletter) | [YouTube](https://www.youtube.com/channel/UCXfOzrv3PIvmur_CmwwmdLA) | [LinkedIn](https://www.linkedin.com/company/walt-id/)
* Get help, request features and report bugs: [GitHub Issues](https://github.com/walt-id/waltid-identity/issues)
* Find more indepth documentation on our [docs site](https://docs.walt.id)

## License

Licensed under the [Apache License, Version 2.0](https://github.com/walt-id/waltid-identity/blob/main/LICENSE)

<div align="center">
<img src="../../../assets/walt-banner.png" alt="walt.id banner" />
</div>
