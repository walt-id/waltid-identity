<div align="center">
<h1>walt.id Crypto OCI</h1>
 <span>by </span><a href="https://walt.id">walt.id</a>
 <p>Oracle Cloud Infrastructure (OCI) KMS integration for cryptographic key management</p>

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

`waltid-crypto-oci` is a JVM-only extension library that provides `OCIKey`, a cryptographic key implementation backed by Oracle Cloud Infrastructure (OCI) Key Management Service (KMS). It uses the native OCI Java SDK to provide direct access to OCI KMS operations. The `waltid-crypto` library provides OCI integration out of the box also, but only via the OCI REST API. If the REST API is sufficient for your use case, it is recommended to use the `waltid-crypto` library instead.

## Main Purpose

This library enables:

- **OCI KMS Integration**: Direct integration with Oracle Cloud Infrastructure Key Management Service
- **Native SDK Access**: Uses OCI Java SDK instead of REST API calls
- **Key Management**: Create and manage keys in OCI KMS vaults
- **Remote Signing**: Sign data using keys stored in OCI KMS
- **Remote Verification**: Verify signatures using OCI KMS public keys
- **Performance**: Improved performance compared to REST API-based implementations

## Key Concepts

### OCI KMS Keys

Keys are managed in OCI KMS vaults:

- **Cloud Storage**: Keys are stored securely in OCI KMS vaults
- **No Private Key Access**: Private keys never leave OCI KMS
- **Remote Operations**: Signing and verification operations are performed in OCI
- **Vault Management**: Keys are organized within OCI KMS vaults
- **Compartment Organization**: Keys are organized within OCI compartments

### Supported Key Types

The library supports key types available in OCI KMS:

- **RSA**: RSA keys with various key sizes
- **ECDSA**: Elliptic curve keys (specific curves supported by OCI)

### OCI Authentication

Uses OCI SDK's authentication mechanisms:

- **Instance Principals**: For OCI compute instances
- **API Keys**: User API keys with configuration file
- **Resource Principals**: For OCI functions and container instances

### Protection Modes

OCI KMS supports different protection modes:

- **Software**: Keys protected by software (default)
- **HSM**: Hardware Security Module protection (when available)

## Assumptions and Dependencies

### Platform Support

- **JVM Only**: This is a JVM-only library
- **Java 15+**: Requires Java 15 or later (for Ed25519 support)
- **OCI Account**: Requires an Oracle Cloud Infrastructure account with KMS access

### Dependencies

- **waltid-crypto**: Core cryptographic library (API dependency)
- **OCI Java SDK**: Native OCI SDK (`com.oracle.oci.sdk:oci-java-sdk-shaded-full`)
- **Nimbus JOSE JWT**: JWT/JWS support
- **Kotlinx Serialization**: JSON serialization
- **Kotlinx Coroutines**: Coroutine support
- **Kotlincrypto Hash**: SHA-2 hashing

### OCI Requirements

- **OCI Account**: Active Oracle Cloud Infrastructure account
- **KMS Vault**: OCI KMS vault must be created
- **IAM Permissions**: IAM policies for KMS operations:
  - `kms:CreateKey`
  - `kms:GetKey`
  - `kms:GetKeyVersion`
  - `kms:Sign`
  - `kms:Verify`
- **Compartment Access**: Access to the compartment containing the vault

## Usage

### Prerequisites

1. **OCI Configuration**: Configure OCI authentication (instance principals, API keys, etc.)
2. **KMS Vault**: Create an OCI KMS vault
3. **IAM Permissions**: Ensure IAM user/role has required KMS permissions
4. **Compartment ID**: Know the compartment ID containing the vault

### Adding Dependency

```kotlin
dependencies {
    implementation(project(":waltid-libraries:crypto:waltid-crypto-oci"))
}
```

### Basic Usage

```kotlin
import id.walt.crypto.keys.oci.OCIKey
import id.walt.crypto.keys.oci.OCIsdkMetadata

// Configure OCI connection
val config = OCIsdkMetadata(
    vaultId = "ocid1.vault.oc1..example",
    compartmentId = "ocid1.compartment.oc1..example"
)

// Generate a new key in OCI KMS
val key = OCIKey.generateKey(config)

// Use the key (implements Key interface from waltid-crypto)
val signature = key.signRaw(data)
val publicKey = key.getPublicKey()
val verification = key.verifyRaw(signature, data)
```

### Key Operations

- **Generate**: Create new keys in OCI KMS
- **Sign**: Sign data using OCI KMS (private key never leaves OCI)
- **Verify**: Verify signatures using OCI KMS
- **Get Public Key**: Retrieve public key from OCI KMS
- **Get Metadata**: Retrieve key metadata from OCI

## Related Libraries

- **[waltid-crypto](../waltid-crypto)**: Core multiplatform cryptographic library (includes `OCIRestKey` for cross-platform use)
- **[waltid-crypto-aws](../waltid-crypto-aws)**: Similar extension for AWS KMS

## Join the community

* Connect and get the latest updates: [Discord](https://discord.gg/AW8AgqJthZ) | [Newsletter](https://walt.id/newsletter) | [YouTube](https://www.youtube.com/channel/UCXfOzrv3PIvmur_CmwwmdLA) | [LinkedIn](https://www.linkedin.com/company/walt-id/)
* Get help, request features and report bugs: [GitHub Issues](https://github.com/walt-id/waltid-identity/issues)
* Find more indepth documentation on our [docs site](https://docs.walt.id)

## License

Licensed under the [Apache License, Version 2.0](https://github.com/walt-id/waltid-identity/blob/main/LICENSE)

<div align="center">
<img src="../../../assets/walt-banner.png" alt="walt.id banner" />
</div>
