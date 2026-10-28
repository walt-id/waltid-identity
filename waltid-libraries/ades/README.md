# walt.id AdES libraries

Kotlin libraries for creating, augmenting and validating ETSI AdES digital signatures at the baseline levels
B-B, B-T, B-LT and B-LTA. Validation follows the ETSI EN 319 102-1 processes.

| Module | Format | Specification | Targets |
|---|---|---|---|
| `waltid-ades-core` | shared building blocks | EN 319 102-1, RFC 5280/6960/3161/5652 | JVM, JS, iOS, Android |
| `waltid-cbades` | CB-AdES (CBOR / COSE) | ETSI TS 119 152-1 V1.1.1 | JVM, JS, iOS, Android |
| `waltid-jades` | JAdES (JSON / JWS) | ETSI TS 119 182-1 | JVM, JS, iOS, Android |
| `waltid-xades` | XAdES (XML / XMLDSig) | ETSI EN 319 132-1 | JVM (Apache Santuario) |
| `waltid-pades` | PAdES (PDF / CAdES) | ETSI EN 319 142-1 | JVM (Apache PDFBox, Bouncy Castle) |
| `waltid-ades-test` | test PKI fixtures (CA, TSA, OCSP, CRL) | - | JVM, tests only |

## Concepts

- **Signing keys** are crypto2 `Key`s, so software, KMS, HSM and PKCS#11 keys all work. Wrap one in
  `AdesSigningCredentials(key, certificateChain)`. No module needs a `java.security.PrivateKey`.
- **Time-stamps** come from a `TimestampSource`, for example `Rfc3161TimestampClient(url, transport)`.
- **Network access** (TSA, OCSP, CRL, AIA, detached documents) goes through `AdesTransport`. Use
  `KtorAdesTransport(httpClient)` with any Ktor engine, or keep validation offline.
- **B-LT material** is gathered by `ValidationDataCollector`. It covers the signing certificate chain,
  TSA certificates, OCSP responders, counter-signers and anything needed for earlier archive time-stamps,
  and it avoids duplicating data that is already embedded.
- **Validation** returns a `DocumentValidationResult` with one `SignatureValidationResult` per signature
  (signers and counter signatures). Each result holds the EN 319 102-1 indication and sub-indication,
  the detected level, the best-signature-time, time-stamp results and every check performed, with
  clause references.

## CB-AdES example

```kotlin
val credentials = AdesSigningCredentials(key, listOf(signingCert, issuingCa, rootCa))
val signer = CbAdesSigner(
    CbAdesSigningContext(
        timestampSource = Rfc3161TimestampClient("https://tsa.example/tsr", KtorAdesTransport(http)),
        transport = KtorAdesTransport(http),
        trustAnchors = listOf(rootCa),
    )
)
val signed = signer.signSign1(payload, credentials, CbAdesSignatureParameters(level = AdesLevel.B_LTA, contentType = "text/plain"))
val lta2 = signer.augment(signed, AdesLevel.B_LTA) // renews the archive time-stamp

val result = CbAdesValidator(AdesValidationPolicy(trustAnchors = listOf(rootCa), transport = KtorAdesTransport(http)))
    .validate(lta2.encode())
check(result.isValid)
```

## CB-AdES interpretation notes (ETSI CB-AdES Plugtests 2026)

- The `arcTst` message imprint is computed over the encoded CBOR array of clause 5.3.5.3. This is what
  the portal checker and the other participants use. The validator also accepts the bstr-wrapped
  variant (`ArcTstImprintMode`).
- The `adoTst` imprint covers the payload encoded as a CBOR byte string. The `sigTst` imprint covers the
  raw signature value.
- Countersignatures follow RFC 9338 clause 3.3:
  - `CounterSignatureV2` for COSE_Sign1 targets (`other_fields = [signature]`).
  - `CounterSignature` for COSE_Signature and countersignature targets (no other fields).
- `srAts.claimed` entries are encoded as `[mediaType, {label: value}]`.

Run the opt-in interoperability check against a downloaded plugtest package with
`./gradlew :waltid-libraries:ades:waltid-cbades:jvmTest -PcbadesInteropDir=<dir>`. The directory holds
participant folders, `Data/` and an optional `trust/` folder with extra roots and CRLs.
