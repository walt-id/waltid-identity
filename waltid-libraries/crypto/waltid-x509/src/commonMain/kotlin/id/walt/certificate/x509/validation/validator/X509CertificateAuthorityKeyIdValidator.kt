package id.walt.certificate.x509.validation.validator

import id.walt.certificate.x509.X509Certificate
import id.walt.certificate.x509.extension.AuthorityKeyIdentifierExtension.Companion.extensionAuthorityKeyIdentifier
import id.walt.certificate.x509.extension.SubjectKeyIdentifierExtension.Companion.extensionSubjectKeyIdentifier
import id.walt.certificate.x509.validation.IssuerSelection
import id.walt.certificate.x509.validation.ValidationContext
import id.walt.certificate.x509.validation.ValidationResult

/**
 * Validation rules for the Authority Key Identifier (AKI) extension (OID 2.5.29.35, RFC 5280 section 4.2.1.1):
 *
 * Key Identifier Match: If the certificate carries an AKI extension, its `keyIdentifier` must equal the
 * Subject Key Identifier (SKI) of the issuer certificate. The issuer certificate is the one selected by
 * [ValidationContext.selectIssuer] in the validation context (trust store plus the certificates of the
 * chain already added to it): candidates are found by matching the certificate's issuer DN against their
 * subject DN and, if several share that DN, told apart by key.
 *
 * The check is deliberately lenient and only reports a mismatch (severity ERROR) when both sides can be compared.
 * Nothing is reported if:
 * - the certificate has no AKI extension (the extension is not required by this validator),
 * - no single issuer certificate can be selected for the issuer DN (not found, none or more than one
 *   candidate verifies; chain building/trust is not judged here, the signature validator reports it), or
 * - the issuer certificate has no SKI extension.
 *
 * Only the `keyIdentifier` field is compared; `authorityCertIssuer` and `authorityCertSerialNumber` are ignored.
 * A mismatch is not a signature failure; the signature is checked separately by [X509CertificateSignatureValidator].
 */
class X509CertificateAuthorityKeyIdValidator
    : X509CertificateValidator {

    override val id: String = ID

    override suspend fun validate(
        context: ValidationContext,
        x509Certificate: X509Certificate
    ) {
        x509Certificate.data.extensionAuthorityKeyIdentifier?.also { aki ->
            val selection = context.selectIssuer(x509Certificate)
            if (selection is IssuerSelection.Selected) {
                selection.issuer.data.extensionSubjectKeyIdentifier?.also { issuerSki ->
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