package id.walt.certificate.x509.profile

import id.walt.certificate.x509.X509Certificate
import id.walt.certificate.x509.profile.IsoDocumentSignerX509CertificateProfile.allowedSignatureAlgorithmsOid
import id.walt.certificate.x509.validation.ValidationContext
import id.walt.certificate.x509.validation.ValidationResult
import kotlin.experimental.and
import kotlin.time.Duration

object IsoProfileX509CertificateValidationUtil {

    /**
     * Shall be v3
     */
    fun validateVersion(
        context: ValidationContext,
        x509Certificate: X509Certificate
    ) {
        if (x509Certificate.data.version != 3) {
            context.addLogEntry(
                ValidationResult.Severity.ERROR,
                "Expected version to be '3' but was '${x509Certificate.data.version}'"
            )
        }
    }

    /**
     * Non-sequential positive, non-zero integer, shall contain at least 63
     * bits of output from a CSPRNG, should contain at least 71 bits of
     * output from a CSPRNG, maximum 20 octets.
     */
    fun validateSerialNumber(
        context: ValidationContext,
        x509Certificate: X509Certificate
    ) {
        val raw = x509Certificate.data.serialNumberRaw
        if (raw.size < 9) {
            //less than 64 bits
            context.addLogEntry(
                ValidationResult.Severity.ERROR,
                "serialNumber",
                "Serial number must have at least 71 bits"
            )
        } else if (raw.size == 9 && (raw[0] and 0x7f).toInt() < 0x40) {
            //less than 71 bits
            context.addLogEntry(
                ValidationResult.Severity.ERROR,
                "serialNumber",
                "Serial number must have at least 71 bits"
            )
        } else if (raw.size > 20) {
            context.addLogEntry(
                ValidationResult.Severity.ERROR,
                "serialNumber",
                "Serial number must have maximum 20 octets, but has ${raw.size}"
            )
        }
        if (raw.size > 0 && raw[0] < 0) {
            context.addLogEntry(
                ValidationResult.Severity.ERROR,
                "serialNumber",
                "Serial number must be positive"
            )
        }
    }

    /**
     * Value shall match the OID in the signature algorithm:
     * Options:
     * 1.2.840.10045.4.3.2 (ECDSA-with SHA256)
     * 1.2.840.10045.4.3.3 (ECDSA-with SHA384)
     * 1.2.840.10045.4.3.4 (ECDSA with SHA512)
     */
    fun validateSignatureAlgorithm(
        context: ValidationContext,
        x509Certificate: X509Certificate
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