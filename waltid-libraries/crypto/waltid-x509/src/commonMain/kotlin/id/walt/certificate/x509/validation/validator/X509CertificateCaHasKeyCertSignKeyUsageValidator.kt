package id.walt.certificate.x509.validation.validator

import id.walt.certificate.x509.X509Certificate
import id.walt.certificate.x509.extension.KeyUsageExtension
import id.walt.certificate.x509.extension.KeyUsageExtension.Companion.extensionKeyUsage
import id.walt.certificate.x509.extension.KeyUsageExtension.KeyUsage
import id.walt.certificate.x509.validation.ValidationContext
import id.walt.certificate.x509.validation.ValidationResult

/**
 * Verifies that every CA certificate in the chain (all but the leaf) that carries a key usage extension
 * asserts the `keyCertSign` bit, as required by RFC 5280 section 4.2.1.3 for certificates whose key is
 * used to verify signatures on certificates.
 *
 * If the key usage extension is absent, the key is not restricted and the certificate is accepted.
 *
 * Findings:
 * - ERROR: the key usage extension is present, but `keyCertSign` is not asserted.
 * - WARNING: the key usage extension is not marked critical (RFC 5280 section 4.2.1.3 says conforming CAs
 *   SHOULD mark it critical).
 */
class X509CertificateCaHasKeyCertSignKeyUsageValidator : X509CertificateValidator {

    override val id: String = ID

    override suspend fun accepts(
        context: ValidationContext,
        x509Certificate: X509Certificate
    ): Boolean = !context.isLeaf

    override suspend fun validate(
        context: ValidationContext,
        x509Certificate: X509Certificate
    ) {
        val keyUsage = x509Certificate.data.extensionKeyUsage ?: return

        if (KeyUsage.keyCertSign !in keyUsage.keyPurposeIdList) {
            context.addLogEntry(
                ValidationResult.Severity.ERROR,
                "Certificate extension '${KeyUsageExtension.OID}' ('${KeyUsageExtension.NAME}') of CA certificate with subject '${x509Certificate.data.subjectDn}' must contain '${KeyUsage.keyCertSign}'"
            )
        }
        if (!keyUsage.critical) {
            context.addLogEntry(
                ValidationResult.Severity.WARNING,
                "Certificate extension '${KeyUsageExtension.OID}' ('${KeyUsageExtension.NAME}') should have critical flag set"
            )
        }
    }

    companion object {
        const val ID = "caHasKeyCertSignKeyUsage"
    }
}
