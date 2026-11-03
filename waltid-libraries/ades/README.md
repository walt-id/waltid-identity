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
| `waltid-ades-test` | test PKI fixtures (CA, TSA, OCSP, CRL) | - | KMP software-key fixture; JVM test PKI, tests only |

## Concepts

- **Signing keys** are crypto2 `Key`s, so software, KMS, HSM and PKCS#11 keys all work. Wrap one in
  `AdesSigningCredentials(key, certificateChain)`. No module needs a `java.security.PrivateKey`.
- **Time-stamps** come from a `TimestampSource`, for example `Rfc3161TimestampClient(url, transport)`.
- **Network access** (TSA, OCSP, CRL, AIA, detached documents) goes through `AdesTransport`. Use
  `KtorAdesTransport(httpClient, checkUrl)` with any Ktor engine, or keep validation offline. Disable
  automatic redirects on the client. The required `checkUrl` rejects disallowed destinations for both
  GET and POST; server applications must use a DNS-aware outbound guard, including protection against
  private addresses and DNS rebinding. Custom transports have the same responsibility.
- **Detached documents** require an explicit `DocumentResolver` (CB-AdES), `DetachedContentResolver`
  (JAdES), or `XadesDocumentResolver` (XAdES). Supplying a transport does not enable document fetching.
- **Trust** requires configured anchors. An empty/default anchor list never trusts a self-signed root.
  Time-stamps require a certificate whose critical EKU extension contains only `id-kp-timeStamping`.
- **B-LT material** is gathered by `ValidationDataCollector`. It covers the signing certificate chain,
  TSA certificates, OCSP responders, counter-signers and anything needed for earlier archive time-stamps,
  and it avoids duplicating data that is already embedded.
- **Validation** returns a `DocumentValidationResult` with one `SignatureValidationResult` per signature
  (signers and counter signatures). Each result holds the EN 319 102-1 indication and sub-indication,
  the detected level, the best-signature-time, time-stamp results and every check performed, with
  clause references.

## CB-AdES example

```kotlin
// http is configured with followRedirects = false; checkUrl enforces the application's outbound policy.
val transport = KtorAdesTransport(http, checkUrl)
val credentials = AdesSigningCredentials(key, listOf(signingCert, issuingCa, rootCa))
val signer = CbAdesSigner(
    CbAdesSigningContext(
        timestampSource = Rfc3161TimestampClient("https://tsa.example/tsr", transport),
        transport = transport,
        trustAnchors = listOf(rootCa),
    )
)
val signed = signer.signSign1(payload, credentials, CbAdesSignatureParameters(level = AdesLevel.B_LTA, contentType = "text/plain"))
val lta2 = signer.augment(signed, AdesLevel.B_LTA) // renews the archive time-stamp

val result = CbAdesValidator(AdesValidationPolicy(trustAnchors = listOf(rootCa), transport = transport))
    .validate(lta2.encode())
check(result.isValid)
```

## Shared X.509 validation

AdES uses `waltid-x509` for certificate-path signatures, authority-key identifiers, CA constraints,
path lengths and CA validity. Explicit anchors are passed through an `InMemoryTrustStore`; system roots
are not implicitly added. Leaf validity, revocation and historical proof-of-existence decisions remain
in the EN 319 102-1 process so expired and revoked signatures retain their precise sub-indications.

The format-facing DER certificate and CRL views remain in AdES for now. The shared complete-CRL verifier
requires current, direct v2 CRLs with AKI and does not directly replace historical AdES revocation evidence.
Consolidating those views and extending shared CRL support remains follow-up work.

## CB-AdES interpretation notes (ETSI CB-AdES Plugtests 2026)

- The `arcTst` message imprint is computed over the clause 5.3.5.3 CBOR array wrapped in a CBOR byte string
  (step 12). The spec editor confirmed this reading in the plugtest forum: the "step 11)" in the final sentence
  is a typo for "step 12)". The validator also accepts imprints over the bare array, which some early
  implementations produce (`ArcTstImprintMode`).
- The `adoTst` imprint covers the payload encoded as a CBOR byte string. The `sigTst` imprint covers the
  raw signature value.
- Countersignatures follow RFC 9338 clause 3.3:
  - `CounterSignatureV2` for COSE_Sign1 targets (`other_fields = [signature]`).
  - `CounterSignature` for COSE_Signature and countersignature targets (no other fields).
- `srAts.claimed` entries are encoded as `[mediaType, {label: value}]`.

Run the opt-in interoperability check against a downloaded plugtest package with
`./gradlew :waltid-libraries:ades:waltid-cbades:jvmTest -PcbadesInteropDir=<dir>`. The directory holds
participant folders, `Data/` and an optional `trust/` folder with extra roots and CRLs.
