package id.walt.mdoc.proximity

import id.walt.certificate.x509.X509Certificate
import id.walt.certificate.x509.X509CertificateUtil
import id.walt.certificate.x509.profile.IsoMdocRicalSignerX509CertificateProfile
import id.walt.certificate.x509.validation.ValidationContext
import id.walt.certificate.x509.validation.validator.X509CertificateValidator
import id.walt.certificate.x509.validation.validator.X509CertificateValidityValidator
import kotlin.time.Clock

object MdocX509CertificateUtil {

    /**
     * creates a X509CertificateUtil for validation of a certificate chain with
     * RICAL (Reader Identity Certificate Authority List) Signer Certificate as leaf.
     * Trust needs to be provided.
     */
    fun mdocRicalSignerCertificateUtil(
        acceptedCertificatePolicyOids: Collection<String>,
        clock: Clock
    ): X509CertificateUtil =
        X509CertificateUtil {
            addValidators(
                X509CertificateValidityValidator(allowValidityInFuture = false, clock = clock),
                object : X509CertificateValidator {
                    override val id: String = IsoMdocRicalSignerX509CertificateProfile.ID
                    private val delegate =
                        IsoMdocRicalSignerX509CertificateProfile(acceptedCertificatePolicyOids.toSet())

                    override suspend fun accepts(
                        context: ValidationContext,
                        x509Certificate: X509Certificate
                    ): Boolean = context.isLeaf

                    override suspend fun validate(
                        context: ValidationContext,
                        x509Certificate: X509Certificate
                    ) =
                        delegate.validate(
                            context,
                            x509Certificate
                        )
                })
        }
}