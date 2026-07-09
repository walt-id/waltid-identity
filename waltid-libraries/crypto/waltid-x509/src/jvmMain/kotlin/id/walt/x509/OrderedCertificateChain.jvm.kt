package id.walt.x509

import java.time.Instant as JavaInstant
import java.util.Date
import java.security.cert.X509Certificate
import kotlin.time.Instant

internal actual class PlatformX509Certificate private constructor(
    private val certificate: X509Certificate,
) {
    actual val subjectKeyIdentifier: ByteArray?
        get() = certificate.subjectKeyIdentifier?.toByteArray()

    actual val authorityKeyIdentifier: ByteArray?
        get() = certificate.authorityKeyIdentifier?.toByteArray()

    actual val subjectAlternativeDnsNames: List<String>
        get() = certificate.subjectAlternativeNames.orEmpty()
            .filter { san -> san.size == 2 && san[0] == 2 }
            .map { san -> san[1].toString() }

    actual fun hasIssuerNameMatching(issuer: PlatformX509Certificate): Boolean =
        certificate.issuerX500Principal == issuer.certificate.subjectX500Principal

    actual fun verifySignedBy(issuer: PlatformX509Certificate) {
        certificate.verify(issuer.certificate.publicKey)
    }

    actual fun isSelfSigned(): Boolean =
        runCatching {
            certificate.issuerX500Principal == certificate.subjectX500Principal &&
                    certificate.verify(certificate.publicKey).let { true }
        }.getOrDefault(false)

    actual fun checkValidityAt(instant: Instant) {
        certificate.checkValidity(Date.from(JavaInstant.ofEpochMilli(instant.toEpochMilliseconds())))
    }

    actual companion object {
        actual fun parse(der: CertificateDer): PlatformX509Certificate =
            PlatformX509Certificate(der.toJcaX509Certificate())
    }
}
