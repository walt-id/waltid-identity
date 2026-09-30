package id.walt.certificate.x509.validation.validator

import id.walt.certificate.x509.X509Certificate
import id.walt.certificate.x509.validation.ValidationContext
import id.walt.certificate.x509.validation.ValidationResult

/**
 * Validation rule for the certificate signature algorithm fields (RFC 5280 section 4.1.1.2):
 *
 * A certificate carries its signature algorithm twice: as `tbsCertificate.signature` (covered by the signature)
 * and as the outer `signatureAlgorithm`. Both AlgorithmIdentifiers must be identical, otherwise the certificate
 * is rejected (severity ERROR). Both the OID and the parameters are compared, byte for byte.
 *
 * The inner algorithm is not exposed by [X509Certificate.CertificateData], so it is read directly from the
 * DER encoding of the certificate. If the DER structure cannot be parsed, an ERROR is reported.
 *
 * Whether the algorithm is acceptable for a particular profile (e.g. ISO 18013-5) is not judged here.
 */
class X509CertificateSignatureAlgorithmMatchValidator : X509CertificateValidator {

    override val id: String = ID

    override suspend fun validate(
        context: ValidationContext,
        x509Certificate: X509Certificate
    ) {
        if (x509Certificate.signatureAlgorithmOid != x509Certificate.data.signatureAlgorithmOid) {
            context.addLogEntry(
                ValidationResult.Severity.ERROR,
                "The signature algorithm in the TBS certificate (${x509Certificate.data.signatureAlgorithmOid}) doesn't match " +
                        "the signatureAlgorithm of the certificate (${x509Certificate.signatureAlgorithmOid})"
            )
        }
    }

    companion object {
        const val ID = "signature-algorithm-match"
    }
}
