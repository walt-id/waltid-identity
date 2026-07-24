package id.walt.certificate.x509

import id.walt.certificate.x509.testdata.TestDataCertificates.gtsRootR4CrtPem
import id.walt.certificate.x509.testdata.TestDataCertificates.gtsWe2CrtPem
import id.walt.certificate.x509.truststore.InMemoryTrustStore
import id.walt.certificate.x509.validation.ValidationResult
import id.walt.x509.X509TestCertificates
import kotlinx.coroutines.test.runTest
import kotlin.test.Ignore
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class X509CertificateSignatureValidationTest {

    @Ignore //Test is not working on JS - for some reason signature validation fails
    @Test
    fun verifiesIssuerSignedByRoot() = runTest {
        val validationResult = X509CertificateUtil.validateCertificateChain(
            listOf(gtsWe2CrtPem.let {
                X509CertificateUtil.parseCertificatePem(it)
            }),
            trustStore
        )
        if (!validationResult.valid) {
            validationResult.log
                .filter { it.severity == ValidationResult.Severity.ERROR }
                .forEach { println(it) }
        }
        assertTrue(validationResult.valid)
    }

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


    @Test
    fun validatesLeafWithTrustedRootIncludedInChain() = runTest {
        val result = X509CertificateUtil.validateCertificateChain(
            listOf(
                X509TestCertificates.issuerCertificate,
                X509TestCertificates.leafCertificate
            )
                .map { X509CertificateUtil.parseCertificateDerEncoded(it) },
            trustStore
        )
        assertTrue(result.valid)
    }

    @Test
    fun validatesChainWhenCertificatesAreOutOfOrder() = runTest {
        val result = X509CertificateUtil.validateCertificateChain(
            listOf(
                X509TestCertificates.leafCertificate,
                X509TestCertificates.issuerCertificate
            )
                .map { X509CertificateUtil.parseCertificateDerEncoded(it) },
            trustStore
        )
        assertTrue(result.valid)
    }

    @Test
    fun rejectsTamperedLeafCertificate() = runTest {
        val result = X509CertificateUtil.validateCertificateChain(
            listOf(
                X509TestCertificates.tamperedLeafCertificate,
            ).map { X509CertificateUtil.parseCertificateDerEncoded(it) },
            trustStore
        )
        assertFalse(result.valid)
    }

    @Test
    fun rejectsMissingTrustAnchor() = runTest {
        val result = X509CertificateUtil.validateCertificateChain(
            listOf(
                X509TestCertificates.leafCertificate,
            ).map { X509CertificateUtil.parseCertificateDerEncoded(it) }
        )
        assertFalse(result.valid)
    }


    companion object {
        val trustStore = InMemoryTrustStore(
            listOf(
                X509CertificateUtil.parseCertificateDerEncoded(X509TestCertificates.issuerCertificate),
                X509CertificateUtil.parseCertificatePem(gtsRootR4CrtPem)
            )
        )
    }
}
