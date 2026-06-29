package id.walt.x509

internal actual class PlatformX509Certificate private constructor() {
    actual val subjectKeyIdentifier: ByteArray?
        get() = unsupported()

    actual val authorityKeyIdentifier: ByteArray?
        get() = unsupported()

    actual fun hasIssuerNameMatching(issuer: PlatformX509Certificate): Boolean = unsupported()

    actual fun verifySignedBy(issuer: PlatformX509Certificate): Unit = unsupported()

    actual companion object {
        actual fun parse(der: CertificateDer): PlatformX509Certificate = unsupported()
    }
}

private fun unsupported(): Nothing =
    throw UnsupportedOperationException("Ordered X.509 certificate chain verification is not supported on JS")
