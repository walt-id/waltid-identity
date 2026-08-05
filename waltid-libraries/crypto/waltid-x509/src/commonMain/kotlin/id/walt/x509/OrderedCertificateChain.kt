package id.walt.x509

import id.walt.x509.iso.DocumentSignerEkuOID
import kotlin.time.Clock
import kotlin.time.Instant

/**
 * Verifies an ordered X.509 certificate chain.
 *
 * The first certificate is expected to be signed by the second, the second by
 * the third, and so on. This validates adjacent issuer/subject linkage,
 * certificate signatures, and AKI/SKI linkage when both extensions are present.
 *
 * This is intentionally narrower than [validateCertificateChain]: it does not
 * build a PKIX path, check trust anchors, or consult platform/system trust.
 */
@Throws(X509ValidationException::class)
fun verifyOrderedCertificateChainSignatures(chain: List<CertificateDer>) {
    if (chain.size <= 1) return

    val certificates = chain.mapIndexed { index, der ->
        runCatching { PlatformX509Certificate.parse(der) }
            .getOrElse { cause ->
                throw X509ValidationException(
                    "Certificate chain validation failed at position $index: invalid X.509 DER certificate: ${cause.message}",
                    cause,
                )
            }
    }

    for (index in 0 until certificates.size - 1) {
        val subject = certificates[index]
        val issuer = certificates[index + 1]

        if (!subject.hasIssuerNameMatching(issuer)) {
            throw X509ValidationException(
                "Certificate chain issuer/subject mismatch at position $index: certificate issuer does not match next certificate subject"
            )
        }

        runCatching { subject.verifySignedBy(issuer) }
            .getOrElse { cause ->
                throw X509ValidationException(
                    "Certificate chain validation failed at position $index: certificate is not signed by the next certificate: ${cause.message}",
                    cause,
                )
            }

        val subjectAki = subject.authorityKeyIdentifier
        val issuerSki = issuer.subjectKeyIdentifier
        if (subjectAki != null && issuerSki != null && !subjectAki.contentEquals(issuerSki)) {
            throw X509ValidationException(
                "Certificate chain AKI/SKI mismatch at position $index: authority key identifier does not match issuer subject key identifier"
            )
        }
    }
}

/**
 * Parsed Subject Key Identifier extension value as raw bytes, or null when absent.
 */
val CertificateDer.subjectKeyIdentifier: ByteArray?
    get() = PlatformX509Certificate.parse(this).subjectKeyIdentifier

/**
 * Parsed Authority Key Identifier extension value as raw bytes, or null when absent.
 */
val CertificateDer.authorityKeyIdentifier: ByteArray?
    get() = PlatformX509Certificate.parse(this).authorityKeyIdentifier

/**
 * Parsed dNSName entries from the Subject Alternative Name extension.
 */
val CertificateDer.subjectAlternativeDnsNames: List<String>
    get() = PlatformX509Certificate.parse(this).subjectAlternativeDnsNames

internal expect class PlatformX509Certificate {
    val subjectKeyIdentifier: ByteArray?
    val authorityKeyIdentifier: ByteArray?
    val subjectAlternativeDnsNames: List<String>
    val isCertificateAuthority: Boolean
    val pathLengthConstraint: Int?
    val canSignCertificates: Boolean
    val canSignData: Boolean
    val extendedKeyUsageOids: Set<String>?
    val basicConstraintsCritical: Boolean
    val keyUsageCritical: Boolean
    val criticalExtensionOids: Set<String>

    fun hasIssuerNameMatching(issuer: PlatformX509Certificate): Boolean
    fun verifySignedBy(issuer: PlatformX509Certificate)
    fun isSelfSigned(): Boolean
    fun checkValidityAt(instant: Instant)

    companion object {
        fun parse(der: CertificateDer): PlatformX509Certificate
    }
}

fun CertificateDer.validateClientAuthenticationCertificateUsage() {
    val certificate = PlatformX509Certificate.parse(this)
    require(!certificate.isCertificateAuthority) { "Client authentication certificate must not be a CA" }
    require(certificate.canSignData) { "Client authentication certificate must permit digitalSignature" }
    certificate.extendedKeyUsageOids?.let { usages ->
        require(CLIENT_AUTH_EXTENDED_KEY_USAGE_OID in usages) {
            "Client authentication certificate extended key usage must permit clientAuth"
        }
    }
}

fun CertificateDer.validateDocumentSigningCertificateUsage(instant: Instant = Clock.System.now()) {
    val certificate = PlatformX509Certificate.parse(this)
    certificate.checkValidityAt(instant)
    require(!certificate.isCertificateAuthority) { "Document signer certificate must not be a CA" }
    require(certificate.canSignData) { "Document signer certificate must permit digitalSignature" }
    certificate.extendedKeyUsageOids?.let { usages ->
        require(DocumentSignerEkuOID in usages) {
            "Document signer certificate extended key usage must contain $DocumentSignerEkuOID"
        }
    }
}

fun CertificateDer.validateCertificateAuthorityUsage(instant: Instant = Clock.System.now()) {
    val certificate = PlatformX509Certificate.parse(this)
    certificate.checkValidityAt(instant)
    require(certificate.isCertificateAuthority) { "Issuer certificate must be a CA" }
    require(certificate.canSignCertificates) { "Issuer certificate must permit keyCertSign" }
}

private const val CLIENT_AUTH_EXTENDED_KEY_USAGE_OID = "1.3.6.1.5.5.7.3.2"
