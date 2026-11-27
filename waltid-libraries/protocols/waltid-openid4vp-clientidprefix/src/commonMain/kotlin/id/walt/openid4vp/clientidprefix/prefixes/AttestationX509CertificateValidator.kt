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
        leafProfileViolations(x509Certificate).forEach { violation ->
            context.addLogEntry(ValidationResult.Severity.ERROR, violation)
        }
    }

    companion object {
        /** Why [certificate] cannot sign a verifier's request; empty when it can. Also used without PKIX, in registry mode. */
        internal fun leafProfileViolations(certificate: X509Certificate): List<String> = buildList {
            // RFC 5280 §4.2.1.3: KeyUsage restricts the key only when the extension is present.
            val keyUsage = certificate.data.extensionKeyUsage
            if (keyUsage != null && KeyUsage.digitalSignature !in keyUsage.keyPurposeIdList) {
                add("Certificate does not contain client Key Usage 'digitalSignature'")
            }

            // RFC 5280 §4.2.1.12: ExtendedKeyUsage is unrestricted when absent. When present, the
            // listed purposes must include one this wallet uses for a verifier: TLS clientAuth, or
            // ISO mdoc reader authentication. OpenID4VP authenticates the chain and client id; it
            // does not require the TLS clientAuth purpose.
            val extendedKeyUsage = certificate.data.extensionExtendedKeyUsage
            if (extendedKeyUsage != null &&
                extendedKeyUsage.keyPurposeIdList.none { it in acceptedVerifierExtendedKeyUsageOids }
            ) {
                add(
                    "Certificate Extended Key Usage does not include a verifier purpose " +
                        "(clientAuth ${ExtendedKeyUsageExtension.KeyUsage.clientAuth.id} or " +
                        "mdoc reader authentication $mdocReaderAuthenticationEkuOid)"
                )
            }
        }

        const val id = "attestation-leaf"

        /** ISO/IEC 18013-5 reader-authentication EKU. */
        const val mdocReaderAuthenticationEkuOid = "1.0.18013.5.1.6"

        /** ISO/IEC 23220-4 reader-authentication EKU. */
        const val mdocReaderAuthentication23220EkuOid = "1.0.23220.4.1.6"

        private val acceptedVerifierExtendedKeyUsageOids = setOf(
            ExtendedKeyUsageExtension.KeyUsage.anyExtendedKeyUsage.id,
            ExtendedKeyUsageExtension.KeyUsage.clientAuth.id,
            mdocReaderAuthenticationEkuOid,
            mdocReaderAuthentication23220EkuOid,
        )
    }
}