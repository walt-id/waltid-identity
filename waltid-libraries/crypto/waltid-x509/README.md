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
- **CSR Support**: Support for creating and fulfilling Certificate Signing Requests (CSRs) using the PKCS#10 standard.
- **Crypto2 signing**: Generic, ISO IACA, and Document Signer certificates plus PKCS#10 CSRs use native crypto2 keys.
- **Crypto2 parsing**: Parsed certificate and CSR public keys are available as typed `id.walt.crypto2.keys.Key` values.
- **Certificate extensions**: Support of most common X509 certificate extensions `KeyUsage`, `Basic Constraints`and `Subject Alternative Names` and more.
- **Extensible certificate chain validation**: Basic validation is platform independently implemented, easy to add additional checks.

---

## Targets

- **JVM / Android**: Based on [Bouncy Castle library](https://www.bouncycastle.org/) - Full chain validation, [ISO/IEC 18013-5](https://github.com/ISOWG10/ISO-18013/blob/main/Working%20Documents/Working%20Draft%20WG%2010_N2549_ISO-IEC%2018013-5-%20Personal%20identification%20%E2%80%94%20ISO-compliant%20driving%20licence%20%E2%80%94%20Part%205-%20Mobile%20driving%20lic.pdf) build/parse/validate, JVM extensions.
- **iOS**: Based on [Signum library](https://github.com/a-sit-plus/signum) - Explicit-trust chain validation plus ISO/IEC 18013-5 build/parse/validate support. Limited set of supported key types.
- **JS**: Based on [Signum library](https://github.com/a-sit-plus/signum) - Explicit-trust chain validation plus ISO/IEC 18013-5 build/parse/validate support. Limited set of supported key types.

> Certificate revocation checks are not yet supported. 
> On iOS and JS system trust anchors are not supported.

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

## Quick start

> Namespaces may differ slightly in your repo; adjust imports to your package.


### Create self-signed root certificate

```kotlin
import id.walt.certificate.x509.X509Certificate
import id.walt.certificate.x509.X509CertificateUtil
import id.walt.certificate.x509.extension.*
import id.walt.certificate.x509.truststore.InMemoryTrustStore
import id.walt.crypto2.CryptoRuntime
import id.walt.crypto2.algorithms.*
import id.walt.crypto2.keys.*
import id.walt.crypto2.providers.*
// imports are shortended in this example

private val cryptoRuntime = CryptoRuntime(defaultSoftwareKeyProviders())
private val keyGen = GenerateSoftwareKeyRequest(
  id = KeyId("ca"),
  spec = KeySpec.Ec(EcCurve.P256),
  usages = setOf(KeyUsage.SIGN, KeyUsage.VERIFY)
)
private val certSigningAlg = SignatureAlgorithm.Ecdsa(DigestAlgorithm.SHA_256, EcdsaSignatureEncoding.DER)
val caKey = cryptoRuntime.generateSoftwareKey(keyGen)

//create self-signed root certificate
//required extensions like 'basic constraints' and 'subject key identifier' are added automatically
val caCert = X509CertificateUtil.createSelfSignedCertificate(caKey, certSigningAlg) {
  subjectDn = "cn=My Root, o=Walt.id, c=AT"
  //add key usage constraint
  extensionKeyUsage {
    addKeyUsage(KeyUsageExtension.KeyUsage.digitalSignature, KeyUsageExtension.KeyUsage.keyCertSign)
  }
  //add subject alternative names
  extensionSan {
    addEmail("office@walt.id")
    addUri("https://walt.id")
  }
}
```

### Create a certificate signed by the root

```kotlin
val leafKey = cryptoRuntime.generateSoftwareKey(keyGen)
val leafCert = X509CertificateUtil.createCertificate(caKey, caCert, certSigningAlg) {
  subjectDn = "cn=My Leaf Certificate, o=Walt.id, c=AT"
  subjectPublicKey(leafKey)
}
```

### Restore Subject Public Key Info (SPKI) from a certificate
```kotlin
val publicKey = leafCert.restoreSubjectPublicKey(cryptoRuntime)
```

### Validate a certificate chain
```kotlin
import id.walt.certificate.x509.truststore.InMemoryTrustStore

//trust our own root certificate
val trustStore = InMemoryTrustStore(listOf(caCert))
//validate the certificate chain (order in the chain does not matter)
//root can be included in the chain,
val validationResult = X509CertificateUtil.validateCertificateChain(listOf(leafCert), trustStore)
println("Validation result: ${validationResult.valid}")
validationResult.log.forEach { println("${it.severity} ${it.subjectDn}/${it.validatorId}: '${it.message}'") }
```

> `trustStore` here fully replaces `X509CertificateUtil.Default`'s configured trust store for this
> call - it is not merged with it. See [Configure X509CertificateUtil](#configure-x509certificateutil)
> below for what that means in practice.

The result contains information about which validations are performed on which certificates and the outcome of each validation:
```
Validation result - isValid: true
Validation result - log:
WARN CN=My Root,O=Walt.id,C=AT/validityPeriod: 'Certificate will expire soon'
INFO CN=My Root,O=Walt.id,C=AT/validityPeriod: 'DONE'
INFO CN=My Root,O=Walt.id,C=AT/basicConstraints: 'DONE'
INFO CN=My Root,O=Walt.id,C=AT/certificateSignature: 'DONE'
WARN CN=My Leaf Certificate,O=Walt.id,C=AT/validityPeriod: 'Certificate will expire soon'
INFO CN=My Leaf Certificate,O=Walt.id,C=AT/validityPeriod: 'DONE'
INFO CN=My Leaf Certificate,O=Walt.id,C=AT/basicConstraints: 'DONE'
INFO CN=My Leaf Certificate,O=Walt.id,C=AT/certificateSignature: '(BouncyCastle) Certificate Signature valid: ecPublicKey / ecdsa-with-SHA256'
INFO CN=My Leaf Certificate,O=Walt.id,C=AT/certificateSignature: 'DONE'
```

## Configure X509CertificateUtil

`X509CertificateUtil.Default` is a ready-to-use singleton configured with sensible platform
defaults - on JVM/Android, that includes the platform's **system CA trust store**. Build a
customized util with `X509CertificateUtil { ... }`, which starts from `Default` and lets you
override its trust store and/or validators:

```kotlin
import id.walt.certificate.x509.X509CertificateUtil
import id.walt.certificate.x509.X509CertificateTrustStore
import id.walt.certificate.x509.truststore.CompositeTrustStore
import id.walt.certificate.x509.truststore.InMemoryTrustStore
import id.walt.certificate.x509.validation.validator.X509CertificateBasicConstraintsValidator

// your own X509CertificateTrustStore implementation, e.g. backed by a database or remote lookup
object MyTrustStore : X509CertificateTrustStore {
    override fun findCertificateBySubjectDn(subjectDn: String): List<X509Certificate> = listOf()
}

val trustAnchors = InMemoryTrustStore(listOf(caCert))

// trust stores can be combined
val combinedTrust = CompositeTrustStore(listOf(MyTrustStore, trustAnchors))

val myUtil = X509CertificateUtil {
    // fully REPLACES the configured trust store - it is not merged with Default's system CA store
    setTrust(combinedTrust)
    // add or override validators, keyed by validator id
    addValidators(X509CertificateBasicConstraintsValidator(leafCanBeCa = true))
}
```

**Trust store scoping.** `setTrust()` and the trust store you pass directly to a validation call
(`validateCertificateChain(chain, trustStore)`, `validatePemCertificateChain(pem, trustStore)`)
behave the same way: they **fully replace** the util's configured trust store for that call, they
are never merged with it. Passing your own anchors on `X509CertificateUtil.Default` therefore
does **not** also trust the platform's system CA store - you get exactly the trust boundary you
asked for. If you want both, compose them explicitly first, e.g.
`CompositeTrustStore(listOf(myAnchors, JavaDefaultTrustStore(...)))`, or `setTrust()` a util
configured with the combined store once and reuse it.

## Loading trust anchors from a JVM KeyStore (JVM helper)

There is no dedicated helper for this in the current API - `X509CertificateTrustStore` is a
small interface, so building an `InMemoryTrustStore` from an existing `java.security.KeyStore` is
a few lines:

```kotlin
import id.walt.certificate.x509.X509CertificateUtil
import id.walt.certificate.x509.truststore.InMemoryTrustStore
import kotlinx.io.bytestring.ByteString
import java.security.KeyStore

fun trustStoreFromKeyStore(ks: KeyStore): InMemoryTrustStore {
    val certs = ks.aliases().asSequence()
        .mapNotNull { alias -> ks.getCertificate(alias) as? java.security.cert.X509Certificate }
        .map { X509CertificateUtil.parseCertificateDerEncoded(ByteString(it.encoded)) }
        .toList()
    return InMemoryTrustStore(certs)
}
```

---

## [ISO/IEC 18013-5](https://github.com/ISOWG10/ISO-18013/blob/main/Working%20Documents/Working%20Draft%20WG%2010_N2549_ISO-IEC%2018013-5-%20Personal%20identification%20%E2%80%94%20ISO-compliant%20driving%20licence%20%E2%80%94%20Part%205-%20Mobile%20driving%20lic.pdf) X.509 certificate tooling (IACA and Document Signer)

> `id.walt.x509.iso` (`IACACertificateBuilder`, `DocumentSignerCertificateBuilder`,
> `IACAValidator`, `DocumentSignerValidator`, `CertificateDer`, ...) is **deprecated**. Use the
> profile helpers in `id.walt.certificate.x509.profile` together with `X509CertificateUtil`,
> documented below, for all new code.

IACA root and Document Signer certificates are built with the same
`X509CertificateUtil.createSelfSignedCertificate`/`createCertificate` calls used for any other
certificate, using profile-specific DSL helpers to fill in the ISO-mandated fields/extensions, and
validated by adding the profile object as a validator on a configured `X509CertificateUtil`.

### IACA root certificate generation and validation

```kotlin
import id.walt.certificate.x509.profile.IsoIaCaRootX509CertificateProfile
import id.walt.certificate.x509.profile.IsoIaCaRootX509CertificateProfile.profileIaCaRootCertificate
import id.walt.certificate.x509.validation.validator.X509CertificateBasicConstraintsValidator
import id.walt.certificate.x509.validation.validator.X509CertificateValidityValidator

// A util for validating a certificate presented as an IACA root, before trusting it as an anchor
val iaCaRootCertUtil = X509CertificateUtil {
    addValidators(
        IsoIaCaRootX509CertificateProfile,
        X509CertificateBasicConstraintsValidator(leafCanBeCa = true),
        X509CertificateValidityValidator(allowValidityInFuture = true)
    )
}

val iacaKey = cryptoRuntime.generateSoftwareKey(keyGen)
val iacaRoot = X509CertificateUtil.createSelfSignedCertificate(iacaKey, certSigningAlg) {
    profileIaCaRootCertificate(
        issuerDnCountryCode = "AT",
        issuerDnOrganizationName = "Walt.id",
        issuerDnCommonName = "Walt ID IACA Root",
        issuerEmailAddress = "office@walt.id",
    )
}

val rootValidationResult = iaCaRootCertUtil.validateCertificateChain(listOf(iacaRoot), iacaRoot)
check(rootValidationResult.valid) { "Not a valid IACA root: ${rootValidationResult.log}" }
```

`profileIaCaRootCertificate` fills in the mandatory subject DN fields, sets `basicConstraints`
(`CA=true`, `pathLenConstraint=0`, critical), `keyUsage` (`keyCertSign` + `cRLSign`, critical), and
the mandatory `issuerAlternativeName` extension - all the ISO-mandated fields you'd otherwise have
to set by hand. `IsoIaCaRootX509CertificateProfile` then re-validates all of that on demand.

### Document Signer certificate generation and validation

```kotlin
import id.walt.certificate.x509.profile.IsoDocumentSignerX509CertificateProfile
import id.walt.certificate.x509.profile.IsoDocumentSignerX509CertificateProfile.profileDocumentSignerCertificate

val documentSignerCertUtil = X509CertificateUtil {
    addValidators(
        IsoDocumentSignerX509CertificateProfile,
        X509CertificateValidityValidator(allowValidityInFuture = true)
    )
}

val documentSignerKey = cryptoRuntime.generateSoftwareKey(keyGen)
val documentSignerCert = X509CertificateUtil.createCertificate(iacaKey, iacaRoot, certSigningAlg) {
    profileDocumentSignerCertificate(
        crlDistributionPointUri = "https://crl.walt.id/crl.der",
        issuerEmailAddress = "office@walt.id",
        subjectKey = documentSignerKey,
        subjectDnCountryCode = "AT",
        subjectDnOrganizationName = "Walt.id",
        subjectDnCommonName = "Walt ID mDL DS",
    )
}

val dsValidationResult = documentSignerCertUtil.validateCertificateChain(listOf(documentSignerCert), iacaRoot)
check(dsValidationResult.valid) { "Not a valid Document Signer certificate: ${dsValidationResult.log}" }
```

> **Always validate a caller-supplied "root" against `IsoIaCaRootX509CertificateProfile` before
> trusting it as a signing anchor.** A Document Signer certificate being profile-compliant and
> correctly signed by a given key does not, by itself, guarantee that key's certificate is a valid
> IACA root - it could be missing `CA=true`, have the wrong `pathLenConstraint`, or lack the
> mandatory issuer-alt-name extension. Skipping the root validation step above would let anyone who
> controls a certificate's private key get a Document Signer certificate issued "under" it,
> regardless of whether that certificate was ever a legitimate IACA root. See
> `IsoMdlOnboardingExample.kt` in [waltid-examples](https://github.com/walt-id/waltid-examples/tree/main/src/main/kotlin/x509)
> for a runnable demonstration, including a rejected non-compliant root.

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
  - Certificate revocation checks (CRL/OCSP) are not currently supported by
    `X509CertificateChainValidator`.
  - Load anchors from a `java.security.KeyStore` with a few lines of your own code - see
    [Loading trust anchors from a JVM KeyStore](#loading-trust-anchors-from-a-jvm-keystore-jvm-helper).

- **iOS**
  - Supports explicit trust anchors and trusted-chain-root validation.
  - System trust anchors and revocation checks are not supported yet.

- **JavaScript (planned)**
  - WebCrypto does not expose a PKIX path builder; integrate a JS PKI lib or a WASM backend.
  - Current actual throws `X509ValidationException("Not implemented on JS yet")`.

---

## Best practices

- Prefer known trust anchors (system/org CA store) for public PKI.
- Use pinned roots from `x5c` only for explicit trust scenarios (private PKI / trusted issuer).
- Passing a trust store to `setTrust()` or directly to a validation call **replaces** the
  configured trust store for that scope, it is never merged with it (see
  [Configure X509CertificateUtil](#configure-x509certificateutil)) - don't assume anchors you pass
  to `X509CertificateUtil.Default` are being added on top of the platform's system CA store.
- For [ISO/IEC 18013-5](https://github.com/ISOWG10/ISO-18013/blob/main/Working%20Documents/Working%20Draft%20WG%2010_N2549_ISO-IEC%2018013-5-%20Personal%20identification%20%E2%80%94%20ISO-compliant%20driving%20licence%20%E2%80%94%20Part%205-%20Mobile%20driving%20lic.pdf) X.509 certificates, **always** validate IACA and Document Signer certificates - including a
  caller-supplied IACA root against `IsoIaCaRootX509CertificateProfile` before trusting it as a
  signing anchor, not just the certificate it signs.

---

## Examples

Runnable, standalone examples live in [waltid-examples](https://github.com/walt-id/waltid-examples/tree/main/src/main/kotlin/x509):

- [`SignCertificateExample.kt`](https://github.com/walt-id/waltid-examples/blob/main/src/main/kotlin/x509/SignCertificateExample.kt) - create a self-signed root, sign a leaf certificate, validate the chain.
- [`ConfigureTrustStoreExample.kt`](https://github.com/walt-id/waltid-examples/blob/main/src/main/kotlin/x509/ConfigureTrustStoreExample.kt) - combine trust stores, configure a custom `X509CertificateUtil`, and see how trust store scoping behaves.
- [`IsoMdlOnboardingExample.kt`](https://github.com/walt-id/waltid-examples/blob/main/src/main/kotlin/x509/IsoMdlOnboardingExample.kt) - build an ISO/IEC 18013-5 IACA root and Document Signer certificate, and see a non-compliant root get rejected.

```bash
git clone https://github.com/walt-id/waltid-examples.git
cd waltid-examples
./gradlew run -PmainClass=x509.SignCertificateExampleKt
```

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
