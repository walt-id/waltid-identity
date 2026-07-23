package id.walt.certificate.x509

import id.walt.certificate.x509.CustomX509CertificateServices.Companion.custom
import id.walt.certificate.x509.testdata.TestDataCertificates.googleComCrtPem
import id.walt.certificate.x509.testdata.TestDataCertificates.gtsRootR4CrtPem
import id.walt.certificate.x509.testdata.TestDataCertificates.gtsWe2CrtPem
import id.walt.certificate.x509.truststore.InMemoryTrustStore
import id.walt.certificate.x509.validation.ValidationResult
import id.walt.certificate.x509.validation.validator.X509CertificateSignatureValidator
import kotlinx.coroutines.test.runTest
import kotlin.test.*

class X509CertificateChainValidationTest {

    @Test
    fun shouldFindMissingRootCertificateErrorChain() = runTest {
        val certificatePem = googleComCrtPem
        assertNotNull(certificatePem)
        val result = validatePemCertificateChain(certificatePem)
        assertFalse(result.valid)
        result.log.filter {
            it.validatorId == X509CertificateSignatureValidator.ID
                    && it.severity == ValidationResult.Severity.ERROR
        }
            .also { signatureValidatorLog ->
                assertEquals(1, signatureValidatorLog.size)
                assertEquals(ValidationResult.Severity.ERROR, signatureValidatorLog[0].severity)
                assertEquals("CN=*.google.com", signatureValidatorLog[0].subjectDn)
                assertEquals(
                    "Trusted issuer certificate 'C=US,O=Google Trust Services,CN=WE2' not found",
                    signatureValidatorLog[0].message
                )
            }
    }


    @Ignore //Does not work on JS TODO: fix it
    @Test
    fun shouldValidateGoogleCertificateChainWithOneEntry() = runTest {
        val result = validatePemCertificateChain(gtsWe2CrtPem)
        if (!result.valid) {
            result.log
                .filter { it.severity == ValidationResult.Severity.ERROR }
                .forEach { println("${it.validatorId}: ${it.subjectDn} - ${it.message}") }
        }
        assertTrue(result.valid)
        result.log.filter { it.validatorId == X509CertificateSignatureValidator.ID }
            .also { signatureValidatorLog ->
                assertEquals(2, signatureValidatorLog.size)
                assertEquals(ValidationResult.Severity.INFO, signatureValidatorLog[0].severity)
                assertEquals("C=US,O=Google Trust Services,CN=WE2", signatureValidatorLog[0].subjectDn)
            }
    }

    @Ignore //Does not work on JS TODO: fix it
    @Test
    fun shouldValidateGoogleCertificateChainWithTwoEntries() = runTest {
        val certificatePem = listOf(
            googleComCrtPem,
            gtsWe2CrtPem,
            googleComCrtPem,
            gtsWe2CrtPem,
        ).joinToString("\n")
        val result = validatePemCertificateChain(certificatePem)
        assertTrue(result.valid)
        result.log.filter { it.validatorId == X509CertificateSignatureValidator.ID }
            .also { signatureValidatorLog ->
                assertEquals(4, signatureValidatorLog.size)
                assertEquals(ValidationResult.Severity.INFO, signatureValidatorLog[0].severity)
                assertEquals("C=US,O=Google Trust Services,CN=WE2", signatureValidatorLog[0].subjectDn)
                assertEquals(ValidationResult.Severity.INFO, signatureValidatorLog[2].severity)
                assertEquals("CN=*.google.com", signatureValidatorLog[2].subjectDn)
            }
    }

    companion object {

        suspend fun validatePemCertificateChain(
            certificateChainPem: String,
            additionalTrust: X509CertificateTrustStore? = null
        ) = X509CertificateUtil.validatePemCertificateChain(services, certificateChainPem, additionalTrust)

        val trustStore = InMemoryTrustStore(
            listOf(gtsRootR4CrtPem)
                .map { X509CertificateUtil.parseCertificatePem(it) })

        /**
         * Trust store with Google Trust Services root certificate
         * and without a system trust store to ensure the same behavior in JS and JVM
         */
        val services = X509CertificateUtilDefaults.custom(
            trustStore = trustStore
        )
    }
}