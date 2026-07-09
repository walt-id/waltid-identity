package id.walt.certificate.x509.validation

import id.walt.certificate.x509.X509Certificate
import id.walt.certificate.x509.X509CertificateTrustStore
import id.walt.certificate.x509.validation.validator.X509CertificateValidator

class X509CertificateChainValidator(
    private val validators: List<X509CertificateValidator>,
    private val trustStore: X509CertificateTrustStore
) {

    suspend fun validate(certificateChain: Collection<X509Certificate>): ValidationResult {
        val chain = X509CertificateChain.of(trustStore, certificateChain)
        val context = ValidationContext(trustStore)
        for (i in 0..<chain.size) {
            validators.forEach { validator ->
                val certificate = chain[i]
                context.setCurrent(validator.id, i, certificate.data.subjectDn)
                validator.validate(context, certificate)
            }
        }
        return ValidationResult(
            context.valid,
            context.log
        )
    }
}