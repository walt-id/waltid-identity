# mdocs2

## Examples

### Parse signed mdoc into Document class

```kotlin
val signed = "ABCDEF12345"
val document = MdocParser.parseToDocument(signed)
document.issuerSigned
document.deviceSigned
// etc
```

### Verify a Mdoc presentation

```kotlin
val verificationContext = VerificationContext(
    expectedNonce = "...",
    expectedAudience = "...",
    responseUri = "..."
)
val sessionTranscript = MdocCryptoHelper.reconstructOid4vpSessionTranscript(context) // For OpenID4VP 1.0

// ---

val verificationResult = MdocVerifier.verify(document, sessionTranscript)
println(verificationResult.valid)
println(verificationResult.issuerSignatureValid)
println(verificationResult.dataIntegrityValid)
println(verificationResult.msoValidityValid)
println(verificationResult.deviceSignatureValid)
println(verificationResult.deviceKeyAuthorized)
```
