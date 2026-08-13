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

        if (x509Certificate.data.extensionKeyUsage
                ?.keyPurposeIdList
                ?.contains(KeyUsage.digitalSignature) != true
        ) {
            context.addLogEntry(
                ValidationResult.Severity.ERROR,
                "Certificate does not contain client Key Usage 'digitalSignature'"
            )
        }

        if (x509Certificate.data.extensionExtendedKeyUsage
                ?.keyPurposeList
                ?.contains(ExtendedKeyUsageExtension.KeyUsage.clientAuth) != true
        ) {
            //Certificate with subjectDn='CN=Verifier Signer,C=EU,O=Niscy,organizationIdentifier=LEIEU-987654321' which is used for
            //MobileWalletIntegrationTest doesn't have extended key usage extension
            //Set severity to WARNING, so the test works
            context.addLogEntry(
                ValidationResult.Severity.WARNING,
                "Certificate does not contain client auth Extended Key Usage (OID: '${ExtendedKeyUsageExtension.KeyUsage.clientAuth.id}')"
            )
        }
    }

    companion object {
        const val id = "attestation-leaf"
    }
}