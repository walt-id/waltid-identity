<div align="center">
 <h1>Kotlin Multiplatform DCQL library</h1>
 <span>by </span><a href="https://walt.id">walt.id</a>
 <p>Digital Credentials Query Language (DCQL) helper library</p>

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

This library provides a complete implementation of DCQL (Digital Credentials Query Language), a standardized way for verifiers to specify which credentials they need from a wallet. The library enables you to:

- **Parse DCQL queries** from JSON format as defined in the OpenID4VP specification
- **Match credentials** against DCQL queries, checking format, metadata, claims, and trusted authorities
- **Support selective disclosure** by identifying which claims should be disclosed from selectively disclosable credentials
- **Handle multiple credential formats** including W3C VCs (JWT and LDP), SD-JWT VCs, mdoc credentials, and more
- **Validate credential sets** to ensure required combinations of credentials are satisfied

## Main Purpose

DCQL is the successor to Presentation Definition objects and is used in the OpenID4VP (OpenID for Verifiable Presentations) protocol. This library enables wallets and verifiers to:

- Express complex credential requirements in a standardized format
- Match available credentials against verifier queries efficiently
- Support fine-grained claim selection for privacy-preserving presentations
- Validate that credential combinations satisfy verifier requirements

The library is particularly useful when building:
- Wallet applications that need to respond to OpenID4VP authorization requests
- Verifier services that need to construct DCQL queries
- Identity systems implementing the OpenID4VP standard

## Key Concepts

### DCQL Query Structure

A DCQL query consists of:
- **Credential Queries**: Individual queries specifying the format, metadata, claims, and constraints for each credential type
- **Credential Sets**: Optional combinations of credential queries that must be satisfied together (required or optional)

### Credential Formats

The library supports multiple credential formats as defined in the OpenID4VP specification:
- **JWT VC JSON**: W3C Verifiable Credentials encoded as JWTs
- **LDP VC**: W3C Verifiable Credentials with Linked Data Proofs
- **SD-JWT VC**: Selective Disclosure JWT Verifiable Credentials
- **MSO MDOC**: ISO/IEC 18013-5 mobile driver's license credentials
- **AC VP**: AnonCreds Verifiable Presentations

### Credential Query Components

Each credential query can specify:
- **Format**: The required credential format identifier
- **Meta**: Format-specific metadata constraints (e.g., credential types for W3C VCs, VCT values for SD-JWTs, docType for mdocs)
- **Claims**: Specific claims that must be present, optionally with value constraints
- **Claim Sets**: Alternative combinations of claims that satisfy the query (for selective disclosure)
- **Trusted Authorities**: Constraints on acceptable issuers or trust frameworks
- **Multiple**: Whether multiple credentials can match this query

### Matching Process

The matching process evaluates credentials against queries in this order:
1. **Format Check**: Verifies the credential format matches the query
2. **Metadata Check**: Validates format-specific metadata (types, VCT values, docTypes, etc.)
3. **Trusted Authorities Check**: Verifies issuer constraints (if specified)
4. **Claims Check**: Validates required claims exist and match value constraints (if any)
5. **Credential Sets Check**: Ensures required credential set combinations are satisfied

### Selective Disclosure Support

For selectively disclosable credentials (like SD-JWTs), the library:
- Identifies which claims should be disclosed based on the query
- Returns disclosure objects for selected claims
- Supports claim sets to allow alternative combinations of disclosed claims

## Assumptions and Dependencies

This library makes several important assumptions:

- **OpenID4VP Compliance**: The library follows Section 6 of the OpenID4VP specification (https://openid.net/specs/openid-4-verifiable-presentations-1_0.html)
- **JSON Format**: DCQL queries are provided as JSON strings following the OpenID4VP schema
- **Credential Interface**: Credentials must implement the `DcqlCredential` interface, which provides format, data, and disclosures
- **Multiplatform Support**: Works on JVM (Kotlin/Java), JavaScript, and iOS platforms (iOS requires `enableIosBuild=true` Gradle property)
- **JSON Path Resolution**: Claims are accessed using JSON path notation (e.g., `["credentialSubject", "given_name"]`)
- **Format-Specific Metadata**: Different credential formats have different metadata requirements that must be properly structured

## How to Use This Library

### Basic Workflow

1. **Parse a DCQL Query**: Use `DcqlParser.parse()` to convert a JSON string into a `DcqlQuery` object
2. **Prepare Credentials**: Convert your wallet's credentials into `DcqlCredential` objects (or use `RawDcqlCredential` for simple cases)
3. **Match Credentials**: Use `DcqlMatcher.match()` to find credentials that satisfy the query
4. **Handle Results**: Process the match results, which include selected disclosures for selectively disclosable credentials

### Key Source Files

For detailed implementation examples and understanding the library internals, refer to:

- **`DcqlParser.kt`**: Parses JSON strings into `DcqlQuery` objects and serializes queries back to JSON
- **`DcqlMatcher.kt`**: The core matching logic that evaluates credentials against queries
- **`DcqlQuery.kt`**: Top-level query structure with credential queries and credential sets
- **`CredentialQuery.kt`**: Individual credential query definition with all constraints
- **`CredentialFormat.kt`**: Enumeration of supported credential formats
- **`CredentialModel.kt`**: Interface and implementations for representing credentials
- **`models/meta/`**: Format-specific metadata classes (W3cCredentialMeta, SdJwtVcMeta, MsoMdocMeta, etc.)
- **Test files**: Located in `src/commonTest`, these provide comprehensive examples of DCQL queries and matching scenarios

## JVM/Kotlin Usage

### Installation

Add the library as a dependency in your `build.gradle.kts`:

```kotlin
repositories {
    maven { url = uri("https://maven.waltid.dev/releases") }
}

dependencies {
    implementation("id.walt.dcql:waltid-dcql:<version>")
}
```

### Basic Example

```kotlin
import id.walt.dcql.*
import kotlinx.serialization.json.*

fun main() {
    // Parse a DCQL query from JSON
    val queryJson = """
    {
      "credentials": [
        {
          "id": "id_card_query",
          "format": "jwt_vc_json",
          "meta": {},
          "claims": [
            { "path": ["credentialSubject", "given_name"] },
            { "path": ["credentialSubject", "family_name"] }
          ]
        }
      ]
    }
    """
    
    val queryResult = DcqlParser.parse(queryJson)
    val query = queryResult.getOrThrow()
    
    // Prepare your credentials
    val credentials = listOf(
        RawDcqlCredential(
            id = "cred-1",
            format = "jwt_vc_json",
            data = buildJsonObject {
                putJsonObject("credentialSubject") {
                    put("given_name", "Alice")
                    put("family_name", "Smith")
                }
            }
        )
    )
    
    // Match credentials against the query
    val matchResult = DcqlMatcher.match(query, credentials)
    
    if (matchResult.isSuccess) {
        val matches = matchResult.getOrThrow()
        println("Found ${matches.size} matching credential(s)")
        matches.forEach { (queryId, results) ->
            results.forEach { result ->
                println("Query '$queryId' matched credential: ${result.credential.id}")
            }
        }
    } else {
        println("No matching credentials found")
    }
}
```

### Format-Specific Metadata

Different credential formats require different metadata:

```kotlin
// W3C VC with type constraints
val w3cQuery = """
{
  "credentials": [
    {
      "id": "w3c_query",
      "format": "jwt_vc_json",
      "meta": {
        "type": "W3cCredentialMeta",
        "type_values": [
          ["VerifiableCredential", "IDCard"]
        ]
      }
    }
  ]
}
"""

// SD-JWT VC with VCT constraints
val sdJwtQuery = """
{
  "credentials": [
    {
      "id": "sdjwt_query",
      "format": "dc+sd-jwt",
      "meta": {
        "type": "SdJwtVcMeta",
        "vct_values": ["org.example.Credential"]
      }
    }
  ]
}
"""

// mdoc with docType constraint
val mdocQuery = """
{
  "credentials": [
    {
      "id": "mdoc_query",
      "format": "mso_mdoc",
      "meta": {
        "type": "MsoMdocMeta",
        "doctype_value": "org.iso.18013.5.1.mDL"
      }
    }
  ]
}
"""
```

### Working with Claim Sets

Claim sets allow alternative combinations of claims for selective disclosure:

```kotlin
val queryWithClaimSets = """
{
  "credentials": [
    {
      "id": "flexible_query",
      "format": "jwt_vc_json",
      "meta": {},
      "claims": [
        { "id": "name", "path": ["credentialSubject", "given_name"] },
        { "id": "email", "path": ["credentialSubject", "email"] },
        { "id": "address", "path": ["credentialSubject", "address"] }
      ],
      "claim_sets": [
        ["name", "email"],
        ["name", "address"]
      ]
    }
  ]
}
"""
// This query will match if EITHER (name AND email) OR (name AND address) are available
```

### Credential Sets

Credential sets allow you to specify required or optional combinations of credentials:

```kotlin
val queryWithCredentialSets = """
{
  "credentials": [
    { "id": "id_card", "format": "jwt_vc_json", "meta": {} },
    { "id": "membership", "format": "jwt_vc_json", "meta": {} }
  ],
  "credential_sets": [
    {
      "required": true,
      "options": [
        ["id_card", "membership"]
      ]
    }
  ]
}
"""
// This requires BOTH id_card AND membership credentials to be present
```

### Handling Selective Disclosure Results

When matching selectively disclosable credentials, the results include selected disclosures:

```kotlin
val matchResult = DcqlMatcher.match(query, credentials)
matchResult.getOrThrow().forEach { (queryId, results) ->
    results.forEach { result ->
        println("Credential: ${result.credential.id}")
        
        // Check if selective disclosures were selected
        result.selectedDisclosures?.forEach { (path, disclosure) ->
            when (disclosure) {
                is DcqlDisclosure -> {
                    println("Selected disclosure: ${disclosure.name} = ${disclosure.value}")
                }
                is JsonElement -> {
                    println("Selected claim: $path = $disclosure")
                }
            }
        }
    }
}
```

## JavaScript Usage

### Installation

Install the library via npm:

```bash
npm install waltid-dcql
```

### Basic Example

```javascript
import { DcqlParser, DcqlMatcher, RawDcqlCredential } from 'waltid-dcql';

// Parse a DCQL query
const queryJson = `{
  "credentials": [
    {
      "id": "id_card_query",
      "format": "jwt_vc_json",
      "meta": {},
      "claims": [
        { "path": ["credentialSubject", "given_name"] }
      ]
    }
  ]
}`;

const queryResult = DcqlParser.parse(queryJson);
const query = queryResult.getOrThrow();

// Prepare credentials
const credentials = [
  new RawDcqlCredential(
    "cred-1",
    "jwt_vc_json",
    {
      credentialSubject: {
        given_name: "Alice",
        family_name: "Smith"
      }
    }
  )
];

// Match credentials
const matchResult = DcqlMatcher.match(query, credentials);

if (matchResult.isSuccess()) {
  const matches = matchResult.getOrThrow();
  console.log(`Found ${matches.size} matching credential(s)`);
} else {
  console.log("No matching credentials found");
}
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
                implementation("id.walt.dcql:waltid-dcql:<version>")
            }
        }
    }
}
```

### Basic Example

```swift
import waltid_dcql

func matchCredentials() async throws {
    // Parse a DCQL query
    let queryJson = """
    {
      "credentials": [
        {
          "id": "id_card_query",
          "format": "jwt_vc_json",
          "meta": {},
          "claims": [
            { "path": ["credentialSubject", "given_name"] }
          ]
        }
      ]
    }
    """
    
    let query = try DcqlParser.Companion.shared.parse(queryJson: queryJson).getOrThrow()
    
    // Prepare credentials
    let credentials = [
        RawDcqlCredential(
            id: "cred-1",
            format: "jwt_vc_json",
            data: [
                "credentialSubject": [
                    "given_name": "Alice",
                    "family_name": "Smith"
                ]
            ],
            originalCredential: nil,
            disclosures: nil
        )
    ]
    
    // Match credentials
    let matchResult = DcqlMatcher.Companion.shared.match(query: query, credentials: credentials)
    
    if let matches = try? matchResult.getOrThrow() {
        print("Found \(matches.count) matching credential(s)")
    } else {
        print("No matching credentials found")
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
