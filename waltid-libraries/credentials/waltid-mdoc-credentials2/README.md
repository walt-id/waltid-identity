<div align="center">
 <h1>Kotlin Multiplatform mdoc2 library</h1>
 <span>by </span><a href="https://walt.id">walt.id</a>
 <p>Create credentials in <b>mdoc format</b> according to <b>ISO/IEC 18013-5:2021</b> and <b>ISO/IEC 23220-2:2023</b> standards</p>

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

## Getting Started

## What is the mdoc library
This library implements the mdoc specification: [ISO/IEC 18013-5:2021](https://www.iso.org/standard/69084.html), Personal identification -- ISO-compliant driving licence -- Part 5: Mobile driving licence (mDL) application.

This library is a successor to the [waltid-mdoc-credentials](../waltid-mdoc-credentials) library. It aims to provide a multiplatform library that is easier to use and maintain compared to the original library.

### Further information

Checkout the [documentation regarding mdoc credentials](https://docs.walt.id/concepts/digital-credentials/mdoc-mdl-iso), to find out more.

## Features

* **Parsing**: Parses binary mdoc data (from Hex or Base64Url strings) into
  structured Kotlin data classes like `Document`.
* **Verification**: Implements a multi-step verification process as specified by ISO/IEC
  18013-5:
    * **Issuer Authentication**: Verifies the `issuerAuth` COSE signature with the public key material of the issuer.
    * **MSO Validity**: Checks the validity period (timestamps) of the Mobile Security Object (
      MSO).
    * **Device Authentication**: Verifies the holder's proof of possession by validating the
      `deviceAuth` signature against the device public key from the MSO.
    * **Data Integrity**: Ensures that presented data elements have not been tampered with by
      matching their digests against the list of `valueDigests` in the MSO.
    * **Device Key Authorization**: Confirms that the device was authorized by the issuer to
      provide device-signed data elements.
* **Data Models**: Provides data classes for major mdoc structures, e.g. `Document`,
  `IssuerSigned`, `MobileSecurityObject`.
* **Utils**: Includes helper functions to merge issuer-signed and device-signed
  namespaces into a single, unified JSON object for easy consumption.

-----

## Key Components

* **`MdocParser`**: Your starting point for any received mdoc. It takes the raw mdoc data string and
  parses it into a `Document` object.
* **`MdocVerifier`**: The core of the verification process. Its `verify` method runs all necessary
  cryptographic checks and returns a detailed `VerificationResult`.
* **`Document`**: The main container for a parsed mdoc, separating data into `issuerSigned` and
  `deviceSigned` parts.
* **`IssuerSigned`**: Contains the data elements signed by the issuer and the `issuerAuth`
  structure, which holds the Mobile Security Object (MSO).
* **`MdocSignedMerger`**: A utility to combine data from `issuerSigned` and `deviceSigned`
  namespaces into a single `JsonObject`.

-----

## Usage Examples

### 1\. Parsing an mdoc Presentation

To inspect the contents of an mdoc, use the `MdocParser`.

```kotlin
import id.walt.mdoc.parser.MdocParser

// The mdoc presentation, typically received as a Base64Url string in a vp_token
val mdocPresentationString =
    "o2d2ZXJzaW9uYzEuMGlkb2N1bWVudHOBo2dkb2NUeXBld..." // (shortened for brevity)

// Parse the string into a Document object
try {
    val document = MdocParser.parseToDocument(mdocPresentationString)

    println("Successfully parsed mdoc.")
    println("Doctype: ${document.docType}") // e.g., "org.iso.18013.5.1.mDL"

    // Convert issuer-signed data to a user-friendly JSON object
    val issuerData = document.issuerSigned.namespacesToJson()
    println("Issuer Signed Data: $issuerData")

    // Access device-signed data if present
    val deviceData = document.deviceSigned?.namespaces?.value?.namespacesToJson()
    if (deviceData != null) {
        println("Device Signed Data: $deviceData")
    }

} catch (e: Exception) {
    println("Failed to parse mdoc: ${e.message}")
}
```

### 2\. Verifying an mdoc Presentation

Verification is the primary use case for this library. The `MdocVerifier` requires a
`VerificationContext` to reconstruct the `SessionTranscript`, which is essential for validating the
device signature.

```kotlin
import id.walt.mdoc.verification.MdocVerifier
import id.walt.mdoc.verification.VerificationContext
import id.walt.mdoc.parser.MdocParser

val mdocPresentationString =
    "o2d2ZXJzaW9uYzEuMGlkb2N1bWVudHOBo2dkb2NUeXBld..." // (from verifier request)

// 1. Create a verification context from your OID4VP session data
val context = VerificationContext(
    expectedNonce = "92698b40-f50d-4be1-97b4-dd1dba9bfe4a", // The nonce you sent in the AuthorizationRequest
    expectedAudience = "my-client-id", // The client_id of your verifier
    responseUri = "https://waltid.enterprise.mdoc-test.waltid.cloud/..." // The response_uri you provided
)

// 2. Perform the verification
val verificationResult = MdocVerifier.verify(mdocPresentationString, context)

// 3. Check the overall result and individual steps
println("Verification successful: ${verificationResult.valid}")
println("- Issuer Signature Valid: ${verificationResult.issuerSignatureValid}")
println("- Data Integrity Valid:   ${verificationResult.dataIntegrityValid}")
println("- MSO Timestamps Valid:   ${verificationResult.msoValidityValid}")
println("- Device Signature Valid: ${verificationResult.deviceSignatureValid}")
println("- Device Key Authorized:  ${verificationResult.deviceKeyAuthorized}")

// 4. If valid, you can securely use the merged credential data
if (verificationResult.valid) {
    println("\nVerified Credential Data:")
    println(verificationResult.credentialData.toString())
} else {
    println("\nErrors:")
    verificationResult.errors.forEach { println("- $it") }
}
```

### 3\. Merging Issuer and Device Data

If you need to combine data from both `issuerSigned` and `deviceSigned` parts manually, you can use
the `MdocSignedMerger`. This is useful when you have device-provided data (like a current timestamp)
that should override older issuer-signed data.

```kotlin
import id.walt.mdoc.namespaces.MdocSignedMerger
import id.walt.mdoc.namespaces.MdocSignedMerger.MdocDuplicatesMergeStrategy

val document: Document = // ... parsed document from MdocParser

// Get JSON representations of both data sources
val issuerSignedJson = document.issuerSigned.namespacesToJson()
val deviceSignedJson = document.deviceSigned?.namespaces?.value?.namespacesToJson()

if (deviceSignedJson != null) {
    // Merge the data. If a data element exists in both, the device-signed one will be used.
    val mergedData = MdocSignedMerger.merge(
        issuerSignedJson,
        deviceSignedJson,
        strategy = MdocDuplicatesMergeStrategy.OVERRIDE
    )
    println("Merged Data (device values override issuer values): $mergedData")
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
