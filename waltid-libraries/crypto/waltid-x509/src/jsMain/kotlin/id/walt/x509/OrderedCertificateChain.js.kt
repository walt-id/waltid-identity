package id.walt.x509

import kotlin.time.Instant

internal actual class PlatformX509Certificate private constructor() {
    actual val subjectKeyIdentifier: ByteArray?
        get() = unsupported()

    actual val authorityKeyIdentifier: ByteArray?
        get() = unsupported()

    actual fun hasIssuerNameMatching(issuer: PlatformX509Certificate): Boolean = unsupported()

    actual fun verifySignedBy(issuer: PlatformX509Certificate): Unit = unsupported()

    actual fun isSelfSigned(): Boolean = unsupported()

    actual fun checkValidityAt(instant: Instant): Unit = unsupported()

    actual companion object {
        actual fun parse(der: CertificateDer): PlatformX509Certificate = unsupported()
    }
}

private fun unsupported(): Nothing =
    throw UnsupportedOperationException("Ordered X.509 certificate chain verification is not supported on JS")
