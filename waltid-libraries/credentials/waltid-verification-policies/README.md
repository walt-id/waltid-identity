<div align="center">
    <h1>Kotlin Multiplatform Verification Policies library</h1>
    <span>by </span><a href="https://walt.id">walt.id</a>
    <p>Composable verification policies for digital credentials</p>
    <a href="https://walt.id/community">
    <img src="https://img.shields.io/badge/Join-The Community-blue.svg?style=flat" alt="Join community!" />
    </a>
    <a href="https://www.linkedin.com/company/walt-id/">
    <img src="https://img.shields.io/badge/-LinkedIn-0072b1?style=flat&logo=linkedin" alt="Follow walt_id" />
    </a>
    <h2>Status</h2>
    <p align="center">
        <img src="https://img.shields.io/badge/🟠%20Planned%20Deprecation-orange?style=for-the-badge&logo=clock" alt="Status: Planned Deprecation" />
        <br/>
        <em>This project is still supported by the development team at walt.id, but is planned for deprecation sometime in Q2 2026.<br />We encourage users to migrate to using alternative libraries listed below.</em>
    </p>
</div>

## What This Library Contains

This library provides a comprehensive set of composable verification policies for digital credentials. The library enables you to:

- **Verify credentials** using a variety of built-in policies (signature, expiration, issuer, schema, status, revocation)
- **Verify presentations** with presentation-level policies (holder binding, credential count limits, presentation definition compliance)
- **Compose policies** by combining multiple policies for complex verification scenarios
- **Support multiple formats** including JWT VC, SD-JWT VC, LDP VC, and mdoc credentials
- **Extend with custom policies** using the `DynamicPolicy` mechanism or by implementing new policy classes

**NOTE:**
The main difference between this library and the newer [`waltid-verification-policies2`](../waltid-verification-policies2) library is the focus on multiplatform, and the usage of the new `DigitalCredential` interface from `waltid-digital-credentials`. This allows the policies to be more stable and easier to use across different credential formats. 

We expect to deprecate this library, alongside the verifier api using it (with draft versions of the OpenID4VP protocol) in Q2 2026 as we migrate to the new verifier api (supporting the OpenID4VP 1.0 major release) which only uses the newer `waltid-verification-policies2` library.

## Main Purpose

It provides a flexible, composable system for verifying credentials and presentations where you can:

- Build verifier services that check credentials against multiple criteria
- Define verification requirements using policy combinations
- Support various credential formats (W3C, SD-JWT, mdoc) in a unified way
- Handle both credential-level and presentation-level verification
- Integrate external verification services through webhook policies

The library is particularly useful when building:
- Services that need to verify credentials from multiple issuers
- Applications requiring complex verification logic (status checks, revocation, schema validation)
- Systems that need to verify both individual credentials and complete presentations

## Key Concepts

### Verification Policies

A **Verification Policy** is a reusable unit of verification logic that checks a specific aspect of a credential or presentation. Each policy:
- Has a unique name and description
- Specifies which credential formats it supports
- Takes optional arguments for configuration
- Returns a `Result` indicating success or failure with details

### Policy Types

Policies are categorized into three types:

- **JwtVerificationPolicy**: Policies that work directly with JWT strings (e.g., signature verification)
- **CredentialDataValidatorPolicy**: Policies that validate credential data (e.g., schema validation, expiration checks)
- **CredentialWrapperValidatorPolicy**: Policies that work with the full credential wrapper (e.g., issuer checks, status validation)

### Policy Requests

A **PolicyRequest** combines a policy with optional arguments:
- Can reference a policy by name (for policies without arguments)
- Can include a full policy configuration with arguments
- Supports JSON serialization for configuration-driven verification

### Policy Manager

The **PolicyManager** registers and manages all available policies:
- Automatically registers built-in policies
- Allows custom policy registration
- Provides policy lookup by name
- Lists all available policies with descriptions

### Verifier

The **Verifier** class orchestrates policy execution:
- Runs multiple policies in parallel on credentials
- Handles policy execution for both credentials and presentations
- Collects results from all policies
- Provides structured verification responses

### Presentation Verification

Presentation verification involves:
- **VP Policies**: Policies applied to the presentation itself (e.g., holder binding, credential count)
- **Global VC Policies**: Policies applied to all credentials in the presentation
- **Specific VC Policies**: Policies applied to specific credentials by ID or format

## Assumptions and Dependencies

This library makes several important assumptions:

- **Credential Format Support**: Supports JWT VC, SD-JWT VC, LDP VC, and mdoc formats
- **Policy Registration**: Policies must be registered with PolicyManager before use
- **Async Operations**: Policy verification uses Kotlin coroutines and suspend functions
- **Status/Revocation**: Status and revocation policies require JVM-specific implementations

## How to Use This Library

### Basic Workflow

1. **Register Policies**: Use `PolicyManager.registerPolicies()` to register custom policies (built-in policies are auto-registered)
2. **Create Policy Requests**: Define `PolicyRequest` objects specifying which policies to apply and their arguments
3. **Verify Credentials**: Use `Verifier.verifyCredential()` to apply policies to a credential
4. **Verify Presentations**: Use `Verifier.verifyPresentation()` to verify presentations with VP and VC policies
5. **Process Results**: Handle `PolicyResult` objects to determine verification outcomes

### Key Source Files

For detailed implementation examples and understanding the library internals, refer to:

- **`PolicyManager.kt`**: Policy registration and management
- **`VerificationPolicy.kt`**: Base class for all verification policies
- **`Verifier.kt`**: Main verification orchestration logic
- **`models/PolicyRequest.kt`**: Policy request structure and JSON parsing
- **`models/PolicyResult.kt`**: Policy execution results
- **`policies/`**: Directory containing all built-in policy implementations
- **Test files**: Located in `src/commonTest` and `src/jvmTest`, these provide comprehensive examples

## JVM/Kotlin Usage

### Installation

Add the library as a dependency in your `build.gradle.kts`:

```kotlin
repositories {
    maven { url = uri("https://maven.waltid.dev/releases") }
}

dependencies {
    implementation("id.walt.policies:waltid-verification-policies:<version>")
}
```

### Basic Example

```kotlin
import id.walt.policies.*
import id.walt.policies.models.*
import kotlinx.coroutines.runBlocking

fun main() = runBlocking {
    val jwtCredential = "eyJraWQiOiJkaWQ6a2V5Ono2TWtqb1JocTFqU05KZExpcnVTWHJGRnhhZ3FyenRaYVhIcUhHVVRLSmJjTnl3cC..."
    
    // Define policies to apply
    val policies = listOf(
        PolicyRequest(PolicyManager.getPolicy("jwt-signature"), null),
        PolicyRequest(PolicyManager.getPolicy("expired"), null),
        PolicyRequest(
            PolicyManager.getPolicy("allowed-issuer"),
            buildJsonObject {
                put("args", buildJsonArray {
                    add("did:example:issuer1")
                    add("did:example:issuer2")
                })
            }
        )
    )
    
    // Verify the credential
    val verifier = Verifier()
    val results = verifier.verifyCredential(jwtCredential, policies)
    
    // Check results
    results.forEach { result ->
        if (result.isSuccess) {
            println("Policy ${result.policyRequest.policy.name} passed")
        } else {
            println("Policy ${result.policyRequest.policy.name} failed: ${result.exceptionOrNull()?.message}")
        }
    }
}
```

### Using Policy Requests from JSON

```kotlin
import id.walt.policies.models.*
import kotlinx.serialization.json.*

fun createPoliciesFromJson(): List<PolicyRequest> {
    val json = """
    [
        "jwt-signature",
        "expired",
        {
            "policy": "allowed-issuer",
            "args": ["did:example:issuer1", "did:example:issuer2"]
        },
        {
            "policy": "schema",
            "args": {
                "schema": {
                    "type": "object",
                    "required": ["credentialSubject"]
                }
            }
        }
    ]
    """
    
    val jsonArray = Json.parseToJsonElement(json).jsonArray
    return jsonArray.parsePolicyRequests()
}
```

### Verifying Presentations

```kotlin
import id.walt.policies.*
import id.walt.w3c.utils.VCFormat
import kotlinx.coroutines.runBlocking

fun main() = runBlocking {
    val vpToken = "eyJraWQiOiJkaWQ6a2V5Ono2TWtqb1JocTFqU05KZExpcnVTWHJGRnhhZ3FyenRaYVhIcUhHVVRLSmJjTnl3cC..."
    
    // VP-level policies
    val vpPolicies = listOf(
        PolicyRequest(PolicyManager.getPolicy("holder-binding"), null),
        PolicyRequest(PolicyManager.getPolicy("minimum-credentials"), buildJsonObject {
            put("args", buildJsonObject { put("minimum", 1) })
        })
    )
    
    // Global VC policies (applied to all credentials)
    val globalVcPolicies = listOf(
        PolicyRequest(PolicyManager.getPolicy("jwt-signature"), null),
        PolicyRequest(PolicyManager.getPolicy("expired"), null)
    )
    
    // Specific VC policies (applied to specific credentials)
    val specificVcPolicies = mapOf(
        "identity_credential" to listOf(
            PolicyRequest(
                PolicyManager.getPolicy("allowed-issuer"),
                buildJsonObject {
                    put("args", buildJsonArray { add("did:example:trusted-issuer") })
                }
            )
        )
    )
    
    val verifier = Verifier()
    val response = verifier.verifyPresentation(
        format = VCFormat.jwt_vc_json,
        vpToken = vpToken,
        vpPolicies = vpPolicies,
        globalVcPolicies = globalVcPolicies,
        specificCredentialPolicies = specificVcPolicies
    )
    
    println("Overall success: ${response.overallSuccess}")
    println("VP results: ${response.vpResults}")
    println("VC results: ${response.vcResults}")
}
```

### Available Built-in Policies

The library includes the following policies:

- **`jwt-signature`**: Verifies JWT signatures
- **`sdjwt-vc-signature`**: Verifies SD-JWT VC signatures
- **`expired`**: Checks credential expiration dates
- **`not-before`**: Checks credential not-before dates
- **`allowed-issuer`**: Validates issuer against an allowlist
- **`schema`**: Validates credential data against JSON Schema
- **`credential-status`**: Checks credential status (active, suspended, revoked)
- **`revoked`**: Checks credential revocation status
- **`webhook`**: Calls external HTTP endpoint for verification
- **`holder-binding`**: Verifies presentation holder binding
- **`minimum-credentials`**: Enforces minimum credential count
- **`maximum-credentials`**: Enforces maximum credential count
- **`presentation-definition`**: Validates against DIF Presentation Definition
- **`vp-required-credentials`**: Validates required credentials in presentation
- **`dynamic`**: Executes custom verification logic

### Using Status Policy

The status policy checks credential status (JVM-only):

```kotlin
val statusPolicy = PolicyRequest(
    PolicyManager.getPolicy("credential-status"),
    null
)

// Status policy supports various status list formats:
// - StatusList2021
// - RevocationList2020
// - BitstringStatusList
// - TokenStatusList
// - IETF Status List
```

### Using Dynamic Policy

Create custom verification logic with dynamic policies:

```kotlin
val dynamicPolicy = PolicyRequest(
    PolicyManager.getPolicy("dynamic"),
    buildJsonObject {
        put("args", buildJsonObject {
            put("code", """
                fun verify(data: JsonObject, args: Any?, context: Map<String, Any>): Result<Any> {
                    // Custom verification logic
                    val value = data["credentialSubject"]?.jsonObject?.get("age")?.jsonPrimitive?.content?.toInt()
                    return if (value != null && value >= 18) {
                        Result.success("Age check passed")
                    } else {
                        Result.failure(Exception("Age must be 18 or older"))
                    }
                }
            """)
        })
    }
)
```

### Registering Custom Policies

```kotlin
import id.walt.policies.*

class CustomPolicy : CredentialWrapperValidatorPolicy() {
    override val name = "custom-policy"
    override val description = "My custom verification policy"
    override val supportedVCFormats = setOf(VCFormat.jwt_vc_json)
    
    override suspend fun verify(
        data: JsonObject,
        args: Any?,
        context: Map<String, Any>
    ): Result<Any> {
        // Custom verification logic
        return Result.success("Custom policy passed")
    }
}

// Register the policy
PolicyManager.registerPolicies(CustomPolicy())
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

