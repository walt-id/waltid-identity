package id.walt.x509

import id.walt.certificate.x509.X509CertificateUtil
import id.walt.certificate.x509.extension.AuthorityKeyIdentifierExtension.Companion.extensionAuthorityKeyIdentifier
import id.walt.certificate.x509.extension.SubjectKeyIdentifierExtension.Companion.extensionSubjectKeyIdentifier
import java.io.ByteArrayInputStream
import java.security.cert.CertificateFactory
import java.security.cert.X509Certificate
import java.util.Date
import kotlin.time.Instant

internal actual class PlatformX509Certificate private constructor(
    der: CertificateDer,
    private val certificate: X509Certificate,
) {
    private val parsed = X509CertificateUtil.parseCertificateDerEncoded(der.bytes)

    actual val subjectKeyIdentifier: ByteArray?
        get() = parsed.data.extensionSubjectKeyIdentifier?.keyIdentifier?.toByteArray()
    actual val authorityKeyIdentifier: ByteArray?
        get() = parsed.data.extensionAuthorityKeyIdentifier?.keyIdentifier?.toByteArray()
    actual val subjectAlternativeDnsNames: List<String>
        get() = certificate.subjectAlternativeNames.orEmpty()
            .filter { san -> san.size == 2 && san[0] == 2 }
            .map { san -> san[1].toString() }
    actual val isCertificateAuthority: Boolean
        get() = certificate.basicConstraints >= 0
    actual val pathLengthConstraint: Int?
        get() = certificate.basicConstraints.takeIf { it >= 0 && it != Int.MAX_VALUE }
    actual val canSignCertificates: Boolean
        get() = certificate.keyUsage?.getOrNull(5) == true
    actual val canSignData: Boolean
        get() = certificate.keyUsage?.getOrNull(0) == true
    actual val extendedKeyUsageOids: Set<String>?
        get() = certificate.extendedKeyUsage?.toSet()
    actual val basicConstraintsCritical: Boolean
        get() = certificate.criticalExtensionOIDs?.contains(BASIC_CONSTRAINTS_OID) == true
    actual val keyUsageCritical: Boolean
        get() = certificate.criticalExtensionOIDs?.contains(KEY_USAGE_OID) == true
    actual val criticalExtensionOids: Set<String>
        get() = certificate.criticalExtensionOIDs.orEmpty()

    actual fun hasIssuerNameMatching(issuer: PlatformX509Certificate): Boolean =
        certificate.issuerX500Principal == issuer.certificate.subjectX500Principal

    actual fun verifySignedBy(issuer: PlatformX509Certificate) {
        certificate.verify(issuer.certificate.publicKey)
    }

    actual fun isSelfSigned(): Boolean = runCatching {
        certificate.issuerX500Principal == certificate.subjectX500Principal &&
            certificate.verify(certificate.publicKey).let { true }
    }.getOrDefault(false)

    actual fun checkValidityAt(instant: Instant) {
        certificate.checkValidity(Date(instant.toEpochMilliseconds()))
    }

    actual companion object {
        actual fun parse(der: CertificateDer): PlatformX509Certificate = PlatformX509Certificate(
            der = der,
            certificate = CertificateFactory.getInstance("X.509")
                .generateCertificate(ByteArrayInputStream(der.bytes.toByteArray())) as X509Certificate,
        )

        private const val BASIC_CONSTRAINTS_OID = "2.5.29.19"
        private const val KEY_USAGE_OID = "2.5.29.15"
    }
}
