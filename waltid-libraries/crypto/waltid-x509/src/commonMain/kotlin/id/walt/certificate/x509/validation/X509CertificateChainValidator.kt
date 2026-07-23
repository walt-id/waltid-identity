package id.walt.certificate.x509.validation

import id.walt.certificate.x509.X509Certificate
import id.walt.certificate.x509.X509CertificateTrustStore
import id.walt.certificate.x509.truststore.CompositeTrustStore
import id.walt.certificate.x509.validation.validator.X509CertificateValidator

class X509CertificateChainValidator(
    val validators: List<X509CertificateValidator>,
    val trustStore: X509CertificateTrustStore
) {

    suspend fun validate(
        certificateChain: Collection<X509Certificate>,
        additionalTrust: X509CertificateTrustStore? = null
    ): ValidationResult {
        trustStore.findCertificateBySubjectDn("second attempt")
        val trustStoreToUse =
            additionalTrust?.let { CompositeTrustStore(listOf(it, trustStore)) } ?: trustStore
        trustStoreToUse.findCertificateBySubjectDn("third attempt")
        val chain = X509CertificateChain.of(trustStoreToUse, certificateChain)
        val context = ValidationContext(trustStoreToUse)
        context.findCertificateBySubjectDn("4th attempt")
        for (i in 0..<chain.size) {
            validators.forEach { validator ->
                val certificate = chain[i]
                context.setCurrent(validator.id, i, certificate.data.subjectDn)
                validator.validate(context, certificate)
                context.addLogEntry(ValidationResult.Severity.INFO, "DONE")
            }
        }
        return ValidationResult(
            context.valid,
            context.log
        )
    }
}