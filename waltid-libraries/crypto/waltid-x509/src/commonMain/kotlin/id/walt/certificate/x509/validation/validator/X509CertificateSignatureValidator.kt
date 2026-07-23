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
        val found = context.findCertificateBySubjectDn("C=US,O=Google Trust Services LLC,CN=GTS Root R4")
        val trustedIssuerCertificates = context.findCertificateBySubjectDn(x509Certificate.data.issuerDn)
        println("found: ${found.size}")
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
            if (issuerCertificate.fingerprintSha256 != certificate.fingerprintSha256) {
                context.addLogEntry(
                    ValidationResult.Severity.ERROR,
                    "Fingerprint of trusted self signed certificate '${issuerCertificate.fingerprintSha256Hex}' " +
                            "not equal to fingerprint '${certificate.fingerprintSha256Hex}' of certificate to be validated in chain"
                )
            }
        } else {
            val publicKeyAlgorithm = issuerCertificate.data.subjectPublicKeyInfo.algorithmName
            val signatureAlgorithmName = certificate.signatureAlgorithmName
            if (signatureValidator.validateCertificateSignature(
                    issuerCertificate.data.subjectPublicKeyInfo,
                    certificate
                )
            ) {
                context.addTrustedCertificate(certificate)
                context.addLogEntry(
                    ValidationResult.Severity.INFO,
                    "(${signatureValidator.name}) Certificate Signature valid: ${publicKeyAlgorithm} / ${signatureAlgorithmName}"
                )
            } else {
                context.addLogEntry(
                    ValidationResult.Severity.ERROR,
                    "(${signatureValidator.name}) Certificate Signature not valid: ${publicKeyAlgorithm} / ${signatureAlgorithmName}"
                )
            }
        }
    }

    companion object {
        const val ID = "certificateSignature"
    }
}