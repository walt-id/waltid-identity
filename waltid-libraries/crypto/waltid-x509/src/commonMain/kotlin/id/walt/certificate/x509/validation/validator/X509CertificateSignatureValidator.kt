package id.walt.certificate.x509.validation.validator

import id.walt.certificate.x509.SignatureValidator
import id.walt.certificate.x509.X509Certificate
import id.walt.certificate.x509.validation.IssuerSelection
import id.walt.certificate.x509.validation.ValidationContext
import id.walt.certificate.x509.validation.ValidationResult

/**
 * Validates that each certificate is signed by a trusted issuer certificate.
 *
 * The issuer is looked up by the certificate's issuer DN. If several trusted certificates share that
 * DN (e.g. two CAs of a key rollover) the issuer is selected by key, see [ValidationContext.selectIssuer]:
 * a certificate chaining to either CA validates, one that matches none fails, and one that more than
 * one candidate would validate fails closed.
 */
class X509CertificateSignatureValidator(
    val signatureValidator: SignatureValidator
) : X509CertificateValidator {

    override val id: String = ID

    override suspend fun validate(
        context: ValidationContext,
        x509Certificate: X509Certificate
    ) {
        when (val selection = context.selectIssuer(x509Certificate)) {
            is IssuerSelection.NotFound -> {
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
                        "Trusted issuer certificate '${x509Certificate.data.issuerDn}' not found"
                    )
                }
            }

            is IssuerSelection.Selected -> validateCertificate(context, selection.issuer, x509Certificate)

            is IssuerSelection.NoMatch ->
                if (x509Certificate.data.subjectDn == x509Certificate.data.issuerDn) {
                    context.addLogEntry(
                        ValidationResult.Severity.ERROR,
                        "Fingerprint '${x509Certificate.fingerprintSha256Hex}' of certificate to be validated in chain " +
                                "is not equal to the fingerprint of any of the ${selection.candidates.size} trusted self signed " +
                                "certificates with subjectDn '${x509Certificate.data.issuerDn}'"
                    )
                } else {
                    context.addLogEntry(
                        ValidationResult.Severity.ERROR,
                        "(${signatureValidator.name}) Certificate Signature not valid: none of the " +
                                "${selection.candidates.size} trusted certificates with subjectDn " +
                                "'${x509Certificate.data.issuerDn}' is the issuer"
                    )
                }

            is IssuerSelection.Ambiguous -> context.addLogEntry(
                ValidationResult.Severity.ERROR,
                "Multiple trusted certificates with subjectDn '${x509Certificate.data.issuerDn}' qualify as issuer " +
                        "(fingerprints ${selection.candidates.joinToString { it.fingerprintSha256Hex }}). " +
                        "Refusing to select one"
            )
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
