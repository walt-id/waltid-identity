package id.walt.certificate.x509.validation

import id.walt.certificate.x509.X509Certificate
import id.walt.certificate.x509.X509CertificateTrustStore
import id.walt.certificate.x509.truststore.InMemoryTrustStore
import id.walt.certificate.x509.validation.validator.X509CertificateValidator
import id.walt.crypto2.CryptoRuntime
import id.walt.crypto2.providers.cryptography.defaultSoftwareKeyProviders

class X509SingleCertificateValidator(
    private val validators: List<X509CertificateValidator>,
    private val trustStore: X509CertificateTrustStore = InMemoryTrustStore(),
    private val cryptoRuntime: CryptoRuntime = CryptoRuntime(defaultSoftwareKeyProviders())
) {

    suspend fun validate(certificate: X509Certificate): ValidationResult {
        val context = ValidationContext(cryptoRuntime, trustStore)
        validators.forEach { validator ->
            context.setCurrent(validator.id, 0, certificate.data.subjectDn)
            validator.validate(context, certificate)
        }
        return ValidationResult(
            context.valid,
            context.log
        )
    }
}