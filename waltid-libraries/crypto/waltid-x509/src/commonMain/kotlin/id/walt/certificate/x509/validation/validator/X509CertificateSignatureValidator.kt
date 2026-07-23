package id.walt.certificate.x509.validation.validator

import id.walt.certificate.x509.SignatureValidator
import id.walt.certificate.x509.X509Certificate
import id.walt.certificate.x509.validation.ValidationContext
import id.walt.certificate.x509.validation.ValidationResult

class X509CertificateSignatureValidator(
    private val signatureValidator: SignatureValidator
) : X509CertificateValidator {

    override val id: String = ID

    override suspend fun validate(
        context: ValidationContext,
        x509Certificate: X509Certificate
    ) {

        val trustedIssuerCertificates = context.findCertificateBySubjectDn(x509Certificate.data.issuerDn)
        if (trustedIssuerCertificates.isEmpty()) {
            context.addLogEntry(
                ValidationResult.Severity.ERROR,
                "Trusted issuer certificate '${x509Certificate.data.issuerDn}' not found"
            )
        } else {
            require(trustedIssuerCertificates.size == 1) { "Multiple certificates with subjectDn '${x509Certificate.data.issuerDn}' in truststore. Select CA certificate by pubic key not yet supported" }
            validateCertificate(context, trustedIssuerCertificates.first(), x509Certificate)
        }
    }

    private suspend fun validateCertificate(
        context: ValidationContext,
        issuerCertificate: X509Certificate,
        certificate: X509Certificate
    ) {

        if (issuerCertificate.data.subjectDn == certificate.data.subjectDn) {
            context.addLogEntry(
                ValidationResult.Severity.ERROR,
                "Serial number of trusted self signed certificate '${issuerCertificate.data.serialNumberHex}' " +
                        "not equal to serial number '${certificate.data.serialNumberHex}'"
            )
        } else {
            if (signatureValidator.validateCertificateSignature(
                    issuerCertificate.data.subjectPublicKeyInfo,
                    certificate
                )
            ) {
                context.addLogEntry(ValidationResult.Severity.INFO, "OK")
                context.addTrustedCertificate(certificate)
            } else {
                context.addLogEntry(ValidationResult.Severity.ERROR, "Certificate Signature not valid")
            }
        }
    }

    companion object {
        const val ID = "certificateSignature"
    }
}