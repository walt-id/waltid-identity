# waltid-cose

(Code excerpts taken from tests)

## Create & sign COSE Sign1 signature

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

## Verify COSE Sign1 signature

```kotlin
val signedHex = "d28443a10126a1044231315454..."

val coseSign1 = CoseSign1.fromTagged(signedHex) // provide signature as hex string or ByteArray

val verifier = key.toCoseVerifier()
val verified: Boolean = coseSelf.verify(verifier, externalAad)

println(verified) // true / false
```
