<div align="center">
 <h1>Kotlin Multiplatform Verification Policies library (v2)</h1>
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
    <img src="https://img.shields.io/badge/🟢%20Actively%20Maintained-success?style=for-the-badge&logo=check-circle" alt="Status: Actively Maintained" />
    <br/>
    <em>This project is being actively maintained by the development team at walt.id.<br />Regular updates, bug fixes, and new features are being added.</em>
  </p>
</div>

Kotlin Multiplatform library that provides reusable verification policies for Digital Credentials (W3C VC, IETF SD-JWT WC, mDL/mdoc ISO 18013-5). It builds on top of the [walt.id generic Digital Credential interface](../waltid-digital-credentials) and powers high-level services such as the [walt.id Verifier APIs](../../services/waltid-verifier-api2).

The main difference between this library and the legacy [`waltid-verification-policies`](../waltid-verification-policies) library is the focus on multiplatform, and the usage of the new `DigitalCredential` interface from `waltid-digital-credentials`. This allows the policies to be more stable and easier to use across different credential formats.

## Table of contents
- [Features](#features)
- [Supported platforms](#supported-platforms)
- [Installation](#installation)
- [Usage](#usage)
- [Available policies](#available-policies)
- [Development](#development)
- [Contributing](#contributing)
- [License](#license)

## Features
- Composable verification policies with a consistent `Result<JsonElement>` contract.
- Out-of-the-box signature, lifecycle, issuer, schema, regex, webhook, and VICAL validation.
- Ready for server-side and client-side Kotlin/JS consumers with optional iOS targets.

## Supported platforms

This module targets JVM and JavaScript by default. iOS targets can be enabled by setting the Gradle property `enableIosBuild=true` (see the root build).

```
kotlin {
    jvm()
    js(IR) { nodejs() }
    // iosArm64() / iosSimulatorArm64() when enableIosBuild=true
}
```

## Installation

Make sure your project has access to the walt.id Maven repositories:

```kotlin
repositories {
    mavenCentral()
    maven("https://maven.waltid.dev/releases")
    maven("https://maven.waltid.dev/snapshots") // optional for snapshot versions
}
```

Add the dependency to your Gradle module (Kotlin DSL):

```kotlin
dependencies {
    implementation("id.walt.credentials:waltid-verification-policies2:<version>")
}
```

A TypeScript definition file is generated when the JS target is built, and npm artefacts can be published via `./gradlew :waltid-libraries:credentials:waltid-verification-policies2:npmPublish` when the `NPM_TOKEN` is configured.

## Usage

### Verifying a credential programmatically

```kotlin
import id.walt.credentials.CredentialParser
import id.walt.policies2.policies.CredentialSignaturePolicy

suspend fun verifySignature(rawCredential: String) {
    val credential = CredentialParser.parseOnly(rawCredential)
    val policy = CredentialSignaturePolicy()

    val result = policy.verify(credential)
    result.onFailure { error ->
        throw IllegalStateException("Signature validation failed", error)
    }
    println("Verified with issuer key: ${result.getOrThrow()}")
}
```

All policies share the same signature: they accept a `DigitalCredential` and return a `Result<JsonElement>` that contains a policy-specific payload on success or an exception on failure.

### Defining policies via JSON

`PolicyList` makes policies configurable at runtime. In JSON form you can mix simple string identifiers with fully configured policy objects. Only policies without mandatory arguments can be referenced by their id (currently `signature`, `expiration`, and `not-before`).

```json
{
  "vcPolicies": [
    "signature",
    "expiration",
    {
      "policy": "allowed-issuer",
      "allowed_issuer": [
        "https://issuer.demo.walt.id"
      ]
    },
    {
      "policy": "regex",
      "path": "$.credentialSubject.email",
      "regex": "^.+@example\\.com$"
    },
    {
      "policy": "vical",
      "vical": "<base64 encoded VICAL file>",
      "enableDocumentTypeValidation": false,
      "enableTrustedChainRoot": false,
      "enableSystemTrustAnchors": false,
      "enableRevocation": false
    }
  ],
  "specificVcPolicies": {
    "identity_credential": [
      {
        "policy": "schema",
        "schema": {
          "type": "object",
          "required": [
            "credentialSubject"
          ]
        }
      }
    ]
  }
}
```

In Kotlin you can deserialize the JSON into `PolicyList` or the higher-level `Verification2Session.DefinedVerificationPolicies` to drive policy execution.

### Collecting results

`PolicyResult` and `PolicyResults` capture the outcome of each policy and expose an `overallSuccess` helper that checks all VC policy groups. Persist the results to reconstruct verification decisions or to return structured responses from APIs.

### Validating DCQL claim paths

When working with DCQL presentations, you can re-check that revealed claims satisfy the original query:

## Available policies

| Policy id        | Class                                   | Description |
|-----------------|-----------------------------------------|-------------|
| `signature`     | `CredentialSignaturePolicy`             | Resolves the issuer key (DID, x5c, etc.) and verifies digital signatures. |
| `expiration`    | `ExpirationDatePolicy`                  | Rejects credentials that have passed their `exp` / `validUntil`. |
| `not-before`    | `NotBeforePolicy`                       | Prevents usage before the `nbf` / `validFrom` timestamp. |
| `allowed-issuer`| `AllowedIssuerPolicy`                   | Checks the issuer DID / identifier against an allowlist. |
| `schema`        | `JsonSchemaPolicy`                      | Validates arbitrary credential data against a JSON schema (powered by optimumcode/json-schema-validator). |
| `regex`         | `CredentialDataMatcherPolicy`           | Runs regex checks on JSON path values, optionally allowing nulls. |
| `webhook`       | `WebhookPolicy`                         | Calls out to an external HTTP endpoint and interprets the response. |
| `vical`         | `VicalPolicy`                           | Validates mdoc certificates against VICAL trust anchors. |

Additional policies (for example credential status) are available in the legacy [`waltid-verification-policies`](../waltid-verification-policies) module.

### VICAL policy tips

`VicalPolicy` validates mdoc credentials against a VICAL trust list:

- Pass the VICAL file as a Base64-encoded string.
- Optional flags allow you to enforce document type matching, trust anchor handling, and revocation logic.
- Only mdoc credentials signed with COSE and carrying an `x5c` chain are supported at the moment.

## Development

- Build the module: `./gradlew :waltid-libraries:credentials:waltid-verification-policies2:build`
- Run the multiplatform tests: `./gradlew :waltid-libraries:credentials:waltid-verification-policies2:check`
- JVM-only tests (including VICAL tests): `./gradlew :waltid-libraries:credentials:waltid-verification-policies2:jvmTest`

The tests rely on example credentials from `waltid-digital-credentials-examples`. Some JVM tests initialise minimal DID services; make sure the required dependencies are available when adding new test cases.

## Join the community

* Connect and get the latest updates: [Discord](https://discord.gg/AW8AgqJthZ) | [Newsletter](https://walt.id/newsletter) | [YouTube](https://www.youtube.com/channel/UCXfOzrv3PIvmur_CmwwmdLA) | [LinkedIn](https://www.linkedin.com/company/walt-id/)
* Get help, request features and report bugs: [GitHub Issues ](https://github.com/walt-id/waltid-identity/issues)
* Find more indepth documentation on our [docs site](https://docs.walt.id)


## License

Licensed under the [Apache License, Version 2.0](https://github.com/walt-id/waltid-identity/blob/main/LICENSE)

<div align="center">
<img src="../../../assets/walt-banner.png" alt="walt.id banner" />
</div>
