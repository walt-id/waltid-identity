package id.walt.openid4vp.clientidprefix.prefixes

import id.walt.certificate.x509.X509Certificate
import id.walt.certificate.x509.extension.ExtendedKeyUsageExtension
import id.walt.certificate.x509.extension.ExtendedKeyUsageExtension.Companion.extensionExtendedKeyUsage
import id.walt.certificate.x509.extension.KeyUsageExtension.Companion.extensionKeyUsage
import id.walt.certificate.x509.extension.KeyUsageExtension.KeyUsage
import id.walt.certificate.x509.validation.ValidationContext
import id.walt.certificate.x509.validation.ValidationResult
import id.walt.certificate.x509.validation.validator.X509CertificateValidator

class AttestationX509CertificateValidator : X509CertificateValidator {

    override val id: String = Companion.id

    override suspend fun accepts(
        context: ValidationContext,
        x509Certificate: X509Certificate
    ): Boolean = context.isLeaf

    override suspend fun validate(
        context: ValidationContext,
        x509Certificate: X509Certificate
    ) {

        // RFC 5280 §4.2.1.3: KeyUsage restricts the key only when the extension is present.
        val keyUsage = x509Certificate.data.extensionKeyUsage
        if (keyUsage != null && KeyUsage.digitalSignature !in keyUsage.keyPurposeIdList) {
            context.addLogEntry(
                ValidationResult.Severity.ERROR,
                "Certificate does not contain client Key Usage 'digitalSignature'"
            )
        }

        // RFC 5280 §4.2.1.12: ExtendedKeyUsage is unrestricted when absent. When present it must
        // include clientAuth for this Request Object signing profile.
        val extendedKeyUsage = x509Certificate.data.extensionExtendedKeyUsage
        if (extendedKeyUsage != null &&
            ExtendedKeyUsageExtension.KeyUsage.clientAuth !in extendedKeyUsage.keyPurposeList
        ) {
            context.addLogEntry(
                ValidationResult.Severity.ERROR,
                "Certificate does not contain client auth Extended Key Usage (OID: '${ExtendedKeyUsageExtension.KeyUsage.clientAuth.id}')"
            )
        }
    }

    companion object {
        const val id = "attestation-leaf"
    }
}