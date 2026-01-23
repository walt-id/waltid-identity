<div align="center">
 <h1>Kotlin Multiplatform DIF Definitions Parser library</h1>
 <span>by </span><a href="https://walt.id">walt.id</a>
 <p>Parse and match credentials against DIF Presentation Definition objects</p>

<a href="https://walt.id/community">
<img src="https://img.shields.io/badge/Join-The Community-blue.svg?style=flat" alt="Join community!" />
</a>
<a href="https://www.linkedin.com/company/walt-id/">
<img src="https://img.shields.io/badge/-LinkedIn-0072b1?style=flat&logo=linkedin" alt="Follow walt_id" />
</a>
  
  <h2>Status</h2>
  <img src="https://img.shields.io/badge/🟠%20Planned%20Deprecation-orange?style=for-the-badge&logo=clock" alt="Status: Planned Deprecation" />
  <br/>
  <em>This project is still supported by the development team at walt.id, but is planned for deprecation sometime in Q2 2026.<br />We encourage users to migrate to using alternative libraries listed below.</em>
</div>


## What This Library Contains

This library provides a complete implementation for parsing and processing DIF (Decentralized Identity Foundation) Presentation Definition objects. The library enables you to:

- **Parse Presentation Definitions** from JSON format as defined in the DIF Presentation Exchange specification
- **Match credentials** against input descriptors using JSON path queries and JSON Schema filters
- **Validate constraints** including field requirements, status checks, and format specifications
- **Support submission requirements** for complex credential selection rules (all, pick, count, min, max)
- **Handle presentation submissions** to map matched credentials back to input descriptors

## Main Purpose

**Note:** [DCQL (Digital Credentials Query Language)](../waltid-dcql) is the successor to Presentation Definition and is being used in the 1.0 major release of the OpenID4VP protocol (where Presentation Definition is deprecated). If you're building new systems, consider using DCQL instead.

DIF Presentation Definition is a standardized format for verifiers to specify which credentials they need from a wallet. This library enables wallets and verifiers to:

- Parse and validate Presentation Definition objects from JSON
- Match available credentials against verifier requirements using JSON path queries
- Apply JSON Schema filters to validate credential structure and values
- Support complex selection rules through submission requirements
- Create presentation submissions that map selected credentials to input descriptors

The library is particularly useful when building:
- Wallet applications that need to respond to Presentation Exchange requests
- Verifier services that need to construct Presentation Definitions
- Identity systems implementing the DIF Presentation Exchange protocol

## Key Concepts

### Presentation Definition

A **Presentation Definition** is a JSON object that describes what credentials a verifier needs. It contains:
- **ID**: A unique identifier for the definition
- **Input Descriptors**: Descriptions of the credentials being requested
- **Submission Requirements**: Rules for how credentials should be selected and combined
- **Format**: Optional format specifications for supported credential formats

### Input Descriptors

An **Input Descriptor** specifies what a single credential (or group of credentials) should contain:
- **ID**: Unique identifier within the definition
- **Constraints**: Field requirements, status checks, and other validation rules
- **Groups**: Optional grouping for submission requirements
- **Format**: Optional format requirements

### Constraints

Constraints define what a credential must satisfy:
- **Fields**: Required fields with JSON path queries and optional JSON Schema filters
- **Statuses**: Requirements for credential status (active, suspended, revoked)
- **Limit Disclosure**: Directive for selective disclosure (required, preferred, disallowed)
- **Subject Relationships**: Rules for subject_is_issuer, is_holder, and same_subject constraints

### Field Constraints

Each field constraint specifies:
- **Path**: JSON path expressions (e.g., `$.credentialSubject.given_name`, `$.vc.type`)
- **Filter**: Optional JSON Schema filter for value validation
- **Optional**: Whether the field is required or optional
- **Intent to Retain**: Whether the verifier intends to store the field value

### Submission Requirements

Submission Requirements define how credentials should be selected:
- **Rule**: Either `all` (all credentials must match) or `pick` (select from options)
- **Count/Min/Max**: Numeric constraints for selection
- **From**: Reference to input descriptor groups
- **From Nested**: Nested requirements for complex selection logic

### Presentation Submission

A **Presentation Submission** is the wallet's response, mapping selected credentials to input descriptors:
- **Definition ID**: References the Presentation Definition
- **Descriptor Map**: Maps each selected credential to an input descriptor ID and path

## Assumptions and Dependencies

This library makes several important assumptions:

- **DIF Presentation Exchange Compliance**: The library follows the DIF Presentation Exchange specification (https://identity.foundation/presentation-exchange/)
- **JSON Format**: Presentation Definitions are provided as JSON strings following the DIF schema
- **JSON Path Queries**: Fields are specified using JSON path notation (e.g., `$.credentialSubject.given_name`)
- **JSON Schema Filters**: Field filters use JSON Schema for validation
- **Multiplatform Support**: Works on JVM (Kotlin/Java), JavaScript, and iOS platforms (iOS requires `enableIosBuild=true` Gradle property)
- **W3C Credential Model**: The library integrates with W3C Verifiable Credentials through the `waltid-w3c-credentials` library

## How to Use This Library

### Basic Workflow

1. **Parse a Presentation Definition**: Use Kotlinx serialization to deserialize JSON into a `PresentationDefinition` object
2. **Prepare Credentials**: Convert your wallet's credentials into `JsonObject` format
3. **Match Credentials**: Use `PresentationDefinitionParser.matchCredentialsForInputDescriptor()` to find credentials matching each input descriptor
4. **Check Submission Requirements**: Validate that selected credentials satisfy all submission requirements
5. **Create Presentation Submission**: Build a `PresentationSubmission` mapping selected credentials to input descriptors

### Key Source Files

For detailed implementation examples and understanding the library internals, refer to:

- **`PresentationDefinition.kt`**: Data classes for Presentation Definition structure, including InputDescriptor and Constraints
- **`PresentationDefinitionParser.kt`**: Core parsing and matching logic using JSON path queries and JSON Schema validation
- **`SubmissionRequirement.kt`**: Data classes for submission requirement rules and constraints
- **`PresentationSubmission.kt`**: Data classes for presentation submission structure
- **Test files**: Located in `src/commonTest`, these provide comprehensive examples of Presentation Definitions and matching scenarios

## JVM/Kotlin Usage

### Installation

Add the library as a dependency in your `build.gradle.kts`:

```kotlin
repositories {
    maven { url = uri("https://maven.waltid.dev/releases") }
}

dependencies {
    implementation("id.walt.dif-definitions-parser:waltid-dif-definitions-parser:<version>")
}
```

### Basic Example

```kotlin
import id.walt.definitionparser.*
import kotlinx.coroutines.flow.asFlow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.json.*

fun main() {
    // Parse a Presentation Definition from JSON
    val definitionJson = """
    {
      "id": "32f54163-7166-48f1-93d8-ff217bdb0653",
      "input_descriptors": [
        {
          "id": "id_card",
          "name": "ID Card",
          "constraints": {
            "fields": [
              {
                "path": ["$.credentialSubject.given_name"],
                "filter": {
                  "type": "string"
                }
              },
              {
                "path": ["$.credentialSubject.family_name"],
                "filter": {
                  "type": "string"
                }
              }
            ]
          }
        }
      ]
    }
    """
    
    val json = Json { ignoreUnknownKeys = true }
    val definition = json.decodeFromString<PresentationDefinition>(definitionJson)
    
    // Prepare your credentials as JsonObjects
    val credentials = listOf(
        buildJsonObject {
            putJsonObject("credentialSubject") {
                put("given_name", "Alice")
                put("family_name", "Smith")
            }
        }
    ).asFlow()
    
    // Match credentials against each input descriptor
    definition.inputDescriptors.forEach { inputDescriptor ->
        val matchedCredentials = PresentationDefinitionParser.matchCredentialsForInputDescriptor(
            credentials,
            inputDescriptor
        )
        
        matchedCredentials.collect { credential ->
            println("Matched credential for ${inputDescriptor.name}: $credential")
        }
    }
}
```

### Working with Field Constraints

Field constraints use JSON path queries and optional JSON Schema filters:

```kotlin
val definition = PresentationDefinition(
    id = "example",
    inputDescriptors = listOf(
        PresentationDefinition.InputDescriptor(
            id = "banking_info",
            constraints = PresentationDefinition.InputDescriptor.Constraints(
                fields = listOf(
                    PresentationDefinition.InputDescriptor.Constraints.Field(
                        path = listOf(
                            "$.issuer",
                            "$.vc.issuer"
                        ),
                        filter = buildJsonObject {
                            put("type", "string")
                            put("pattern", "^did:example:123$|^did:example:456$")
                        }
                    ),
                    PresentationDefinition.InputDescriptor.Constraints.Field(
                        path = listOf("$.credentialSubject.account_number"),
                        filter = buildJsonObject {
                            put("type", "string")
                            put("pattern", "^[0-9]{10-12}$")
                        },
                        intentToRetain = true
                    )
                )
            )
        )
    )
)
```

### Using Submission Requirements

Submission requirements allow complex selection rules:

```kotlin
val definition = PresentationDefinition(
    id = "example",
    inputDescriptors = listOf(
        PresentationDefinition.InputDescriptor(
            id = "id_card",
            group = listOf("A"),
            constraints = PresentationDefinition.InputDescriptor.Constraints(
                fields = listOf(/* ... */)
            )
        ),
        PresentationDefinition.InputDescriptor(
            id = "membership",
            group = listOf("A"),
            constraints = PresentationDefinition.InputDescriptor.Constraints(
                fields = listOf(/* ... */)
            )
        )
    ),
    submissionRequirements = listOf(
        SubmissionRequirement(
            rule = SubmissionRequirement.Rule.pick,
            count = 1,
            from = "A"
        )
    )
)
```

### Creating Presentation Submissions

After matching credentials, create a presentation submission:

```kotlin
val submission = PresentationSubmission(
    id = "submission-123",
    definitionId = definition.id,
    descriptorMap = listOf(
        PresentationSubmission.Descriptor(
            id = "id_card",
            format = buildJsonObject { put("jwt_vc_json", buildJsonObject {}) },
            path = "$.verifiableCredential[0]"
        )
    )
)
```

### Working with Status Constraints

You can specify requirements for credential status:

```kotlin
val constraints = PresentationDefinition.InputDescriptor.Constraints(
    statuses = PresentationDefinition.InputDescriptor.Constraints.Statuses(
        active = PresentationDefinition.InputDescriptor.Constraints.StatusDirective(
            type = listOf("StatusList2021Entry"),
            directive = PresentationDefinition.InputDescriptor.Directive.required
        ),
        revoked = PresentationDefinition.InputDescriptor.Constraints.StatusDirective(
            type = listOf("StatusList2021Entry"),
            directive = PresentationDefinition.InputDescriptor.Directive.disallowed
        )
    )
)
```

### Limit Disclosure Directive

Control selective disclosure behavior:

```kotlin
val constraints = PresentationDefinition.InputDescriptor.Constraints(
    limitDisclosure = PresentationDefinition.InputDescriptor.Directive.required,
    fields = listOf(/* fields that should be selectively disclosed */)
)
```

## JavaScript Usage

### Installation

Install the library via npm:

```bash
npm install waltid-dif-definitions-parser
```

### Basic Example

```javascript
import { PresentationDefinition, PresentationDefinitionParser } from 'waltid-dif-definitions-parser';
import { Json } from 'kotlinx-serialization-json';

// Parse a Presentation Definition
const definitionJson = `{
  "id": "32f54163-7166-48f1-93d8-ff217bdb0653",
  "input_descriptors": [
    {
      "id": "id_card",
      "name": "ID Card",
      "constraints": {
        "fields": [
          {
            "path": ["$.credentialSubject.given_name"]
          }
        ]
      }
    }
  ]
}`;

const json = Json.Default;
const definition = json.decodeFromString(PresentationDefinition.serializer(), definitionJson);

// Prepare credentials
const credentials = [
  {
    credentialSubject: {
      given_name: "Alice",
      family_name: "Smith"
    }
  }
];

// Match credentials
definition.inputDescriptors.forEach(inputDescriptor => {
  const matched = PresentationDefinitionParser.matchCredentialsForInputDescriptor(
    credentials,
    inputDescriptor
  );
  
  matched.collect(credential => {
    console.log(`Matched credential for ${inputDescriptor.name}:`, credential);
  });
});
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
                implementation("id.walt.dif-definitions-parser:waltid-dif-definitions-parser:<version>")
            }
        }
    }
}
```

### Basic Example

```swift
import waltid_dif_definitions_parser
import kotlinx_serialization_json

func parseAndMatchCredentials() async throws {
    // Parse a Presentation Definition
    let definitionJson = """
    {
      "id": "32f54163-7166-48f1-93d8-ff217bdb0653",
      "input_descriptors": [
        {
          "id": "id_card",
          "name": "ID Card",
          "constraints": {
            "fields": [
              {
                "path": ["$.credentialSubject.given_name"]
              }
            ]
          }
        }
      ]
    }
    """
    
    let json = Json.Companion.shared.default
    let definition = try json.decodeFromString(
        serializer: PresentationDefinition.serializer(),
        string: definitionJson
    )
    
    // Prepare credentials
    let credentials = [
        [
            "credentialSubject": [
                "given_name": "Alice",
                "family_name": "Smith"
            ]
        ]
    ]
    
    // Match credentials
    for inputDescriptor in definition.inputDescriptors {
        let matched = PresentationDefinitionParser.Companion.shared
            .matchCredentialsForInputDescriptor(
                credentials: credentials,
                inputDescriptor: inputDescriptor
            )
        
        // Process matched credentials
        // Note: Flow collection in Swift requires additional coroutine handling
    }
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

