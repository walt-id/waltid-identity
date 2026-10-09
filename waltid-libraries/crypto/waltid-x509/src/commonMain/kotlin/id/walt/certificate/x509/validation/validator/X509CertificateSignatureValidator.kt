package id.walt.certificate.x509.validation.validator

import id.walt.certificate.x509.SignatureValidator
import id.walt.certificate.x509.X509Certificate
import id.walt.certificate.x509.extension.AuthorityKeyIdentifierExtension.Companion.extensionAuthorityKeyIdentifier
import id.walt.certificate.x509.extension.SubjectKeyIdentifierExtension.Companion.extensionSubjectKeyIdentifier
import id.walt.certificate.x509.validation.ValidationContext
import id.walt.certificate.x509.validation.ValidationResult
import kotlinx.io.bytestring.toHexString

class X509CertificateSignatureValidator(
    private val signatureValidator: SignatureValidator
) : X509CertificateValidator {

    override val id: String = ID

    override suspend fun validate(
        context: ValidationContext,
        x509Certificate: X509Certificate
    ) {
        val trustedIssuerCertificates =
            context.findCertificate(
                x509Certificate.data.issuerDn,
                x509Certificate.data.extensionAuthorityKeyIdentifier?.keyIdentifier
            )
        if (trustedIssuerCertificates.isEmpty()) {
            // issuer is not trusted, check if this certificate is trusted
            val isCertificateTrusted =
                context.findCertificateBySubjectDn(x509Certificate.data.subjectDn).any {
                    it.encodedDer == x509Certificate.encodedDer
                }
            if (isCertificateTrusted) {
                context.addLogEntry(
                    ValidationResult.Severity.INFO,
                    "Certificate in chain with subjectDn '${x509Certificate.data.issuerDn}' is trusted. Issuer DN '${x509Certificate.data.issuerDn}' not found in trust"
                )
            } else {
                context.addLogEntry(
                    ValidationResult.Severity.ERROR,
                    "Trusted issuer certificate '${x509Certificate.data.issuerDn}' (SKI: '${x509Certificate.data.extensionSubjectKeyIdentifier?.keyIdentifier?.toHexString() ?: "NULL"}') not found"
                )
            }
        } else {
            if (trustedIssuerCertificates.size > 1) {
                context.addLogEntry(
                    ValidationResult.Severity.ERROR,
                    "Multiple trusted certificates with subjectDn '${x509Certificate.data.issuerDn}' " +
                            "and SKI '${x509Certificate.data.extensionAuthorityKeyIdentifier?.keyIdentifier?.toHexString() ?: "NULL"}' qualify as issuer " +
                            "(fingerprints ${trustedIssuerCertificates.joinToString { it.fingerprintSha256Hex }}). " +
                            "Refusing to select one"
                )
            } else {
                validateCertificate(context, trustedIssuerCertificates.first(), x509Certificate)
            }
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
                    context.cryptoRuntime,
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
