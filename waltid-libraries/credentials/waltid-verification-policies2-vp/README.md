<div align="center">
 <h1>Kotlin Multiplatform VP Verification Policies library (v2)</h1>
 <span>by </span><a href="https://walt.id">walt.id</a>
 <p>Composable verification policies for Verifiable Presentations</p>

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

Kotlin Multiplatform library that provides reusable verification policies for Verifiable Presentations (VP) across multiple formats (JWT VC JSON, SD-JWT, mdoc). It builds on top of the [walt.id generic Digital Credential interface](../waltid-digital-credentials) and powers high-level services such as the [walt.id Verifier APIs](../../services/waltid-verifier-api2).

This library focuses on presentation-level verification policies, complementing the credential-level policies in [`waltid-verification-policies2`](../waltid-verification-policies2).

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
- Composable VP verification policies organized by presentation format.
- Out-of-the-box signature, audience, nonce, and integrity validation for presentations.
- Format-specific policies for JWT VC JSON, SD-JWT, and mdoc presentations.
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
    implementation("id.walt.credentials:waltid-verification-policies2-vp:<version>")
}
```

A TypeScript definition file is generated when the JS target is built, and npm artefacts can be published via `./gradlew :waltid-libraries:credentials:waltid-verification-policies2-vp:npmPublish` when the `NPM_TOKEN` is configured.

## Usage

### Verifying a presentation programmatically

```kotlin
import id.walt.credentials.presentations.PresentationParser
import id.walt.policies2.vp.policies.AudienceCheckJwtVcJsonVPPolicy

suspend fun verifyPresentation(rawPresentation: String) {
    val presentation = PresentationParser.parseOnly(rawPresentation)
    val policy = AudienceCheckJwtVcJsonVPPolicy()
    
    val result = policy.runPolicy(presentation, verificationContext)
    result.onFailure { error ->
        throw IllegalStateException("Presentation validation failed", error)
    }
    println("Verified: ${result.getOrThrow()}")
}
```

### Defining policies via JSON

`VPPolicyList` makes policies configurable at runtime. Policies are organized by presentation format and can be referenced by their ID or configured as JSON objects.

```json
{
  "jwt_vc_json": [
    "jwt_vc_json/audience-check",
    "jwt_vc_json/nonce-check",
    "jwt_vc_json/envelope_signature"
  ],
  "dc+sd-jwt": [
    "dc+sd-jwt/audience-check",
    "dc+sd-jwt/nonce-check",
    "dc+sd-jwt/kb-jwt_signature",
    "dc+sd-jwt/sd_hash-check"
  ],
  "mso_mdoc": [
    "mso_mdoc/device-auth",
    "mso_mdoc/issuer_auth",
    "mso_mdoc/issuer_signed_integrity",
    "mso_mdoc/mso"
  ]
}
```

## Available policies

Policies are organized by presentation format. All policies can be referenced by their ID string or configured as JSON objects.

### JWT VC JSON Policies

#### `jwt_vc_json/audience-check`
Validates that the presentation audience matches the expected audience for the verification session.

**Use case:** Ensure the presentation is intended for the correct verifier.

**Example:**
```json
{
  "policy": "jwt_vc_json/audience-check"
}
```

---

#### `jwt_vc_json/nonce-check`
Validates that the presentation nonce matches the expected nonce for the verification session.

**Use case:** Prevent replay attacks by ensuring the presentation was created for this specific session.

**Example:**
```json
{
  "policy": "jwt_vc_json/nonce-check"
}
```

---

#### `jwt_vc_json/envelope_signature`
Verifies the presentation envelope signature using the holder's public key.

**Use case:** Ensure the presentation was signed by the holder and hasn't been tampered with.

**Example:**
```json
{
  "policy": "jwt_vc_json/envelope_signature"
}
```

---

### SD-JWT Policies

#### `dc+sd-jwt/audience-check`
Validates that the SD-JWT presentation audience matches the expected audience for the verification session.

**Use case:** Ensure the SD-JWT presentation is intended for the correct verifier.

**Example:**
```json
{
  "policy": "dc+sd-jwt/audience-check"
}
```

---

#### `dc+sd-jwt/nonce-check`
Validates that the SD-JWT presentation nonce matches the expected nonce for the verification session.

**Use case:** Prevent replay attacks by ensuring the presentation was created for this specific session.

**Example:**
```json
{
  "policy": "dc+sd-jwt/nonce-check"
}
```

---

#### `dc+sd-jwt/kb-jwt_signature`
Verifies the Key Binding JWT (KB-JWT) signature using the holder's public key.

**Use case:** Ensure the KB-JWT was signed by the holder and binds the presentation to the holder's key.

**Example:**
```json
{
  "policy": "dc+sd-jwt/kb-jwt_signature"
}
```

---

#### `dc+sd-jwt/sd_hash-check`
Verifies SD-JWT key binding by recalculating and comparing the SD hash.

**Use case:** Ensure the presentation hash matches the expected hash, validating key binding integrity.

**Example:**
```json
{
  "policy": "dc+sd-jwt/sd_hash-check"
}
```

---

### mdoc Policies

#### `mso_mdoc/device-auth`
Verifies device authentication using device signature or MAC.

**Use case:** Ensure the mdoc presentation was authenticated by the device that holds the credential.

**Example:**
```json
{
  "policy": "mso_mdoc/device-auth"
}
```

---

#### `mso_mdoc/device_key_auth`
Verifies holder-verified data authorization using KeyAuthorization from the MSO.

**Use case:** Ensure holder-verified data elements are authorized according to the issuer's KeyAuthorization.

**Example:**
```json
{
  "policy": "mso_mdoc/device_key_auth"
}
```

---

#### `mso_mdoc/issuer_auth`
Verifies issuer authentication by validating the COSE_Sign1 signature using the issuer's certificate chain.

**Use case:** Ensure the issuer-signed data was authenticated by the credential issuer.

**Example:**
```json
{
  "policy": "mso_mdoc/issuer_auth"
}
```

---

#### `mso_mdoc/issuer_signed_integrity`
Verifies issuer-signed data integrity by comparing value digests with the MSO.

**Use case:** Ensure issuer-signed data hasn't been tampered with by validating digest hashes.

**Example:**
```json
{
  "policy": "mso_mdoc/issuer_signed_integrity"
}
```

---

#### `mso_mdoc/mso`
Verifies the Mobile Security Object (MSO) validity, including timestamps and digest algorithm support.

**Use case:** Ensure the MSO is valid and within its validity period.

**Example:**
```json
{
  "policy": "mso_mdoc/mso"
}
```

---

## Development

- Build the module: `./gradlew :waltid-libraries:credentials:waltid-verification-policies2-vp:build`
- Run the multiplatform tests: `./gradlew :waltid-libraries:credentials:waltid-verification-policies2-vp:check`
- JVM-only tests: `./gradlew :waltid-libraries:credentials:waltid-verification-policies2-vp:jvmTest`

The tests rely on example credentials from `waltid-digital-credentials-examples`. Some JVM tests initialise minimal DID services; make sure the required dependencies are available when adding new test cases.

## Join the community

* Connect and get the latest updates: [Discord](https://discord.gg/AW8AgqJthZ) | [Newsletter](https://walt.id/newsletter) | [YouTube](https://www.youtube.com/channel/UCXfOzrv3PIvmur_CmwwmdLA) | [LinkedIn](https://www.linkedin.com/company/walt-id/)
* Get help, request features and report bugs: [GitHub Issues](https://github.com/walt-id/waltid-identity/issues)
* Find more indepth documentation on our [docs site](https://docs.walt.id)


## License

Licensed under the [Apache License, Version 2.0](https://github.com/walt-id/waltid-identity/blob/main/LICENSE)

<div align="center">
<img src="../../../assets/walt-banner.png" alt="walt.id banner" />
</div>

