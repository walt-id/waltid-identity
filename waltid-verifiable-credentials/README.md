<div align="center">
<h1>Kotlin Multiplatform Verifiable Credentials library</h1>
 <span>by </span><a href="https://walt.id">walt.id</a>
  <p>Verifiable Credentials operations for 
<a href="https://www.w3.org/TR/vc-data-model">W3C v1.1</a>
and <a href="https://www.w3.org/TR/vc-data-model-2.0">W3C v2.0</a>
data models.<p>

<a href="https://walt.id/community">
<img src="https://img.shields.io/badge/Join-The Community-blue.svg?style=flat" alt="Join community!" />
</a>
<a href="https://twitter.com/intent/follow?screen_name=walt_id">
<img src="https://img.shields.io/twitter/follow/walt_id.svg?label=Follow%20@walt_id" alt="Follow @walt_id" />
</a>
</div>

## What it provides

- create and sign [W3Cv1.1](https://www.w3.org/TR/vc-data-model)
  and [W3Cv2.0](https://www.w3.org/TR/vc-data-model-2.0) verifiable credentials (jwt, sd-jwt)
  using the JWS signature scheme
    - static properties
    - dynamically configurable properties
      (using [data-functions](https://docs.oss.walt.id/issuer/api/data-functions))
- perform policy validation for verifiable credentials and presentations
    - [static policies](https://docs.oss.walt.id/verifier/api/policies#static-verification-policies)
    - [parameterized policies](https://docs.oss.walt.id/verifier/api/policies#parameterized-verification-policies)
- policy management

Verifiable Credentials library relies on the following walt.id libraries:

- [waltid-sd-jwt library](https://github.com/walt-id/waltid-identity/tree/main/waltid-sdjwt)
  for sd-jwt related processing
- [waltid-did library](https://github.com/walt-id/waltid-identity/tree/main/waltid-did)
  for DID related operations
- [waltid-crypto library](https://github.com/walt-id/waltid-identity/tree/main/waltid-crypto)
  for key related operations


## Installation
Add the verifiable credentials library as a dependency to your Kotlin or Java project, which includes the crypto and did lib.

### walt.id Repository

Add the Maven repository which hosts the walt.id libraries to your build.gradle file.

```kotlin
repositories {
    maven { url = uri("https://maven.waltid.dev/releases") }
} 
```

### Library Dependency

Adding the verifiable credentials library as dependency. Specify the version that coincides with the latest or required
snapshot for your project. [Latest releases](https://github.com/walt-id/waltid-identity/releases).

```kotlin
dependencies {
    implementation("id.walt.credentials:waltid-verifiable-credentials:<version>")
}
```

Replace `version` with the version of the walt.id verifiable credential library you want to use.
Note: As the verifiable credentials lib is part of the mono-repo walt.id identity, you need to use the version of
walt.id identity.

## How to use it

#### Build credential

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

### Issue credential

#### Static configuration

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

#### Dynamic configuration

Issue a jwt-formatted verifiable credential:

```kotlin
val jwt = w3cCredential.mergingJwtIssue(
  issuerKey = issuerKey,
  issuerDid = issuerDid,
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
  issuerDid = issuerDid,
  subjectDid = holderDid,
  mappings = mapping,
  additionalJwtHeader = emptyMap(),
  additionalJwtOptions = emptyMap(),
  disclosureMap = selectiveDisclosureMap
)
```

#### Validate policy

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
