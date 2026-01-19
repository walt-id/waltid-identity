<div align="center">
 <h1>Kotlin Multiplatform Holder Policies library</h1>
 <span>by </span><a href="https://walt.id">walt.id</a>
 <p>Policy-based access control for wallet holders to manage credential reception and presentation</p>

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

This library provides a policy-based access control system for wallet holders to control credential operations. The library enables you to:

- **Define holder policies** that control whether credentials can be received or presented
- **Apply policies based on credential properties** such as issuer, format, subject, and claims
- **Use priority-based policy evaluation** where policies are evaluated in priority order
- **Support multiple policy check types** including basic checks, JSON Schema validation, and DCQL queries
- **Implement default allow/deny patterns** using catch-all policies
- **Work with unified credentials** using the `DigitalCredential` interface from `waltid-digital-credentials`

You can learn more about the concept and how we use it in our enterprise stack in our [documentation](https://docs.walt.id/enterprise-stack/services/holder-policy-store-service/overview)

## Main Purpose

This library enables wallet holders to implement fine-grained access control over credential operations. It's designed for scenarios where holders need to:

- **Block receiving credentials** from untrusted issuers or with specific attributes
- **Prevent presenting credentials** that contain sensitive information
- **Implement privacy controls** to prevent accidental disclosure of PII
- **Enforce organizational policies** on credential usage
- **Apply default-deny or default-allow** security models

The library is particularly useful when building:
- Wallet applications that need to protect users from malicious or unwanted credentials
- Enterprise wallets requiring organizational policy enforcement
- Privacy-focused wallets that need to prevent accidental data disclosure
- Systems that need to implement consent and data minimization principles

## Key Concepts

### Holder Policies

A **HolderPolicy** defines access control rules for credential operations:
- **Priority**: Controls evaluation order (lower numbers evaluated first)
- **Direction**: Whether the policy applies to `RECEIVE` (credential reception) or `PRESENT` (credential presentation) operations
- **Apply**: Optional check that determines if the policy applies to the credentials
- **Check**: Optional check that validates credential properties
- **Action**: Either `ALLOW` or `BLOCK` the operation

### Policy Evaluation

The **HolderPolicyEngine** evaluates policies:
1. Filters policies by the `apply` check (if present) or includes all policies if `apply` is null
2. Sorts policies by priority (ascending)
3. Evaluates the first policy where the `check` condition matches
4. Returns the action (`ALLOW` or `BLOCK`) from the matching policy, or `null` if no policy matches

### Policy Checks

Policy checks determine if credentials match policy criteria:
- **BasicHolderPolicyCheck**: Checks format, issuer, subject, and claims (presence and values)
- **JsonSchemaHolderPolicyCheck**: Validates credentials against JSON Schema
- **DcqlHolderPolicyCheck**: Matches credentials against DCQL queries
- **ApplyAllHolderPolicyCheck**: Matches all credentials (used for default policies)

### Priority-Based Evaluation

Policies are evaluated in priority order:
- Lower priority numbers are evaluated first
- The first matching policy determines the action
- Higher priority policies can override lower priority ones
- Default policies typically use high priority (e.g., 99) to apply last

### Apply vs. Check

- **Apply**: Determines if a policy should be considered for evaluation (filtering)
- **Check**: Determines if the policy's condition matches the credentials (validation)
- If `apply` is null, the policy applies to all credentials
- If `check` is null, the policy matches all credentials that passed the `apply` filter

## Assumptions and Dependencies

This library makes several important assumptions:

- **DigitalCredential Interface**: Works with the unified credential interface from `waltid-digital-credentials`
- **Multiplatform Support**: Works on JVM, JavaScript, and iOS platforms (iOS requires `enableIosBuild=true` Gradle property)
- **Flow-Based API**: Uses Kotlin Flow for asynchronous credential and policy streams
- **Priority Ordering**: Policies are evaluated in ascending priority order
- **First Match Wins**: The first matching policy determines the action
- **DCQL Integration**: DCQL checks require the `waltid-dcql` library for credential matching

## How to Use This Library

### Basic Workflow

1. **Define Policies**: Create `HolderPolicy` objects with priority, direction, checks, and actions
2. **Prepare Credentials**: Parse credentials into `DigitalCredential` objects using `CredentialParser`
3. **Evaluate Policies**: Use `HolderPolicyEngine.evaluate()` with a Flow of policies and a Flow of credentials
4. **Handle Results**: Process the returned `HolderPolicyAction` (`ALLOW`, `BLOCK`, or `null`)

### Key Source Files

For detailed implementation examples and understanding the library internals, refer to:

- **`HolderPolicy.kt`**: Policy data structure and action/direction enums
- **`HolderPolicyEngine.kt`**: Core policy evaluation engine
- **`checks/HolderPolicyCheck.kt`**: Base interface for all policy checks
- **`checks/BasicHolderPolicyCheck.kt`**: Basic credential property checks
- **`checks/JsonSchemaHolderPolicyCheck.kt`**: JSON Schema validation checks
- **`checks/DcqlHolderPolicyCheck.kt`**: DCQL query matching checks
- **`checks/ApplyAllHolderPolicyCheck.kt`**: Catch-all check for default policies
- **Test files**: Located in `src/commonTest`, these provide comprehensive examples

## JVM/Kotlin Usage

### Installation

Add the library as a dependency in your `build.gradle.kts`:

```kotlin
repositories {
    maven { url = uri("https://maven.waltid.dev/releases") }
}

dependencies {
    implementation("id.walt.holderpolicies:waltid-holder-policies:<version>")
}
```

### Basic Example

```kotlin
import id.walt.credentials.CredentialParser
import id.walt.holderpolicies.*
import id.walt.holderpolicies.checks.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.runBlocking

fun main() = runBlocking {
    val credentialString = "eyJraWQiOiJkaWQ6a2V5Ono2TWtqb1JocTFqU05KZExpcnVTWHJGRnhhZ3FyenRaYVhIcUhHVVRLSmJjTnl3cC..."
    
    // Parse credential
    val credential = CredentialParser.parseOnly(credentialString)
    val credentials = flowOf(credential)
    
    // Define policies
    val policies = flowOf(
        HolderPolicy(
            priority = 1,
            description = "Block credentials from untrusted issuer",
            direction = HolderPolicy.HolderPolicyDirection.RECEIVE,
            check = BasicHolderPolicyCheck(
                issuer = "did:example:untrusted-issuer"
            ),
            action = HolderPolicy.HolderPolicyAction.BLOCK
        ),
        HolderPolicy(
            priority = 99,
            description = "Default allow",
            direction = HolderPolicy.HolderPolicyDirection.RECEIVE,
            apply = ApplyAllHolderPolicyCheck(),
            action = HolderPolicy.HolderPolicyAction.ALLOW
        )
    )
    
    // Evaluate policies
    val result = HolderPolicyEngine.evaluate(policies, credentials)
    
    when (result) {
        HolderPolicy.HolderPolicyAction.ALLOW -> println("Credential can be received")
        HolderPolicy.HolderPolicyAction.BLOCK -> println("Credential is blocked")
        null -> println("No matching policy")
    }
}
```

### Using Basic Policy Checks

```kotlin
// Block credentials from specific issuer
val blockIssuerPolicy = HolderPolicy(
    priority = 1,
    description = "Block untrusted issuer",
    direction = HolderPolicy.HolderPolicyDirection.RECEIVE,
    check = BasicHolderPolicyCheck(
        issuer = "did:example:untrusted-issuer"
    ),
    action = HolderPolicy.HolderPolicyAction.BLOCK
)

// Block credentials with specific format
val blockFormatPolicy = HolderPolicy(
    priority = 1,
    description = "Block mdoc credentials",
    direction = HolderPolicy.HolderPolicyDirection.RECEIVE,
    check = BasicHolderPolicyCheck(
        format = "mso_mdoc"
    ),
    action = HolderPolicy.HolderPolicyAction.BLOCK
)

// Block credentials containing specific claims
val blockClaimPolicy = HolderPolicy(
    priority = 1,
    description = "Block credentials with SSN",
    direction = HolderPolicy.HolderPolicyDirection.PRESENT,
    check = BasicHolderPolicyCheck(
        claimsPresent = listOf("$.credentialSubject.ssn")
    ),
    action = HolderPolicy.HolderPolicyAction.BLOCK
)

// Block credentials with specific claim values
val blockClaimValuePolicy = HolderPolicy(
    priority = 1,
    description = "Block credentials with high-value data",
    direction = HolderPolicy.HolderPolicyDirection.PRESENT,
    check = BasicHolderPolicyCheck(
        claimsValues = mapOf(
            "$.credentialSubject.riskScore" to JsonPrimitive(100)
        )
    ),
    action = HolderPolicy.HolderPolicyAction.BLOCK
)
```

### Using JSON Schema Checks

```kotlin
import kotlinx.serialization.json.*

val schemaPolicy = HolderPolicy(
    priority = 1,
    description = "Block credentials containing sensitive PII",
    direction = HolderPolicy.HolderPolicyDirection.PRESENT,
    check = JsonSchemaHolderPolicyCheck(
        schema = buildJsonObject {
            put("type", "object")
            put("properties", buildJsonObject {
                putJsonObject("credentialSubject") {
                    put("type", "object")
                    put("properties", buildJsonObject {
                        putJsonObject("ssn") {
                            put("type", "string")
                        }
                        putJsonObject("creditCard") {
                            put("type", "string")
                        }
                    })
                    put("required", buildJsonArray { add("ssn") })
                }
            })
            put("required", buildJsonArray { add("credentialSubject") })
        }
    ),
    action = HolderPolicy.HolderPolicyAction.BLOCK
)
```

### Using DCQL Checks

```kotlin
import id.walt.dcql.models.*
import kotlinx.serialization.json.*

val dcqlPolicy = HolderPolicy(
    priority = 1,
    description = "Block credentials matching sensitive DCQL query",
    direction = HolderPolicy.HolderPolicyDirection.PRESENT,
    check = DcqlHolderPolicyCheck(
        dcqlQuery = DcqlQuery(
            credentials = listOf(
                CredentialQuery(
                    id = "identity",
                    format = CredentialFormat.DC_SD_JWT,
                    claims = listOf(
                        ClaimsQuery(
                            path = listOf("address", "street_address")
                        ),
                        ClaimsQuery(
                            path = listOf("phone_number")
                        )
                    )
                )
            )
        )
    ),
    action = HolderPolicy.HolderPolicyAction.BLOCK
)
```

### Implementing Default Deny

```kotlin
val defaultDenyPolicies = flowOf(
    // Specific allow policies first (low priority)
    HolderPolicy(
        priority = 1,
        description = "Allow trusted issuer",
        direction = HolderPolicy.HolderPolicyDirection.RECEIVE,
        check = BasicHolderPolicyCheck(
            issuer = "did:example:trusted-issuer"
        ),
        action = HolderPolicy.HolderPolicyAction.ALLOW
    ),
    
    // Default deny (high priority, applies to all)
    HolderPolicy(
        priority = 99,
        description = "Default deny all",
        direction = HolderPolicy.HolderPolicyDirection.RECEIVE,
        apply = ApplyAllHolderPolicyCheck(),
        action = HolderPolicy.HolderPolicyAction.BLOCK
    )
)
```

### Implementing Default Allow

```kotlin
val defaultAllowPolicies = flowOf(
    // Specific block policies first (low priority)
    HolderPolicy(
        priority = 1,
        description = "Block blacklisted issuer",
        direction = HolderPolicy.HolderPolicyDirection.RECEIVE,
        check = BasicHolderPolicyCheck(
            issuer = "did:example:blacklisted-issuer"
        ),
        action = HolderPolicy.HolderPolicyAction.BLOCK
    ),
    
    // Default allow (high priority, applies to all)
    HolderPolicy(
        priority = 99,
        description = "Default allow all",
        direction = HolderPolicy.HolderPolicyDirection.RECEIVE,
        apply = ApplyAllHolderPolicyCheck(),
        action = HolderPolicy.HolderPolicyAction.ALLOW
    )
)
```

### Using Apply Filters

The `apply` field filters which policies are considered:

```kotlin
val policies = flowOf(
    // This policy only applies to credentials from specific issuer
    HolderPolicy(
        priority = 1,
        description = "Block specific claim from issuer",
        direction = HolderPolicy.HolderPolicyDirection.PRESENT,
        apply = BasicHolderPolicyCheck(
            issuer = "did:example:issuer1"
        ),
        check = BasicHolderPolicyCheck(
            claimsPresent = listOf("$.credentialSubject.ssn")
        ),
        action = HolderPolicy.HolderPolicyAction.BLOCK
    ),
    
    // This policy applies to all credentials (apply is null)
    HolderPolicy(
        priority = 2,
        description = "Block SSN from any issuer",
        direction = HolderPolicy.HolderPolicyDirection.PRESENT,
        check = BasicHolderPolicyCheck(
            claimsPresent = listOf("$.credentialSubject.ssn")
        ),
        action = HolderPolicy.HolderPolicyAction.BLOCK
    )
)
```

### Policy Direction (Receive vs. Present)

```kotlin
// Policy for receiving credentials
val receivePolicy = HolderPolicy(
    priority = 1,
    description = "Block receiving from untrusted issuer",
    direction = HolderPolicy.HolderPolicyDirection.RECEIVE,
    check = BasicHolderPolicyCheck(
        issuer = "did:example:untrusted"
    ),
    action = HolderPolicy.HolderPolicyAction.BLOCK
)

// Policy for presenting credentials
val presentPolicy = HolderPolicy(
    priority = 1,
    description = "Block presenting sensitive data",
    direction = HolderPolicy.HolderPolicyDirection.PRESENT,
    check = BasicHolderPolicyCheck(
        claimsPresent = listOf("$.credentialSubject.ssn")
    ),
    action = HolderPolicy.HolderPolicyAction.BLOCK
)

// Policy that applies to both (direction is null)
val bothPolicy = HolderPolicy(
    priority = 1,
    description = "Block credentials with specific format",
    direction = null, // Applies to both receive and present
    check = BasicHolderPolicyCheck(
        format = "mso_mdoc"
    ),
    action = HolderPolicy.HolderPolicyAction.BLOCK
)
```

### Complex Policy Example

```kotlin
val complexPolicies = flowOf(
    // Priority 1: Block high-risk credentials
    HolderPolicy(
        priority = 1,
        description = "Block credentials with high risk score",
        direction = HolderPolicy.HolderPolicyDirection.PRESENT,
        check = BasicHolderPolicyCheck(
            claimsValues = mapOf(
                "$.credentialSubject.riskScore" to JsonPrimitive(100)
            )
        ),
        action = HolderPolicy.HolderPolicyAction.BLOCK
    ),
    
    // Priority 2: Allow trusted issuer even with high risk
    HolderPolicy(
        priority = 2,
        description = "Allow trusted issuer regardless of risk",
        direction = HolderPolicy.HolderPolicyDirection.PRESENT,
        apply = BasicHolderPolicyCheck(
            issuer = "did:example:trusted-issuer"
        ),
        action = HolderPolicy.HolderPolicyAction.ALLOW
    ),
    
    // Priority 10: Block credentials matching sensitive schema
    HolderPolicy(
        priority = 10,
        description = "Block credentials with PII",
        direction = HolderPolicy.HolderPolicyDirection.PRESENT,
        check = JsonSchemaHolderPolicyCheck(
            schema = /* sensitive PII schema */
        ),
        action = HolderPolicy.HolderPolicyAction.BLOCK
    ),
    
    // Priority 99: Default allow
    HolderPolicy(
        priority = 99,
        description = "Default allow",
        direction = HolderPolicy.HolderPolicyDirection.PRESENT,
        apply = ApplyAllHolderPolicyCheck(),
        action = HolderPolicy.HolderPolicyAction.ALLOW
    )
)
```

## JavaScript Usage

### Installation

Install the library via npm:

```bash
npm install waltid-holder-policies
```

### Basic Example

```javascript
import { HolderPolicyEngine, HolderPolicy, HolderPolicyAction } from 'waltid-holder-policies';
import { BasicHolderPolicyCheck, ApplyAllHolderPolicyCheck } from 'waltid-holder-policies/checks';
import { CredentialParser } from 'waltid-digital-credentials';
import { flowOf } from 'kotlinx-coroutines-core';

async function evaluatePolicies() {
    const credentialString = "eyJraWQiOiJkaWQ6a2V5Ono2TWtqb1JocTFqU05KZExpcnVTWHJGRnhhZ3FyenRaYVhIcUhHVVRLSmJjTnl3cC...";
    
    // Parse credential
    const credential = await CredentialParser.parseOnly(credentialString);
    const credentials = flowOf(credential);
    
    // Define policies
    const policies = flowOf(
        new HolderPolicy({
            priority: 1,
            description: "Block untrusted issuer",
            direction: HolderPolicy.HolderPolicyDirection.RECEIVE,
            check: new BasicHolderPolicyCheck({
                issuer: "did:example:untrusted"
            }),
            action: HolderPolicyAction.BLOCK
        }),
        new HolderPolicy({
            priority: 99,
            description: "Default allow",
            direction: HolderPolicy.HolderPolicyDirection.RECEIVE,
            apply: new ApplyAllHolderPolicyCheck(),
            action: HolderPolicyAction.ALLOW
        })
    );
    
    // Evaluate
    const result = await HolderPolicyEngine.evaluate(policies, credentials);
    
    if (result === HolderPolicyAction.ALLOW) {
        console.log("Credential allowed");
    } else if (result === HolderPolicyAction.BLOCK) {
        console.log("Credential blocked");
    } else {
        console.log("No matching policy");
    }
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
                implementation("id.walt.holderpolicies:waltid-holder-policies:<version>")
            }
        }
    }
}
```

### Basic Example

```swift
import waltid_holder_policies
import kotlinx_coroutines_core

func evaluatePolicies() async throws {
    let credentialString = "eyJraWQiOiJkaWQ6a2V5Ono2TWtqb1JocTFqU05KZExpcnVTWHJGRnhhZ3FyenRaYVhIcUhHVVRLSmJjTnl3cC..."
    
    // Parse credential
    let credential = try await CredentialParser.Companion.shared.parseOnly(credentialString: credentialString)
    let credentials = FlowBuilderKt.flowOf(credential)
    
    // Define policies
    let policies = FlowBuilderKt.flowOf(
        HolderPolicy(
            priority: 1,
            description: "Block untrusted issuer",
            direction: HolderPolicyDirection.receive,
            check: BasicHolderPolicyCheck(issuer: "did:example:untrusted"),
            action: HolderPolicyAction.block
        ),
        HolderPolicy(
            priority: 99,
            description: "Default allow",
            direction: HolderPolicyDirection.receive,
            apply: ApplyAllHolderPolicyCheck(),
            action: HolderPolicyAction.allow
        )
    )
    
    // Evaluate
    let result = try await HolderPolicyEngine.Companion.shared.evaluate(
        policies: policies,
        credentials: credentials
    )
    
    if let action = result {
        switch action {
        case .allow:
            print("Credential allowed")
        case .block:
            print("Credential blocked")
        default:
            break
        }
    } else {
        print("No matching policy")
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

