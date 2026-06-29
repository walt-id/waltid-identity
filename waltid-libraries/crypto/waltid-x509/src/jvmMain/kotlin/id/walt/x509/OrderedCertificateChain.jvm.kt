package id.walt.x509

import java.security.cert.X509Certificate

internal actual class PlatformX509Certificate private constructor(
    private val certificate: X509Certificate,
) {
    actual val subjectKeyIdentifier: ByteArray?
        get() = certificate.subjectKeyIdentifier?.toByteArray()

    actual val authorityKeyIdentifier: ByteArray?
        get() = certificate.authorityKeyIdentifier?.toByteArray()

    actual fun hasIssuerNameMatching(issuer: PlatformX509Certificate): Boolean =
        certificate.issuerX500Principal == issuer.certificate.subjectX500Principal

    actual fun verifySignedBy(issuer: PlatformX509Certificate) {
        certificate.verify(issuer.certificate.publicKey)
    }

    actual companion object {
        actual fun parse(der: CertificateDer): PlatformX509Certificate =
            PlatformX509Certificate(der.toJcaX509Certificate())
    }
}
