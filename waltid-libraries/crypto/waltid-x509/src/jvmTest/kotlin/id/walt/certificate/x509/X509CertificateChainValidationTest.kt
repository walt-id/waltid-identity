package id.walt.certificate.x509

import id.walt.certificate.x509.validation.ValidationResult
import id.walt.certificate.x509.validation.validator.X509CertificateSignatureValidator
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class X509CertificateChainValidationTest {

    @Test
    fun shouldFindMissingRootCertificateErrorChain() = runTest {
        val certificatePem = ResourceUtil.loadClassPathResourceAsString("certificates/_.google.com.cert.pem")
        assertNotNull(certificatePem)
        val result = X509CertificateUtil.validatePemCertificateChain(certificatePem)
        assertFalse(result.valid)
        result.log.filter { it.validatorId == X509CertificateSignatureValidator.ID }
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


    @Test
    fun shouldValidateGoogleCertificateChainWithOneEntry() = runTest {
        val certificatePem = ResourceUtil.loadClassPathResourceAsString("certificates/gts-we2.cert.pem")
        assertNotNull(certificatePem)
        val result = X509CertificateUtil.validatePemCertificateChain(certificatePem)
        assertTrue(result.valid)
        result.log.filter { it.validatorId == X509CertificateSignatureValidator.ID }
            .also { signatureValidatorLog ->
                assertEquals(1, signatureValidatorLog.size)
                assertEquals(ValidationResult.Severity.INFO, signatureValidatorLog[0].severity)
                assertEquals("C=US,O=Google Trust Services,CN=WE2", signatureValidatorLog[0].subjectDn)
            }
    }

    @Test
    fun shouldValidateGoogleCertificateChainWithTwoEntries() = runTest {
        val certificatePem = listOf(
            ResourceUtil.loadClassPathResourceAsString("certificates/_.google.com.cert.pem"),
            ResourceUtil.loadClassPathResourceAsString("certificates/gts-we2.cert.pem"),
            ResourceUtil.loadClassPathResourceAsString("certificates/_.google.com.cert.pem"),
            ResourceUtil.loadClassPathResourceAsString("certificates/gts-we2.cert.pem")
        ).joinToString("\n")
        assertNotNull(certificatePem)
        val result = X509CertificateUtil.validatePemCertificateChain(certificatePem)
        assertTrue(result.valid)
        result.log.filter { it.validatorId == X509CertificateSignatureValidator.ID }
            .also { signatureValidatorLog ->
                assertEquals(2, signatureValidatorLog.size)
                assertEquals(ValidationResult.Severity.INFO, signatureValidatorLog[0].severity)
                assertEquals("C=US,O=Google Trust Services,CN=WE2", signatureValidatorLog[0].subjectDn)
                assertEquals(ValidationResult.Severity.INFO, signatureValidatorLog[1].severity)
                assertEquals("CN=*.google.com", signatureValidatorLog[1].subjectDn)
            }
    }
}