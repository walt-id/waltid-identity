package id.walt.certificate.x509.validation

import id.walt.certificate.x509.X509Certificate
import id.walt.certificate.x509.X509CertificateTrustStore
import id.walt.certificate.x509.truststore.CompositeTrustStore
import id.walt.certificate.x509.validation.validator.X509CertificateValidator
import id.walt.crypto2.CryptoRuntime

class X509CertificateChainValidator(
    val validators: Collection<X509CertificateValidator>,
    val trustStore: X509CertificateTrustStore
) {

    suspend fun validate(
        cryptoRuntime: CryptoRuntime,
        certificateChain: Collection<X509Certificate>,
        additionalTrust: X509CertificateTrustStore? = null
    ): ValidationResult {
        val trustStoreToUse =
            additionalTrust?.let { CompositeTrustStore(listOf(it, trustStore)) } ?: trustStore
        val chain = X509CertificateChain.of(certificateChain)
        val context = ValidationContext(cryptoRuntime, chain.size, trustStoreToUse)
        for (i in 0..<chain.size) {
            validators.forEach { validator ->
                val certificate = chain[i]
                if (validator.accepts(context, certificate)) {
                    context.setCurrent(validator.id, i, certificate.data.subjectDn)
                    validator.validate(context, certificate)
                    context.addLogEntry(ValidationResult.Severity.INFO, "DONE")
                }
            }
        }
        return ValidationResult(
            context.valid,
            context.log
        )
    }
}