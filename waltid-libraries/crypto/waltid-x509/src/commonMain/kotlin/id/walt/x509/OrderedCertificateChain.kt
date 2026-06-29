package id.walt.x509

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

internal expect class PlatformX509Certificate {
    val subjectKeyIdentifier: ByteArray?
    val authorityKeyIdentifier: ByteArray?

    fun hasIssuerNameMatching(issuer: PlatformX509Certificate): Boolean
    fun verifySignedBy(issuer: PlatformX509Certificate)
    fun isSelfSigned(): Boolean
    fun checkValidityAt(instant: Instant)

    companion object {
        fun parse(der: CertificateDer): PlatformX509Certificate
    }
}
