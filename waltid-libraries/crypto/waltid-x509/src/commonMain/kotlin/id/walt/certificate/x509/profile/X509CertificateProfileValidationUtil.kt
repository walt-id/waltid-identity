package id.walt.certificate.x509.profile

import id.walt.certificate.x509.X509Certificate
import id.walt.certificate.x509.extension.BasicConstraintsExtension.Companion.extensionBasicConstraints
import id.walt.certificate.x509.extension.KeyUsageExtension
import id.walt.certificate.x509.extension.KeyUsageExtension.Companion.extensionKeyUsage
import id.walt.certificate.x509.extension.SubjectKeyIdentifierExtension.Companion.extensionSubjectKeyIdentifier
import id.walt.certificate.x509.validation.ValidationContext
import id.walt.certificate.x509.validation.ValidationResult
import kotlinx.io.bytestring.isEmpty
import kotlin.experimental.and

object X509CertificateProfileValidationUtil {

    /**
     * Shall be v3
     */
    fun validateVersionV3(
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
        if (raw.size < 8) {
            //less than 52 bits
            context.addLogEntry(
                ValidationResult.Severity.ERROR,
                "serialNumber",
                "Serial number must have at least 63 bits"
            )
        } else if (raw.size == 8 && (raw[0] and 0x7f).toInt() < 0x40) {
            //less than 63 bits
            context.addLogEntry(
                ValidationResult.Severity.ERROR,
                "serialNumber",
                "Serial number must have at least 63 bits"
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


    fun validatePositiveValidity(context: ValidationContext, x509Certificate: X509Certificate) {
        val validity = x509Certificate.data.validity
        if ((validity.notAfter - validity.notBefore).isNegative()) {
            context.addLogEntry(ValidationResult.Severity.ERROR, "validityTime", "Validity time must be positive")
        }
    }

    fun validateNotSelfSigned(context: ValidationContext, x509Certificate: X509Certificate) {
        if (x509Certificate.data.issuerDn == x509Certificate.data.subjectDn) {
            context.addLogEntry(ValidationResult.Severity.ERROR, "issuerDn", "Certificate must not be self-signed")
        }
    }

    fun validateBasicConstraintIsEndEntity(context: ValidationContext, x509Certificate: X509Certificate) {
        val extension = x509Certificate.data.extensionBasicConstraints
        if (extension == null) {
            context.addLogEntry(
                ValidationResult.Severity.ERROR,
                "basicConstraints",
                "Certificate extension 'basicConstraints' is not present"
            )
        } else {
            if (!extension.critical) {
                context.addLogEntry(
                    ValidationResult.Severity.ERROR,
                    "basicConstraints",
                    "Certificate extension 'basicConstraints' must have a critical flag set"
                )
            }
            if (extension.cA) {
                context.addLogEntry(
                    ValidationResult.Severity.ERROR,
                    "basicConstraints",
                    "Certificate must be an end-entity certificate (cA=FALSE)"
                )
            }
        }
    }

    fun validateKeyUsageIsDigitalSignature(context: ValidationContext, x509Certificate: X509Certificate) {
        val extension = x509Certificate.data.extensionKeyUsage
        if (extension == null) {
            context.addLogEntry(
                ValidationResult.Severity.ERROR,
                "keyUsage",
                "Certificate extension 'keyUsage' is not present"
            )
        } else {
            if (!extension.critical) {
                context.addLogEntry(
                    ValidationResult.Severity.ERROR,
                    "keyUsage",
                    "Certificate extension 'keyUsage' must have a critical flag set"
                )
            }
            if (extension.keyPurposeIdList != setOf(KeyUsageExtension.KeyUsage.digitalSignature)) {
                context.addLogEntry(
                    ValidationResult.Severity.ERROR,
                    "keyUsage",
                    "Certificate extension 'keyUsage' requires only 'digitalSignature' flag set, but has ${extension.keyPurposeIdList}"
                )
            }
        }
    }

    fun validateSubjectKeyIdentifierIsPresent(context: ValidationContext, x509Certificate: X509Certificate) {
        val extension = x509Certificate.data.extensionSubjectKeyIdentifier
        if (extension == null || extension.keyIdentifier.isEmpty()) {
            context.addLogEntry(
                ValidationResult.Severity.ERROR,
                "subjectKeyIdentifier",
                "Certificate extension 'subjectKeyIdentifier' is not present"
            )
        }
    }

    fun validateExtensionsAreNotCritical(
        context: ValidationContext,
        x509Certificate: X509Certificate,
        criticalExtensions: Set<String>
    ) {
        x509Certificate.data.extensions.forEach {
            if (it.value.critical && !criticalExtensions.contains(it.key)) {
                context.addLogEntry(
                    ValidationResult.Severity.ERROR,
                    "extensionNotCritical",
                    "Extension '${it.key}' is critical"
                )
            }
        }
    }
}