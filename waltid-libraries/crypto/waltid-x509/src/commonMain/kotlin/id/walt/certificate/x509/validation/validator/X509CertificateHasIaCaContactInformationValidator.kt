package id.walt.certificate.x509.validation.validator

import id.walt.certificate.x509.X509Certificate
import id.walt.certificate.x509.extension.IssuerAlternativeNameExtension.Companion.extensionIssuerAltName
import id.walt.certificate.x509.model.GeneralName
import id.walt.certificate.x509.validation.ValidationContext
import id.walt.certificate.x509.validation.ValidationResult

/**
 * Validation rule for the IACA contact information carried in the Issuer Alternative Name (IAN) extension
 * (OID 2.5.29.18, RFC 5280 section 4.2.1.7), as required by the ISO/IEC 18013-5 certificate profiles:
 *
 * Contact Information: The certificate must carry an IAN extension with at least one non-empty
 * `rfc822Name` (email) or `uniformResourceIdentifier` (URI) entry. Other name types (e.g. DNS names or
 * IP addresses) do not count as contact information.
 *
 * If the contact information is missing (no IAN extension, or no qualifying entry), a single log entry is added:
 * - severity ERROR if [contactInformationIsMandatory] is `true`, which makes the validation fail,
 * - severity WARNING otherwise, which keeps the validation result valid.
 *
 * Nothing is reported if the contact information is present. The check applies to every certificate it is run
 * against; the contact information in the IAN extension describes the issuer of that certificate.
 *
 * @property contactInformationIsMandatory whether missing contact information is an error (`true`) or only a
 * warning (`false`)
 */
class X509CertificateHasIaCaContactInformationValidator(val contactInformationIsMandatory: Boolean) :
    X509CertificateValidator {

    /** Creates a validator that only warns about missing contact information. */
    constructor() : this(false)

    override val id: String = ID

    override suspend fun validate(
        context: ValidationContext,
        x509Certificate: X509Certificate
    ) {
        val contactInfoInCert =
            x509Certificate.data.extensionIssuerAltName?.let { issuerAltNameExtension ->
                issuerAltNameExtension.alternativeNames.any {
                    it.value.isNotEmpty() &&
                            (it.type == GeneralName.NameType.rfc822Name ||
                                    it.type == GeneralName.NameType.uniformResourceIdentifier)
                }
            } ?: false
        if (!contactInfoInCert) {
            val severity = if (contactInformationIsMandatory) {
                ValidationResult.Severity.ERROR
            } else {
                ValidationResult.Severity.WARNING
            }
            context.addLogEntry(
                severity,
                "Certificate doesn't contain required IACA contact information (IssuerAltName extension containing email or URI)"
            )
        }
    }

    companion object {
        val ID = "iacaContactInformation"
    }
}