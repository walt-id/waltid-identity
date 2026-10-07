package id.walt.certificate.x509.validation.validator

import id.walt.certificate.x509.X509Certificate
import id.walt.certificate.x509.extension.AuthorityKeyIdentifierExtension.Companion.extensionAuthorityKeyIdentifier
import id.walt.certificate.x509.extension.SubjectKeyIdentifierExtension.Companion.extensionSubjectKeyIdentifier
import id.walt.certificate.x509.validation.ValidationContext
import id.walt.certificate.x509.validation.ValidationResult

/**
 * Validation rules for the Authority Key Identifier (AKI) extension (OID 2.5.29.35, RFC 5280 section 4.2.1.1):
 *
 * Key Identifier Match: If the certificate carries an AKI extension, its `keyIdentifier` must equal the
 * Subject Key Identifier (SKI) of the issuer certificate. The issuer certificate is looked up in the
 * validation context (trust store plus the certificates of the chain already added to it) by matching
 * the certificate's issuer DN against the candidate's subject DN.
 *
 * The check is deliberately lenient and only reports a mismatch (severity ERROR) when both sides can be compared.
 * Nothing is reported if:
 * - the certificate has no AKI extension (the extension is not required by this validator),
 * - no issuer certificate can be found for the issuer DN (chain building/trust is not judged here), or
 * - the issuer certificate has no SKI extension.
 *
 * Only the `keyIdentifier` field is compared; `authorityCertIssuer` and `authorityCertSerialNumber` are ignored.
 * A mismatch is not a signature failure; the signature is checked separately by [X509CertificateSignatureValidator].
 *
 * @throws IllegalArgumentException (via `require`) if more than one certificate matches the issuer DN,
 * because selecting the right issuer among several candidates is not supported.
 */
class X509CertificateAuthorityKeyIdValidator
    : X509CertificateValidator {

    override val id: String = ID

    override suspend fun validate(
        context: ValidationContext,
        x509Certificate: X509Certificate
    ) {
        x509Certificate.data.extensionAuthorityKeyIdentifier?.also { aki ->
            val issuerCerts = context.findCertificateBySubjectDn(x509Certificate.data.issuerDn)
            if (!issuerCerts.isEmpty()) {
                require(issuerCerts.size == 1) { "Multiple possible issuer certificates is not supported (subjectDn='${x509Certificate.data.issuerDn}')" }
                val issuerCert = issuerCerts.first()
                issuerCert.data.extensionSubjectKeyIdentifier?.also { issuerSki ->
                    if (aki.keyIdentifier != issuerSki.keyIdentifier) {
                        context.addLogEntry(
                            ValidationResult.Severity.ERROR,
                            "The certificate's authority key identifier doesn't match the subject key identifier of its issuer certificate (subjectDn='${x509Certificate.data.issuerDn}')"
                        )
                    }
                }
            }
        }
    }

    companion object {
        const val ID = "authority-key-id"
    }
}