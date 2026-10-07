<div align="center">
  <h1>Kotlin Multiplatform mdoc reader trust library</h1>
  <p>by <a href="https://walt.id">walt.id</a></p>
  <p>Transport-independent trust evaluation for ISO mdoc reader authentication.</p>
</div>

## Overview

`waltid-mdoc-reader-trust` decides whether an mdoc reader whose `ReaderAuth` signature has already
been verified is trusted by the holder. It is shared by every presentation path that carries ISO
18013-5 reader authentication — proximity (ISO/IEC 18013-5) and Digital Credentials API retrieval
(ISO/IEC 18013-7 Annex C) — so one holder-configured trust policy yields the same decision on both.

ISO/IEC 18013-7 §6.4.4 requires the mdoc reader authentication certificate to be signed by a
certificate authority trusted by the mdoc for this purpose, and every certificate issued by a CA to
be validated according to ISO/IEC 18013-5. A reader-supplied certificate chain is a path input only
and never becomes an implicit trust anchor.

## Getting started

Within the coordinated source build, add the module to the consuming source set:

```kotlin
commonMain.dependencies {
    implementation(project(":waltid-libraries:credentials:waltid-mdoc-reader-trust"))
}
```
