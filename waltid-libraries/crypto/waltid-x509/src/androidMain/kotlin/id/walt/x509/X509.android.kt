package id.walt.x509

import id.walt.certificate.x509.X509CertificateUtil
import id.walt.certificate.x509.truststore.InMemoryTrustStore
import id.walt.certificate.x509.validation.ValidationResult
import kotlinx.coroutines.runBlocking

actual val platformSupportsPkixCertificatePathValidation: Boolean = true

@Throws(exceptionClasses = [X509ValidationException::class])
actual fun validateCertificateChain(
    leaf: CertificateDer,
    chain: List<CertificateDer>,
    trustAnchors: List<CertificateDer>?,
    enableTrustedChainRoot: Boolean,
    enableSystemTrustAnchors: Boolean,
    enableRevocation: Boolean
) {
    val leafCert = X509CertificateUtil.parseCertificateDerEncoded(leaf.bytes)
    val chainCerts = chain.map { X509CertificateUtil.parseCertificateDerEncoded(it.bytes) }
    val trustStore = trustAnchors?.map { X509CertificateUtil.parseCertificateDerEncoded(it.bytes) }
        ?.let { InMemoryTrustStore(it) }

    val validationResult = runBlocking {
        X509CertificateUtil.validateCertificateChain(
            chainCerts + leafCert,
            trustStore
        )
    }
    if (!validationResult.valid) {
        val errorMessage = validationResult.log
            .filter { it.severity == ValidationResult.Severity.ERROR }
            .map { "(${it.validatorId})'${it.subjectDn}':${it.message}" }
            .reduce { acc, string -> acc + "\n" + string }
        throw X509ValidationException(errorMessage)
    }
}