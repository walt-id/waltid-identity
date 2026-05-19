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
<img src="https://img.shields.io/badge/Status-Active-brightgreen?style=for-the-badge" alt="Status: Active" />

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

## Key Concepts

### OCI KMS Keys

Keys are managed in OCI KMS vaults:

- **Cloud Storage**: Keys are stored securely in OCI KMS vaults
- **No Private Key Access**: Private keys never leave OCI KMS
- **Remote Operations**: Signing and verification operations are performed in OCI
- **Compartment Organization**: Keys are organized within OCI compartments

### Supported Key Types

| KeyType     | OCI Algorithm | Signing Algorithm      |
|-------------|---------------|------------------------|
| `secp256r1` | ECDSA P-256   | `ECDSA_SHA_256`        |
| `secp384r1` | ECDSA P-384   | `ECDSA_SHA_384`        |
| `secp521r1` | ECDSA P-521   | `ECDSA_SHA_512`        |
| `RSA`       | RSA-2048      | `SHA_256_RSA_PKCS_PSS` |
| `RSA3072`   | RSA-3072      | `SHA_384_RSA_PKCS_PSS` |
| `RSA4096`   | RSA-4096      | `SHA_512_RSA_PKCS_PSS` |

> `Ed25519` and `secp256k1` are **not** supported by OCI KMS.

### OCI Authentication

Configurable via `OCIsdkMetadata.authType`:

| authType             | Description                                        |
|----------------------|----------------------------------------------------|
| `INSTANCE_PRINCIPAL` | Default — for OCI compute instances                |
| `CONFIG_FILE`        | User API keys via `~/.oci/config` (or custom path) |
| `RESOURCE_PRINCIPAL` | For OCI functions and container instances          |

### Protection Modes

- **Software**: Keys protected by software (default)
- **HSM**: Hardware Security Module protection (when available)

## Assumptions and Dependencies

### Platform Support

- **JVM Only**: This is a JVM-only library
- **Java 15+**: Requires Java 15 or later
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
// Instance Principals (default — for OCI compute instances)
val config = OCIsdkMetadata(
    vaultId = "ocid1.vault.oc1.eu-frankfurt-1.example",
    compartmentId = "ocid1.compartment.oc1..example"
)

// Config-file auth (API key — for local dev / non-OCI environments)
val configFileAuth = OCIsdkMetadata(
    vaultId = "ocid1.vault.oc1.eu-frankfurt-1.example",
    compartmentId = "ocid1.compartment.oc1..example",
    authType = "CONFIG_FILE",
    configFilePath = "/home/user/.oci/config",
    configProfile = "DEFAULT"
)

// Generate a new secp256r1 key (default)
val key = OCIKey.generateKey(config)

// Generate a specific key type
val p384key = OCIKey.generateKey(KeyType.secp384r1, config)
val rsaKey = OCIKey.generateKey(KeyType.RSA4096, config)

// Sign and verify
val plaintext = "Hello OCI".encodeToByteArray()
val signed = key.signJws(plaintext, mapOf("kid" to JsonPrimitive(key.getKeyId())))
val result = key.verifyJws(signed)

// Schedule key deletion (minimum 7-day OCI notice period)
key.deleteKey()
```

### Key Operations

- **Generate**: Create ECDSA or RSA keys in OCI KMS; key type is inferred automatically on load
- **Sign (raw)**: SHA-256 digest sent as `messageType=DIGEST`; returns DER-encoded signature bytes
- **Sign (JWS)**: DER signature converted to IEEE P1363 (R||S) for EC keys, standard RSA-PSS for RSA
- **Verify (raw)**: Mirrors signing — digest + DER signature sent to OCI verify
- **Verify (JWS)**: IEEE P1363 signature converted back to DER before OCI verification
- **Get Public Key**: PEM retrieved from OCI and cached as a local `JWKKey`
- **Delete Key**: Schedules deletion with a 7-day pending window (OCI minimum)

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
