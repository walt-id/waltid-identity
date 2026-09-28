package id.walt.certificate.x509.validation.validator

import id.walt.certificate.x509.X509Certificate
import id.walt.certificate.x509.extension.BasicConstraintsExtension
import id.walt.certificate.x509.extension.BasicConstraintsExtension.Companion.extensionBasicConstraints
import id.walt.certificate.x509.validation.ValidationContext
import id.walt.certificate.x509.validation.ValidationResult

/**
 * Verifies that the certificate is a CA certificate, i.e. that it carries a basic constraints
 * extension with `cA:TRUE`, independent of its position in the chain.
 *
 * In contrast to [X509CertificateBasicConstraintsValidator], which derives the expectation from the
 * position of the certificate in the chain (all but the leaf must be CAs), this validator is meant for
 * certificates that must be a CA by themselves, e.g. a trust anchor or a self-signed root that is
 * validated as a chain of one. It does not evaluate path length constraints.
 *
 * Findings:
 * - ERROR: the basic constraints extension is missing, or `cA` is not set.
 * - WARNING: the basic constraints extension is not marked critical (RFC 5280 section 4.2.1.9 requires
 *   this for CA certificates, but section 6.1.4 does not make it a path validation failure).
 */
class X509CertificateBasicConstraintsIsCaValidator : X509CertificateValidator {

    override val id: String = ID

    override suspend fun validate(
        context: ValidationContext,
        x509Certificate: X509Certificate
    ) {
        val basicConstraints = x509Certificate.data.extensionBasicConstraints
        if (basicConstraints == null) {
            context.addLogEntry(
                ValidationResult.Severity.ERROR,
                "Certificate extension '${BasicConstraintsExtension.OID}' ('${BasicConstraintsExtension.NAME}') is not present, certificate must be a CA"
            )
            return
        }
        if (!basicConstraints.cA) {
            context.addLogEntry(
                ValidationResult.Severity.ERROR,
                "Certificate with subject '${x509Certificate.data.subjectDn}' must have cA flag set"
            )
        }
        if (!basicConstraints.critical) {
            context.addLogEntry(
                ValidationResult.Severity.WARNING,
                "Certificate extension '${BasicConstraintsExtension.OID}' ('${BasicConstraintsExtension.NAME}') must have critical flag set"
            )
        }
    }

    companion object {
        /** Distinct from [X509CertificateBasicConstraintsValidator.ID] so both can be registered together. */
        const val ID = "basicConstraintsIsCa"
    }
}
