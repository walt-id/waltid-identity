package id.walt.mdoc.readertrust

import id.walt.certificate.x509.X509Certificate
import id.walt.certificate.x509.X509CertificateUtil
import id.walt.certificate.x509.profile.IsoMdocReaderAuthenticationX509CertificateProfile
import id.walt.certificate.x509.validation.ValidationContext
import id.walt.certificate.x509.validation.ValidationResult
import id.walt.certificate.x509.validation.validator.X509CertificateValidator
import id.walt.certificate.x509.validation.validator.X509CertificateValidityValidator
import kotlin.time.Clock

object MdocReaderAuthenticationCertificateUtil {

    /**
     * creates a X509CertificateUtil for validation of a certificate chain with
     * Mdoc Reader Certificate as leaf. Trust needs to be provided, can be RICAL
     * or other configured trust
     */
    fun mdocReaderAuthentication(clock: Clock): X509CertificateUtil =
        X509CertificateUtil {
            addValidators(
                X509CertificateValidityValidator(allowValidityInFuture = false, clock = clock),
                object : X509CertificateValidator {
                    override val id: String = IsoMdocReaderAuthenticationX509CertificateProfile.ID

                    override suspend fun accepts(
                        context: ValidationContext,
                        x509Certificate: X509Certificate
                    ): Boolean = context.isLeaf

                    override suspend fun validate(
                        context: ValidationContext,
                        x509Certificate: X509Certificate
                    ) {
                        IsoMdocReaderAuthenticationX509CertificateProfile.validate(
                            context,
                            x509Certificate
                        )
                        validateChainLength(context, x509Certificate)
                    }

                    private fun validateChainLength(
                        context: ValidationContext,
                        x509Certificate: X509Certificate
                    ) {
                        if (context.chainLength < 2) {
                            val trustedIssuers =
                                context.findCertificateBySubjectDn(x509Certificate.data.issuerDn)
                            if (trustedIssuers.isEmpty()) {
                                context.addLogEntry(
                                    ValidationResult.Severity.ERROR, "chain-length",
                                    "A reader end-entity certificate cannot be its own trust anchor"
                                )
                            }
                        }
                    }
                })
        }
}
