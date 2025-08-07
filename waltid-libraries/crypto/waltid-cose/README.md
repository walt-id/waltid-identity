# waltid-cose

## Explanation of COSE

COSE (CBOR Object Signing and Encryption) is a security framework defined
in [RFC 8152](https://www.rfc-editor.org/rfc/rfc8152.html) for protecting small data payloads. Its
defining feature is the use of [CBOR (Concise Binary Object Representation)](https://cbor.io/),
defined in RFC 8949, a compact binary format that makes COSE messages significantly smaller and more
efficient to process than text-based alternatives.

### COSE vs JOSE

For developers familiar with the **JOSE** (JSON Object Signing and Encryption) framework, COSE (CBOR Object Signing and Encryption) offers a
very similar set of concepts and structures. Think of COSE as a direct binary counterpart to JOSE.

Whereas JOSE uses JSON, COSE uses CBOR. The equivalent to JWS (JSON Web Signature) (with a single
signature) is the COSE_Sign1 message type.

## Examples

(Code excerpts taken from tests)

### Create & sign COSE Sign1 signature

```kotlin
val signer = key.toCoseSigner() // your key
val signed = CoseSign1.createAndSign(
    protectedHeaders = protectedHeaders,
    unprotectedHeaders = unprotectedHeaders,
    payload = payload,
    signer = signer,
    externalAad = externalAad
)

val signedHex: String = signed.toTagged().toHexString()

println(signedHex) // d28443a10126a1044231315454...
```

### Verify COSE Sign1 signature

```kotlin
val signedHex = "d28443a10126a1044231315454..."

val coseSign1 = CoseSign1.fromTagged(signedHex) // provide signature as hex string or ByteArray

val verifier = key.toCoseVerifier()
val verified: Boolean = coseSelf.verify(verifier, externalAad)

println(verified) // true / false
```
