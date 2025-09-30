# waltid-mdocs2 Library

waltid-mdocs is a Kotlin Multiplatform library for creating, parsing, and verifying mobile
documents (mdocs) that conform to the respective standards (e.g. ISO/IEC 18013, 23220).
It provides the core cryptographic and data structure logic required to build
applications, such as digital wallets and verifier services, that are compliant with the mdoc
standard.

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
