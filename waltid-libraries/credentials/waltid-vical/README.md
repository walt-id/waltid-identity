# waltid-vical

A Kotlin/JVM library for parsing and verifying Verifier Integrated Credential Authentication
Ledger (VICAL) data, as defined in [ISO/IEC 18013-5](https://www.iso.org/standard/69084.html).

This library makes it easy to handle VICAL structures, which are essential for establishing trust
with issuers of credentials like Mobile Driving Licenses (mDL).

It is built on top of waltid-cose, a multiplatform COSE library.

## Explanation of VICAL

VICAL is a trust framework designed for credentials like the Mobile Driving License (mDL). A driving
license can be issued by many different authorities across various countries. For a Relying Party (a
verifier), keeping track of all these trusted issuers and their public keys would be a massive,
ongoing effort.

VICAL solves this problem. A central, trusted entity (like AAMVA in the US, Austroads in Australia)
compiles a list of all approved issuer certificates. This list is then signed and distributed as a
single file in the VICAL format (typically as a CBOR file).

By using VICAL, a verifier only needs to trust the central entity. It can download the VICAL list,
verify its signature, and then have access to a complete, up-to-date registry of all trusted
issuers.

## Examples

(Code excerpts taken from tests)

### Verify American Association of Motor Vehicles Administrators VICAL

This example uses a test VICAL file from the American Association of Motor Vehicle Administrators (
AAMVA).

#### Decode the VICAL file

First, you need the VICAL data as a `ByteArray`.
VICAL is typically distributed as a CBOR file.
The `Vical.decode()` function parses this byte array into a structured `Vical` object.

```kotlin
val rawFile: ByteArray = readFile("vicals/aamva.cbor").readBytes()

val vical = Vical.decode(rawFile)
```

#### Verify the VICAL Signature

The VICAL structure is contained within a `COSE_Sign1` message,
meaning the entire list is cryptographically signed.
To trust its contents, you must verify this signature.

```kotlin
// 1. Extract the signer's certificate from the VICAL header
val x5Chain = vical.getCertificateChain()
requireNotNull(x5Chain) { "Signer certificate chain (x5chain) not found in header." }
val signerCertificate = x5Chain.first().rawBytes // select a certificate to verify

// 2. Import the certificate as a key that can be used for verification
val signerKey = JWKKey.importFromDerCertificate(signerCertificate).getOrThrow()

// 3. Verify the signature
val isSignatureValid: Boolean = vical.verify(signerKey.toCoseVerifier())
println(isSignatureValid) // true/false
```

#### Access the Trusted Issuer List

```kotlin
// List allowed issuers
val allowedIssuers = vical.vicalData.getAllAllowedIssuers().entries
vical.vicalData.getAllAllowedIssuers().entries.forEachIndexed { idx, (certificateInfo, certKeyResult) ->
    println("--- ${idx + 1}: Certificate key for: ${certificateInfo.issuingAuthority}")
    val certKey = certKeyResult.getOrNull()
    println("Key: $certKeyResult (${certKey?.getKeyId() ?: "Error"})")
}
println("Allowed issuers per this VICAL: ${allowedIssuers.size}")
```

## Licence
This library is licensed under the Apache 2.0 License.
