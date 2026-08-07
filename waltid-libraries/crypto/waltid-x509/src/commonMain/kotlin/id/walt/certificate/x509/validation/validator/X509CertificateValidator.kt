package id.walt.certificate.x509.validation.validator

import id.walt.certificate.x509.X509Certificate
import id.walt.certificate.x509.validation.ValidationContext

interface X509CertificateValidator {

    val id: String

    suspend fun validate(context: ValidationContext, x509Certificate: X509Certificate)

}