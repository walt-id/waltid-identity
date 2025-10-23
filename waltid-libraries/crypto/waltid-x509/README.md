# walt.id X.509 (Kotlin Multiplatform)

A tiny, pragmatic **Kotlin Multiplatform** library for working with **X.509 certificates** across JVM/Android, iOS, and JS.  
It focuses on developer-friendly APIs for parsing `x5c` JWT headers and validating a leaf certificate against a provided chain and trust anchors—using platform-native validation where possible.

---

## Features

- **KMP-first API**: common `expect/actual` with a consistent developer experience.
- **PKIX validation (JVM/Android)**: order-independent path building & validation using the platform PKI.
- **Pluggable trust model**: validate against:
  - your **organization trust store** (recommended), or
  - an explicit **pinned root** included in the `x5c` chain (private PKI / pinning).
- **Clear exceptions**: failures raise `X509ValidationException` with context.

---

## Targets

- **JVM / Android**: Full implementation via `PKIX` path builder/validator.
- **iOS**: Stub provided; intended to use `SecTrust` in a follow-up implementation.
- **JS**: Stub provided; intended to integrate with a JS PKI lib or Web APIs.

> If you only need JVM/Android today, you can use it immediately. iOS/JS will throw `X509ValidationException("Not implemented…")` until completed.

---

## Minimal API surface

> Namespaces may differ slightly in your repo; adjust imports to your package.

```kotlin
// Common API (expect)
data class CertificateDer(val bytes: ByteArray)

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

---

## 📦 Installation

Add the module as a dependency to your multiplatform project:

```kotlin
// build.gradle.kts

implementation("id.walt:waltid-x509:<version>") //when published

OR

include(":waltid-libraries:crypto:waltid-x509")  // if used as a composite build/module

```

---

## Quick start (JVM/Android)

```kotlin
import waltid.x509.* // adjust to your actual package name
import java.util.Base64

fun validateFromX5cExample(
    x5cBase64: List<String>,             // JWT header "x5c": Base64 DER certs
    trustAnchorsDer: List<ByteArray>?,   // null = use self-signed root from chain (pinning/private PKI)
    enableRevocation: Boolean = false
) {
    // 1) Parse the x5c array into DER bytes (platform-agnostic wrapper)
    val chain = x5cBase64.map { CertificateDer(Base64.getDecoder().decode(it)) }

    // 2) The leaf is usually the first element in x5c (but you can pick explicitly)
    val leaf = chain.first()

    // 3) Convert DER roots (if you use a custom trust store)
    val anchors = trustAnchorsDer?.map { CertificateDer(it) }

    // 4) Validate: leaf -> intermediates -> trust anchors
    validateCertificateChain(
        leaf = leaf,
        chain = chain,
        trustAnchors = anchors,
        enableTrustedChainRoot = anchors.isNullOrEmpty(),
        enableSystemTrustAnchors = false,
        enableRevocation = enableRevocation
    )
    // If no exception is thrown, validation succeeded.
}
```

### Loading trust anchors from a JVM KeyStore (JVM helper)

```kotlin
import waltid.x509.*
import java.security.KeyStore

fun anchorsFromKeyStore(ks: KeyStore): List<CertificateDer> {
    // Helper is JVM-only
    return loadTrustAnchorsFromKeyStore(ks)
}
```

---

JVM extras:

```kotlin
// JVM-only helper to turn a KeyStore into DER roots (TrustAnchors)
fun loadTrustAnchorsFromKeyStore(ks: KeyStore): List<CertificateDer>
```

---

## 📱 Platform notes

- **JVM / Android**
  - Uses `PKIX` builder/validator. Order of `chain` doesn’t matter.
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
  - WebCrypto doesn’t expose a PKIX path builder; integrate a JS PKI lib or a WASM backend.
  - Current actual throws `X509ValidationException("Not implemented on JS yet")`.

---

## Best practices

- Prefer **known trust anchors** (system/org CA store) for public PKI.
- Use pinned roots from `x5c` only for **explicit trust** scenarios (private PKI / trusted issuer).
- Consider checking **key usage / EKU / policies** as required by your application.


---

## License

Apache 2.0 (same as the rest of walt.id unless otherwise noted). See `LICENSE`.
