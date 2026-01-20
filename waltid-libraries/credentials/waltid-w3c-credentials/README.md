<div align="center">
<h1>Kotlin Multiplatform Verifiable Credentials library</h1>
 <span>by </span><a href="https://walt.id">walt.id</a>
  <p>Verifiable Credentials operations for 
<a href="https://www.w3.org/TR/vc-data-model">W3C v1.1</a>
and <a href="https://www.w3.org/TR/vc-data-model-2.0">W3C v2.0</a>
data models.</p>

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

## What This Library Contains

This library provides comprehensive operations for creating, issuing, and managing W3C Verifiable Credentials. The library enables you to:

- **Build W3C Verifiable Credentials** using the `CredentialBuilder` for both W3C v1.1 and v2.0 data models
- **Issue credentials** in JWT format using the JWS signature scheme
- **Issue credentials** in SD-JWT format with selective disclosure support
- **Configure credential issuance** using static properties or dynamic data functions
- **Build Verifiable Presentations** using the `PresentationBuilder` class
- **Sign credentials and presentations** using cryptographic keys from the `waltid-crypto` library
- **Integrate with verification policies** from the `waltid-verification-policies` library for credential validation

Learn more about W3C Credentials [here](https://docs.walt.id/concepts/digital-credentials/verifiable-credentials-w3c).

## Main Purpose

This library provides a complete implementation for working with W3C Verifiable Credentials, enabling you to:

- Create issuer services that generate and sign W3C Verifiable Credentials
- Build wallet applications that create and present Verifiable Presentations
- Support both W3C v1.1 and v2.0 data models in a single codebase
- Implement dynamic credential issuance with data functions for flexible credential creation
- Generate credentials in multiple formats (JWT, SD-JWT) based on requirements

The library is particularly useful when building:
- Credential issuer services that need to create standards-compliant W3C credentials
- Wallet applications that need to build and sign Verifiable Presentations
- Systems that need to support both W3C v1.1 (legacy) and v2.0 (modern) data models
- Applications requiring selective disclosure capabilities through SD-JWT

## Key Concepts

### W3C Data Model Versions

The library supports two W3C Verifiable Credential data model versions:

- **W3C v1.1**: The original W3C Verifiable Credentials Data Model specification
  - Uses `issuanceDate` and `expirationDate` fields
  - Default context: `https://www.w3.org/2018/credentials/v1`
  - Backward compatible with existing credential ecosystems

- **W3C v2.0**: The updated W3C Verifiable Credentials Data Model specification
  - Uses `validFrom` and `validUntil` fields
  - Default context: `https://www.w3.org/ns/credentials/v2`
  - Provides enhanced metadata and extensibility

### Credential Builder

The `CredentialBuilder` class provides a fluent API for constructing W3C credentials:
- **Type selection**: Choose between `W3CV11CredentialBuilder` or `W3CV2CredentialBuilder`
- **Credential subject**: Define the credential subject data using `useCredentialSubject()`
- **Metadata**: Set issuer, subject DID, validity dates, credential status, and terms of use
- **Extensions**: Add custom data using `useData()` for additional properties

### Issuance Methods

The library provides multiple ways to issue credentials:

- **Static Configuration** (`baseIssue`): Directly specify data overwrites and updates
- **Dynamic Configuration** (`mergingJwtIssue`, `mergingSdJwtIssue`): Use data functions and mappings for flexible credential creation
- **Data Functions**: Built-in functions for accessing context data (issuerDid, subjectDid, display, etc.)

### Signature Formats

Credentials can be issued in two signature formats:

- **JWT**: Standard JSON Web Token format with JWS signature
- **SD-JWT**: Selective Disclosure JWT format that allows selective disclosure of credential claims

### Presentation Builder

The `PresentationBuilder` class enables creating Verifiable Presentations:
- **Holder binding**: Specify holder DID or public key JWK
- **Credential aggregation**: Add multiple verifiable credentials to a presentation
- **JWT claims**: Configure nonce, audience, issuance time, and expiration
- **Signing**: Sign presentations using holder keys

### Policy Validation

The library integrates with `waltid-verification-policies` for credential and presentation validation:
- **Static policies**: Pre-defined verification policies (signature, expiration, etc.)
- **Parameterized policies**: Policies with configurable arguments (schema validation, allowed issuers, etc.)
- **Presentation validation**: Validate complete Verifiable Presentations with VP and VC policies

## Assumptions and Dependencies

This library makes several important assumptions:

- **W3C Compliance**: Credentials follow the W3C Verifiable Credentials Data Model v1.1 or v2.0 specifications
- **Multiplatform Support**: Works on JVM (Kotlin/Java), JavaScript, and iOS platforms (iOS requires `enableIosBuild=true` Gradle property)
- **JWS Signature Scheme**: Credentials are signed using JSON Web Signature (JWS) scheme
- **DID Integration**: Uses `waltid-did` library for DID-related operations and key resolution
- **Crypto Integration**: Uses `waltid-crypto` library for cryptographic key operations and signing
- **SD-JWT Integration**: Uses `waltid-sdjwt` library for selective disclosure JWT processing
- **Policy Integration**: Verification policies are provided by `waltid-verification-policies` library

The library relies on the following walt.id libraries:

- [waltid-sd-jwt library](../sdjwt/waltid-sdjwt) for SD-JWT related processing
- [waltid-did library](../waltid-did) for DID related operations
- [waltid-crypto library](../crypto/waltid-crypto) for key related operations
- [waltid-verification-policies library](./waltid-verification-policies) for credential and presentation validation

## How to Use This Library

### Basic Workflow

1. **Build Credential**: Use `CredentialBuilder` to construct a W3C credential with desired properties
2. **Issue Credential**: Use `Issuer.baseIssue()`, `Issuer.mergingJwtIssue()`, or `Issuer.mergingSdJwtIssue()` to sign and issue the credential
3. **Build Presentation** (optional): Use `PresentationBuilder` to create a Verifiable Presentation containing credentials
4. **Validate** (optional): Use `Verifier` from `waltid-verification-policies` to validate credentials and presentations against policies

### Key Source Files

For detailed implementation examples and understanding the library internals, refer to:

- **`CredentialBuilder.kt`**: Main credential builder class with fluent API for constructing credentials
- **`issuance/Issuer.kt`**: Credential issuance methods (static and dynamic configuration)
- **`issuance/DataFunctions.kt`**: Built-in data functions for dynamic credential creation
- **`PresentationBuilder.kt`**: Verifiable Presentation construction and signing
- **`vc/vcs/W3CVC.kt`**: Core W3C Verifiable Credential data structure and signing methods
- **`vc/vcs/W3CV11DataModel.kt`**: W3C v1.1 data model implementation
- **`vc/vcs/W3CV2DataModel.kt`**: W3C v2.0 data model implementation
- **`schemes/JwsSignatureScheme.kt`**: JWS signature scheme implementation
- **Test files**: Located in `src/commonTest`, these provide comprehensive examples of all library features

## JVM/Kotlin Usage

### Installation

Add the verifiable credentials library as a dependency to your Kotlin or Java project.

#### walt.id Repository

Add the Maven repository which hosts the walt.id libraries to your build.gradle file:

```kotlin
repositories {
    maven { url = uri("https://maven.waltid.dev/releases") }
}
```

#### Library Dependency

Add the verifiable credentials library as a dependency. Specify the version that coincides with the latest or required snapshot for your project. [Latest releases](https://github.com/walt-id/waltid-identity/releases).

```kotlin
dependencies {
    implementation("id.walt.credentials:waltid-w3c-credentials:<version>")
}
```

Replace `version` with the version of the walt.id verifiable credential library you want to use.
Note: As the verifiable credentials lib is part of the mono-repo walt.id identity, you need to use the version of walt.id identity.

### Build Credential

Build a W3Cv2.0 verifiable credential:

```kotlin
val credentialBuilder = CredentialBuilderType.W3CV2CredentialBuilder
val credentialSubject = mapOf(
  "entityIdentification" to entityIdentificationNumber,
  "issuingAuthority" to issuingAuthorityId,
  "issuingCircumstances" to mapOf(
    "proofType" to proofType,
    "locationType" to "physicalLocation",
    "location" to proofLocation
  )
).toJsonObject()
val w3cCredential = CredentialBuilder(credentialBuilder).apply {
  useCredentialSubject(credentialSubject)
}.buildW3C()
```

### Issue Credential

#### Static Configuration

Issue a jwt-formatted verifiable credential:

```kotlin
val dataOverWrites = mapOf("entityIdentification" to entityIdentificationNumber.toJsonElement())
val dataUpdates = mapOf("issuingAuthority" to issuingAuthorityId.toJsonElement())
val jwt = w3cCredential.baseIssue(
  key = issuerKey,
  did = issuerDid,
  subject = holderDid,
  dataOverwrites = dataOverwrites,
  dataUpdates = dataUpdates,
  additionalJwtHeader = emptyMap(),
  additionalJwtOptions = emptyMap(),
)
```

#### Dynamic Configuration

Issue a jwt-formatted verifiable credential:

```kotlin
val jwt = w3cCredential.mergingJwtIssue(
  issuerKey = issuerKey,
  issuerId = issuerDid,
  subjectDid = holderDid,
  mappings = mapping,
  additionalJwtHeader = emptyMap(),
  additionalJwtOptions = emptyMap(),
)
```

Issue an sdjwt-formatted verifiable credential:

```kotlin
val sdjwt = w3cCredential.mergingSdJwtIssue(
  issuerKey = issuerKey,
  issuerId = issuerDid,
  subjectDid = holderDid,
  mappings = mapping,
  additionalJwtHeader = emptyMap(),
  additionalJwtOptions = emptyMap(),
  disclosureMap = selectiveDisclosureMap
)
```

### Validate Policy

Validate credentials and presentations using verification policies:

```kotlin
val vpToken = "jwt"
// configure the validation policies
val vcPolicies = Json.parseToJsonElement(
  """
       [
          "signature",
          "expired",
          "not-before"
        ] 
    """
).jsonArray.parsePolicyRequests()
val vpPolicies = Json.parseToJsonElement(
  """
        [
          "signature",
          "expired",
          "not-before"
        ]
    """
).jsonArray.parsePolicyRequests()
val specificPolicies = Json.parseToJsonElement(
  """
       {
          "OpenBadgeCredential": [
              {
                "policy": "schema",
                "args": {
                    "type": "object",
                    "required": ["issuer"],
                    "properties": {
                        "issuer": {
                            "type": "object"
                        }
                    }
                }
            }
          ]
       } 
    """
).jsonObject.mapValues { it.value.jsonArray.parsePolicyRequests() }

// validate verifiable presentation against the configured policies
val validationResult = Verifier.verifyPresentation(
        vpTokenJwt = vpToken,
        vpPolicies = vpPolicies,
        globalVcPolicies = vcPolicies,
        specificCredentialPolicies = specificPolicies,
        mapOf(
            "presentationSubmission" to JsonObject(emptyMap()),
            "challenge" to "abc"
        )
    )
```

### Building Verifiable Presentations

```kotlin
val presentationBuilder = PresentationBuilder().apply {
    did = holderDid
    nonce = challengeFromVerifier
    audience = verifierId
    addCredential(credential1, credential2)
}

val vpToken = presentationBuilder.buildAndSign(holderKey)
```

### Working with W3C v1.1 vs v2.0

```kotlin
// Build W3C v1.1 credential
val v11Credential = CredentialBuilder(CredentialBuilderType.W3CV11CredentialBuilder).apply {
    issuerDid = "did:example:issuer"
    subjectDid = "did:example:holder"
    useCredentialSubject(buildJsonObject {
        put("degree", buildJsonObject {
            put("type", "BachelorDegree")
            put("name", "Bachelor of Science")
        })
    })
}.buildW3C()

// Build W3C v2.0 credential
val v20Credential = CredentialBuilder(CredentialBuilderType.W3CV2CredentialBuilder).apply {
    issuerDid = "did:example:issuer"
    subjectDid = "did:example:holder"
    validFromNow()
    validFor(Duration.parse("P1Y")) // Valid for 1 year
    useCredentialSubject(buildJsonObject {
        put("degree", buildJsonObject {
            put("type", "BachelorDegree")
            put("name", "Bachelor of Science")
        })
    })
}.buildW3C()
```

## JavaScript Usage

### Installation

Install the library via npm:

```bash
npm install waltid-w3c-credentials
```

### Basic Example

```javascript
import { CredentialBuilder, CredentialBuilderType, Issuer } from 'waltid-w3c-credentials';

// Build a credential
const credentialBuilder = CredentialBuilderType.W3CV2CredentialBuilder;
const credentialSubject = {
  "degree": {
    "type": "BachelorDegree",
    "name": "Bachelor of Science"
  }
};

const w3cCredential = new CredentialBuilder(credentialBuilder)
  .useCredentialSubject(credentialSubject)
  .buildW3C();

// Issue the credential
const jwt = await Issuer.Companion.shared.baseIssue(
  w3cCredential,
  issuerKey,
  issuerDid,
  holderDid,
  {}, // dataOverwrites
  {}, // dataUpdates
  {}, // additionalJwtHeader
  {}  // additionalJwtOptions
);
```

## iOS/Swift Usage

### Installation

Add the library as a dependency in your `Package.swift` or Xcode project:

```swift
dependencies: [
    .package(url: "https://github.com/walt-id/waltid-identity", from: "x.x.x")
]
```

Or add it to your `build.gradle.kts` in a Kotlin Multiplatform Mobile project:

```kotlin
kotlin {
    iosArm64()
    iosSimulatorArm64()
    
    sourceSets {
        val iosMain by creating {
            dependencies {
                implementation("id.walt.credentials:waltid-w3c-credentials:<version>")
            }
        }
    }
}
```

### Basic Example

```swift
import waltid_w3c_credentials

func buildAndIssueCredential() async throws {
    // Build a credential
    let credentialBuilder = CredentialBuilderType.w3cV2CredentialBuilder
    let credentialSubject = [
        "degree": [
            "type": "BachelorDegree",
            "name": "Bachelor of Science"
        ]
    ]
    
    let w3cCredential = CredentialBuilder(builderType: credentialBuilder)
        .useCredentialSubject(data: credentialSubject)
        .buildW3C()
    
    // Issue the credential
    let jwt = try await Issuer.Companion.shared.baseIssue(
        w3cVc: w3cCredential,
        key: issuerKey,
        issuerId: issuerDid,
        subject: holderDid,
        dataOverwrites: [:],
        dataUpdates: [:],
        additionalJwtHeaders: [:],
        additionalJwtOptions: [:]
    )
    
    print("Issued credential: \(jwt)")
}
```

## Join the community

* Connect and get the latest updates: [Discord](https://discord.gg/AW8AgqJthZ) | [Newsletter](https://walt.id/newsletter) | [YouTube](https://www.youtube.com/channel/UCXfOzrv3PIvmur_CmwwmdLA) | [LinkedIn](https://www.linkedin.com/company/walt-id/)
* Get help, request features and report bugs: [GitHub Issues](https://github.com/walt-id/waltid-identity/issues)
* Find more indepth documentation on our [docs site](https://docs.walt.id)


## License

Licensed under the [Apache License, Version 2.0](https://github.com/walt-id/waltid-identity/blob/main/LICENSE)

<div align="center">
<img src="../../../assets/walt-banner.png" alt="walt.id banner" />
</div>
