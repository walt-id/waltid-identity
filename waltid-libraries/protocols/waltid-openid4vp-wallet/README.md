<div align="center">
<h1>OpenID4VP Wallet Implementation</h1>
 <span>by </span><a href="https://walt.id">walt.id</a>
 <p>Complete Wallet/Holder implementation for OpenID4VP 1.0</p>

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

A complete implementation of the **Holder/Wallet** functionality as defined in **OpenID for Verifiable Presentations (OpenID4VP) 1.0**. This library enables your application to act as a digital wallet, allowing users to present their digital credentials to verifiers.

Learn more about OpenID4VP [here](https://docs.walt.id/concepts/data-exchange-protocols/openid4vp).

**This library handles:**
- Parsing and processing authorization requests from Verifiers
- Matching credentials against DCQL queries
- Generating verifiable presentations in multiple formats
- Delivering responses via various response modes (fragment, query, direct_post)
- Supporting selective disclosure for privacy-preserving presentations

## Main Purpose

This library enables you to build Wallet applications that receive authorization requests from Verifiers, select matching credentials, and generate verifiable presentations.

**Use this library when:**
- Building a mobile or web wallet application
- Implementing OpenID4VP 1.0 wallet functionality
- Creating credential presentation flows for user authentication or access control
- Building applications that store and present digital credentials

## Key Concepts

### Authorization Request Processing

The library receives authorization requests from Verifiers that specify:
- **DCQL query** - What credentials are needed
- **Response mode** - How to return the presentation
- **Security parameters** - Nonce, state, client ID

### Credential Selection

You provide a function (`selectCredentialsForQuery`) that:
- Takes a DCQL query
- Searches your credential storage
- Returns matching credentials using the DCQL matcher

The library uses [waltid-dcql](../../credentials/waltid-dcql/README.md) for matching.

### Presentation Generation

The library generates format-specific presentations:
- **SD-JWT VC** - Combines original SD-JWT with disclosures and Key Binding JWT
- **W3C VC** - Wraps credential JWT in a Verifiable Presentation JWT
- **mdoc** - Builds SessionTranscript and DeviceAuth structures

### Response Modes

The library supports multiple ways to return presentations:
- **`fragment`** - URL fragment redirect (same-device)
- **`query`** - URL query string redirect
- **`form_post`** - Self-submitting HTML form
- **`direct_post`** - Direct HTTP POST to Verifier

## Assumptions and Dependencies

### Multiplatform Support

Works on JVM (Kotlin/Java), JavaScript, and iOS platforms (iOS requires `enableIosBuild=true` Gradle property).

### Dependencies

- **waltid-openid4vp** - Core OpenID4VP models
- **waltid-dcql** - DCQL query matching engine
- **waltid-digital-credentials** - Credential format implementations
- **waltid-holder-policies** - Optional holder-side access control policies
- **waltid-verification-policies2** - For policy integration

### Your Responsibilities

You must provide:
- **Credential storage** - Database or storage for user credentials
- **Credential selection logic** - Function to query your storage
- **User consent UI** - Interface for user to approve credential sharing
- **Holder key management** - Private key for signing presentations

## How to Use This Library

### Integration Workflow

Here's how to integrate the wallet library into your application.

#### Step 1: Set Up Holder Identity

You need a holder key and DID for signing presentations.

```kotlin
import id.walt.crypto.keys.Key
import id.walt.crypto.KeyManager

// Load or generate holder's key
val holderKey: Key = KeyManager.loadKey("holder_key_id") // or generate new key
val holderDid: String = "did:key:z6M..." // Holder's DID
```

#### Step 2: Implement Credential Selection

Implement the credential selection function that queries your storage.

```kotlin
import id.walt.dcql.DcqlMatcher
import id.walt.dcql.models.DcqlQuery
import id.walt.dcql.models.RawDcqlCredential

val selectCredentialsForQuery: suspend (DcqlQuery) -> Map<String, List<DcqlMatcher.DcqlMatchResult>> =
    { query ->
        // 1. Fetch credentials from your storage
        val storedCredentials = fetchCredentialsFromStorage() // Your storage query
        
        // 2. Convert to RawDcqlCredential format
        val rawCredentials = storedCredentials.map { credential ->
            RawDcqlCredential(
                id = credential.id,
                format = credential.format,
                data = credential.data,
                originalCredential = credential,
                disclosures = credential.disclosures
            )
        }
        
        // 3. Use DCQL matcher to find matches
        val matches = DcqlMatcher.match(query, rawCredentials)
        
        // 4. Return matches grouped by query ID
        matches
    }
```

#### Step 3: Process Authorization Request

When your wallet receives an authorization request (e.g., from a QR code or deep link), process it:

```kotlin
import id.waltid.openid4vp.wallet.WalletPresentFunctionality2
import io.ktor.http.*

// Receive presentation request URL (e.g., from QR code or deep link)
val presentationRequestUrl = Url("openid4vp://?request_uri=https://verifier.com/request/123")

// Optional: Holder policies for access control
val holderPolicies: Flow<HolderPolicy>? = null // or load from your policy storage

// Process the presentation request
val presentationResult: Result<JsonElement> = WalletPresentFunctionality2.walletPresentHandling(
    holderKey = holderKey,
    holderDid = holderDid,
    presentationRequestUrl = presentationRequestUrl,
    selectCredentialsForQuery = selectCredentialsForQuery,
    holderPoliciesToRun = holderPolicies,
    runPolicies = true // Set to true to enforce policies
)
```

#### Step 4: Handle the Result

Process the result and handle the response based on the response mode.

```kotlin
presentationResult.fold(
    onSuccess = { responseJson ->
        // Check response mode
        when {
            responseJson.jsonObject.containsKey("get_url") -> {
                // Fragment or query mode - redirect user
                val redirectUrl = responseJson.jsonObject["get_url"]!!.jsonPrimitive.content
                // Redirect user's browser to this URL
                navigateTo(redirectUrl)
            }
            responseJson.jsonObject.containsKey("transmission_success") -> {
                // Direct post mode - already sent
                val success = responseJson.jsonObject["transmission_success"]!!.jsonPrimitive.boolean
                if (success) {
                    println("Presentation sent successfully")
                }
            }
        }
    },
    onFailure = { error ->
        println("Presentation failed: ${error.message}")
        // Handle error (e.g., show error message to user)
    }
)
```

### Complete Example

Here's a complete example integrating the wallet:

```kotlin
import id.waltid.openid4vp.wallet.WalletPresentFunctionality2
import id.walt.dcql.DcqlMatcher
import id.walt.dcql.models.*
import id.walt.crypto.keys.Key
import io.ktor.http.*

class WalletService(
    private val credentialStorage: CredentialStorage,
    private val holderKey: Key,
    private val holderDid: String
) {
    suspend fun handlePresentationRequest(
        requestUrl: String,
        userConsent: Boolean
    ): Result<JsonElement> {
        if (!userConsent) {
            return Result.failure(Exception("User declined consent"))
        }
        
        val presentationRequestUrl = Url(requestUrl)
        
        // Credential selection function
        val selectCredentials: suspend (DcqlQuery) -> Map<String, List<DcqlMatcher.DcqlMatchResult>> =
            { query ->
                val credentials = credentialStorage.findAll()
                val rawCredentials = credentials.map { cred ->
                    RawDcqlCredential(
                        id = cred.id,
                        format = cred.format,
                        data = cred.data,
                        originalCredential = cred.toDigitalCredential(),
                        disclosures = cred.disclosures
                    )
                }
                DcqlMatcher.match(query, rawCredentials)
            }
        
        // Process presentation
        return WalletPresentFunctionality2.walletPresentHandling(
            holderKey = holderKey,
            holderDid = holderDid,
            presentationRequestUrl = presentationRequestUrl,
            selectCredentialsForQuery = selectCredentials,
            holderPoliciesToRun = null, // Optional: add holder policies
            runPolicies = null
        )
    }
}
```

### Supported Credential Formats

| Format | Identifier | Status | Notes |
|--------|-----------|--------|-------|
| SD-JWT VC | `dc+sd-jwt` | ✅ Supported | Includes selective disclosure and Key Binding JWT |
| W3C VC (JWT) | `jwt_vc_json` | ✅ Supported | Wraps credential in Verifiable Presentation JWT |
| mdoc | `mso_mdoc` | ✅ Supported | Builds SessionTranscript and DeviceAuth |
| LDP VC | `ldp_vc` | ❌ Not Supported | Placeholder for future implementation |

### Holder Policies (Optional)

You can integrate holder policies to control credential presentation:

```kotlin
import id.walt.holder.policies.*

val holderPolicies = flowOf(
    HolderPolicy(
        priority = 1,
        description = "Block untrusted verifier",
        direction = HolderPolicyDirection.PRESENT,
        check = BasicHolderPolicyCheck(issuer = "did:example:untrusted"),
        action = HolderPolicyAction.BLOCK
    ),
    HolderPolicy(
        priority = 99,
        description = "Default allow",
        direction = HolderPolicyDirection.PRESENT,
        apply = ApplyAllHolderPolicyCheck(),
        action = HolderPolicyAction.ALLOW
    )
)
```

See [waltid-holder-policies](../../credentials/waltid-holder-policies/README.md) for more details.

### Key Components

| Component | Description | Usage |
|-----------|-------------|-------|
| `WalletPresentFunctionality2` | Main entry point for processing requests | `walletPresentHandling()` |
| `SdJwtVcPresenter` | Creates SD-JWT VC presentations | Used internally |
| `W3CPresenter` | Creates W3C VC presentations | Used internally |
| `MdocPresenter` | Creates mdoc presentations | Used internally |

## Related Libraries

- **[waltid-openid4vp](../waltid-openid4vp/README.md)** - Core OpenID4VP models and types
- **[waltid-openid4vp-clientidprefix](../waltid-openid4vp-clientidprefix/README.md)** - Client ID prefix parsing for Verifier authentication
- **[waltid-dcql](../../credentials/waltid-dcql/README.md)** - Digital Credentials Query Language engine for credential matching
- **[waltid-digital-credentials](../../credentials/waltid-digital-credentials/README.md)** - Credential format implementations and parsing
- **[waltid-holder-policies](../../credentials/waltid-holder-policies/README.md)** - Holder-side access control policies

## Join the community

* Connect and get the latest updates: [Discord](https://discord.gg/AW8AgqJthZ) | [Newsletter](https://walt.id/newsletter) | [YouTube](https://www.youtube.com/channel/UCXfOzrv3PIvmur_CmwwmdLA) | [LinkedIn](https://www.linkedin.com/company/walt-id/)
* Get help, request features and report bugs: [GitHub Issues](https://github.com/walt-id/waltid-identity/issues)
* Find more indepth documentation on our [docs site](https://docs.walt.id)

## License

Licensed under the [Apache License, Version 2.0](https://github.com/walt-id/waltid-identity/blob/main/LICENSE)

<div align="center">
<img src="../../../assets/walt-banner.png" alt="walt.id banner" />
</div>
