<div align="center">
 <h1>Kotlin Multiplatform X.509 library</h1>
 <span>by </span><a href="https://walt.id">walt.id</a>
 <p>Work with X.509 certificates</p>

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

A tiny, pragmatic **Kotlin Multiplatform** library for working with **X.509 certificates** across JVM/Android, iOS, and JS.

---

## Features

- **KMP-first API**: common `expect/actual` with a consistent developer experience.
- **PKIX chain validation (JVM/Android)**: 
  - Order-independent path building & validation using the platform PKI.
  - **Pluggable trust model**: validate against:
    - your **organization trust store** (recommended), or
    - an explicit **pinned root** included in the `x5c` chain (private PKI / pinning).
- **[ISO/IEC 18013-5](https://github.com/ISOWG10/ISO-18013/blob/main/Working%20Documents/Working%20Draft%20WG%2010_N2549_ISO-IEC%2018013-5-%20Personal%20identification%20%E2%80%94%20ISO-compliant%20driving%20licence%20%E2%80%94%20Part%205-%20Mobile%20driving%20lic.pdf) X.509 certificate tooling (JVM)**:
  - IACA and Document Signer X.509 certificate generation and parsing.
  - Configurable validators with profile-compliant defaults.
- **JVM extensions**: helpers for `X500Name`, `KeyUsage`, and `X509Certificate` v3 extension extraction.
- **Clear exceptions**: failures raise `X509ValidationException` with context.

---

## Targets

- **JVM / Android**: Full chain validation, [ISO/IEC 18013-5](https://github.com/ISOWG10/ISO-18013/blob/main/Working%20Documents/Working%20Draft%20WG%2010_N2549_ISO-IEC%2018013-5-%20Personal%20identification%20%E2%80%94%20ISO-compliant%20driving%20licence%20%E2%80%94%20Part%205-%20Mobile%20driving%20lic.pdf) build/parse/validate, JVM extensions.
- **iOS**: Chain validation stub; ISO tooling not implemented yet.
- **JS**: Chain validation stub; ISO tooling not implemented yet.

> If you only need JVM/Android today, you can use it immediately. iOS/JS will throw `X509ValidationException("Not implemented…")` until completed.

---

## Installation

Add the module as a dependency to your multiplatform project:

```kotlin
// build.gradle.kts

implementation("id.walt:waltid-x509:<version>") // when published

OR

include(":waltid-libraries:crypto:waltid-x509") // if used as a composite build/module
```

---

## PKIX Certificate Chain Validation

> Namespaces may differ slightly in your repo; adjust imports to your package.

```kotlin
// Common API (expect)
import okio.ByteString

data class CertificateDer(val bytes: ByteString)

/** Validate a leaf X.509 cert against a provided chain and trust anchors. */
@Throws(X509ValidationException::class)
expect fun validateCertificateChain(
    leaf: CertificateDer,
    chain: List<CertificateDer>,
    trustAnchors: List<CertificateDer>? = null,
    enableTrustedChainRoot: Boolean = false,
    enableSystemTrustAnchors: Boolean = false,
    enableRevocation: Boolean = false
)

class X509ValidationException(message: String, cause: Throwable? = null) : Exception(message, cause)
```

### Quick start (JVM/Android)

```kotlin
import id.walt.x509.CertificateDer
import id.walt.x509.validateCertificateChain
import okio.ByteString.Companion.toByteString
import java.util.Base64

fun validateFromX5cExample(
    x5cBase64: List<String>,             // JWT header "x5c": Base64 DER certs
    trustAnchorsDer: List<ByteArray>?,   // null = use self-signed root from chain (pinning/private PKI)
    enableRevocation: Boolean = false
) {
    val chain = x5cBase64.map { CertificateDer(Base64.getDecoder().decode(it).toByteString()) }
    val leaf = chain.first()
    val anchors = trustAnchorsDer?.map { CertificateDer(it.toByteString()) }

    validateCertificateChain(
        leaf = leaf,
        chain = chain,
        trustAnchors = anchors,
        enableTrustedChainRoot = anchors.isNullOrEmpty(),
        enableSystemTrustAnchors = false,
        enableRevocation = enableRevocation
    )
}
```

### Loading trust anchors from a JVM KeyStore (JVM helper)

```kotlin
import id.walt.x509.CertificateDer
import id.walt.x509.loadTrustAnchorsFromKeyStore
import java.security.KeyStore

fun anchorsFromKeyStore(ks: KeyStore): List<CertificateDer> {
    return loadTrustAnchorsFromKeyStore(ks)
}
```

---

## [ISO/IEC 18013-5](https://github.com/ISOWG10/ISO-18013/blob/main/Working%20Documents/Working%20Draft%20WG%2010_N2549_ISO-IEC%2018013-5-%20Personal%20identification%20%E2%80%94%20ISO-compliant%20driving%20licence%20%E2%80%94%20Part%205-%20Mobile%20driving%20lic.pdf) X.509 certificate tooling (IACA and Document Signer)

The `id.walt.x509.iso` package provides the following:

- Builder classes for IACA (via `IACACertificateBuilder`) and Document Signer (via `DocumentSignerCertificateBuilder`) X.509 certificates.
- Parser classes for IACA (via `IACACertificateParser`) and Document Signer (via `DocumentSignerCertificateParser`) DER-encoded (via the `CertificateDer` platform-agnostic wrapper) X.509 certificates. **Note:** Decoded certificates **are not validated** by the parsers; use the validator classes for validation.
- Validator classes for IACA (via `IACAValidator`) and Document Signer (via `DocumentSignerValidator`) decoded certificate instances (`IACADecodedCertificate` and `DocumentSignerDecodedCertificate` respectively) with a simple and flexible validation configuration tuning (refer to `IACAValidationConfig` and `DocumentSignerValidationConfig` for the respective configuration options).

### IACA X.509 Certificate Generation

Generating an IACA X.509 certificate with all the mandatory fields is achieved as follows: 

```kotlin
@OptIn(ExperimentalTime::class)
suspend fun buildIaca(signingKey: Key) = IACACertificateBuilder().build(
    profileData = IACACertificateProfileData(
        principalName = IACAPrincipalName(
            country = "AT",
            commonName = "Example IACA",
            organizationName = "Example Org",
        ),
        validityPeriod = X509ValidityPeriod(
            notBefore = Clock.System.now(),
            notAfter = Clock.System.now() + 365.days,
        ),
        issuerAlternativeName = IssuerAlternativeName(
            uri = "https://issuer.example"
        ),
        crlDistributionPointUri = "https://issuer.example/crl",
    ),
    signingKey = signingKey,
)
```

### Document Signer X.509 Certificate Generation

Generating Document Signer X.509 certificate with all the mandatory fields is achieved as follows:

```kotlin
@OptIn(ExperimentalTime::class)
suspend fun buildDocumentSigner(
    dsPublicKey: Key,
    iacaProfileData: IACACertificateProfileData,
    iacaSigningKey: Key,
) = DocumentSignerCertificateBuilder().build(
    profileData = DocumentSignerCertificateProfileData(
        principalName = DocumentSignerPrincipalName(
            country = "AT",
            commonName = "Example DS",
            organizationName = "Example Org",
        ),
        validityPeriod = X509ValidityPeriod(
            notBefore = Clock.System.now(),
            notAfter = Clock.System.now() + 180.days,
        ),
        crlDistributionPointUri = "https://issuer.example/crl",
    ),
    publicKey = dsPublicKey,
    iacaSignerSpec = IACASignerSpecification(
        profileData = iacaProfileData,
        signingKey = iacaSigningKey,
    ),
)
```

> Notes:
> - Builder classes always generate profile compliant X.509 certificates. To achieve this, they perform internally all
> the necessary validations and throw with an appropriate error message indicating the issue.
> - Input certificate validity instants are stored with second-level precision. Any sub-second
> precision (e.g., milliseconds) is discarded during certificate generation,
> a fact that is also reflected in the decoded certificate returned by the builders.

### Parsing \& Validation

Parsing yields decoded data only. Validation is explicit via the validators.  
By default, **all ISO profile constraints are enforced**. Clients can relax checks
with the validation config classes (more info is provided below).

```kotlin
val iacaDecoded = IACACertificateParser().parse(iacaDer) // certificate data decoding only - no validation performed here
IACAValidator().validate(iacaDecoded) // strict ISO profile by default

val relaxedIaca = IACAValidationConfig(signature = false) // turn-off certificate signature validation
IACAValidator(relaxedIaca).validate(iacaDecoded)
```

```kotlin
val dsDecoded = DocumentSignerCertificateParser().parse(dsDer) // certificate data decoding only - no validation performed here
DocumentSignerValidator().validate(dsDecoded, iacaDecoded) // strict ISO profile by default

val relaxedDs = DocumentSignerValidationConfig(
    signature = false,
    profileDataAgainstIACAProfileData = false,
) //turn-off certificate signature validation and cross-checking of profile data against that of the IACA
DocumentSignerValidator(relaxedDs).validate(dsDecoded, iacaDecoded)
```

### Blocking API (JVM)

Blocking variants are available for all ISO builders, parsers, and validators. These call the underlying suspend APIs
via a platform bridge.

```kotlin
val iacaBundle = IACACertificateBuilder().buildBlocking(profileData, signingKey)
val iacaDecoded = IACACertificateParser().parseBlocking(iacaBundle.certificateDer)
IACAValidator().validateBlocking(iacaDecoded)

val dsBundle = DocumentSignerCertificateBuilder().buildBlocking(dsProfileData, dsPublicKey, iacaSignerSpec)
val dsDecoded = DocumentSignerCertificateParser().parseBlocking(dsBundle.certificateDer)
DocumentSignerValidator().validateBlocking(dsDecoded, iacaDecoded)
```

> Note: blocking APIs are not supported on JS targets. Prefer suspend APIs in asynchronous contexts.

#### Configurable validation system (flags map)

Both `IACAValidator` and `DocumentSignerValidator` accept config objects that toggle
cohesive groups of ISO/IEC 18013-5 checks. All flags default to `true` (strict profile).
Failures throw `IllegalArgumentException` with a descriptive message.

IACA validation flags (`IACAValidationConfig`):
- `keyType`: enforce allowed IACA key types.
- `principalName`: validate X.500 principal name fields and ISO 3166-1 country code.
- `serialNo`: enforce ISO serial number size/entropy constraints.
- `basicConstraints`: require CA=true and pathLengthConstraint=0.
- `keyUsage`: require key usage set to KeyCertSign + CRLSign.
- `issuerAlternativeName`: require at least one non-blank IssuerAlternativeName entry.
- `validityPeriod`: enforce notBefore < notAfter, notAfter > now, and max 20 years.
- `crlDistributionPointUri`: optional CRL distribution point must be non-blank when present.
- `requiredCriticalExtensionOIDs`: require ISO-mandated critical extension OIDs.
- `requiredNonCriticalExtensionOIDs`: require ISO-mandated non-critical extension OIDs.
- `signature`: verify the certificate signature using the IACA public key.

Document Signer validation flags (`DocumentSignerValidationConfig`):
- `keyType`: enforce allowed Document Signer public key types.
- `principalName`: validate X.500 principal name fields and ISO 3166-1 country code.
- `serialNo`: enforce ISO serial number size/entropy constraints.
- `basicConstraints`: require CA=false.
- `keyUsage`: require key usage set to DigitalSignature.
- `extendedKeyUsage`: require the Document Signer EKU OID to be present.
- `authorityKeyIdentifier`: require AKI to match the issuing IACA SKI.
- `validityPeriod`: enforce notBefore < notAfter, notAfter > now, and max 457 days.
- `crlDistributionPointUri`: require CRL distribution point URI to be non-blank.
- `profileDataAgainstIACAProfileData`: cross-validate country/state and validity window against the IACA.
- `requiredCriticalExtensionOIDs`: require ISO-mandated critical extension OIDs.
- `requiredNonCriticalExtensionOIDs`: require ISO-mandated non-critical extension OIDs.
- `signature`: verify the certificate signature with the issuing IACA public key.

---

## JVM X.509 Extensions Overview

The JVM-only extensions bridge common JCA/Bouncy Castle types to the multiplatform models:

- **X500Name utilities**: read or build X.500 rDNs (C, CN, ST, O, L).
- **X509 v3 parsing**: extract SKI/AKI, key usage, basic constraints, and known extension OIDs from `X509Certificate`.
- **KeyUsage conversions**: convert between Bouncy Castle `KeyUsage` and `X509KeyUsage`.

```kotlin
fun jvmExtensionsExample(cert: X509Certificate) {
    val name = buildX500Name(country = "AT", commonName = "Example")
    val country = name.getCountryCode()
    val commonName = name.getCommonName()

    val keyUsages = cert.x509KeyUsages
    val basicConstraints = cert.x509BasicConstraints
    val ski = cert.subjectKeyIdentifier
    val aki = cert.authorityKeyIdentifier
    val criticalOids = cert.criticalX509V3ExtensionOIDs
    val nonCriticalOids = cert.nonCriticalX509V3ExtensionOIDs

    val bcKeyUsage = setOf(X509KeyUsage.DigitalSignature).toBouncyCastleKeyUsage()
}
```

---

## Platform notes

- **JVM / Android**
  - Uses `PKIX` builder/validator. Order of `chain` does not matter.
  - **Revocation**: If you pass `enableRevocation = true`, enable CRL/OCSP in the JVM:
    ```
    -Dcom.sun.security.enableCRLDP=true
    -Docsp.enable=true
    # optional:
    -Docsp.responderURL=https://ocsp.example.com
    ```
  - You can load the system/organizational trust store and pass anchors via `loadTrustAnchorsFromKeyStore`.

- **iOS (planned)**
  - Implement with `SecTrust`: `SecPolicyCreateBasicX509`, `SecTrustSetAnchorCertificates`, `SecTrustEvaluateWithError`.
  - Current actual throws `X509ValidationException("Not implemented on iOS yet")`.

- **JavaScript (planned)**
  - WebCrypto does not expose a PKIX path builder; integrate a JS PKI lib or a WASM backend.
  - Current actual throws `X509ValidationException("Not implemented on JS yet")`.

---

## Best practices

- Prefer known trust anchors (system/org CA store) for public PKI.
- Use pinned roots from `x5c` only for explicit trust scenarios (private PKI / trusted issuer).
- For [ISO/IEC 18013-5](https://github.com/ISOWG10/ISO-18013/blob/main/Working%20Documents/Working%20Draft%20WG%2010_N2549_ISO-IEC%2018013-5-%20Personal%20identification%20%E2%80%94%20ISO-compliant%20driving%20licence%20%E2%80%94%20Part%205-%20Mobile%20driving%20lic.pdf) X.509 certificates, **always** validate IACA and Document Signer certificates.

---

## Join the community

* Connect and get the latest updates: [Discord](https://discord.gg/AW8AgqJthZ) | [Newsletter](https://walt.id/newsletter) | [YouTube](https://www.youtube.com/channel/UCXfOzrv3PIvmur_CmwwmdLA) | [LinkedIn](https://www.linkedin.com/company/walt-id/)
* Get help, request features and report bugs: [GitHub Issues](https://github.com/walt-id/waltid-identity/issues)
* Find more indepth documentation on our [docs site](https://docs.walt.id)


## License

Licensed under the [Apache License, Version 2.0](https://github.com/walt-id/waltid-identity/blob/main/LICENSE)

<div align="center">
<img src="../../../assets/walt-banner.png" alt="walt.id banner" />
</div>
