package id.walt.certificate.x509

import id.walt.certificate.TestKeys.opensslHexFormat
import id.walt.certificate.x509.extension.AuthorityKeyIdentifierExtension.Companion.extensionAuthorityKeyIdentifier
import id.walt.certificate.x509.extension.SubjectAlternativeNameExtension.Companion.extensionSan
import id.walt.certificate.x509.extension.SubjectKeyIdentifierExtension.Companion.extensionSubjectKeyIdentifier
import id.walt.certificate.x509.truststore.InMemoryTrustStore
import id.walt.x509.X509TestCertificates
import kotlinx.coroutines.test.runTest
import kotlinx.io.bytestring.toHexString
import kotlin.test.*

class X509CertificateSignatureValidationTest {

    @Test
    fun verifiesLeafSignedByIssuer() = runTest {
        val validationResult = X509CertificateUtil.validateCertificateChain(
            listOf(X509TestCertificates.leafCertificate.let {
                X509CertificateUtil.parseCertificateDerEncoded(it)
            }),
            trustStore
        )
        assertTrue(validationResult.valid)
    }

    @Test
    fun rejectsLeafWithTamperedSignature() = runTest {

        val validationResult = X509CertificateUtil.validateCertificateChain(
            listOf(X509TestCertificates.tamperedLeafCertificate.let {
                X509CertificateUtil.parseCertificateDerEncoded(it)
            }),
            trustStore
        )
        assertFalse(validationResult.valid)
    }

    @Test
    fun rejectsIssuerSubjectMismatch() = runTest {
        val validationResult = X509CertificateUtil.validateCertificateChain(
            listOf(
                X509TestCertificates.issuerCertificate,
                X509TestCertificates.leafCertificate,
            ).map { X509CertificateUtil.parseCertificateDerEncoded(it) })
        assertFalse(validationResult.valid)
    }

    companion object {
        val trustStore = InMemoryTrustStore(listOf(X509TestCertificates.issuerCertificate.let {
            X509CertificateUtil.parseCertificateDerEncoded(it)
        }))
    }
}
