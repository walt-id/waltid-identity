package id.walt.x509

import kotlin.time.Instant

internal actual class PlatformX509Certificate {
    actual val subjectKeyIdentifier: ByteArray?
        get() = TODO("Not yet implemented")
    actual val authorityKeyIdentifier: ByteArray?
        get() = TODO("Not yet implemented")
    actual val subjectAlternativeDnsNames: List<String>
        get() = TODO("Not yet implemented")
    actual val isCertificateAuthority: Boolean
        get() = TODO("Not yet implemented")
    actual val pathLengthConstraint: Int?
        get() = TODO("Not yet implemented")
    actual val canSignCertificates: Boolean
        get() = TODO("Not yet implemented")
    actual val canSignData: Boolean
        get() = TODO("Not yet implemented")
    actual val extendedKeyUsageOids: Set<String>?
        get() = TODO("Not yet implemented")
    actual val basicConstraintsCritical: Boolean
        get() = TODO("Not yet implemented")
    actual val keyUsageCritical: Boolean
        get() = TODO("Not yet implemented")
    actual val criticalExtensionOids: Set<String>
        get() = TODO("Not yet implemented")

    actual fun hasIssuerNameMatching(issuer: PlatformX509Certificate): Boolean {
        TODO("Not yet implemented")
    }

    actual fun verifySignedBy(issuer: PlatformX509Certificate) {
    }

    actual fun isSelfSigned(): Boolean {
        TODO("Not yet implemented")
    }

    actual fun checkValidityAt(instant: Instant) {
    }

    actual companion object {
        actual fun parse(der: CertificateDer): PlatformX509Certificate {
            TODO("Not yet implemented")
        }
    }
}