package id.walt.certificate.x509.profile

import id.walt.certificate.x509.X509Certificate
import id.walt.certificate.x509.validation.ValidationContext
import id.walt.certificate.x509.validation.ValidationResult
import kotlin.time.Duration

object IsoProfileX509CertificateValidationUtil {

    /**
     * Value shall match the OID in the signature algorithm:
     * Options:
     * 1.2.840.10045.4.3.2 (ECDSA-with SHA256)
     * 1.2.840.10045.4.3.3 (ECDSA-with SHA384)
     * 1.2.840.10045.4.3.4 (ECDSA with SHA512)
     */
    fun validateSignatureAlgorithm(
        context: ValidationContext,
        x509Certificate: X509Certificate,
        allowedSignatureAlgorithmsOid: Set<String>
    ) {
        if (!allowedSignatureAlgorithmsOid.contains(x509Certificate.signatureAlgorithmOid)) {
            context.addLogEntry(
                ValidationResult.Severity.ERROR,
                "signatureAlgorithm",
                "Expected signature algorithm  to be one of ${allowedSignatureAlgorithmsOid} but was " +
                        "'${x509Certificate.signatureAlgorithmOid}' (${x509Certificate.signatureAlgorithmName})"
            )
        }

    }

    fun validateValidityTime(
        context: ValidationContext,
        x509Certificate: X509Certificate,
        maxValidityTime: Duration
    ) {
        val validityPeriod = x509Certificate.data.validity.notAfter - x509Certificate.data.validity.notBefore
        if (validityPeriod.isNegative() || validityPeriod == Duration.ZERO) {
            context.addLogEntry(
                ValidationResult.Severity.ERROR,
                "validityTime",
                "Validity time must be positive"
            )
        } else if (validityPeriod > maxValidityTime) {
            context.addLogEntry(
                ValidationResult.Severity.ERROR,
                "validityTime",
                "Validity time must be less than ${maxValidityTime}"
            )
        }
    }
}